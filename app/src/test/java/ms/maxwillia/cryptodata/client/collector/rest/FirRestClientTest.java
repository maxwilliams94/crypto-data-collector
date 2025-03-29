package ms.maxwillia.cryptodata.client.collector.rest;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import ms.maxwillia.cryptodata.client.ClientStatus;
import ms.maxwillia.cryptodata.model.CryptoTick;
import ms.maxwillia.cryptodata.utils.ReflectionTestUtils;
import okhttp3.OkHttpClient;
import okhttp3.mockwebserver.Dispatcher;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import okhttp3.mockwebserver.RecordedRequest;
import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Path;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;

class FiriRestClientTest {
    private static final Path TEST_DATA_ROOT = Path.of("src/test/resources/rest").toAbsolutePath();
    private static final String TEST_CURRENCY = "BTC";
    private static final String TEST_TRADE_PAIR = "BTCUSDC";
    private static final ObjectMapper objectMapper = new ObjectMapper();
    private MockWebServer mockWebServer;
    private FiriRestCollector client;
    private BlockingQueue<CryptoTick> dataQueue;
    private JsonNode testData;

    @BeforeEach
    void setUp() throws IOException {
        // Load test data first
        Path testDataFile = TEST_DATA_ROOT.resolve("firi-rest-test-data.json");
        if (!testDataFile.toFile().exists()) {
            throw new IllegalStateException(
                    "Test data file not found at: " + testDataFile.toAbsolutePath()
            );
        }
        testData = objectMapper.readTree(testDataFile.toFile());
        assertNotNull(testData.get("validResponses"), "Missing validResponses section");
        assertNotNull(testData.get("invalidResponses"), "Missing invalidResponses section");

        // Set up MockWebServer with dispatcher
        mockWebServer = new MockWebServer();
        mockWebServer.setDispatcher(createDispatcher());
        mockWebServer.start();

        // Create the client with mock server URL
        dataQueue = new LinkedBlockingQueue<>();

        String mockBaseUrl = mockWebServer.url("/").toString().replaceAll("/$", "");
        client = new FiriRestCollector(TEST_CURRENCY, "USDC", dataQueue);
        client.setBaseUrl(mockBaseUrl);

        // Use shorter timeouts for testing
        OkHttpClient testHttpClient = new OkHttpClient.Builder()
                .connectTimeout(1, TimeUnit.SECONDS)
                .readTimeout(1, TimeUnit.SECONDS)
                .writeTimeout(1, TimeUnit.SECONDS)
                .build();
        ReflectionTestUtils.setField(client, "httpClient", testHttpClient);
    }

    private Dispatcher createDispatcher() {
        return new Dispatcher() {
            @NotNull
            @Override
            public MockResponse dispatch(@NotNull RecordedRequest request) {
                String path = request.getPath();

                // Default error response
                MockResponse errorResponse = new MockResponse()
                        .setResponseCode(404)
                        .setBody("Not found");

                try {
                    if (path == null) return errorResponse;

                    // Test error endpoints
                    if (path.contains("/error")) {
                        return new MockResponse().setResponseCode(500);
                    }

                    // Test malformed data endpoints
                    if (path.contains("/malformed") && path.contains("depth")) {
                        return new MockResponse()
                                .setResponseCode(200)
                                .setHeader("Content-Type", "application/json")
                                .setBody(testData.get("invalidResponses").get("malformed").get("depth").toString());
                    }

                    // USDC/NOK rate endpoint
                    if (path.contains("markets/USDCNOK")) {
                        return new MockResponse()
                                .setResponseCode(200)
                                .setHeader("Content-Type", "application/json")
                                .setBody(testData.get("validResponses").get("USDCNOK").get("/").toString());
                    }

                    // Order book endpoint
                    if (path.contains("/BTCNOK/depth")) {
                        return new MockResponse()
                                .setResponseCode(200)
                                .setHeader("Content-Type", "application/json")
                                .setBody(testData.get("validResponses").get("BTCNOK").get("depth").toString());
                    }

                    // markets/BTCNOK endpoint
                    if (path.endsWith("/markets/BTCNOK")) {
                        return new MockResponse()
                                .setResponseCode(200)
                                .setHeader("Content-Type", "application/json")
                                .setBody(testData.get("validResponses").get("BTCNOK").get("/").toString());
                    }
                    // Markets endpoint
                    if (path.endsWith("/markets")) {
                        return new MockResponse()
                                .setResponseCode(200)
                                .setHeader("Content-Type", "application/json")
                                .setBody(testData.get("validResponses").get("markets").toString());
                    }

                    return errorResponse;
                } catch (Exception e) {
                    return new MockResponse()
                            .setResponseCode(500)
                            .setBody("Error processing request: " + e.getMessage());
                }
            }
        };
    }

