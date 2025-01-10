package ms.maxwillia.cryptodata.client.websocket;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import ms.maxwillia.cryptodata.client.ClientStatus;
import org.java_websocket.client.WebSocketClient;
import org.java_websocket.handshake.ServerHandshake;
import java.net.URI;
import java.util.concurrent.BlockingQueue;

import ms.maxwillia.cryptodata.model.CryptoTick;

public class CoinbaseWebSocketClient extends BaseWebSocketClient {
    private static final String COINBASE_WS_URL = "wss://advanced-trade-ws.coinbase.com";
    private final ObjectMapper objectMapper = new ObjectMapper();
    private WebSocketClient wsClient;
    private double usdRate = 1.0;

    public CoinbaseWebSocketClient(String symbol, BlockingQueue<CryptoTick> dataQueue) {
        super("Coinbase", symbol, dataQueue);
    }

    @Override
    protected void setSymbolFromCurrency(String currency) {
        this.symbol = currency + "-USD";
    }

    @Override
    public String getExchangeSymbol() {
        // Coinbase uses BTC-USD format
        return symbol;
    }

    @Override
    public void updateUsdRate() {
        if (getSubscribedSymbol().contains("USD")) {
            usdRate = 1.0;
        } else {
            // TODO: Implement
        }
    }

    @Override
    public boolean initialize() {
        return configure();
    }

    @Override
    public boolean configure() {
        return true;
    }

    @Override
    public boolean connect() {
        try {
            wsClient = createWebSocketClient();
            return wsClient.connectBlocking();
        } catch (Exception e) {
            logger.error("Error connecting to Coinbase", e);
            setStatus(ClientStatus.ERROR);
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
                // Immediately subscribe as soon as connection is established
                // Status of COLLECTING is set once subscription is successful
                subscribeToMarketData();

            }

            @Override
            public void onClose(int code, String reason, boolean remote) {
                logger.info("Coinbase connection closed: {} (code: {})", reason, code);
                setStatus(ClientStatus.STOPPED);
                if (remote) {
                    handleReconnect();
                }
            }

            @Override
            public void onError(Exception ex) {
                logger.error("Coinbase WebSocket error", ex);
                setStatus(ClientStatus.ERROR);
            }
        };
    }

    @Override
    public void disconnect() {
        if (wsClient != null) {
            wsClient.close();
        }
        setStatus(ClientStatus.STOPPED);
    }

    @Override
    public boolean isConnected() {
        return wsClient != null && wsClient.isOpen();
    }

    public boolean reconnect() {
        disconnect();
        return connect();
    }

    @Override
    protected void handleReconnect() {
        new Thread(() -> {
            try {
                setStatus(ClientStatus.RECONNECTING);
                Thread.sleep(100);
                if (!isConnected()) {
                    if (!reconnect()) {
                        setStatus(ClientStatus.ERROR);
                    }
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }).start();
    }

    @Override
    protected void initializeDataCollection() {

    }

    protected boolean subscribeToMarketData() {
        try {
            setStatus(ClientStatus.STARTING);
            ObjectNode subscribeMessage = createSubscribeMessage();
            wsClient.send(objectMapper.writeValueAsString(subscribeMessage));
            logger.info("Sent subscription request for symbol: {}", symbol);
        } catch (Exception e) {
            logger.error("Error subscribing to symbols", e);
            setStatus(ClientStatus.ERROR);
            return false;
        }
        return true;
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
                    if (getStatus() == ClientStatus.COLLECTING) {
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
                    parseTimestamp(timestampString),
                    tickerEvent.get("price").asDouble(),
                    usdRate
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
                    setStatus(ClientStatus.COLLECTING);
                } else {
                    logger.error("Unexpected subscription response");
                    setStatus(ClientStatus.ERROR);
                }
            }
        } catch (Exception e) {
            logger.error("Error processing subscription", e);
            setStatus(ClientStatus.ERROR);
        }
    }

    private long parseTimestamp(String timestamp) {
        return java.time.Instant.parse(timestamp).toEpochMilli();
    }


    @Override
    public boolean startDataCollection() {
        return connect();
    }

    @Override
    public void stopDataCollection() {
        disconnect();
    }

    @Override
    public boolean isCollecting() {
        return getStatus() == ClientStatus.COLLECTING && isConnected();
    }

    // Factory method for convenience
    public static CoinbaseWebSocketClient forSymbol(BlockingQueue<CryptoTick> dataQueue,
                                            String symbol) {
        return new CoinbaseWebSocketClient(symbol, dataQueue);
    }
}