package ms.maxwillia.cryptodata.client.collector.websocket;

import ms.maxwillia.cryptodata.client.collector.ExchangeCollector;

/**
 * Interface for WebSocket-specific client functionality
 */
public interface WebSocketCollector extends ExchangeCollector {
    /**
     * Connect to the WebSocket
     * @return true if connection successful
     */
    boolean connect();

    /**
     * Disconnect from the WebSocket
     */
    void disconnect();

    /**
     * Check if WebSocket is connected
     * @return true if connected
     */
    boolean isConnected();
}