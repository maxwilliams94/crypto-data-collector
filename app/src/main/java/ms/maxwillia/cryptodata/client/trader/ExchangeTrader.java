package ms.maxwillia.cryptodata.client.trader;

import ms.maxwillia.cryptodata.apis.firi.v2.invoker.ApiException;
import ms.maxwillia.cryptodata.client.ExchangeClient;
import ms.maxwillia.cryptodata.model.Transaction;
import ms.maxwillia.cryptodata.model.TransactionSide;

import javax.money.MonetaryAmount;
import java.util.HashMap;
import java.util.List;

public interface ExchangeTrader extends ExchangeClient {
    public boolean connect();
    public void disconnect();
    public List<Transaction> marketBuy(double quantity);

    public List<Transaction> marketSell(double quantity);

    public List<Transaction> limitBuy(double targetPrice, double quantity);

    public List<Transaction> limitSell(double targetPrice, double quantity);

    public boolean walletWithdraw(MonetaryAmount currency, String walletAddress);

    public void enableTrading();

    public void disableTrading();

    public void enablePreviewTrading();

    public void disablePreviewTrading();

    public boolean canTrade();

    public HashMap<String, Double> getBalances();

    public double getSpotAssetPrice(TransactionSide side) throws Exception;

    public double getSpotIntermediatePrice(TransactionSide side) throws Exception;
}
