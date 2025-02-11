package ms.maxwillia.cryptodata.client.trader;

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
import okhttp3.*;
import org.bouncycastle.jce.provider.BouncyCastleProvider;
import org.bouncycastle.openssl.PEMParser;
import org.bouncycastle.openssl.jcajce.JcaPEMKeyConverter;

import java.io.IOException;
import java.io.StringReader;
import java.security.KeyFactory;
import java.security.PrivateKey;
import java.security.Security;
import java.security.interfaces.ECPrivateKey;
import java.security.spec.PKCS8EncodedKeySpec;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;

public class CoinbaseTrader extends BaseExchangeTrader {
    private static final String COINBASE_API_URL = "https://api.coinbase.com/api/v3/brokerage";
    private final OkHttpClient httpClient;
    private final ObjectMapper objectMapper;
    private final ExchangeCredentials credentials;
    private ECPrivateKey privateKey;
    private volatile boolean isConnected = false;

    public CoinbaseTrader(String currency, ExchangeCredentials credentials) {
        super("Coinbase", currency);
        this.credentials = credentials;
        this.httpClient = new OkHttpClient.Builder()
                .connectTimeout(10, TimeUnit.SECONDS)
                .readTimeout(10, TimeUnit.SECONDS)
                .writeTimeout(10, TimeUnit.SECONDS)
                .build();
        this.objectMapper = new ObjectMapper();
    }

    @Override
    protected void setSymbolFromCurrency(String currency) {
        this.symbol = currency + "-USD";
    }

    @Override
    public String getExchangeSymbol() {
        return symbol;
    }

    @Override
    public boolean initialize() {
        try {
            if (!configure()) {
                return false;
            }
            setStatus(ClientStatus.STARTING);
            return true;
        } catch (Exception e) {
            logger.error("Failed to initialize Coinbase trader: {}", e.getMessage());
            setStatus(ClientStatus.ERROR);
            return false;
        }
    }

    @Override
    public boolean configure() {
        try {
            if (credentials == null || credentials.getName() == null || credentials.getPrivateKey() == null) {
                logger.error("Missing Coinbase credentials");
                return false;
            }
            initializeSecurity();
            return true;
        } catch (Exception e) {
            logger.error("Error configuring Coinbase trader: {}", e.getMessage());
            return false;
        }
    }

    private void initializeSecurity() throws Exception {
        Security.addProvider(new BouncyCastleProvider());

        String privateKeyPEM = credentials.getPrivateKey().replace("\\n", "\n");
        try (PEMParser pemParser = new PEMParser(new StringReader(privateKeyPEM))) {
            JcaPEMKeyConverter converter = new JcaPEMKeyConverter().setProvider("BC");
            Object object = pemParser.readObject();
            PrivateKey rawPrivateKey;

            if (object instanceof PrivateKey) {
                rawPrivateKey = (PrivateKey) object;
            } else if (object instanceof org.bouncycastle.openssl.PEMKeyPair) {
                rawPrivateKey = converter.getPrivateKey(((org.bouncycastle.openssl.PEMKeyPair) object).getPrivateKeyInfo());
            } else {
                throw new Exception("Unexpected private key format");
            }

            KeyFactory keyFactory = KeyFactory.getInstance("EC");
            PKCS8EncodedKeySpec keySpec = new PKCS8EncodedKeySpec(rawPrivateKey.getEncoded());
            this.privateKey = (ECPrivateKey) keyFactory.generatePrivate(keySpec);
        }
    }

    private String generateJWT(String method, String requestPath) throws Exception {
        if (privateKey == null) {
            throw new IllegalStateException("Private key not initialized");
        }

        long timestamp = Instant.now().getEpochSecond();
        String uri = method + " " + requestPath;

        // Create header
        JWSHeader jwsHeader = new JWSHeader.Builder(JWSAlgorithm.ES256)
                .customParam("kid", credentials.getName())
                .customParam("nonce", String.valueOf(timestamp))
                .build();

        // Create claims
        JWTClaimsSet claimsSet = new JWTClaimsSet.Builder()
                .claim("iss", "cdp")
                .claim("sub", credentials.getName())
                .claim("nbf", timestamp)
                .claim("exp", timestamp + 120)
                .claim("uri", uri)
                .build();

        // Sign JWT
        SignedJWT signedJWT = new SignedJWT(jwsHeader, claimsSet);
        JWSSigner signer = new ECDSASigner(privateKey);
        signedJWT.sign(signer);

        return signedJWT.serialize();
    }

