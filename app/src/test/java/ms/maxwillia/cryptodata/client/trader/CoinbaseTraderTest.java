package ms.maxwillia.cryptodata.client.trader;

import ms.maxwillia.cryptodata.config.ExchangeCredentials;
import ms.maxwillia.cryptodata.client.ClientStatus;
import ms.maxwillia.cryptodata.model.TransactionStatus;
import okhttp3.HttpUrl;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Path;

import org.junit.jupiter.api.*;
import org.testcontainers.junit.jupiter.*;
import org.wiremock.integrations.testcontainers.WireMockContainer;

class CoinbaseTraderTest {
    private static final String TEST_CURRENCY = "BTC";
    private static final String TEST_EXCHANGE_NAME = "Coinbase";

    private CoinbaseTrader coinbaseTrader;
    private static ExchangeCredentials credentials;

    @Container
    static WireMockContainer wireMockContainer = new WireMockContainer("wiremock/wiremock:latest")
            .withMappingFromResource("mappings/coinbase.json")
            .withFileFromResource("files/coinbase/accounts.json")
            .withCommand("--global-response-templating", "--verbose");

    @BeforeAll
    static void setUpAll() throws IOException {
        credentials = ExchangeCredentials.loadFromFile(Path.of("src/test/resources/config/ec_private_key.json").toAbsolutePath());
        wireMockContainer.start();
    }

    @BeforeEach
    public void setUp() throws IOException {
        coinbaseTrader = new CoinbaseTrader(TEST_CURRENCY, credentials);
        coinbaseTrader.setApiRoot(HttpUrl.parse(wireMockContainer.getBaseUrl()));
    }

    @AfterEach
    public void tearDown() {
        coinbaseTrader = null;
    }

    @AfterAll
    public static void tearDownAll() {
        credentials = null;
    }

    @Test
    void testPreInitialize() {
        assert coinbaseTrader != null;
    }

    @Test
    void testInitialize() {
        coinbaseTrader.initialize();
        assert coinbaseTrader.getStatus() == ClientStatus.INITIALIZED;
    }

    @Test
    void testGenerateJTW() throws Exception {
        coinbaseTrader.initialize();
            assert coinbaseTrader.generateJWT("GET", "api.coinbase.com/api/v3/brokerage/accounts") != null;
        }

    @Test
    void testConnect() {
        coinbaseTrader.initialize();
        coinbaseTrader.connect();
        assert coinbaseTrader.isConnected;
    }

    @Test
    void testDryMarketBuy() {
        coinbaseTrader.initialize();
        coinbaseTrader.connect();
        var success = coinbaseTrader.marketBuy(0.01, 0.01);
        assert coinbaseTrader.getTransactions().size() == 1;
        assert coinbaseTrader.getTransactions().getFirst().getStatus() == TransactionStatus.CREATED;
        assert success;
    }

    @Test
    void testDryBuys() {
        coinbaseTrader.initialize();
        coinbaseTrader.connect();
        var success = coinbaseTrader.marketBuy(0.01, 0.01);
        coinbaseTrader.marketBuy(0.01, 0.01);
        assert coinbaseTrader.getTransactions().size() == 2;
        assert coinbaseTrader.getTransactions().stream()
                .allMatch(t -> t.getStatus() == TransactionStatus.CREATED);
        assert success;
    }

    /*
    Market buy with trading enabled.
     */
    @Test
    void testMarketBuy() {
        coinbaseTrader.initialize();
        coinbaseTrader.enableTrading();
        coinbaseTrader.connect();
        var success = coinbaseTrader.marketBuy(0.01, 0.01);
        assert coinbaseTrader.getTransactions().size() == 1;
        assert coinbaseTrader.getTransactions().getFirst().getStatus() == TransactionStatus.EXECUTED;
        assert success;
    }
}
