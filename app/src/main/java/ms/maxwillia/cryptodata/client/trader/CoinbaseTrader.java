package ms.maxwillia.cryptodata.client.trader;

import java.io.IOException;
import java.net.URI;
import java.util.Map;
import java.util.HashMap;
import java.time.Instant;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;

import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.JWSSigner;
import com.nimbusds.jose.crypto.ECDSASigner;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import ms.maxwillia.cryptodata.client.ClientStatus;
import ms.maxwillia.cryptodata.config.ExchangeCredentials;
import ms.maxwillia.cryptodata.model.Transaction;
import ms.maxwillia.cryptodata.model.TransactionSide;
import ms.maxwillia.cryptodata.model.TransactionStatus;
import ms.maxwillia.cryptodata.model.TransactionType;
import okhttp3.*;
import org.jetbrains.annotations.Nullable;

import java.util.UUID;
import java.util.concurrent.TimeUnit;


public class CoinbaseTrader extends BaseExchangeTrader {
    private HttpUrl COINBASE_API_ROOT = HttpUrl.parse("https://api.coinbase.com");
    private final OkHttpClient httpClient;
    private final ObjectMapper objectMapper;

    public CoinbaseTrader(String currency, ExchangeCredentials credentials) {
        super("Coinbase", currency, credentials);
        this.httpClient = new OkHttpClient.Builder()
                .connectTimeout(10, TimeUnit.SECONDS)
                .readTimeout(10, TimeUnit.SECONDS)
                .writeTimeout(10, TimeUnit.SECONDS)
                .build();
        this.objectMapper = new ObjectMapper();
    }

    private static String getSchemelessURL(String url) {
        return url.substring(URI.create(url).getScheme().length() + 3);
    }

    @Override
    protected void setSymbolFromCurrency(String currency) {
        this.symbol = currency + "-USD";
    }

    @Override
    public String getExchangeSymbol() {
        return symbol;
    }

    public void setApiRoot(HttpUrl apiRoot) {
        this.COINBASE_API_ROOT = apiRoot;
    }

    @Override
    public boolean initialize() {
        setStatus(ClientStatus.STARTING);
        try {
            if (!configure()) {
                return false;
            }
            setStatus(ClientStatus.INITIALIZED);
            return true;
        } catch (Exception e) {
            logger.error("Failed to initialize Coinbase trader: {}", e.getMessage());
            setStatus(ClientStatus.ERROR);
            return false;
        }
    }

    protected String generateJWT(String requestMethod, String requestUrl) throws Exception {
        // create header object
        Map<String, Object> header = new HashMap<>();
        header.put("alg", "ES256");
        header.put("typ", "JWT");
        header.put("kid", credentials.getName());
        header.put("nonce", String.valueOf(Instant.now().getEpochSecond()));

        // create uri string for current request
        String uri = requestMethod + " " + requestUrl;

        // create data object
        Map<String, Object> data = new HashMap<>();
        data.put("iss", "cdp");
        data.put("nbf", Instant.now().getEpochSecond());
        data.put("exp", Instant.now().getEpochSecond() + 120);
        data.put("sub", credentials.getName());
        data.put("uri", uri);

        // create JWT
        JWTClaimsSet.Builder claimsSetBuilder = new JWTClaimsSet.Builder();
        for (Map.Entry<String, Object> entry : data.entrySet()) {
            claimsSetBuilder.claim(entry.getKey(), entry.getValue());
        }
        JWTClaimsSet claimsSet = claimsSetBuilder.build();

        JWSHeader jwsHeader = new JWSHeader.Builder(JWSAlgorithm.ES256).customParams(header).build();
        SignedJWT signedJWT = new SignedJWT(jwsHeader, claimsSet);

        JWSSigner signer = new ECDSASigner(ecPrivateKey);
        signedJWT.sign(signer);

        return signedJWT.serialize();
    }

    @Override
    public boolean connect() {
        if (isConnected) {
            return true;
        }

        try {
            HttpUrl url = COINBASE_API_ROOT.resolve("/api/v3/brokerage/accounts?limit=1");
            String jwt = generateJWT("GET", getSchemelessURL(url.toString()));
            Request request = new Request.Builder()
                    .url(url)
                    .header("Authorization", "Bearer " + jwt)
                    .build();
            try (Response response = httpClient.newCall(request).execute()) {
                if (!response.isSuccessful()) {
                    logger.error("Connection test failed with response code: {}", response.code());
                    setStatus(ClientStatus.ERROR);
                    return false;
                }
                isConnected = true;
                setStatus(ClientStatus.CONNECTED);
                return true;
            }
        } catch (Exception e) {
            logger.error("Failed to connect to Coinbase: {}", e.getMessage());
            setStatus(ClientStatus.ERROR);
            return false;
        }
    }

