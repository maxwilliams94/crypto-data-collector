package ms.maxwillia.cryptodata.client;

import java.util.HashMap;

public interface ExchangeTrader extends ExchangeClient{
    public boolean connect();
    public void disconnect();
    public boolean marketBuy(double targetPrice, double quantity);

    public boolean marketSell(double targetPrice, double quantity);

    public boolean limitBuy(double targetPrice, double quantity);

    public boolean limitSell(double targetPrice, double quantity);

    public boolean walletWithdraw(double amount, String walletAddress);

    public HashMap<String, Double> getBalances();
}