    @AfterEach
    void tearDown() throws IOException {
        mockWebServer.shutdown();
        if (client != null) {
            client.stopDataCollection();
        }
    }

    @Test
    void testSuccessfulDataCollection() throws Exception {
        // Initialize and start the client
        assertTrue(client.initialize());
        assertTrue(client.startDataCollection());
        assertEquals(ClientStatus.COLLECTING, client.getStatus());

        // Wait for data collection
        Thread.sleep(1500); // Wait for at least one polling cycle

        // Get expected values from test data
        JsonNode validData = testData.get("validResponses");
        double expectedUsdRate = validData.get("USDCNOK").get("/").get("last").asDouble();
        double expectedPrice = validData.get("BTCNOK").get("/").get("last").asDouble();
        double expectedVolume = validData.get("BTCNOK").get("/").get("volume").asDouble();
        double expectedBestBid = validData.get("BTCNOK").get("depth").get("bids").get(0).get(0).asDouble();
        double expectedBestBidQty = validData.get("BTCNOK").get("depth").get("bids").get(0).get(1).asDouble();
        double expectedBestAsk = validData.get("BTCNOK").get("depth").get("asks").get(0).get(0).asDouble();
        double expectedBestAskQty = validData.get("BTCNOK").get("depth").get("asks").get(0).get(1).asDouble();

        // Verify we received market data
        CryptoTick tick = dataQueue.poll(1, TimeUnit.SECONDS);
        assertNotNull(tick);
        assertEquals(TEST_TRADE_PAIR, tick.symbol());
        assertEquals(expectedPrice / expectedUsdRate, tick.price(), 0.0001); // Price in USD
        assertEquals(expectedVolume, tick.volume_24_h());
        assertEquals(expectedBestBid / expectedUsdRate, tick.best_bid());
        assertEquals(expectedBestBidQty, tick.best_bid_quantity());
        assertEquals(expectedBestAsk / expectedUsdRate, tick.best_ask());
        assertEquals(expectedBestAskQty, tick.best_ask_quantity());
    }

    @Test
    void testStopDataCollection() throws Exception {
        assertTrue(client.initialize());
        assertTrue(client.startDataCollection());
        assertEquals(ClientStatus.COLLECTING, client.getStatus());

        Thread.sleep(100); // Let it run briefly
        client.stopDataCollection();

        assertEquals(ClientStatus.STOPPED, client.getStatus());
        assertFalse(client.isCollecting());
    }

    @Test
    void testHandleServerError() throws Exception {
        // Override baseUrl to hit error endpoint
        String errorBaseUrl = mockWebServer.url("/error").toString().replaceAll("/$", "");
        client = new FiriRestCollector(TEST_CURRENCY, "USDC", dataQueue);
        client.setBaseUrl(errorBaseUrl);

        assertFalse(client.initialize());
        assertEquals(ClientStatus.ERROR, client.getStatus());
    }

    @Test
    void testHandleMalformedData() throws Exception {
        // Override baseUrl to hit malformed data endpoint
        String malformedBaseUrl = mockWebServer.url("/malformed").toString().replaceAll("/$", "");
        client = new FiriRestCollector(TEST_CURRENCY, "USDC", dataQueue);
        client.setBaseUrl(malformedBaseUrl);

        assertTrue(client.initialize());
        assertTrue(client.startDataCollection());

        // Wait for data collection attempt
        Thread.sleep(1500);

        // Verify no data was added to queue due to malformed data
        assertNull(dataQueue.poll());
    }
}