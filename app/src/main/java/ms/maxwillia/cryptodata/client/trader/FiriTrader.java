package ms.maxwillia.cryptodata.client.trader;

import ms.maxwillia.cryptodata.apis.firi.v2.invoker.ApiException;
import ms.maxwillia.cryptodata.client.ClientStatus;
import ms.maxwillia.cryptodata.config.ExchangeCredentials;
import ms.maxwillia.cryptodata.model.Transaction;
import ms.maxwillia.cryptodata.model.TransactionSide;
import ms.maxwillia.cryptodata.model.TransactionType;
import okhttp3.*;
import org.jetbrains.annotations.Nullable;

import javax.money.Monetary;
import javax.money.MonetaryAmount;
import java.util.HashMap;

import ms.maxwillia.cryptodata.apis.firi.v2.invoker.ApiClient;

import ms.maxwillia.cryptodata.apis.firi.v2.api.BalanceApi;
import ms.maxwillia.cryptodata.apis.firi.v2.api.WalletApi;
import ms.maxwillia.cryptodata.apis.firi.v2.api.OrderApi;

import ms.maxwillia.cryptodata.apis.firi.v2.model.NewOrder;


public class FiriTrader extends BaseExchangeTrader {
        private final ApiClient apiClient;

    public FiriTrader(String assetCurrency, String intermediateCurrency, ExchangeCredentials credentials) {
        super("Firi", assetCurrency, intermediateCurrency, credentials, false);
        setSettlementCurrency(Monetary.getCurrency("NOK"));
        apiClient = new ApiClient();
        apiClient.addDefaultHeader("firi-access-key", credentials.getPrivateKey());
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


    @Override
    public Transaction marketBuy(double targetPrice, double quantity) {
        return executeOrder(TransactionType.MARKET, TransactionSide.BUY, targetPrice, quantity,"");
    }

    @Override
    public Transaction marketSell(double targetPrice, double quantity) {
        return executeOrder(TransactionType.MARKET, TransactionSide.SELL, targetPrice, quantity, "");
    }

    @Override
    @Nullable
    public Transaction limitBuy(double targetPrice, double quantity) { return null; }

    @Override
    @Nullable
    public Transaction limitSell(double targetPrice, double quantity) { return null; }

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
        return "%s%s".formatted(getAssetCurrency().getCurrencyCode(), getIntermediateCurrency().getCurrencyCode());
    }

    @Override
    Transaction executeOrder(TransactionType orderType, TransactionSide side, double price, double quantity, String clientOrderId) {
        return null;
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
}