package ms.maxwillia.cryptodata.websocket;


import static java.lang.Thread.sleep;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import java.io.IOException;
import java.lang.reflect.Method;
import java.nio.file.Path;
import java.util.EnumSet;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import org.java_websocket.client.WebSocketClient;
import org.java_websocket.handshake.ServerHandshake;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.fasterxml.jackson.databind.ObjectMapper;

import ms.maxwillia.cryptodata.model.CryptoTick;
import ms.maxwillia.cryptodata.utils.ReflectionTestUtils;


@ExtendWith(MockitoExtension.class)
class CoinbaseWebSocketClientTest {
    private static final Path TEST_DATA_ROOT = Path.of("src/test/resources/websocket").toAbsolutePath();
    private CoinbaseWebSocketClient client;
    private BlockingQueue<CryptoTick> dataQueue;
    private static final String TEST_SYMBOL = "BTC-USD";
    private static final ObjectMapper objectMapper = new ObjectMapper();
    private JsonNode testData;

    @Mock
    private WebSocketClient mockWebSocketClient;

    @Mock
    private ServerHandshake mockHandshake;

    @BeforeEach
    void setUp() throws IOException {
        dataQueue = new LinkedBlockingQueue<>();
        client = new CoinbaseWebSocketClient(TEST_SYMBOL, dataQueue) {
            @Override
            protected WebSocketClient createWebSocketClient() {
                return mockWebSocketClient;
            }
        };

        // Load test data
        Path testDataFile = TEST_DATA_ROOT.resolve("coinbase-ws-test-data.json");
        if (!testDataFile.toFile().exists()) {
            throw new IllegalStateException(
                    "Test data file not found at: " + testDataFile.toAbsolutePath()
            );
        }
        testData = objectMapper.readTree(testDataFile.toFile());
        assertNotNull(testData.get("validMessages"), "Missing validMessages section");
        assertNotNull(testData.get("invalidMessages"), "Missing invalidMessages section");
    }


    @Test
    void testInitialStatus() {
        assertEquals(ConnectionStatus.DISCONNECTED, client.getStatus());
        assertEquals("Coinbase", client.getExchangeName());
        assertEquals(TEST_SYMBOL, client.getSubscribedSymbol());
    }

    @Test
    void testMessageHandling() throws JsonProcessingException {
        // Get test message from our test data file
        String message = objectMapper.writeValueAsString(
                testData.get("validMessages").get("singleTicker"));

        Method handleMessage = ReflectionTestUtils.getMethod(
                CoinbaseWebSocketClient.class,
                client,
                "handleMessage",
                String.class
        );
        ReflectionTestUtils.invokeMethod(
                client,
                handleMessage,
                message
        );

        // Verify the tick was added to the queue
        CryptoTick tick = dataQueue.poll();
        assertNotNull(tick);
        assertEquals("BTC-USD", tick.symbol());
        assertEquals(45000.00, tick.price());
        assertEquals(1000.5, tick.volume_24_h());
        assertEquals(44999.00, tick.best_bid());
        assertEquals(1.5, tick.best_bid_quantity());
        assertEquals(45001.00, tick.best_ask());
        assertEquals(2.0, tick.best_ask_quantity());
    }

    @Test
    void testInvalidMessages() throws JsonProcessingException {
        Method handleMessage = ReflectionTestUtils.getMethod(
                CoinbaseWebSocketClient.class,
                client,
                "handleMessage",
                String.class
        );
        // Test wrong channel
        String wrongChannelMessage = objectMapper.writeValueAsString(
                testData.get("invalidMessages").get("wrongChannel"));

        ReflectionTestUtils.invokeMethod(
                client,
                handleMessage,
                wrongChannelMessage
        );
        assertTrue(dataQueue.isEmpty(), "Wrong channel message should not produce tick");

        // Test missing required fields
        String missingFieldsMessage = objectMapper.writeValueAsString(
                testData.get("invalidMessages").get("missingRequired"));
        ReflectionTestUtils.invokeMethod(
                client,
                handleMessage,
                missingFieldsMessage
        );
        assertTrue(dataQueue.isEmpty(), "Message with missing fields should not produce tick");

        // Test malformed JSON
        String malformedMessage = testData.get("invalidMessages").get("malformedJson").asText();
        ReflectionTestUtils.invokeMethod(
                client,
                handleMessage,
                malformedMessage
        );
        assertTrue(dataQueue.isEmpty(), "Malformed JSON should not produce tick");
    }

