package ms.maxwillia.cryptodata.websocket;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.java_websocket.client.WebSocketClient;
import org.java_websocket.handshake.ServerHandshake;
import java.net.URI;
import java.util.concurrent.BlockingQueue;

import ms.maxwillia.cryptodata.model.CryptoTick;

public class CoinbaseWebSocketClient extends BaseExchangeClient {
    private static final String COINBASE_WS_URL = "wss://advanced-trade-ws.coinbase.com";
    private final ObjectMapper objectMapper = new ObjectMapper();
    private WebSocketClient wsClient;

    public CoinbaseWebSocketClient(String symbol, BlockingQueue<CryptoTick> dataQueue) {
        super("Coinbase", symbol, dataQueue);
    }

    @Override
    public boolean connect() {
        try {
            setStatus(ConnectionStatus.CONNECTING);
            wsClient = createWebSocketClient();
            return wsClient.connectBlocking();
        } catch (Exception e) {
            logger.error("Error connecting to Coinbase", e);
            setStatus(ConnectionStatus.ERROR);
            return false;
        }
    }

    // Protected for testing
    protected WebSocketClient createWebSocketClient() {
        return new WebSocketClient(URI.create(COINBASE_WS_URL)) {
            @Override
            public void onMessage(String message) {
                handleMessage(message);
            }

            @Override
            public void onOpen(ServerHandshake handshake) {
                setStatus(ConnectionStatus.CONNECTED);
                //TODO Definitely shouldn't subscribe immediately onOpen - no point in a CONNECTED state
                subscribeToSymbol();
            }

            @Override
            public void onClose(int code, String reason, boolean remote) {
                logger.info("Coinbase connection closed: {} (code: {})", reason, code);
                setStatus(ConnectionStatus.DISCONNECTED);
                if (remote) {
                    handleReconnect();
                }
            }

            @Override
            public void onError(Exception ex) {
                logger.error("Coinbase WebSocket error", ex);
                setStatus(ConnectionStatus.ERROR);
            }
        };
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
                Thread.sleep(100);
                if (!isConnected()) {
                    reconnect();
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }).start();
    }

    @Override
    protected void subscribeToSymbol() {
        try {
            setStatus(ConnectionStatus.SUBSCRIBING);
            ObjectNode subscribeMessage = createSubscribeMessage();
            wsClient.send(objectMapper.writeValueAsString(subscribeMessage));
            logger.info("Sent subscription request for symbol: {}", symbol);
        } catch (Exception e) {
            logger.error("Error subscribing to symbols", e);
            setStatus(ConnectionStatus.ERROR);
        }
    }

    // Package-private for testing
    ObjectNode createSubscribeMessage() {
        ObjectNode subscribeMessage = objectMapper.createObjectNode();
        subscribeMessage.put("type", "subscribe");
        subscribeMessage.put("channel", "ticker");
        subscribeMessage.putArray("product_ids").add(symbol.toUpperCase());
        return subscribeMessage;
    }

    // Package-private for testing
    void handleMessage(String message) {
        try {
            JsonNode node = objectMapper.readTree(message);
            logger.debug("Received message: {}", message);

            if (node.has("sequence_num") &&
                    node.get("sequence_num").asLong() <= lastSequenceNumber) {
                logger.debug("Skipping message: sequence number not greater than last");
                return;
            }

            String channel = node.get("channel").asText();
            switch (channel) {
                case "ticker":
                    if (getStatus() != ConnectionStatus.SUBSCRIBING) {
                        processTicker(node);
                    }
                    break;
                case "subscriptions":
                    processSubscription(node);
                    break;
                default:
                    logger.error("Unexpected channel: {}", channel);
            }
        } catch (Exception e) {
            logger.error("Error processing message: {}", message, e);
        }
    }

    private void processTicker(JsonNode node) {
        try {
            JsonNode tickerEvent = node.get("events").get(0).get("tickers").get(0);
            String timestampString = node.get("timestamp").asText();
            lastSequenceNumber = node.get("sequence_num").asLong();

            CryptoTick tick = new CryptoTick(
                    tickerEvent.get("product_id").asText(),
                    tickerEvent.get("price").asDouble(),
                    tickerEvent.get("volume_24_h").asDouble(),
                    tickerEvent.get("best_bid").asDouble(),
                    tickerEvent.get("best_bid_quantity").asDouble(),
                    tickerEvent.get("best_ask").asDouble(),
                    tickerEvent.get("best_ask_quantity").asDouble(),
                    parseTimestamp(timestampString)
            );
            offerTick(tick);
        } catch (Exception e) {
            logger.error("Error processing ticker", e);
        }
    }

    private void processSubscription(JsonNode node) {
        try {
            lastSequenceNumber = node.get("sequence_num").asLong();
            JsonNode events = node.get("events");

            if (!events.isEmpty() && events.get(0).has("subscriptions")) {
                JsonNode subscriptions = events.get(0).get("subscriptions").get("ticker");
                if (!subscriptions.isEmpty() &&
                        subscriptions.get(0).asText().equals(symbol.toUpperCase())) {
                    setStatus(ConnectionStatus.SUBSCRIBED);
                } else {
                    logger.error("Unexpected subscription response");
                    setStatus(ConnectionStatus.ERROR);
                }
            }
        } catch (Exception e) {
            logger.error("Error processing subscription", e);
            setStatus(ConnectionStatus.ERROR);
        }
    }

    private long parseTimestamp(String timestamp) {
        return java.time.Instant.parse(timestamp).toEpochMilli();
    }

    // Factory method for convenience
    public static ExchangeWebSocketClient forSymbol(BlockingQueue<CryptoTick> dataQueue,
                                                    String symbol) {
        return new CoinbaseWebSocketClient(symbol, dataQueue);
    }
}