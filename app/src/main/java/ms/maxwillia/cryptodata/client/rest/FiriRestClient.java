package ms.maxwillia.cryptodata.client.rest;

import com.fasterxml.jackson.databind.JsonNode;
import ms.maxwillia.cryptodata.model.CryptoTick;

import java.io.IOException;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.atomic.AtomicReference;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class FiriRestClient extends BaseRestClient {
    private static final Logger logger = LoggerFactory.getLogger(FiriRestClient.class);
    private static final String FIRI_API_BASE_URL = "https://api.firi.com/v2";
    private final String FIRI_REST_API_ORDER_BOOK_URL;
    private final String FIRI_REST_API_TICKER_URL;
    private static final String USD_SYMBOL = "USDCNOK";
    private static final long USDC_RATE_UPDATE_INTERVAL_MS = 5 * 60 * 1000; // 5 minutes

    private final AtomicReference<Double> usdRate;
    private long lastRateUpdateTime;

    public FiriRestClient(String symbol, BlockingQueue<CryptoTick> dataQueue) {
        super("Firi", symbol, dataQueue);
        this.FIRI_REST_API_ORDER_BOOK_URL = String.format("%s/markets/%s/depth", FIRI_API_BASE_URL, getExchangeSymbol());
        this.FIRI_REST_API_TICKER_URL = String.format("%s/markets/%s/ticker", FIRI_API_BASE_URL, getExchangeSymbol());
        this.usdRate = new AtomicReference<>(-1.0);
    }

    @Override
    public String getExchangeSymbol() {
        return symbol.replace("-", "");
    }

    @Override
    public boolean configure() {
        try {
            updateUsdRate();
            return true;
        } catch (Exception e) {
            logger.error("Error configuring Firi client: {}", e.getMessage());
            return false;
        }
    }

    @Override
    public boolean testConnection() {
        try {
            JsonNode response = makeRequest(FIRI_REST_API_TICKER_URL);
            return response != null && !response.isEmpty();
        } catch (IOException e) {
            logger.error("Failed to test Firi connection: {}", e.getMessage());
            return false;
        }
    }

    @Override
    public void updateUsdRate() {
        if (System.currentTimeMillis() - lastRateUpdateTime < USDC_RATE_UPDATE_INTERVAL_MS) {
            return;
        }

        try {
            String usdcNokUrl = String.format("%s/markets/%s/ticker", FIRI_API_BASE_URL, USD_SYMBOL);
            JsonNode response = makeRequest(usdcNokUrl);
            if (response.has("last")) {
                usdRate.set(response.get("last").asDouble());
                lastRateUpdateTime = System.currentTimeMillis();
                logger.debug("Updated USD/NOK rate to: {}", usdRate.get());
            }
        } catch (Exception e) {
            logger.error("Error updating USD/NOK rate: {}", e.getMessage());
            throw new RuntimeException("Failed to update USD/NOK rate", e);
        }
    }

    @Override
    protected JsonNode fetchOrderBook() throws IOException {
        return makeRequest(FIRI_REST_API_ORDER_BOOK_URL);
    }

    @Override
    protected CryptoTick processOrderBookData(JsonNode data) throws IOException {
        try {
            if (data == null || !data.has("bids") || !data.has("asks")) {
                throw new IOException("Invalid order book data format");
            }

            JsonNode bids = data.get("bids");
            JsonNode asks = data.get("asks");

            if (bids.isEmpty() || asks.isEmpty()) {
                logger.warn("Empty order book for {}", symbol);
                return null;
            }

            // Get best bid and ask
            JsonNode bestBid = bids.get(0);
            JsonNode bestAsk = asks.get(0);

            // Get ticker data for last price and volume
            JsonNode tickerData = makeRequest(FIRI_REST_API_TICKER_URL);

            return new CryptoTick(
                    symbol,                           // symbol
                    tickerData.get("volume").asDouble(), // volume_24h
                    bestBid.get(0).asDouble(),       // best_bid
                    bestBid.get(1).asDouble(),       // best_bid_quantity
                    bestAsk.get(0).asDouble(),       // best_ask
                    bestAsk.get(1).asDouble(),       // best_ask_quantity
                    System.currentTimeMillis(),       // timestamp
                    tickerData.get("last").asDouble(),// nativePrice (NOK)
                    usdRate.get()                     // usdRate
            );
        } catch (Exception e) {
            logger.error("Error processing order book data: {}", e.getMessage());
            throw new IOException("Failed to process order book data", e);
        }
    }

    @Override
    protected void initializeDataCollection() {
        // Nothing specific needed here as the base class handles the core initialization
    }
}