package ms.maxwillia.cryptodata.client.collector.websocket;

import ms.maxwillia.cryptodata.client.collector.BaseExchangeCollector;
import ms.maxwillia.cryptodata.model.CryptoTick;
import ms.maxwillia.cryptodata.client.ClientStatus;

import java.util.concurrent.BlockingQueue;

/**
 * Base implementation for WebSocket clients
 */
public abstract class BaseWebSocketCollector extends BaseExchangeCollector implements WebSocketCollector {
    protected BaseWebSocketCollector(String exchangeName, String assetCurrency, String intermediateCurrency, BlockingQueue<CryptoTick> dataQueue) {
        super(exchangeName, assetCurrency, intermediateCurrency, dataQueue);
    }

    @Override
    public boolean startDataCollection() {
        if (!isConnected()) {
            return false;
        }
        initializeDataCollection();
        return true;
    }

    @Override
    public void stopDataCollection() {
        disconnect();
    }

    @Override
    public boolean isCollecting() {
        return isConnected() && getStatus() == ClientStatus.COLLECTING;
    }

    /**
     * Subscribe to market data
     *
     * @return true if successful
     */
    protected abstract boolean subscribeToMarketData();
}