package ms.maxwillia.cryptodata.client.trader;

import ms.maxwillia.cryptodata.apis.firi.v2.invoker.ApiException;
import ms.maxwillia.cryptodata.apis.firi.v2.model.CreatedOrder;
import ms.maxwillia.cryptodata.apis.firi.v2.model.Ticker;
import ms.maxwillia.cryptodata.client.ClientStatus;
import ms.maxwillia.cryptodata.config.ExchangeCredentials;
import ms.maxwillia.cryptodata.model.*;
import okhttp3.*;
import org.jetbrains.annotations.Nullable;

import javax.money.Monetary;
import javax.money.MonetaryAmount;
import java.math.BigDecimal;
import java.util.HashMap;
import java.util.List;

import ms.maxwillia.cryptodata.apis.firi.v2.invoker.ApiClient;

import ms.maxwillia.cryptodata.apis.firi.v2.api.BalanceApi;
import ms.maxwillia.cryptodata.apis.firi.v2.api.OrderApi;
import ms.maxwillia.cryptodata.apis.firi.v2.api.MarketsApi;

import ms.maxwillia.cryptodata.apis.firi.v2.model.NewOrder;


public class FiriTrader extends BaseExchangeTrader {
        private final ApiClient apiClient;

    public FiriTrader(String assetCurrency, String intermediateCurrency, ExchangeCredentials credentials) {
        super("Firi", assetCurrency, intermediateCurrency, credentials, false);
        setSettlementCurrency(Monetary.getCurrency("NOK"));
        apiClient = new ApiClient();
        apiClient.setApiKey(credentials.getPrivateKey());
    }

    public void setApiRoot(HttpUrl apiRoot) {
        this.apiClient.setBasePath(apiRoot.toString());
    }

    @Override
    public boolean connect() {
        try {
            BalanceApi balanceApi = new BalanceApi(apiClient);
            balanceApi.getBalances();
            isConnected = true;
            setStatus(ClientStatus.CONNECTED);
            return true;
        } catch (ApiException e) {
            logger.error("Failed to connect to exchange.", e);
            return false;
        }
    }

    @Override
    public boolean initialize() {
        return true;
    }

    @Override
    public void disconnect() {
        isConnected = false;
        setStatus(ClientStatus.STOPPED);
    }


    /*
        Non-native market SELL of asset currency - assuming comfortable reserves
        - SELL Asset currency for Settlement currency (ASAP)
        - Get price of IntermediateCurrency-SettlementCurrency
        - BUY Intermediate currency for SettlementCurrency
     */
    @Override
    public List<Transaction> marketBuy(double quantity) {
        double assetMarketPrice;
        try {
            assetMarketPrice = getSpotAssetPrice(TransactionSide.BUY);
        } catch (ApiException e) {
            logger.error("Failed to get market price", e);
            return List.of();
        }
        Transaction buyTransaction = executeOrder(TransactionType.MARKET, TransactionSide.BUY, CurrencyType.ASSET,
                assetMarketPrice, quantity, generateClientOrderId());
        Transaction intermediateTransaction;
        try {
            double intermediatePrice = getSpotIntermediatePrice(TransactionSide.SELL);
            intermediateTransaction = executeOrder(TransactionType.MARKET, TransactionSide.SELL, CurrencyType.INTERMEDIATE,
                    intermediatePrice, (assetMarketPrice * quantity) / intermediatePrice ,generateClientOrderId());
        } catch (Exception e) {
            logger.error("Failed to execute intermediate transaction.", e);
            return List.of(buyTransaction);
        }
        return List.of(buyTransaction, intermediateTransaction);
    }

    /*
        Non-native market BUY of asset currency - assuming comfortable reserves
        - BUY Asset currency for Settlement currency (ASAP)
        - Get price of IntermediateCurrency-SettlementCurrency
        - SELL Intermediate currency for SettlementCurrency
     */
    @Override
    public List<Transaction> marketSell(double quantity) {
        double assetMarketPrice;
        try {
            assetMarketPrice = getSpotAssetPrice(TransactionSide.SELL);
        } catch (ApiException e) {
            logger.error("Failed to get market price", e);
            return List.of();
        }
        Transaction sellTransaction;
        Transaction intermediateTransaction;
        try {
            sellTransaction = executeOrder(TransactionType.MARKET, TransactionSide.SELL, CurrencyType.ASSET,
                    assetMarketPrice, quantity, generateClientOrderId());
        } catch (Exception e) {
            logger.error("Failed to execute asset transaction.", e);
            return List.of();
        }
        try {
            double intermediatePrice = getSpotIntermediatePrice(TransactionSide.BUY);
            intermediateTransaction = executeOrder(TransactionType.MARKET, TransactionSide.BUY, CurrencyType.INTERMEDIATE,
                    intermediatePrice, (assetMarketPrice * quantity) / intermediatePrice,generateClientOrderId());
        } catch (Exception e) {
            logger.error("Failed to execute intermediate transaction.", e);
            return List.of(sellTransaction);
        }
        return List.of(sellTransaction, intermediateTransaction);
    }

