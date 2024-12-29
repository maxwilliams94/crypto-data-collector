package ms.maxwillia.cryptodata.websocket;

import com.cryptodata.model.CryptoTick;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.java_websocket.client.WebSocketClient;
import org.java_websocket.handshake.ServerHandshake;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.URI;
import java.util.concurrent.BlockingQueue;

public class BinanceWebSocketClient extends WebSocketClient {
    private static final Logger logger = LoggerFactory.getLogger(BinanceWebSocketClient.class);
    private static final String BINANCE_WS_URL = "wss://stream.binance.com:9443/ws/";
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final BlockingQueue<CryptoTick> dataQueue;
    private final String symbol;

    public BinanceWebSocketClient(String symbol, BlockingQueue<CryptoTick> dataQueue) {
        super(URI.create(BINANCE_WS_URL + symbol.toLowerCase() + "@trade"));
        this.symbol = symbol;
        this.dataQueue = dataQueue;
    }

    @Override
    public void onMessage(String message) {
        try {
            JsonNode node = objectMapper.readTree(message);
            CryptoTick tick = new CryptoTick(
                node.get("s").asText(),
                node.get("p").asDouble(),
                node.get("q").asDouble(),
                node.get("T").asLong()
            );
            if (!dataQueue.offer(tick)) {
                logger.warn("Queue full, dropping tick for {}", symbol);
            }
        } catch (Exception e) {
            logger.error("Error processing message: " + message, e);
        }
    }

    @Override
    public void onOpen(ServerHandshake handshake) {
        logger.info("Connected to Binance WebSocket for {}", symbol);
    }

    @Override
    public void onClose(int code, String reason, boolean remote) {
        logger.info("Connection closed for {}: {} (code: {})", symbol, reason, code);
    }

    @Override
    public void onError(Exception ex) {
        logger.error("WebSocket error for " + symbol, ex);
    }
}

