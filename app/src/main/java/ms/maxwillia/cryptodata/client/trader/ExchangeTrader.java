package ms.maxwillia.cryptodata.client.trader;

import ms.maxwillia.cryptodata.client.ExchangeClient;
import ms.maxwillia.cryptodata.model.Transaction;

import javax.money.MonetaryAmount;
import java.util.HashMap;

public interface ExchangeTrader extends ExchangeClient {
    public boolean connect();
    public void disconnect();
    public Transaction marketBuy(double targetPrice, double quantity);

    public Transaction marketSell(double targetPrice, double quantity);

    public Transaction limitBuy(double targetPrice, double quantity);

    public Transaction limitSell(double targetPrice, double quantity);

    public boolean walletWithdraw(MonetaryAmount currency, String walletAddress);

    public void enableTrading();

    public void disableTrading();

    public void enablePreviewTrading();

    public void disablePreviewTrading();

    public boolean canTrade();

    public HashMap<String, Double> getBalances();
}
