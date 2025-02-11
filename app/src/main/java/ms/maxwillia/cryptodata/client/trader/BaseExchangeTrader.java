package ms.maxwillia.cryptodata.client.trader;

import ms.maxwillia.cryptodata.client.BaseExchangeClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public abstract class BaseExchangeTrader extends BaseExchangeClient implements ExchangeTrader {
    protected final Logger logger = LoggerFactory.getLogger(getClass());

    public BaseExchangeTrader(String exchangeName, String currency) {
        super(exchangeName, currency);
    }
}