    @Override
    public void disconnect() {
        isConnected = false;
        setStatus(ClientStatus.STOPPED);
    }

    public Transaction marketBuy(double targetPrice, double quantity, String clientOrderId) {
        return executeOrder(TransactionType.MARKET, TransactionSide.BUY, targetPrice, quantity, clientOrderId);
    }

    @Override
    public Transaction marketBuy(double targetPrice, double quantity) {
        return executeOrder(TransactionType.MARKET, TransactionSide.BUY, targetPrice, quantity, generateClientOrderId());
    }

    public Transaction marketSell(double targetPrice, double quantity, String clientOrderId) {
        return executeOrder(TransactionType.MARKET, TransactionSide.SELL, targetPrice, quantity, clientOrderId);
    }

    @Override
    public Transaction marketSell(double targetPrice, double quantity) {
        return executeOrder(TransactionType.MARKET, TransactionSide.SELL, targetPrice, quantity, generateClientOrderId());
    }

    @Override
    @Nullable
    public Transaction limitBuy(double targetPrice, double quantity) { return null; }

    @Override
    @Nullable
    public Transaction limitSell(double targetPrice, double quantity) { return null; }

    /**
     * Executes an order on the Coinbase exchange.
     *
     * @param orderType The type of the order (e.g., MARKET).
     * @param side The side of the transaction (e.g., BUY or SELL).
     * @param price The target price for the order (e.g USD).
     * @param quantity The quantity for the order (e.g., BTC).
     * @return Transaction if the order was executed successfully, null otherwise.
     */
    @Nullable
    private Transaction executeOrder(TransactionType orderType, TransactionSide side, double price, double quantity, String clientOrderId) {
        logger.info("{}: executing {} {} order - Price: {}, Quantity: {}", getCurrency(), orderType, side, price, quantity);
        if (!isConnected) {
            logger.error("Cannot execute order - not connected");
            return null;
        }

        Request request;

        Transaction transaction = Transaction.builder()
                .id(isPreviewTrade() ? null : clientOrderId)
                .exchange(getExchangeName())
                .currency(getCurrency())
                .orderType(orderType)
                .preview(isPreviewTrade())
                .side(side)
                .price(price)
                .quantity(quantity)
                .build();

        try {
            ObjectNode orderRequest = objectMapper.createObjectNode()
                    .put("client_order_id", clientOrderId)
                    .put("product_id", getExchangeSymbol())
                    .put("side", side.name());

            ObjectNode orderConfiguration = objectMapper.createObjectNode();
            switch (orderType) {
                case MARKET -> {
                    ObjectNode marketConfig = objectMapper.createObjectNode();
                    marketConfig.put("quote_size", String.format("%.8f", price));
                    marketConfig.put("base_size", String.format("%.8f", quantity));
                    orderConfiguration.set("market_market_ioc", marketConfig);
                }
                default -> {
                    throw new UnsupportedOperationException(
                            "Order type not implemented: " + orderType);
                }
            }
            orderRequest.set("order_configuration", orderConfiguration);

            HttpUrl url;
            if (isPreviewTrade()) {
                transaction.setPreview(true);
                url = COINBASE_API_ROOT.resolve("/api/v3/brokerage/orders/preview");
            } else {
                url = COINBASE_API_ROOT.resolve("/api/v3/brokerage/orders");
            }
            String jwt = generateJWT("POST", getSchemelessURL(url.toString()));
            request = new Request.Builder()
                    .url(url)
                    .header("Authorization", "Bearer " + jwt)
                    .post(RequestBody.create(
                            objectMapper.writeValueAsString(orderRequest),
                            MediaType.parse("application/json")))
                    .build();

        } catch (Exception e) {
            logger.error("Failed to create order request: {}", e.getMessage());
            transaction.setStatus(TransactionStatus.REQUEST_ERROR);
            addTransaction(transaction);
            return transaction;
        }

            if (!canTrade()) {
                logger.info("Trading disabled: transaction will not be executed");
                addTransaction(transaction);
                return transaction;
            }

            try (Response response = httpClient.newCall(request).execute()) {
                transaction.setResponse(response.toString());
                if (!response.isSuccessful()) {
                    String errorBody = response.body() != null ? response.body().string() : "No error details";
                    logger.error("Order execution failed - Status: {}, Error: {}",
                            response.code(), errorBody);
                    if (response.code() >= 400 && response.code() < 500) {
                        transaction.setStatus(TransactionStatus.REQUEST_ERROR);
                    } else {
                        transaction.setStatus(TransactionStatus.EXECUTION_ERROR);
                    }
                    addTransaction(transaction);
                    return transaction;
                }

                String responseBody;
                ResponseBody body = response.body();
                if (body == null) {
                    responseBody = "";
                } else {
                    responseBody = body.string();
                }
                JsonNode orderResponse = objectMapper.readTree(responseBody);
                transaction.setResponse(orderResponse.toString());
                if (isPreviewTrade()) {
                    if (orderResponse.get("errs").isEmpty() && orderResponse.get("warning").isEmpty()) {
                        transaction.setStatus(TransactionStatus.PREVIEW_SUCCESS);
                    } else if (!orderResponse.get("errs").isEmpty()) {
                        logger.error("Preview order with errors: {}", orderResponse.get("errs").toString());
                        transaction.setStatus(TransactionStatus.PREVIEW_ERROR);
                    } else {
                        logger.error("Preview order with warnings: {}", orderResponse.get("warning").toString());
                        transaction.setStatus(TransactionStatus.PREVIEW_WARNING);
                    }
                } else {
                    if (orderResponse.get("success").asBoolean()) {
                        transaction.setExchangeId(orderResponse.get("order_id").asText("unknown"));
                        transaction.setStatus(TransactionStatus.EXECUTED);
                    } else {
                        transaction.setStatus(TransactionStatus.EXECUTION_ERROR);
                        transaction.setExchangeId("n/a");
                    }
                    if (transaction.getStatus().equals(TransactionStatus.EXECUTED)) {
                        logger.info("Order: {} executed successfully with exchangeId: {}",
                                transaction.getId(),
                                transaction.getExchangeId());
                    } else {
                        logger.error("Unsuccessful execution of Order: {}. Message: {}}",
                                transaction.getId(),
                                transaction.getResponse());
                    }
                }
                addTransaction(transaction);
                return transaction;

            } catch (IOException e) {
                logger.error("Failed to execute {} {} order: {}", orderType, side, e.getMessage());
                transaction.setStatus(TransactionStatus.REQUEST_ERROR);
            }
             catch (Exception e) {
                logger.error("Failed to parse order response: {}", e.getMessage());
                transaction.setStatus(TransactionStatus.EXECUTION_ERROR);
            }
    addTransaction(transaction);
    return transaction;
    }

