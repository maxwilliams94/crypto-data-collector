package ms.maxwillia.cryptodata.client.websocket;

import ms.maxwillia.cryptodata.client.ExchangeClient;

/**
 * Interface for WebSocket-specific client functionality
 */
public interface WebSocketClient extends ExchangeClient {
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