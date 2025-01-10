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
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;

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