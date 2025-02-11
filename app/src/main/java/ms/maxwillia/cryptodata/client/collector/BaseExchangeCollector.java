package ms.maxwillia.cryptodata.client.collector;

import java.util.concurrent.BlockingQueue;

import ms.maxwillia.cryptodata.client.BaseExchangeClient;

import ms.maxwillia.cryptodata.model.CryptoTick;

public abstract class BaseExchangeCollector extends BaseExchangeClient implements ExchangeCollector {
    protected final BlockingQueue<CryptoTick> dataQueue;
    protected long lastSequenceNumber = -1;

    protected BaseExchangeCollector(String exchangeName, String currency, BlockingQueue<CryptoTick> dataQueue) {
        super(exchangeName, currency);
        this.dataQueue = dataQueue;
        setSymbolFromCurrency(currency);
        logger.info("Created client for {} with symbol: {}", exchangeName, currency);
    }

    protected void offerTick(CryptoTick tick) {
        if (!dataQueue.offer(tick)) {
            logger.warn("{}: Queue full, dropping tick for {}", exchangeName, tick.symbol());
        }
    }

    /**
     * Handle reconnection logic for the client
     */
    protected abstract void handleReconnect();

    /**
     * Initialize data collection parameters
     */
    protected abstract void initializeDataCollection();
}