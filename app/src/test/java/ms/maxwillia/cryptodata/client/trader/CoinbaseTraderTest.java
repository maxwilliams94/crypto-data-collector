package ms.maxwillia.cryptodata.client.trader;

import ms.maxwillia.cryptodata.config.ExchangeCredentials;
import ms.maxwillia.cryptodata.client.ClientStatus;
import ms.maxwillia.cryptodata.model.TransactionSide;
import ms.maxwillia.cryptodata.model.TransactionStatus;
import ms.maxwillia.cryptodata.model.TransactionType;
import okhttp3.HttpUrl;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Path;
import java.util.Objects;

import org.junit.jupiter.api.*;
import org.testcontainers.junit.jupiter.*;
import org.wiremock.integrations.testcontainers.WireMockContainer;

class CoinbaseTraderTest {
    private static final String TEST_CURRENCY = "BTC";
    private static final String SUCCESS_CLIENT_ORDER_ID = "1111-11111-111111";
    private static final String FAIL_CLIENT_ORDER_ID = "2222-22222-222222";
    private static final String WARNING_CLIENT_ORDER_ID = "3333-33333-333333";
    private static final double SUCCESS_PREVIEW_QUOTE_SIZE = 10.0;
    private static final double ERRORS_PREVIEW_QUOTE_SIZE = 20.0;
    private static final double WARNINGS_PREVIEW_QUOTE_SIZE = 30.0;

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
        coinbaseTrader = new CoinbaseTrader(TEST_CURRENCY, null, credentials);
        coinbaseTrader.setApiRoot(Objects.requireNonNull(HttpUrl.parse(wireMockContainer.getBaseUrl() + "/api/v3")));
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
        var transaction = coinbaseTrader.marketBuy(0.01);
        assert coinbaseTrader.getTransactions().size() == 1;
        assert coinbaseTrader.getTransactions().getFirst().getStatus() == TransactionStatus.CREATED;
        assert coinbaseTrader.getTransactions().getFirst().getSide() == TransactionSide.BUY;
        assert coinbaseTrader.getTransactions().getFirst().getOrderType() == TransactionType.MARKET;
        assert transaction.get(0).getStatus() == TransactionStatus.CREATED;
    }

    @Test
    void testDryMarketSell() {
        coinbaseTrader.initialize();
        coinbaseTrader.connect();
        var transaction = coinbaseTrader.marketSell(0.01);
        assert coinbaseTrader.getTransactions().size() == 1;
        assert coinbaseTrader.getTransactions().getFirst().getStatus() == TransactionStatus.CREATED;
        assert coinbaseTrader.getTransactions().getFirst().getSide() == TransactionSide.SELL;
        assert coinbaseTrader.getTransactions().getFirst().getOrderType() == TransactionType.MARKET;
        assert transaction.get(0).getStatus() == TransactionStatus.CREATED;
    }

    @Test
    void testDryBuys() {
        coinbaseTrader.initialize();
        coinbaseTrader.connect();
        var transaction = coinbaseTrader.marketBuy(0.01);
        coinbaseTrader.marketBuy(0.01);
        assert coinbaseTrader.getTransactions().size() == 2;
        assert coinbaseTrader.getTransactions().stream()
                .allMatch(t -> t.getStatus() == TransactionStatus.CREATED);
        assert transaction.get(0).getStatus() == TransactionStatus.CREATED;
    }

    /*
    Market buy with trading enabled.
     */
    @Test
    void testMarketBuy() {
        coinbaseTrader.initialize();
        coinbaseTrader.enableTrading();
        coinbaseTrader.disablePreviewTrading();
        coinbaseTrader.connect();
        var transaction = coinbaseTrader.marketBuy(0.01, SUCCESS_CLIENT_ORDER_ID);
        assert coinbaseTrader.getTransactions().size() == 1;
        assert coinbaseTrader.getTransactions().getFirst().getStatus().equals(TransactionStatus.REQUESTED);
        assert transaction.getFirst().getStatus().equals(TransactionStatus.REQUESTED);
        assert transaction.getFirst().getResponse() != null;
        assert transaction.getFirst().getExchangeId().equals("11111-00000-000000");
    }

    @Test
    void testMarketBuyFail() {
        coinbaseTrader.initialize();
        coinbaseTrader.enableTrading();
        coinbaseTrader.disablePreviewTrading();
        coinbaseTrader.connect();
        var transaction = coinbaseTrader.marketBuy(0.01, FAIL_CLIENT_ORDER_ID);
        assert coinbaseTrader.getTransactions().size() == 1;
        assert coinbaseTrader.getTransactions().getFirst().getStatus().equals(TransactionStatus.REQUEST_ERROR);
        assert transaction.getFirst().getStatus().equals(TransactionStatus.REQUEST_ERROR);
        assert transaction.getFirst().getResponse() != null;
        assert transaction.getFirst().getId().equals(FAIL_CLIENT_ORDER_ID);
    }

    @Test
    void testMarketPreviewBuy() {
        coinbaseTrader.initialize();
        coinbaseTrader.enableTrading();
        coinbaseTrader.enablePreviewTrading();
        coinbaseTrader.connect();
        var transaction = coinbaseTrader.marketBuy(SUCCESS_PREVIEW_QUOTE_SIZE, "1111-11111-111111");
        assert coinbaseTrader.getTransactions().size() == 1;
        assert transaction.getFirst().getStatus().equals(TransactionStatus.PREVIEW_SUCCESS);
        assert transaction.getFirst().isPreview();
        assert transaction.getFirst().getResponse() != null;
        assert transaction.getFirst().getExchangeId().equals("preview_id");
    }

    @Test
    void testMarketPreviewBuyWithErrors() {
        coinbaseTrader.initialize();
        coinbaseTrader.enableTrading();
        coinbaseTrader.enablePreviewTrading();
        coinbaseTrader.connect();
        var transaction = coinbaseTrader.marketBuy(ERRORS_PREVIEW_QUOTE_SIZE, "1111-11111-111111");
        assert coinbaseTrader.getTransactions().size() == 1;
        assert transaction.getFirst().isPreview();
        assert transaction.getFirst().getStatus().equals(TransactionStatus.PREVIEW_ERROR);
        assert transaction.getFirst().getResponse() != null;
        assert transaction.getFirst().getExchangeId().equals("preview_id");
    }

    @Test
    void testMarketPreviewBuyWithWarnings() {
        coinbaseTrader.initialize();
        coinbaseTrader.enableTrading();
        coinbaseTrader.enablePreviewTrading();
        coinbaseTrader.connect();
        var transaction = coinbaseTrader.marketBuy(WARNINGS_PREVIEW_QUOTE_SIZE, "1111-11111-111111");
        assert coinbaseTrader.getTransactions().size() == 1;
        assert transaction.getFirst().isPreview();
        assert transaction.getFirst().getStatus().equals(TransactionStatus.PREVIEW_WARNING);
        assert transaction.getFirst().getResponse() != null;
        assert transaction.getFirst().getExchangeId().equals("preview_id");
    }
}
