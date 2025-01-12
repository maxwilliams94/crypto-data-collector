package ms.maxwillia.cryptodata.client.rest;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import ms.maxwillia.cryptodata.model.CryptoTick;
import ms.maxwillia.cryptodata.client.ClientStatus;
import okhttp3.*;

import java.io.IOException;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;


public class FiriRestClient extends BaseRestClient {
    private static final Logger logger = LoggerFactory.getLogger(FiriRestClient.class);
    private static final String DEFAULT_FIRI_API_BASE_URL = "https://api.firi.com/v2";
    private final String baseUrl;
    private final String FIRI_REST_API_ORDER_BOOK_URL;
    private final String FIRI_REST_API_TICKER_URL;
    private static final String USD_SYMBOL = "USDCNOK";
    private static final long USDC_RATE_UPDATE_INTERVAL_MS = 5 * 60 * 1000; // 5 minutes
    protected static final long USD_MAX_STALE_MS = 30 * 60 * 1000; // 30 minutes
    private static final int MAX_RETRIES = 3;
    private static final long RETRY_DELAY_MS = 500; // 1 second between retries

    private final AtomicReference<Double> usdRate;
    private long lastRateUpdateTime;
    private final OkHttpClient httpClient;
    private final ObjectMapper objectMapper;

    public FiriRestClient(String symbol, BlockingQueue<CryptoTick> dataQueue) {
        this(symbol, dataQueue, DEFAULT_FIRI_API_BASE_URL);
    }

    public FiriRestClient(String symbol, BlockingQueue<CryptoTick> dataQueue, String baseUrl) {
        super("Firi", symbol, dataQueue);
        this.baseUrl = baseUrl;
        this.FIRI_REST_API_ORDER_BOOK_URL = String.format("%s/markets/%s/depth", baseUrl, getExchangeSymbol());
        this.FIRI_REST_API_TICKER_URL = String.format("%s/markets/%s/ticker", baseUrl, getExchangeSymbol());
        this.usdRate = new AtomicReference<>(-1.0);
        this.httpClient = new OkHttpClient.Builder()
                .connectTimeout(10, TimeUnit.SECONDS)
                .readTimeout(10, TimeUnit.SECONDS)
                .writeTimeout(10, TimeUnit.SECONDS)
                .build();
        this.objectMapper = new ObjectMapper();
    }

    @Override
    protected void setSymbolFromCurrency(String currency) {
        this.symbol = currency + "-NOK";
    }

    @Override
    public String getExchangeSymbol() {
        return symbol.replace("-", "");
    }

    @Override
    public boolean configure() {
        try {
            updateUsdRate();
            return usdRate.get() > 0;
        } catch (Exception e) {
            logger.error("Error configuring Firi client: {}", e.getMessage());
            setStatus(ClientStatus.ERROR);
            return false;
        }
    }

    @Override
    public boolean testConnection() {
        try {
            Request request = new Request.Builder()
                    .url(baseUrl + "/markets")
                    .build();
            try (Response response = httpClient.newCall(request).execute()) {
                if (!response.isSuccessful()) {
                    logger.error("Test connection failed with response code: {}", response.code());
                    return false;
                }
                return true;
            }
        } catch (IOException e) {
            logger.error("Failed to test Firi connection: {}", e.getMessage());
            return false;
        }
    }