    @Override
    @Nullable
    public List<Transaction> limitBuy(double targetPrice, double quantity) { return null; }

    @Override
    @Nullable
    public List<Transaction> limitSell(double targetPrice, double quantity) { return null; }

    @Override
    public boolean walletWithdraw(MonetaryAmount currency, String walletAddress) {
        return false;
    }

    @Override
    public HashMap<String, Double> getBalances() {
        if (!isConnected) {
            logger.error("Cannot fetch balances - not connected");
            return new HashMap<>();
        }

        if (!isOrderSinceLastBalance()) {
            return getAccountBalances();
        }

        try {
            BalanceApi balanceApi = new BalanceApi(apiClient);
            var balances = balanceApi.getBalances();
            balances.forEach(balance -> {
                var currency = balance.getCurrency();
                var available = balance.getAvailable();
                logger.debug("{} : {}", currency, available);
                if (currency != null && available != null) {
                    if (getCurrencies().contains(currency)) getAccountBalances().put(currency, Double.parseDouble(available));
                }
            });
        } catch (ApiException e) {
            logger.error("Could not get Balances", e);
            getAccountBalances().clear();
            return getAccountBalances();
        }
        return getAccountBalances();

    }

    public String getExchangeTradePair() {
        return "%s%s".formatted(getAssetCurrency().getCurrencyCode(), getSettlementCurrency().getCurrencyCode());
    }

    @Override
    public String getExchangeIntermediatePair() {
        return "%s%s".formatted(getIntermediateCurrency().getCurrencyCode(), getSettlementCurrency().getCurrencyCode());
    }

    Transaction executeOrder(TransactionType orderType, TransactionSide side, CurrencyType currencyType, double price, double quantity, String clientOrderId) {
        NewOrder newOrder = new NewOrder();
        newOrder.symbol(currencyType.equals(CurrencyType.ASSET) ? getExchangeTradePair() : getExchangeIntermediatePair());
        newOrder.setPrice(BigDecimal.valueOf(price));
        newOrder.setQuantity(BigDecimal.valueOf(quantity));
        newOrder.setSide(side.equals(TransactionSide.BUY) ? NewOrder.SideEnum.BID : NewOrder.SideEnum.ASK);
        logger.debug("New order: {}", newOrder);

        Transaction transaction = Transaction.builder()
                .exchange("Firi")
                .id(clientOrderId)
                .currency(currencyType.equals(CurrencyType.ASSET) ? getTradePair() : getIntermediatePair())
                .orderType(orderType)
                .side(side)
                .price(price)
                .quantity(quantity)
                .fee(null)
                .build();
        logger.info("New order: {}", transaction);

        if (isCanTrade()) {
            OrderApi orderApi = new OrderApi(apiClient);
            CreatedOrder createdOrder;
            try {
                createdOrder = orderApi.createOrder(newOrder);
                transaction.setResponse(createdOrder.toString());
                transaction.setExchangeId(createdOrder.getId().toString());
                transaction.setStatus(TransactionStatus.EXECUTED);
            } catch (ApiException e) {
                logger.error("Failed to request or execute order: {}", e.getMessage());
                transaction.setStatus(TransactionStatus.EXECUTION_ERROR);
            } catch (Exception e) {
                logger.error("Unable to determine orderId", e);
                transaction.setStatus(TransactionStatus.EXECUTION_ERROR);
            }
        }
        this.getTransactions().add(transaction);
        this.setOrderSinceLastBalance(true);
        return transaction;

    }

    @Override
    String getAssetWalletAddress() {
        //TODO implement once firi confirms /deposit/address endpoint availability and return
        return "";
    }

    @Override
    String getIntermediateWalletAddress() {
        //TODO implement once firi confirms /deposit/address endpoint availability and return
        return "";
    }

    private double getBidOrAsk(TransactionSide side, String pair) throws ApiException {
        MarketsApi marketsApi = new MarketsApi(apiClient);
        Ticker ticker = marketsApi.getTicker(pair);

        if (ticker.getBid() == null || ticker.getAsk() == null) {
            throw new ApiException("Ticker bids are unavailable");
        }
        logger.debug(ticker.toString());
        return Double.parseDouble(side.equals(TransactionSide.BUY) ? ticker.getBid() : ticker.getAsk());
    }

    public double getSpotIntermediatePrice(TransactionSide side) throws ApiException {
        return getBidOrAsk(side, getExchangeIntermediatePair());
    }

    public double getSpotAssetPrice(TransactionSide side) throws ApiException {
        return getBidOrAsk(side, getExchangeTradePair());
    }

    public void enablePreviewTrading() {
        logger.warn("No preview trading feature available. Cannot enable.");
    }

    public void disablePreviewTrading() {
        logger.warn("No preview trading feature available. Cannot disable.");
    }
}