    @Override
    public boolean walletWithdraw(double amount, String walletAddress) {
        if (!isConnected) {
            logger.error("Cannot withdraw - not connected");
            return false;
        }

        try {
            ObjectNode withdrawRequest = objectMapper.createObjectNode()
                    .put("amount", String.format("%.8f", amount))
                    .put("currency", getCurrency())
                    .put("address", walletAddress);

            HttpUrl url = COINBASE_API_ROOT.resolve("/api/v3/withdrawals/crypto");
            String jwt = generateJWT("POST", getSchemelessURL(url.toString()));
            Request request = new Request.Builder()
                    .url(url)
                    .header("Authorization", "Bearer " + jwt)
                    .post(RequestBody.create(
                            objectMapper.writeValueAsString(withdrawRequest),
                            MediaType.parse("application/json")))
                    .build();

            try (Response response = httpClient.newCall(request).execute()) {
                if (!response.isSuccessful()) {
                    String errorBody = response.body() != null ? response.body().string() : "No error details";
                    logger.error("Withdrawal failed - Status: {}, Error: {}",
                            response.code(), errorBody);
                    return false;
                }
                return true;
            }
        } catch (Exception e) {
            logger.error("Failed to execute withdrawal: {}", e.getMessage());
            return false;
        }
    }

    private static String generateClientOrderId() {
        return UUID.randomUUID().toString();
    }

    @Override
    public HashMap<String, Double> getBalances() {
        HashMap<String, Double> balances = new HashMap<>();
        if (!isConnected) {
            logger.error("Cannot fetch balances - not connected");
            return balances;
        }

        try {
            HttpUrl url = COINBASE_API_ROOT.resolve("/api/v3/brokerage/accounts");
            String jwt = generateJWT("GET", getSchemelessURL(url.toString()));
            Request request = new Request.Builder()
                    .url(COINBASE_API_ROOT + "/api/v3/brokerage/accounts")
                    .header("Authorization", "Bearer " + jwt)
                    .build();

            try (Response response = httpClient.newCall(request).execute()) {
                if (!response.isSuccessful()) {
                    logger.error("Failed to fetch balances - Status: {}", response.code());
                    return balances;
                }

                JsonNode accountsData = objectMapper.readTree(response.body().string());
                JsonNode accounts = accountsData.path("accounts");

                if (accounts.isArray()) {
                    for (JsonNode account : accounts) {
                        String currency = account.path("currency").asText();
                        double available = account.path("available_balance")
                                .path("value").asDouble(0.0);
                        balances.put(currency, available);
                    }
                }
            }
        } catch (Exception e) {
            logger.error("Failed to fetch balances: {}", e.getMessage());
        }
        return balances;
    }
}