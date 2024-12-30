package ms.maxwillia.cryptodata.websocket;

import java.net.URI;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.BlockingQueue;
import java.util.stream.Collectors;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import ms.maxwillia.cryptodata.model.CryptoTick;
import org.java_websocket.client.WebSocketClient;
import org.java_websocket.handshake.ServerHandshake;

// Updated Binance client using the new interface
public class BinanceWebSocketClient extends BaseExchangeClient {
    private static final String BINANCE_WS_URL = "wss://stream.binance.com:9443/ws/";
    private final ObjectMapper objectMapper = new ObjectMapper();
    private WebSocketClient wsClient;

    public BinanceWebSocketClient(List<String> symbols, BlockingQueue<CryptoTick> dataQueue) {
        super("Binance", symbols, dataQueue);
    }

    @Override
    public boolean connect() {
        try {
            setStatus(ConnectionStatus.CONNECTING);

            // Create composite stream name for multiple symbols
            String streamName = symbols.stream()
                .map(s -> s.toLowerCase() + "@trade")
                .collect(Collectors.joining("/"));

            wsClient = new WebSocketClient(URI.create(BINANCE_WS_URL + streamName)) {
                @Override
                public void onMessage(String message) {
                    handleMessage(message);
                }

                public void onOpen(ServerHandshake handshake) {
                    setStatus(ConnectionStatus.CONNECTED);
                    subscribeToSymbols();
                }

                @Override
                public void onClose(int code, String reason, boolean remote) {
                    logger.info("Binance connection closed: {} (code: {})", reason, code);
                    setStatus(ConnectionStatus.DISCONNECTED);
                    if (remote) {
                        handleReconnect();
                    }
                }

                @Override
                public void onError(Exception ex) {
                    logger.error("Binance WebSocket error", ex);
                    setStatus(ConnectionStatus.ERROR);
                }
            };

            return wsClient.connectBlocking();
        } catch (Exception e) {
            logger.error("Error connecting to Binance", e);
            setStatus(ConnectionStatus.ERROR);
            return false;
        }
    }

    @Override
    public void disconnect() {
        if (wsClient != null) {
            wsClient.close();
        }
        setStatus(ConnectionStatus.DISCONNECTED);
    }

    @Override
    public boolean isConnected() {
        return wsClient != null && wsClient.isOpen();
    }

    @Override
    public boolean reconnect() {
        disconnect();
        return connect();
    }

    @Override
    protected void handleReconnect() {
        new Thread(() -> {
            try {
                setStatus(ConnectionStatus.RECONNECTING);
                Thread.sleep(5000);  // Wait 5 seconds before reconnecting
                if (!isConnected()) {
                    reconnect();
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }).start();
    }

    @Override
    protected void subscribeToSymbols() {
        // Binance doesn't require explicit subscription for trade streams
        setStatus(ConnectionStatus.SUBSCRIBED);
    }

    private void handleMessage(String message) {
        try {
            JsonNode node = objectMapper.readTree(message);
            CryptoTick tick = new CryptoTick(
                node.get("s").asText(),
                node.get("p").asDouble(),
                node.get("q").asDouble(),
                node.get("T").asLong()
            );
            offerTick(tick);
        } catch (Exception e) {
            logger.error("Error processing message: " + message, e);
        }
    }

    // Factory method for convenience
    public static BinanceWebSocketClient forSymbols(BlockingQueue<CryptoTick> dataQueue, String... symbols) {
        return new BinanceWebSocketClient(Arrays.asList(symbols), dataQueue);
    }
}
