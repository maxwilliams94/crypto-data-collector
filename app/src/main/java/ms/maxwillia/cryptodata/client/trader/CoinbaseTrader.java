package ms.maxwillia.cryptodata.client.trader;

import java.io.IOException;
import java.net.URI;
import java.util.*;
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
import ms.maxwillia.cryptodata.client.mapper.coinbase.CoinbaseOrderMapper;
import ms.maxwillia.cryptodata.config.ExchangeCredentials;
import ms.maxwillia.cryptodata.model.*;
import okhttp3.*;
import org.jetbrains.annotations.Nullable;

import javax.money.Monetary;
import javax.money.MonetaryAmount;
import java.util.concurrent.TimeUnit;

import ms.maxwillia.cryptodata.apis.coinbase.v3.invoker.ApiClient;
import ms.maxwillia.cryptodata.apis.coinbase.v3.invoker.ApiException;
import ms.maxwillia.cryptodata.apis.coinbase.v3.api.AccountsApi;
import ms.maxwillia.cryptodata.apis.coinbase.v3.api.ProductsApi;
import ms.maxwillia.cryptodata.apis.coinbase.v3.model.GetAccountsResponse;
import ms.maxwillia.cryptodata.apis.coinbase.v3.model.GetAccountResponse;
import ms.maxwillia.cryptodata.apis.coinbase.v3.model.Account;
import ms.maxwillia.cryptodata.apis.coinbase.v3.model.PriceBook;
import ms.maxwillia.cryptodata.apis.coinbase.v3.model.PriceLevel;


class CoinbaseTrader extends BaseExchangeTrader {
    private HttpUrl COINBASE_API_ROOT = HttpUrl.parse("https://api.coinbase.com");
    private final OkHttpClient httpClient;
    private final ObjectMapper objectMapper;
    private final ApiClient baseApiClient;
    private final HashMap<String, ApiClient> apiClients = new HashMap<>();


    public CoinbaseTrader(String assetCurrency, String intermediateCurrency, ExchangeCredentials credentials) {
        super("Coinbase", assetCurrency, intermediateCurrency, credentials, true);
        setSettlementCurrency(Monetary.getCurrency("USDC"));
        this.httpClient = new OkHttpClient.Builder()
                .connectTimeout(10, TimeUnit.SECONDS)
                .readTimeout(10, TimeUnit.SECONDS)
                .writeTimeout(10, TimeUnit.SECONDS)
                .build();
        this.objectMapper = new ObjectMapper();
        this.baseApiClient = new ApiClient();
    }

    private static String getSchemelessURL(String url) {
        return url.substring(URI.create(url).getScheme().length() + 3);
    }

