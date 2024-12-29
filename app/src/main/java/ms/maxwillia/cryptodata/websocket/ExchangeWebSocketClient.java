package ms.maxwillia.cryptodata.websocket;

import com.cryptodata.model.CryptoTick;
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
}

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

// Abstract base implementation
public abstract class BaseExchangeClient implements ExchangeWebSocketClient {
    protected final Logger logger = LoggerFactory.getLogger(getClass());
    protected final BlockingQueue<CryptoTick> dataQueue;
    protected final List<String> symbols;
    protected volatile ConnectionStatus status;
    protected final String exchangeName;

    protected BaseExchangeClient(String exchangeName, List<String> symbols, BlockingQueue<CryptoTick> dataQueue) {
        this.exchangeName = exchangeName;
        this.symbols = symbols;
        this.dataQueue = dataQueue;
        this.status = ConnectionStatus.DISCONNECTED;
    }

    @Override
    public String getExchangeName() {
        return exchangeName;
    }

    @Override
    public List<String> getSubscribedSymbols() {
        return new ArrayList<>(symbols);
    }

    @Override
    public ConnectionStatus getStatus() {
        return status;
    }

    protected void setStatus(ConnectionStatus newStatus) {
        ConnectionStatus oldStatus = this.status;
        this.status = newStatus;
        logger.info("{}: Status changed from {} to {}", exchangeName, oldStatus, newStatus);
    }

    protected void offerTick(CryptoTick tick) {
        if (!dataQueue.offer(tick)) {
            logger.warn("{}: Queue full, dropping tick for {}", exchangeName, tick.symbol());
        }
    }

    protected abstract void handleReconnect();
    protected abstract void subscribeToSymbols();
}

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

                @Override
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
