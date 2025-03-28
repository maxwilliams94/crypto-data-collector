package ms.maxwillia.cryptodata.client.collector;

import java.util.concurrent.BlockingQueue;

import ms.maxwillia.cryptodata.client.BaseExchangeClient;

import ms.maxwillia.cryptodata.model.CryptoTick;

public abstract class BaseExchangeCollector extends BaseExchangeClient implements ExchangeCollector {
    protected final BlockingQueue<CryptoTick> dataQueue;
    protected long lastSequenceNumber = -1;

    protected BaseExchangeCollector(String exchangeName, String assetCurrency, String intermediateCurrency, BlockingQueue<CryptoTick> dataQueue) {
        super(exchangeName, assetCurrency, intermediateCurrency);
        this.dataQueue = dataQueue;
        logger.info("Created collector for {}", this);
    }

    protected void offerTick(CryptoTick tick) {
        if (!dataQueue.offer(tick)) {
            logger.warn("{}: Queue full, dropping tick for {}", getExchangeName(), tick.symbol());
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

    public String toString() {
        return String.format("%s: %s: %s %s",this.getClass(), this.getExchangeName(), this.getExchangeTradePair(), this.getExchangeIntermediatePair());
    }


}