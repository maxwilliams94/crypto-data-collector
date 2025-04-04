package ms.maxwillia.cryptodata.client.trader;

import java.net.URI;
import java.util.*;
import java.time.Instant;

import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.JWSSigner;
import com.nimbusds.jose.crypto.ECDSASigner;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import ms.maxwillia.cryptodata.apis.coinbase.v3.model.*;
import ms.maxwillia.cryptodata.client.ClientStatus;
import ms.maxwillia.cryptodata.config.ExchangeCredentials;
import ms.maxwillia.cryptodata.model.*;
import okhttp3.*;
import org.jetbrains.annotations.Nullable;

import javax.money.Monetary;
import javax.money.MonetaryAmount;

import ms.maxwillia.cryptodata.apis.coinbase.v3.invoker.ApiClient;
import ms.maxwillia.cryptodata.apis.coinbase.v3.invoker.ApiException;
import ms.maxwillia.cryptodata.apis.coinbase.v3.api.AccountsApi;
import ms.maxwillia.cryptodata.apis.coinbase.v3.api.OrdersApi;
import ms.maxwillia.cryptodata.apis.coinbase.v3.api.ProductsApi;


class CoinbaseTrader extends BaseExchangeTrader {
    private final ApiClient baseApiClient;
    private final HashMap<String, ApiClient> apiClients = new HashMap<>();


    public CoinbaseTrader(String assetCurrency, String intermediateCurrency, ExchangeCredentials credentials) {
        super("Coinbase", assetCurrency, intermediateCurrency, credentials, true);
        setSettlementCurrency(Monetary.getCurrency("USDC"));
        this.baseApiClient = new ApiClient();
    }

    private static String getSchemelessURL(String url) {
        return url.substring(URI.create(url).getScheme().length() + 3);
    }

    public void setApiRoot(HttpUrl apiRoot) {
        this.baseApiClient.setBasePath(apiRoot.toString());

        for (ApiClient apiClient : apiClients.values()) {
            apiClient.setBasePath(apiRoot.toString());
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

        if (!isCanTrade()) {
            logger.info("Trading disabled: transaction will not be executed");
            addTransaction(transaction);
            return transaction;
        }

        OrdersApi ordersApi = new OrdersApi(getApiClient(OrdersApi.class.getSimpleName()));
        try {
            if (isPreviewTrade()) {
                OrderPreviewRequest orderPreviewRequest = new OrderPreviewRequest()
                        .productId(CurrencyType.ASSET.equals(currencyType) ? getExchangeTradePair() : getExchangeIntermediatePair())
                        .side(TransactionSide.BUY.equals(side) ? OrderPreviewRequest.SideEnum.BUY : OrderPreviewRequest.SideEnum.SELL)
                        .orderConfiguration(new OrderConfiguration()
                                .marketMarketIoc(new MarketMarketIoc()
                                        .quoteSize(Double.toString(price))
                                        .baseSize(Double.toString(quantity))));

                ordersApi.getApiClient().setBearerToken(jwtFromApi(ordersApi.previewOrderCall(orderPreviewRequest, null)));
                OrderPreviewResponse orderResponse = ordersApi.previewOrder(orderPreviewRequest);
                transaction.setResponse(orderResponse.toString());
                if (orderResponse.getPreviewId() != null) {
                    transaction.setExchangeId(orderResponse.getPreviewId());
                } else {
                    transaction.setExchangeId(clientOrderId);
                }
                if (orderResponse.getErrs() != null && !orderResponse.getErrs().isEmpty()) {
                    logger.error("Preview order {} has errors: {}", transaction.getExchangeId(), orderResponse.getErrs());
                    transaction.setStatus(TransactionStatus.PREVIEW_ERROR);
                } else if (orderResponse.getWarning() != null && !orderResponse.getWarning().isEmpty()) {
                    logger.error("Preview order {} has warnings: {}", transaction.getExchangeId(), orderResponse.getWarning());
                    transaction.setStatus(TransactionStatus.PREVIEW_WARNING);
                } else {
                    transaction.setStatus(TransactionStatus.PREVIEW_SUCCESS);
                    if (orderResponse.getCommissionTotal() != null) {
                        transaction.setFee(Double.parseDouble(orderResponse.getCommissionTotal()));
                    }
                    transaction.setExchangeId(orderResponse.getPreviewId());
                }
            } else {
                OrderRequest orderRequest = new OrderRequest()
                        .clientOrderId(clientOrderId)
                        .productId(CurrencyType.ASSET.equals(currencyType) ? getExchangeTradePair() : getExchangeIntermediatePair())
                        .side(TransactionSide.BUY.equals(side) ? OrderRequest.SideEnum.BUY : OrderRequest.SideEnum.SELL)
                        .orderConfiguration(new OrderConfiguration()
                                .marketMarketIoc(new MarketMarketIoc()
                                        .quoteSize(Double.toString(price))
                                        .baseSize(Double.toString(quantity)))
                        );
                ordersApi.getApiClient().setBearerToken(jwtFromApi(ordersApi.createOrderCall(orderRequest, null)));
                CreateOrderResponse orderResponse = ordersApi.createOrder(orderRequest);
                transaction.setResponse(orderResponse.toString());
                if (Boolean.FALSE.equals(orderResponse.getSuccess())) {
                    logger.error("Non-success order response: {}", orderResponse.getErrorResponse());
                    transaction.setStatus(TransactionStatus.REQUEST_ERROR);
                } else {
                    if (orderResponse.getSuccessResponse() != null) {
                        transaction.setExchangeId(orderResponse.getSuccessResponse().getOrderId());
                        transaction.setStatus(TransactionStatus.REQUESTED);
                    } else {
                        logger.warn("No success response found in order response: {}", orderResponse);
                    }
                }
            }
        } catch (ApiException e) {
            logger.error("Failed to execute order {} with return code {}", isPreviewTrade() ? "" : clientOrderId, e.getCode(), e);
            logger.error(e.getResponseBody());
            transaction.setStatus(TransactionStatus.REQUEST_ERROR);
            transaction.setResponse(e.getResponseBody());
        }

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
        return false;
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
        logger.debug("Getting best {} price for {}", side.toString(), pair);
        ProductsApi productsApi = new ProductsApi(getApiClient(ProductsApi.class.getSimpleName()));
        productsApi.getApiClient().setBearerToken(jwtFromApi(productsApi.getBestBidAskCall(List.of(pair), null)));
        PriceBook priceBook;
        try {
            var priceBooks = productsApi.getBestBidAsk(List.of(pair)).getPricebooks();
            if (priceBooks == null || priceBooks.isEmpty()) {
                throw new ApiException(500, "No PriceBook returned from `getBestBidAsk` call");
            }
            priceBook = priceBooks.getFirst();
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
        logger.debug("{}: {}", pair, level);
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