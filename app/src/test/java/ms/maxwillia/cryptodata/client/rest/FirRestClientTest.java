package ms.maxwillia.cryptodata.client.rest;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import ms.maxwillia.cryptodata.client.ClientStatus;
import ms.maxwillia.cryptodata.model.CryptoTick;
import ms.maxwillia.cryptodata.utils.ReflectionTestUtils;
import okhttp3.*;
import okio.Timeout;
import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.io.IOException;
import java.nio.file.Path;
import java.util.LinkedList;
import java.util.Queue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class FiriRestClientTest {
    private static final Path TEST_DATA_ROOT = Path.of("src/test/resources/rest").toAbsolutePath();
    private static final String TEST_SYMBOL = "BTC-NOK";
    private static final ObjectMapper objectMapper = new ObjectMapper();
    private FiriRestClient client;
    private BlockingQueue<CryptoTick> dataQueue;
    private JsonNode testData;

    @Mock
    private OkHttpClient mockHttpClient;

    @Mock
    private Response mockResponse;

    @Mock
    private ResponseBody mockResponseBody;

    @BeforeEach
    void setUp() throws IOException {
        dataQueue = new LinkedBlockingQueue<>();
        client = new FiriRestClient(TEST_SYMBOL, dataQueue);
        // Replace the HTTP client with our mock
        ReflectionTestUtils.setField(client, "httpClient", mockHttpClient);

        // Load test data
        Path testDataFile = TEST_DATA_ROOT.resolve("firi-rest-test-data.json");
        if (!testDataFile.toFile().exists()) {
            throw new IllegalStateException(
                    "Test data file not found at: " + testDataFile.toAbsolutePath()
            );
        }
        testData = objectMapper.readTree(testDataFile.toFile());
        assertNotNull(testData.get("validResponses"), "Missing validResponses section");
        assertNotNull(testData.get("invalidResponses"), "Missing invalidResponses section");
    }

    private void setupMockResponse(String content) throws IOException {
        when(mockHttpClient.newCall(any())).thenAnswer(invocation -> {
            return new okhttp3.Call() {
                @Override
                public Response execute() {
                    return mockResponse;
                }

                @Override
                public void enqueue(okhttp3.Callback responseCallback) {
                }

                @Override
                public void cancel() {
                }

                @Override
                public boolean isExecuted() {
                    return false;
                }

                @Override
                public boolean isCanceled() {
                    return false;
                }

                @Override
                public okhttp3.Call clone() {
                    return this;
                }

                @Override
                public okhttp3.Request request() {
                    return new okhttp3.Request.Builder()
                            .url("https://api.firi.com/v2/markets")
                            .build();
                }
                @NotNull
                @Override
                public Timeout timeout() {
                    return Timeout.NONE;
                }
            };
        });
    }

    private JsonNode createMockOrderBookData(double bestBidPrice, double bestBidQty,
                                             double bestAskPrice, double bestAskQty) {
        ObjectNode orderBook = objectMapper.createObjectNode();
        ArrayNode bids = orderBook.putArray("bids");
        ArrayNode bid = objectMapper.createArrayNode();
        bid.add(bestBidPrice);
        bid.add(bestBidQty);
        bids.add(bid);

        ArrayNode asks = orderBook.putArray("asks");
        ArrayNode ask = objectMapper.createArrayNode();
        ask.add(bestAskPrice);
        ask.add(bestAskQty);
        asks.add(ask);

        return orderBook;
    }

    private JsonNode createMockTickerData(double last, double volume) {
        ObjectNode ticker = objectMapper.createObjectNode();
        ticker.put("last", last);
        ticker.put("volume", volume);
        return ticker;
    }

    private JsonNode createMockUsdcNokData(double rate) {
        ObjectNode ticker = objectMapper.createObjectNode();
        ticker.put("last", rate);
        return ticker;
    }

    @Test
    void testInitialization() {
        assertEquals("Firi", client.getExchangeName());
        assertEquals(TEST_SYMBOL, client.getSubscribedSymbol());
        assertEquals("BTCNOK", client.getExchangeSymbol());
        assertEquals(ClientStatus.INITIALIZED, client.getStatus());
    }

    @Test
    void testConfigure() throws IOException {
        // Setup mock USDC-NOK rate response
        setupMockResponse(createMockUsdcNokData(10.5).toString());

        assertTrue(client.configure());
        assertEquals(ClientStatus.INITIALIZED, client.getStatus());

        // Verify the USDC-NOK rate request was made
        verify(mockHttpClient, times(1)).newCall(any());
    }

    @Test
    void testConfigureWithError() throws IOException {
        when(mockResponse.isSuccessful()).thenReturn(false);
        when(mockHttpClient.newCall(any())).thenReturn(new okhttp3.Call() {
            @Override
            public Response execute() {
                return mockResponse;
            }

            @Override
            public void enqueue(okhttp3.Callback responseCallback) {
            }

            @Override
            public void cancel() {
            }

            @Override
            public boolean isExecuted() {
                return false;
            }

            @Override
            public boolean isCanceled() {
                return false;
            }

            @Override
            public okhttp3.Call clone() {
                return this;
            }

            @Override
            public okhttp3.Request request() {
                return null;
            }

            @Override
            public Timeout timeout() {
                return Timeout.NONE;
            }
        });

        assertFalse(client.configure());
    }

    @Test
    void testProcessValidOrderBookData() throws IOException, InterruptedException {
        // Get test responses from loaded test data
        JsonNode orderBookData = testData.get("validResponses").get("orderBook");
        JsonNode tickerData = testData.get("validResponses").get("ticker");
        JsonNode usdcRateData = testData.get("validResponses").get("usdcRate");

        // Setup initial USD rate response
        setupMockResponse(usdcRateData.toString());
        client.configure();

        // Create a queue for our responses
        Queue<Response> responseQueue = new LinkedList<>();

        // Create response for order book
        Response orderBookResponse = mock(Response.class);
        ResponseBody orderBookBody = mock(ResponseBody.class);
        when(orderBookBody.string()).thenReturn(orderBookData.toString());
        when(orderBookResponse.body()).thenReturn(orderBookBody);
        when(orderBookResponse.isSuccessful()).thenReturn(true);
        responseQueue.add(orderBookResponse);

        // Create response for ticker
        Response tickerResponse = mock(Response.class);
        ResponseBody tickerBody = mock(ResponseBody.class);
        when(tickerBody.string()).thenReturn(tickerData.toString());
        when(tickerResponse.body()).thenReturn(tickerBody);
        when(tickerResponse.isSuccessful()).thenReturn(true);
        responseQueue.add(tickerResponse);

        // Setup mock HTTP client to return responses in sequence
        when(mockHttpClient.newCall(any())).thenAnswer(invocation -> new okhttp3.Call() {
            @NotNull
            @Override
            public Call clone() {
                return null;
            }

            @NotNull
            @Override
            public Timeout timeout() {
                return null;
            }

            @Override
            public boolean isCanceled() {
                return false;
            }

            @Override
            public boolean isExecuted() {
                return false;
            }

            @Override
            public void enqueue(@NotNull Callback callback) {

            }

            @NotNull
            @Override
            public Request request() {
                return null;
            }

            @Override
            public void cancel() {

            }

            @Override
            public Response execute() {
                return responseQueue.poll();
            }

        });

        // Start data collection
        client.initialize();
        client.startDataCollection();

        // Wait for and verify tick
        CryptoTick tick = dataQueue.poll(1, TimeUnit.SECONDS);
        assertNotNull(tick, "Should have received a tick");
        assertEquals(TEST_SYMBOL, tick.symbol());
        assertEquals(Double.parseDouble(tickerData.get("volume").asText()), tick.volume_24_h());
        assertEquals(orderBookData.get("bids").get(0).get(0).asDouble(), tick.best_bid());
        assertEquals(orderBookData.get("bids").get(0).get(1).asDouble(), tick.best_bid_quantity());
        assertEquals(orderBookData.get("asks").get(0).get(0).asDouble(), tick.best_ask());
        assertEquals(orderBookData.get("asks").get(0).get(1).asDouble(), tick.best_ask_quantity());
        assertEquals(Double.parseDouble(tickerData.get("last").asText()), tick.nativePrice());
        assertEquals(Double.parseDouble(usdcRateData.get("last").asText()), tick.usdRate());

        // Verify expected number of API calls
        verify(mockHttpClient, times(3)).newCall(any()); // One each for rate, orderbook, and ticker
    }

    @Test
    void testHandleEmptyOrderBook() throws IOException {
        JsonNode emptyOrderBook = testData.get("invalidResponses").get("emptyOrderBook");

        // Setup mock response
        when(mockResponse.isSuccessful()).thenReturn(true);
        when(mockResponse.body()).thenReturn(mockResponseBody);
        when(mockResponseBody.string()).thenReturn(emptyOrderBook.toString());

        setupMockResponse(emptyOrderBook.toString());

        // Process order book directly
        CryptoTick tick = client.processOrderBookData(emptyOrderBook);
        assertNull(tick, "Empty order book should return null tick");

        verify(mockHttpClient, times(1)).newCall(any());
    }


    @Test
    void testHandleInvalidOrderBookData() {
        ObjectNode invalidData = objectMapper.createObjectNode();
        invalidData.put("someField", "someValue");

        assertThrows(IOException.class, () -> {
            client.processOrderBookData(invalidData);
        });
    }

    @Test
    void testStopDataCollection() throws Exception {
        setupMockResponse(createMockOrderBookData(450000.0, 1.5,
                451000.0, 2.0).toString());

        client.initialize();
        client.startDataCollection();
        assertEquals(ClientStatus.COLLECTING, client.getStatus());

        Thread.sleep(100); // Let it run briefly
        client.stopDataCollection();

        assertEquals(ClientStatus.STOPPED, client.getStatus());
        assertFalse(client.isCollecting());
    }

    @Test
    void testReconnectOnError() throws IOException {
        // First request fails
        when(mockResponse.isSuccessful()).thenReturn(false);
        setupMockResponse("{}");

        client.initialize();
        client.startDataCollection();

        // Verify it goes into reconnecting state
        verify(mockHttpClient, atLeastOnce()).newCall(any());
        assertEquals(ClientStatus.RECONNECTING, client.getStatus());

    }
}