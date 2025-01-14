package ms.maxwillia.cryptodata.client.websocket;

import ms.maxwillia.cryptodata.client.BaseExchangeClient;
import ms.maxwillia.cryptodata.model.CryptoTick;
import ms.maxwillia.cryptodata.client.ClientStatus;

import java.util.concurrent.BlockingQueue;

/**
 * Base implementation for WebSocket clients
 */
public abstract class BaseWebSocketClient extends BaseExchangeClient implements WebSocketClient {
    protected BaseWebSocketClient(String exchangeName, String currency, BlockingQueue<CryptoTick> dataQueue) {
        super(exchangeName, currency, dataQueue);
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
        return isConnected() && status == ClientStatus.COLLECTING;
    }

    /**
     * Subscribe to market data
     *
     * @return true if successful
     */
    protected abstract boolean subscribeToMarketData();
}