    public void setApiRoot(HttpUrl apiRoot) {
        this.COINBASE_API_ROOT = apiRoot;
        this.baseApiClient.setBasePath(apiRoot.toString() + "api/v3");

        for (ApiClient apiClient : apiClients.values()) {
            apiClient.setBasePath(apiRoot + "api/v3");
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

        logger.debug("generateJWT for {}", uri);
        return signedJWT.serialize();
    }

    @Override
    public boolean connect() {
        if (isConnected) {
            return true;
        }

        try {
            AccountsApi accountsApi = new AccountsApi(getApiClient(AccountsApi.class.getSimpleName()));
            accountsApi.getApiClient().setBearerToken(jwtFromApi(accountsApi.listAccountsCall(1, null, null)));
            accountsApi.listAccounts(1,null);
            isConnected = true;
            setStatus(ClientStatus.CONNECTED);
            return true;
        } catch (ApiException e) {
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

    public List<Transaction> marketBuy(double quantity, String clientOrderId) {
        try {
            return List.of(executeOrder(TransactionType.MARKET, TransactionSide.BUY, CurrencyType.ASSET, getSpotAssetPrice(TransactionSide.BUY), quantity, clientOrderId));
        } catch (Exception e) {
            logger.error("Failed to execute order", e);
            return List.of();
        }
    }

    @Override
    public List<Transaction> marketBuy(double quantity) {
        try {
            return List.of(executeOrder(TransactionType.MARKET, TransactionSide.BUY, CurrencyType.ASSET, getSpotAssetPrice(TransactionSide.BUY), quantity, generateClientOrderId()));
        } catch (Exception e) {
            logger.error("Failed to execute order", e);
            return List.of();
        }
    }

    public List<Transaction> marketSell(double quantity, String clientOrderId) {
        try {
            return List.of(executeOrder(TransactionType.MARKET, TransactionSide.SELL, CurrencyType.ASSET, getSpotAssetPrice(TransactionSide.SELL), quantity, clientOrderId));
        } catch (Exception e) {
            logger.error("Failed to execute order", e);
            return List.of();
        }
    }

    @Override
    public List<Transaction> marketSell(double quantity) {
        try {
        return List.of(executeOrder(TransactionType.MARKET, TransactionSide.SELL, CurrencyType.ASSET, getSpotAssetPrice(TransactionSide.SELL), quantity, generateClientOrderId()));
        } catch (Exception e) {
            logger.error("Failed to execute order", e);
            return List.of();
        }
    }

    @Override
    @Nullable
    public List<Transaction> limitBuy(double targetPrice, double quantity) {
        return List.of();
    }

    @Override
    @Nullable
    public List<Transaction> limitSell(double targetPrice, double quantity) { return null; }

    /**
     * Executes an order on the Coinbase exchange.
     *
     * @param orderType The type of the order (e.g., MARKET).
     * @param side The side of the transaction (e.g., BUY or SELL).
     * @param price The target price for the order (e.g USD).
     * @param quantity The quantity for the order (e.g., BTC).
     * @return Transaction if the order was executed successfully, null otherwise.
     */
    Transaction executeOrder(TransactionType orderType, TransactionSide side, CurrencyType currencyType, double price, double quantity, String clientOrderId) {
        logger.info("{}: executing {} {} {} order - Price: {}, Quantity: {}", getTradePair(), isPreviewTrade() ? "preview" : "", orderType, side, price, quantity);
        if (!isConnected) {
            logger.error("Cannot execute order - not connected");
            return Transaction.builder().exchange("coinbase").id(clientOrderId).status(TransactionStatus.REQUEST_ERROR).build();
        }

        Request request;
        Transaction transaction = Transaction.builder()
                .id(clientOrderId)
                .exchange(getExchangeName())
                .currency(getTradePair())
                .orderType(orderType)
                .preview(isPreviewTrade())
                .side(side)
                .price(price)
                .quantity(quantity)
                .build();

        try {
            ObjectNode orderRequest = objectMapper.createObjectNode()
                    .put("product_id", getExchangeTradePair())
                    .put("side", side.name());
            if (!isPreviewTrade()) {
                    orderRequest.put("client_order_id", clientOrderId);
            }

            ObjectNode orderConfiguration = objectMapper.createObjectNode();
            switch (orderType) {
                case MARKET -> {
                    ObjectNode marketConfig = objectMapper.createObjectNode();
                    marketConfig.put("quote_size", Double.toString(price));
                    marketConfig.put("base_size", Double.toString(quantity));
                    orderConfiguration.set("market_market_ioc", marketConfig);
                }
                default -> {
                    throw new UnsupportedOperationException(
                            "Order type not implemented: " + orderType);
                }
            }
            orderRequest.set("order_configuration", orderConfiguration);

            HttpUrl baseUrl;
            if (isPreviewTrade()) {
                transaction.setPreview(true);
                baseUrl = COINBASE_API_ROOT.resolve("/api/v3/brokerage/orders/preview");
            } else {
                baseUrl = COINBASE_API_ROOT.resolve("/api/v3/brokerage/orders");
            }
            String jwt = generateJWT("POST", getSchemelessURL(baseUrl.toString()));
            request = new Request.Builder()
                    .url(baseUrl)
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
                    String responseBody;
                    ResponseBody body = response.body();
                    if (body == null) {
                        responseBody = null;
                    } else {
                        responseBody = body.string();
                    }
                    var responseMapper = new CoinbaseOrderMapper(transaction);
                    JsonNode orderResponse = objectMapper.readTree(responseBody);
                    transaction = responseMapper.map(orderResponse);
                    if (!response.isSuccessful()) {
                        logger.error("Order execution failed - Status: {}, Error: {}",
                                response.code(), responseMapper.getErrorDetails(orderResponse));
                        if (response.code() >= 400 && response.code() < 500) {
                            transaction.setStatus(TransactionStatus.REQUEST_ERROR);
                        } else {
                            transaction.setStatus(TransactionStatus.EXECUTION_ERROR);
                        }
                        addTransaction(transaction);
                        return transaction;
                    }

                    if (responseMapper.isError(orderResponse)) {
                        var errorDetails = responseMapper.getErrorDetails(orderResponse);
                        logger.error("Order execution ({}) failed - Error: {}", transaction.getId() ,errorDetails);
                        transaction.setStatus(isPreviewTrade() ? TransactionStatus.PREVIEW_ERROR : TransactionStatus.EXECUTION_ERROR);
                    } else {
                        if (responseMapper.isWarning(orderResponse)) {
                            logger.warn("Order execution ({}) returned a warning: {}", transaction.getId(), orderResponse.get("warning").toString());
                            transaction.setStatus(TransactionStatus.PREVIEW_WARNING);
                        } else {
                            transaction.setStatus(isPreviewTrade() ? TransactionStatus.PREVIEW_SUCCESS : TransactionStatus.EXECUTED);
                            logger.info("Order: {} executed successfully with exchangeId: {}",
                                    transaction.getId(),
                                    isPreviewTrade() ? "preview" : transaction.getExchangeId());
                        }
                    }
                } catch (IOException e) {
                    logger.error("Failed to execute {} {} order: {}", orderType, side, e.getMessage());
                }
                 catch (Exception e) {
                     logger.error("Failed to parse order response: {}", e.getMessage());
                     transaction.setStatus(TransactionStatus.EXECUTION_ERROR);
                 }
        setOrderSinceLastBalance(true);
        addTransaction(transaction);
        return transaction;
    }

    @Override
    String getAssetWalletAddress() {
        return "";
    }

    @Override
    String getIntermediateWalletAddress() {
        return "";
    }

    @Override
    public boolean walletWithdraw(MonetaryAmount amount, String walletAddress) {
        if (!isConnected) {
            logger.error("Cannot withdraw - not connected");
            return false;
        }

        try {
            ObjectNode withdrawRequest = objectMapper.createObjectNode()
                    .put("amount", String.format("%.8s", amount.getNumber()))
                    .put("currency", amount.getCurrency().getCurrencyCode())
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

    /**
     * Finds account ids for the specified currencies.
     * We must iterate through all possible accounts until we find the ones we need
     * @return true if all account ids were found, false otherwise.
     */
    public boolean findAccountIds() {
        logger.debug("Finding account ids for currencies: {}", String.join(", ", getCurrencies()));
        AccountsApi accountsApi = new AccountsApi(getApiClient(AccountsApi.class.getSimpleName()));

        String cursor = null;
        while (this.getAccountIds().size() < getCurrencies().size()) {
            try {
                accountsApi.getApiClient().setBearerToken(jwtFromApi(accountsApi.listAccountsCall(250, cursor, null)));
                GetAccountsResponse accountsResponse = accountsApi.listAccounts(250, cursor);
                List<Account> accounts = accountsResponse.getAccounts();
                if (accounts.isEmpty()) {
                    logger.error("No accounts found");
                    return false;
                }

                for (Account account : accounts) {
                    if (getCurrencies().contains(account.getCurrency())) {
                        this.getAccountIds().put(account.getCurrency(), account.getUuid().toString());
                        this.getAccountBalances().put(account.getCurrency(), Double.parseDouble(account.getAvailableBalance().getValue()));
                        logger.debug("Found account id for {}: {}", account.getCurrency(), account.getUuid());
                    }
                }
                if (accountsResponse.getHasNext()) {
                    cursor = accountsResponse.getCursor();
                }
            } catch (ApiException e) {
                logger.error("API error during findAccountIds. Return code was {}", e.getCode(), e);
                return false;
            } catch (Exception e) {
                logger.error("Unexpected error within findAccountIds", e);
                return false;
            }
        }
        logger.info("Found {} accountIds ({})", this.getAccountIds().size(), String.join(", ", this.getAccountBalances().keySet()));
        return this.getAccountIds().size() == getCurrencies().size();
    }

    @Override
    public HashMap<String, Double> getBalances() {
        if (!isConnected) {
            logger.error("Cannot fetch balances - not connected");
            return new HashMap<>();
        }

        if (getAccountIds().size() != getCurrencies().size()) {
            if (!findAccountIds()) {
                setStatus(ClientStatus.ERROR);
                throw new IllegalStateException("Failed to find account ids for all currencies");
            }
        }

        AccountsApi accountsApi = new AccountsApi(getApiClient(AccountsApi.class.getSimpleName()));
        for (String currency : getAccountBalances().keySet()) {
            String accountId = getAccountIds().get(currency);
            try {
                accountsApi.getApiClient().setBearerToken(jwtFromApi(accountsApi.getAccountCall(UUID.fromString(accountId), null)));
                GetAccountResponse accountResponse = accountsApi.getAccount(UUID.fromString(accountId));
                Account account = accountResponse.getAccount();
                getAccountBalances().put(account.getAvailableBalance().getCurrency(), Double.parseDouble(account.getAvailableBalance().getValue()));
            } catch (ApiException e) {
                setStatus(ClientStatus.ERROR);
                logger.error("API error when attempting to getBalances. Return code was {}", e.getCode(), e);
            } catch (Exception e) {
                setStatus(ClientStatus.ERROR);
                logger.error("Unexpected error while getting balances", e);
            }
        }
        return getAccountBalances();
    }

    private double getBidOrAsk(TransactionSide side, String pair) throws ApiException {
        ProductsApi productsApi = new ProductsApi(getApiClient(ProductsApi.class.getSimpleName()));
        productsApi.getApiClient().setBearerToken(jwtFromApi(productsApi.getBestBidAskCall(List.of(pair), null)));
        PriceBook priceBook;
        try {
            priceBook = productsApi.getBestBidAsk(List.of(pair)).getPricebooks().getFirst();
            if (Objects.isNull(priceBook)) {
                throw new ApiException(400, "No price book found");
            }
            logger.trace(priceBook.toString());
        } catch (Exception e) {
            logger.error("Exception during ProductsApi.getBestBidAsk {}",pair, e);
            throw e;
        }
        PriceLevel level = null;
        try {
            level = side.equals(TransactionSide.BUY) ? priceBook.getBids().getFirst() : priceBook.getAsks().getFirst();
        } catch (Exception e) {
            logger.error("PriceLevel format error {}", level, e);
            throw e;
        }
        return Double.parseDouble(level.getPrice());

    }

    public double getSpotAssetPrice(TransactionSide side) throws Exception {
        return getBidOrAsk(side, getExchangeTradePair());
    }

    public double getSpotIntermediatePrice(TransactionSide side) throws Exception {
        return 0;
    }

    public String getExchangeTradePair() {
        return "%s-%s".formatted(getAssetCurrency().getCurrencyCode(), getSettlementCurrency().getCurrencyCode());
    }

    @Override
    public String getExchangeIntermediatePair() {
        return "";
    }

    private String jwtFromApi(Call apiCall) {
        String method = apiCall.request().method();
        HttpUrl url = apiCall.request().url();
        String jwtUrl = url.host() + url.encodedPath();
        try {
            return this.generateJWT(method, jwtUrl);
        } catch (Exception e) {
            logger.error("Failed to generate JWT for {}: {}", method, jwtUrl, e);
            return "";
        }
    }

    private ApiClient getApiClient(String key) {
        if (apiClients.containsKey(key)) {
            return apiClients.get(key);
        } else {
            ApiClient apiClient = new ApiClient();
            apiClient.setBasePath(baseApiClient.getBasePath());
            apiClients.put(key, apiClient);
            logger.debug("Created new ApiClient for {} ({})", key, apiClients.size());
            return apiClient;
        }
    }

}