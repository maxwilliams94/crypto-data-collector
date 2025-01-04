package ms.maxwillia.cryptodata.websocket;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.java_websocket.client.WebSocketClient;
import org.java_websocket.handshake.ServerHandshake;
import com.nimbusds.jose.JOSEException;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.JWSSigner;
import com.nimbusds.jose.crypto.ECDSASigner;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import org.bouncycastle.openssl.PEMParser;
import org.bouncycastle.openssl.jcajce.JcaPEMKeyConverter;
import java.security.KeyFactory;
import java.security.PrivateKey;
import java.security.interfaces.ECPrivateKey;
import java.security.spec.PKCS8EncodedKeySpec;
import io.github.cdimascio.dotenv.Dotenv;
import java.net.URI;
import java.io.StringReader;
import java.time.Instant;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.BlockingQueue;

import ms.maxwillia.cryptodata.model.CryptoTick;


public class CoinbaseWebSocketClient extends BaseExchangeClient {
    private static final String COINBASE_WS_URL = "wss://advanced-trade-ws.coinbase.com";
    private final ObjectMapper objectMapper = new ObjectMapper();
    private WebSocketClient wsClient;
    private static final Set<String> AVAILABLE_MARKETS = new HashSet<>(Arrays.asList(
            "ADA-USDC",
            "BNB-USDC",
            "DAI-USDC",
            "SOL-USDC",
            "LTC-USDC",
            "DOT-USDC",
            "XRP-USDC",
            "BTC-USDC",
            "ETH-USDC"
    ));
    private static final String SIGNATURE_ALGORITHM = "ES256";

    public CoinbaseWebSocketClient(String symbol, BlockingQueue<CryptoTick> dataQueue) {
        super("Coinbase", symbol, dataQueue);
    }

    @Override
    public boolean connect() {
        try {
            setStatus(ConnectionStatus.CONNECTING);
            
            wsClient = new WebSocketClient(URI.create(COINBASE_WS_URL)) {
                @Override
                public void onMessage(String message) {
                    handleMessage(message);
                }

                public void onOpen(ServerHandshake handshake) {
                    setStatus(ConnectionStatus.CONNECTED);
                    subscribeToSymbol();
                }

                @Override
                public void onClose(int code, String reason, boolean remote) {
                    logger.info("Coinbase connection closed: {} (code: {})", reason, code);
                    setStatus(ConnectionStatus.DISCONNECTED);
                    if (remote) {
                        handleReconnect();
                    }
                }

                @Override
                public void onError(Exception ex) {
                    logger.error("Coinbase WebSocket error", ex);
                    setStatus(ConnectionStatus.ERROR);
                }
            };

            return wsClient.connectBlocking();
        } catch (Exception e) {
            logger.error("Error connecting to Coinbase", e);
            setStatus(ConnectionStatus.ERROR);
            return false;
        }
    }

    @Override
    public void disconnect() {
        if (wsClient != null) {
            wsClient.close();
        }
        setStatus(ConnectionStatus.DISCONNECTED);
    }

