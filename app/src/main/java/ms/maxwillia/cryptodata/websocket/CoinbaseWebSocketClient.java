package ms.maxwillia.cryptodata.websocket;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.java_websocket.client.WebSocketClient;
import org.java_websocket.handshake.ServerHandshake;

import java.net.URI;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.BlockingQueue;
import java.util.stream.Collectors;

import ms.maxwillia.cryptodata.model.CryptoTick;


public class CoinbaseWebSocketClient extends BaseExchangeClient {
    private static final String COINBASE_WS_URL = "wss://ws-feed.pro.coinbase.com";
    private final ObjectMapper objectMapper = new ObjectMapper();
    private WebSocketClient wsClient;

    public CoinbaseWebSocketClient(List<String> symbols, BlockingQueue<CryptoTick> dataQueue) {
        super("Coinbase", symbols, dataQueue);
    }

    @Override
    public boolean connect() {
        try {
            setStatus(ConnectionStatus.CONNECTING);
            
            wsClient = new WebSocketClient(URI.create(COINBASE_WS_URL)) {
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

            return wsClient.connectBlocking();
        } catch (Exception e) {
            logger.error("Error connecting to Coinbase", e);
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
                Thread.sleep(5000);
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
        try {
            setStatus(ConnectionStatus.SUBSCRIBING);
            
            ObjectNode subscribeMessage = objectMapper.createObjectNode();
            subscribeMessage.put("type", "subscribe");
            
            // Convert symbols to Coinbase format
            List<JsonNode> coinbaseSymbols = symbols.stream()
                    .map(s -> s.replace("usdt", "-USD").toUpperCase())
                    .map(symbol -> objectMapper.createObjectNode().put("id", symbol))
                    .collect(Collectors.toList());

            subscribeMessage.putArray("product_ids").addAll(coinbaseSymbols);
            subscribeMessage.putArray("channels").add("matches");

            wsClient.send(objectMapper.writeValueAsString(subscribeMessage));
            logger.info("Sent subscription request for symbols: {}", coinbaseSymbols);
            
            setStatus(ConnectionStatus.SUBSCRIBED);
        } catch (Exception e) {
            logger.error("Error subscribing to symbols", e);
            setStatus(ConnectionStatus.ERROR);
        }
    }

    private void handleMessage(String message) {
        try {
            JsonNode node = objectMapper.readTree(message);
            String type = node.get("type").asText();
            
            if ("match".equals(type)) {
                processTrade(node);
            } else if ("subscriptions".equals(type)) {
                logger.info("Subscription confirmed: {}", message);
            } else if ("error".equals(type)) {
                logger.error("Coinbase error message: {}", message);
                setStatus(ConnectionStatus.ERROR);
            }
        } catch (Exception e) {
            logger.error("Error processing message: {}", message, e);
        }
    }

    private void processTrade(JsonNode node) {
        try {
            CryptoTick tick = new CryptoTick(
                node.get("product_id").asText().replace("-USD", "USDT"),
                node.get("price").asDouble(),
                node.get("size").asDouble(),
                parseTimestamp(node.get("time").asText())
            );
            offerTick(tick);
        } catch (Exception e) {
            logger.error("Error creating tick from trade data", e);
        }
    }

    private long parseTimestamp(String timestamp) {
        return java.time.Instant.parse(timestamp).toEpochMilli();
    }

    // Factory method for convenience
    public static ExchangeWebSocketClient forSymbols(BlockingQueue<CryptoTick> dataQueue, String... symbols) {
        return new CoinbaseWebSocketClient(Arrays.asList(symbols), dataQueue);
    }
}