    @Test
    void testSequentialMessages() throws JsonProcessingException {
        Method handleMessage = ReflectionTestUtils.getMethod(
                CoinbaseWebSocketClient.class,
                client,
                "handleMessage",
                String.class
        );

        // Subscribe to symbol
        client.connect();
        String message = objectMapper.writeValueAsString(
                testData.get("subscriptionMessages").get("subscribeResponse"));
        assertDoesNotThrow(() -> {
            ReflectionTestUtils.invokeMethod(
                    client,
                    handleMessage,
                    message
            );
        });

        // Handle Ticker data
        String message1 = objectMapper.writeValueAsString(
                testData.get("validMessages").get("singleTicker"));
        ReflectionTestUtils.invokeMethod(
                client,
                handleMessage,
                message1
        );
        assertEquals(1, dataQueue.size());

        // Send message with new sequence number
        String message2 = objectMapper.writeValueAsString(
                testData.get("validMessages").get("newSequence"));
        ReflectionTestUtils.invokeMethod(
                client,
                handleMessage,
                message2
        );
        assertEquals(2, dataQueue.size());

        // Verify the ticks are in order
        CryptoTick firstTick = dataQueue.poll();
        CryptoTick secondTick = dataQueue.poll();
        assertTrue(firstTick.timestamp() < secondTick.timestamp());
    }

    @Test
    void testSubscriptionMessage() throws JsonProcessingException {
        // Get the subscription message from test data
        JsonNode subscribeMsg = testData.get("subscriptionMessages").get("subscribe");

        client.connect();
        // Trigger subscription
        client.subscribeToSymbol();

        // Verify the subscription message
        ArgumentCaptor<String> messageCaptor = ArgumentCaptor.forClass(String.class);
        verify(mockWebSocketClient).send(messageCaptor.capture());

        // Parse the sent message and compare with expected
        JsonNode sentMessage = objectMapper.readTree(messageCaptor.getValue());
        assertEquals(subscribeMsg.get("type").asText(), sentMessage.get("type").asText());
        assertEquals(subscribeMsg.get("channel").asText(), sentMessage.get("channel").asText());
        JsonNode sentProductIds = sentMessage.get("product_ids");
        assertTrue(sentProductIds.isArray());
        assertEquals(1, sentProductIds.size());
        assertEquals(TEST_SYMBOL, sentProductIds.get(0).asText());
    }

    @Test
    void testHandleSuccessfulSubscribe() throws JsonProcessingException {
        client.connect();
        Method handleMessage = ReflectionTestUtils.getMethod(
                CoinbaseWebSocketClient.class,
                client,
                "handleMessage",
                String.class
        );
        String message = objectMapper.writeValueAsString(
                testData.get("subscriptionMessages").get("subscribeResponse"));

        assertDoesNotThrow(() -> {
            ReflectionTestUtils.invokeMethod(
                    client,
                    handleMessage,
                    message
            );
        });

        // Verify the queue remains empty (subscription responses shouldn't generate ticks)
        assertTrue(dataQueue.isEmpty(), "Subscription response should not produce market data ticks");

        // Optionally verify connection status - depends on your implementation
        assertEquals(ConnectionStatus.SUBSCRIBED, client.getStatus(),
                "Client should be in SUBSCRIBED state after successful subscription response");
    }

    @Test
    void testDuplicateSequenceNumber() throws JsonProcessingException {
        Method handleMessage = ReflectionTestUtils.getMethod(
                CoinbaseWebSocketClient.class,
                client,
                "handleMessage",
                String.class
        );
        String message1 = objectMapper.writeValueAsString(
                testData.get("validMessages").get("singleTicker"));

        ReflectionTestUtils.invokeMethod(
                client,
                handleMessage,
                message1
        );
        CryptoTick firstTick = dataQueue.poll();
        assertNotNull(firstTick);

        // Send same message again (same sequence number)
        ReflectionTestUtils.invokeMethod(
                client,
                handleMessage,
                message1
        );
        assertTrue(dataQueue.isEmpty(), "Should not process duplicate sequence number");
    }

    @Test
    void testStatusTransitions() {
        client.connect();
        assertEquals(ConnectionStatus.CONNECTING, client.getStatus());

        client.disconnect();
        assertEquals(ConnectionStatus.DISCONNECTED, client.getStatus());
    }

}