    @Override
    public boolean isConnected() {
        return wsClient != null && wsClient.isOpen();
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

    @Override
    protected void subscribeToSymbol() {
        try {
            setStatus(ConnectionStatus.SUBSCRIBING);
            
            ObjectNode subscribeMessage = objectMapper.createObjectNode();
            subscribeMessage.put("type", "subscribe");
            
            // Convert symbols to Coinbase format
            symbol = symbol.toUpperCase();
            subscribeMessage.putArray("product_ids").add(symbol);
            subscribeMessage.put("channel", "ticker");
            logger.debug("Sending subscription request: {}", objectMapper.writeValueAsString(subscribeMessage));
            wsClient.send(objectMapper.writeValueAsString(subscribeMessage));
            logger.info("Sent subscription request for symbol: {}", symbol);
            
            setStatus(ConnectionStatus.SUBSCRIBED);
        } catch (Exception e) {
            logger.error("Error subscribing to symbols", e);
            setStatus(ConnectionStatus.ERROR);
        }
    }

    private void handleMessage(String message) {
        try {
            JsonNode node = objectMapper.readTree(message);
            logger.debug("Received message: {}", message);
            String channel = node.get("channel").asText();
            
            if ("ticker".equals(channel)) {
                processSingleTicker(node);
            } else {
                logger.error("Unexpected channel: {}", channel);
                logger.error(message);
                }
        } catch (Exception e) {
            logger.error("Error processing message: {}", message, e);
        }
    }

    private void processSingleTicker(JsonNode node) {
        logger.debug("Processing ticker: channel: {}, sequence: {}", node.get("channel"), node.get("sequence_num"));
        if (node.get("sequence_num").asLong() <= lastSequenceNumber) {
            logger.debug("Skipping ticker: sequence number is not greater than last sequence number");
            return;
        }
        lastSequenceNumber = node.get("sequence_num").asLong();
        JsonNode ticker_event = node.get("events").get(0).get("tickers").get(0);
        String timestampString = node.get("timestamp").asText();

        try {
            CryptoTick tick = new CryptoTick(
                ticker_event.get("product_id").asText(),
                ticker_event.get("price").asDouble(),
                ticker_event.get("volume_24_h").asDouble(),
                ticker_event.get("best_bid").asDouble(),
                ticker_event.get("best_bid_quantity").asDouble(),
                ticker_event.get("best_ask").asDouble(),
                ticker_event.get("best_ask_quantity").asDouble(),
                parseTimestamp(timestampString)
            );
            offerTick(tick);
        } catch (NullPointerException e) {
            logger.error("Unexpected ticker event format", e);
        }
        catch (Exception e) {
            logger.error("Error creating tick from trade data", e);
        }
    }

    private long parseTimestamp(String timestamp) {
        return java.time.Instant.parse(timestamp).toEpochMilli();
    }

    private String generateWT(String uri) throws JOSEException {
        try {
            // Load environment variables
            Dotenv dotenv = Dotenv.load();
            String privateKeyPEM = dotenv.get("COINBASE_PRIVATE_KEY").replace("\\n", "\n");
            String name = dotenv.get("COINBASE_KEY_NAME");

            // Create header
            Map<String, Object> headerParams = new HashMap<>();
            headerParams.put("alg", SIGNATURE_ALGORITHM);
            headerParams.put("typ", "JWT");
            headerParams.put("kid", name);
            headerParams.put("nonce", String.valueOf(Instant.now().getEpochSecond()));

            // Create claims
            Map<String, Object> claims = new HashMap<>();
            claims.put("iss", "cdp");
            claims.put("nbf", Instant.now().getEpochSecond());
            claims.put("exp", Instant.now().getEpochSecond() + 120); // 2 minute expiry
            claims.put("sub", name);
            claims.put("uri", "GET ");

            // Load private key
            PEMParser pemParser = new PEMParser(new StringReader(privateKeyPEM));
            JcaPEMKeyConverter converter = new JcaPEMKeyConverter().setProvider("BC");
            Object keyPair = pemParser.readObject();
            PrivateKey privateKey;

            if (keyPair instanceof PrivateKey) {
                privateKey = (PrivateKey) keyPair;
            } else if (keyPair instanceof org.bouncycastle.openssl.PEMKeyPair) {
                privateKey = converter.getPrivateKey(((org.bouncycastle.openssl.PEMKeyPair) keyPair).getPrivateKeyInfo());
            } else {
                throw new JOSEException("Invalid private key format");
            }
            pemParser.close();

            // Convert to ECPrivateKey
            KeyFactory keyFactory = KeyFactory.getInstance("EC");
            PKCS8EncodedKeySpec keySpec = new PKCS8EncodedKeySpec(privateKey.getEncoded());
            ECPrivateKey ecPrivateKey = (ECPrivateKey) keyFactory.generatePrivate(keySpec);

            // Create and sign JWT
            JWSHeader header = new JWSHeader.Builder(JWSAlgorithm.ES256)
                    .customParams(headerParams)
                    .build();

            JWTClaimsSet.Builder claimsSetBuilder = new JWTClaimsSet.Builder();
            claims.forEach(claimsSetBuilder::claim);
            JWTClaimsSet claimsSet = claimsSetBuilder.build();

            SignedJWT signedJWT = new SignedJWT(header, claimsSet);
            JWSSigner signer = new ECDSASigner(ecPrivateKey);
            signedJWT.sign(signer);

            return signedJWT.serialize();

        } catch (Exception e) {
            throw new JOSEException("Error generating JWT", e);
        }
    }

    // Factory method for convenience
    public static ExchangeWebSocketClient forSymbol(BlockingQueue<CryptoTick> dataQueue, String symbol) {
        return new CoinbaseWebSocketClient(symbol, dataQueue);
    }
}
