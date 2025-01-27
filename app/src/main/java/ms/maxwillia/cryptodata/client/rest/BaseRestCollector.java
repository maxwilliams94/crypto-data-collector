package ms.maxwillia.cryptodata.client.rest;

import ms.maxwillia.cryptodata.client.BaseExchangeCollector;
import ms.maxwillia.cryptodata.client.ClientStatus;
import ms.maxwillia.cryptodata.model.CryptoTick;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.JsonNode;

import java.io.IOException;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public abstract class BaseRestCollector extends BaseExchangeCollector implements RestCollector {
    private static final Logger logger = LoggerFactory.getLogger(BaseRestCollector.class);

    protected final ScheduledExecutorService scheduler;
    protected final OkHttpClient httpClient;
    protected final ObjectMapper objectMapper;
    protected final AtomicBoolean isPolling;
    protected ScheduledFuture<?> pollingTask;
    protected long pollingIntervalMs;
    protected static final long DEFAULT_POLLING_INTERVAL_MS = 1000; // 1 second default

    protected BaseRestCollector(String exchangeName, String currency, BlockingQueue<CryptoTick> dataQueue) {
        super(exchangeName, currency, dataQueue);
        this.scheduler = Executors.newScheduledThreadPool(2);
        this.httpClient = new OkHttpClient.Builder()
                .connectTimeout(10, TimeUnit.SECONDS)
                .readTimeout(10, TimeUnit.SECONDS)
                .writeTimeout(10, TimeUnit.SECONDS)
                .build();
        this.objectMapper = new ObjectMapper();
        this.isPolling = new AtomicBoolean(false);
        this.pollingIntervalMs = DEFAULT_POLLING_INTERVAL_MS;
    }

    @Override
    public boolean initialize() {
        setStatus(ClientStatus.STARTING);
        return configure() && testConnection();
    }

    @Override
    public boolean startDataCollection() {
        if (getStatus() != ClientStatus.STARTING) {
            logger.warn("Client not in STARTING state, cannot start data collection");
            return false;
        }

        try {
            scheduleDataCollection();
            setStatus(ClientStatus.COLLECTING);
            return true;
        } catch (Exception e) {
            logger.error("Failed to start data collection: {}", e.getMessage());
            setStatus(ClientStatus.ERROR);
            return false;
        }
    }

    @Override
    public void stopDataCollection() {
        isPolling.set(false);
        if (pollingTask != null) {
            pollingTask.cancel(true);
        }
        scheduler.shutdown();
        try {
            if (!scheduler.awaitTermination(5, TimeUnit.SECONDS)) {
                scheduler.shutdownNow();
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            scheduler.shutdownNow();
        }
        setStatus(ClientStatus.STOPPED);
    }

    @Override
    public boolean isCollecting() {
        return isPolling.get() && getStatus() == ClientStatus.COLLECTING;
    }

    protected void scheduleDataCollection() {
        if (isPolling.compareAndSet(false, true)) {
            pollingTask = scheduler.scheduleAtFixedRate(
                    this::pollMarketData,
                    0,
                    pollingIntervalMs,
                    TimeUnit.MILLISECONDS
            );
        }
    }

    protected void pollMarketData() {
        try {
            JsonNode orderBookData = fetchOrderBook();
            CryptoTick tick = processOrderBookData(orderBookData);
            if (tick != null) {
                offerTick(tick);
            }
        } catch (Exception e) {
            logger.error("Error polling market data: {}", e.getMessage());
            handlePollingError(e);
        }
    }

    protected JsonNode makeRequest(String url) throws IOException {
        Request request = new Request.Builder()
                .url(url)
                .build();

        try (Response response = httpClient.newCall(request).execute()) {
            if (!response.isSuccessful()) {
                throw new IOException("Unexpected response code: " + response.code());
            }
            String responseBody = response.body().string();
            return objectMapper.readTree(responseBody);
        }
    }

    protected void handlePollingError(Exception e) {
        if (e instanceof IOException) {
            handleReconnect();
        } else {
            setStatus(ClientStatus.ERROR);
        }
    }

    @Override
    protected void handleReconnect() {
        setStatus(ClientStatus.RECONNECTING);
        try {
            Thread.sleep(1000); // Wait 5 seconds before trying to reconnect
            if (testConnection()) {
                setStatus(ClientStatus.COLLECTING);
            } else {
                setStatus(ClientStatus.ERROR);
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            setStatus(ClientStatus.ERROR);
        }
    }

    // Abstract methods that must be implemented by concrete classes
    protected abstract JsonNode fetchOrderBook() throws IOException;
    protected abstract CryptoTick processOrderBookData(JsonNode data) throws IOException;
}