package ms.maxwillia.cryptodata.websocket;

import ms.maxwillia.cryptodata.model.CryptoTick;
import ms.maxwillia.cryptodata.model.CryptoTick;
import java.util.List;
import java.util.concurrent.BlockingQueue;

// Core interface for all exchange clients
public interface ExchangeWebSocketClient {
    /**
     * Connect to the exchange websocket
     * @return true if connection was successful
     */
    boolean connect();
    
    /**
     * Disconnect from the exchange
     */
    void disconnect();
    
    /**
     * Get the exchange name
     * @return exchange name
     */
    String getExchangeName();
    
    /**
     * Get the subscribed symbols
     * @return list of symbols
     */
    List<String> getSubscribedSymbols();
    
    /**
     * Check if client is connected
     * @return true if connected
     */
    boolean isConnected();
    
    /**
     * Get connection status
     * @return current status
     */
    ConnectionStatus getStatus();
    
    /**
     * Reconnect to the exchange
     * @return true if reconnection was successful
     */
    boolean reconnect();

    static ExchangeWebSocketClient forSymbols(BlockingQueue<CryptoTick> dataQueue, String... symbols) {
        throw new UnsupportedOperationException("Factory method must be implemented by concrete exchange clients");
    }
}