    @Override
    public void updateUsdRate() {
        if (System.currentTimeMillis() - lastRateUpdateTime < USDC_RATE_UPDATE_INTERVAL_MS) {
            return; // Rate is still fresh
        }

        String url = String.format("%s/markets/%s/ticker", baseUrl, USD_SYMBOL);
        Request request = new Request.Builder().url(url).build();

        for (int attempt = 0; attempt < MAX_RETRIES; attempt++) {
            try (Response response = httpClient.newCall(request).execute()) {
                if (!response.isSuccessful()) {
                    throw new IOException("Unexpected response code: " + response.code());
                }

                ResponseBody responseBody = response.body();
                if (responseBody == null) {
                    throw new IOException("Empty response body");
                }

                JsonNode rateData = objectMapper.readTree(responseBody.string());
                if (!rateData.has("last")) {
                    throw new IOException("Invalid rate data format - missing 'last' field");
                }

                double newRate = rateData.get("last").asDouble();
                if (newRate <= 0) {
                    throw new IOException("Invalid rate value: " + newRate);
                }

                usdRate.set(newRate);
                lastRateUpdateTime = System.currentTimeMillis();
                logger.debug("Successfully updated USD/NOK rate to: {}", newRate);
                return;

            } catch (IOException e) {
                logger.warn("Failed to update USD/NOK rate (attempt {}/{}): {}",
                        attempt, MAX_RETRIES, e.getMessage());

                try {
                    Thread.sleep(RETRY_DELAY_MS);
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                    logger.warn("Rate update interrupted during retry delay");
                    return;
                }
            }
        }
        if (System.currentTimeMillis() - lastRateUpdateTime > USD_MAX_STALE_MS) {
            logger.warn("USD rate is too stale.");
            stopDataCollection();
            setStatus(ClientStatus.ERROR);
        }
    }

    @Override
    protected JsonNode fetchOrderBook() throws IOException {
        try (Response response = httpClient.newCall(new Request.Builder()
                        .url(FIRI_REST_API_ORDER_BOOK_URL)
                        .build())
                .execute()) {

            if (!response.isSuccessful()) {
                throw new IOException("Failed to fetch order book. Response code: " + response.code());
            }

            ResponseBody responseBody = response.body();
            if (responseBody == null) {
                throw new IOException("Empty order book response body");
            }

            JsonNode orderBook = objectMapper.readTree(responseBody.string());
            if (!orderBook.has("bids") || !orderBook.has("asks")) {
                throw new IOException("Invalid order book data format");
            }

            return orderBook;
        }
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

            // Validate bid/ask data
            if (bestBid.size() < 2 || bestAsk.size() < 2) {
                throw new IOException("Invalid bid/ask data format");
            }

            // Get ticker data for last price and volume
            JsonNode tickerData = fetchTickerData();

            return new CryptoTick(
                    symbol,              // symbol
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

    private JsonNode fetchTickerData() throws IOException {
        try (Response response = httpClient.newCall(new Request.Builder()
                        .url(FIRI_REST_API_TICKER_URL)
                        .build())
                .execute()) {

            if (!response.isSuccessful()) {
                throw new IOException("Failed to fetch ticker data. Response code: " + response.code());
            }

            ResponseBody responseBody = response.body();
            if (responseBody == null) {
                throw new IOException("Empty ticker response body");
            }

            JsonNode tickerData = objectMapper.readTree(responseBody.string());
            if (!tickerData.has("last") || !tickerData.has("volume")) {
                throw new IOException("Invalid ticker data format");
            }

            return tickerData;
        }
    }

    @Override
    protected void initializeDataCollection() {
        try {
            updateUsdRate();
            if (usdRate.get() <= 0) {
                throw new RuntimeException("Invalid USD rate after initialization");
            }
        } catch (Exception e) {
            logger.error("Failed to initialize data collection: {}", e.getMessage());
            throw new RuntimeException("Data collection initialization failed", e);
        }
    }

    @Override
    protected void scheduleDataCollection() {
        scheduler.scheduleAtFixedRate(this::pollMarketData, 0, 1, TimeUnit.SECONDS);

        // Schedule regular rate updates
        scheduler.scheduleAtFixedRate(() -> {
            try {
                updateUsdRate();
            } catch (Exception e) {
                logger.error("Error during scheduled rate update: {}", e.getMessage());
            }
        }, USDC_RATE_UPDATE_INTERVAL_MS, USDC_RATE_UPDATE_INTERVAL_MS, TimeUnit.MILLISECONDS);
    }
}