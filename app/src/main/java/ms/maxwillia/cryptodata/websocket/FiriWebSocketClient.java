package ms.maxwillia.cryptodata.websocket;

import ms.maxwillia.cryptodata.model.CryptoTick;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.java_websocket.client.WebSocketClient;
import org.java_websocket.handshake.ServerHandshake;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.URI;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.BlockingQueue;
import java.util.stream.Collectors;


import java.net.URI;
import java.util.*;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Collectors;

public class FiriWebSocketClient extends BaseExchangeClient {
    private static final String FIRI_REST_API_URL = "https://api.firi.com";
    private final ObjectMapper objectMapper = new ObjectMapper();

    // Latest USDC-NOK rate for USD conversion
    private final AtomicReference<Double> usdcNokRate = new AtomicReference<>(null);
    
    // Cache of latest prices for each market
    private final Map<String, Double> latestPrices = new ConcurrentHashMap<>();
    
    // Available markets on Firi
    private static final Set<String> AVAILABLE_MARKETS = new HashSet<>(Arrays.asList(
        "BTC-NOK",
        "ETH-NOK",
        "LTC-NOK",
        "XRP-NOK",
        "ADA-NOK",
        "DOT-NOK",
        "DAI-NOK",
        "USDC-NOK"  // Added USDC-NOK for USD price reference
    ));

    public FiriWebSocketClient(List<String> markets, BlockingQueue<CryptoTick> dataQueue) {
        super("Firi", validateAndAddUsdcMarket(markets), dataQueue);
    }

    // Ensure USDC-NOK is included in the subscription list
    private static List<String> validateAndAddUsdcMarket(List<String> requestedMarkets) {
        Set<String> markets = requestedMarkets.stream()
                .map(String::toUpperCase)
                .filter(AVAILABLE_MARKETS::contains).collect(Collectors.toSet());
        
        // Always add USDC-NOK for USD conversion
        markets.add("USDC-NOK");
        
        if (markets.size() == 1 && markets.contains("USDC-NOK")) {
            throw new IllegalArgumentException(
                "No valid markets provided besides USDC-NOK. Available markets are: " + 
                AVAILABLE_MARKETS.stream()
                    .filter(m -> !m.equals("USDC-NOK"))
                    .collect(Collectors.joining(", "))
            );
        }

        return new ArrayList<>(markets);
    }

    private void processTrade(JsonNode node) {
        try {
            JsonNode data = node.get("data");
            if (data != null) {
                String market = data.get("market").asText();
                double price = data.get("price").asDouble();
                double amount = data.get("amount").asDouble();
                long timestamp = data.get("timestamp").asLong() * 1000; // Convert to milliseconds

                // Update latest price for this market
                latestPrices.put(market, price);

                // If this is USDC-NOK, update our USD reference rate
                if ("USDC-NOK".equals(market)) {
                    usdcNokRate.set(price);
                    logger.debug("Updated USDC-NOK rate to: {}", price);
                    return; // Don't emit USDC-NOK ticks to the queue
                }

                // Only emit ticks if we have a valid USDC-NOK rate
                Double currentUsdcRate = usdcNokRate.get();
                if (currentUsdcRate != null && currentUsdcRate > 0) {
                    // Convert NOK price to USD
                    double priceInUsd = price / currentUsdcRate;
                    
                    // Create standardized tick with USD price
                    CryptoTick tick = new CryptoTick(
                        market,               // Original market (e.g., "BTC-NOK")
                        priceInUsd,          // Price in USD
                        amount,              // Original amount
                        timestamp,           // Timestamp
                        price,               // Original NOK price
                        currentUsdcRate      // USDC-NOK rate used for conversion
                    );
                    offerTick(tick);
                } else {
                    logger.warn("Received trade for {} but USDC-NOK rate not yet available", market);
                }
            }
        } catch (Exception e) {
            logger.error("Error creating tick from trade data", e);
        }
    }

    // Get the latest known USD price for a market
    public Optional<Double> getLatestUsdPrice(String market) {
        Double nokPrice = latestPrices.get(market);
        Double currentUsdcRate = usdcNokRate.get();
        
        if (nokPrice != null && currentUsdcRate != null && currentUsdcRate > 0) {
            return Optional.of(nokPrice / currentUsdcRate);
        }
        return Optional.empty();
    }

    // Get the latest USDC-NOK rate
    public Optional<Double> getLatestUsdcNokRate() {
        Double rate = usdcNokRate.get();
        return rate != null ? Optional.of(rate) : Optional.empty();
    }

    // Check if we have a valid USDC-NOK rate
    public boolean hasValidUsdRate() {
        Double rate = usdcNokRate.get();
        return rate != null && rate > 0;
    }

    @Override
    public boolean connect() {
        try {
            setStatus(ConnectionStatus.CONNECTING);

        } catch (Exception e) {
            logger.error("Error connecting to Firi", e);
            setStatus(ConnectionStatus.ERROR);
            return false;
        }
        return false;
    }

    @Override
    protected void subscribeToSymbols() {
        try {
            setStatus(ConnectionStatus.SUBSCRIBING);
            
            setStatus(ConnectionStatus.SUBSCRIBED);
        } catch (Exception e) {
            logger.error("Error subscribing to markets", e);
            setStatus(ConnectionStatus.ERROR);
        }
    }

    private void handleMessage(String message) {
        try {
            JsonNode node = objectMapper.readTree(message);

            // Check if it's a trade message
            if (node.has("event") && "trade".equals(node.get("event").asText())) {
                processTrade(node);
            } else if (node.has("event") && "error".equals(node.get("event").asText())) {
                logger.error("Firi error message: {}", message);
                setStatus(ConnectionStatus.ERROR);
            }
        } catch (Exception e) {
            logger.error("Error processing message: " + message, e);
        }
    }
    @Override
    public void disconnect() {
        setStatus(ConnectionStatus.DISCONNECTED);
    }

    @Override
    public boolean isConnected() {
        return false;
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

    // Factory method for convenience
    public static FiriWebSocketClient forSymbols(BlockingQueue<CryptoTick> dataQueue, String... symbols) {
        return new FiriWebSocketClient(Arrays.asList(symbols), dataQueue);
    }
}
