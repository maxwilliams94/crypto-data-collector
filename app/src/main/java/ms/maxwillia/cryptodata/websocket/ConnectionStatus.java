package ms.maxwillia.cryptodata.websocket;

// Connection status enum
public enum ConnectionStatus {
    DISCONNECTED,
    CONNECTING,
    CONNECTED,
    SUBSCRIBING,
    SUBSCRIBED,
    ERROR,
    RECONNECTING
}