    @Override
    public boolean connect() {
        if (isConnected) {
            return true;
        }

        try {
            String jwt = generateJWT("GET", "/api/v3/brokerage/accounts");
            Request request = new Request.Builder()
                    .url(COINBASE_API_URL + "/accounts")
                    .header("Authorization", "Bearer " + jwt)
                    .build();

            try (Response response = httpClient.newCall(request).execute()) {
                if (!response.isSuccessful()) {
                    logger.error("Connection test failed with response code: {}", response.code());
                    setStatus(ClientStatus.ERROR);
                    return false;
                }
                isConnected = true;
                setStatus(ClientStatus.COLLECTING);
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

    @Override
    public boolean marketBuy(double targetPrice, double quantity) {
        return executeOrder("MARKET", "BUY", targetPrice, quantity);
    }

    @Override
    public boolean marketSell(double targetPrice, double quantity) {
        return executeOrder("MARKET", "SELL", targetPrice, quantity);
    }

    @Override
    public boolean limitBuy(double targetPrice, double quantity) {
        return executeOrder("LIMIT", "BUY", targetPrice, quantity);
    }

    @Override
    public boolean limitSell(double targetPrice, double quantity) {
        return executeOrder("LIMIT", "SELL", targetPrice, quantity);
    }

    private boolean executeOrder(String orderType, String side, double price, double quantity) {
        if (!isConnected) {
            logger.error("Cannot execute order - not connected");
            return false;
        }

        try {
            ObjectNode orderRequest = objectMapper.createObjectNode()
                    .put("client_order_id", String.valueOf(System.currentTimeMillis()))
                    .put("product_id", getExchangeSymbol())
                    .put("side", side)
                    .put("order_type", orderType);

            if (orderType.equals("LIMIT")) {
                orderRequest.put("limit_price", String.format("%.2f", price))
                        .put("time_in_force", "GTC");
            }

            orderRequest.put("base_quantity", String.format("%.8f", quantity));

            String jwt = generateJWT("POST", "/api/v3/brokerage/orders");
            Request request = new Request.Builder()
                    .url(COINBASE_API_URL + "/orders")
                    .header("Authorization", "Bearer " + jwt)
                    .post(RequestBody.create(
                            objectMapper.writeValueAsString(orderRequest),
                            MediaType.parse("application/json")))
                    .build();

            try (Response response = httpClient.newCall(request).execute()) {
                if (!response.isSuccessful()) {
                    String errorBody = response.body() != null ? response.body().string() : "No error details";
                    logger.error("Order execution failed - Status: {}, Error: {}",
                            response.code(), errorBody);
                    return false;
                }

                String responseBody = response.body() != null ? response.body().string() : "{}";
                JsonNode orderResponse = objectMapper.readTree(responseBody);
                logger.info("Order executed successfully - Order ID: {}",
                        orderResponse.path("order_id").asText("unknown"));
                return true;
            }
        } catch (Exception e) {
            logger.error("Failed to execute {} {} order: {}", orderType, side, e.getMessage());
            return false;
        }
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

            String jwt = generateJWT("POST", "/api/v3/brokerage/withdrawals/crypto");
            Request request = new Request.Builder()
                    .url(COINBASE_API_URL + "/withdrawals/crypto")
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

    @Override
    public HashMap<String, Double> getBalances() {
        HashMap<String, Double> balances = new HashMap<>();
        if (!isConnected) {
            logger.error("Cannot fetch balances - not connected");
            return balances;
        }

        try {
            String jwt = generateJWT("GET", "/api/v3/brokerage/accounts");
            Request request = new Request.Builder()
                    .url(COINBASE_API_URL + "/accounts")
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