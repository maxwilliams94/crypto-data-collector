package ms.maxwillia.cryptodata.client.trader;

import ms.maxwillia.cryptodata.config.ExchangeCredentials;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Path;

class CoinbaseTraderTest {
    private static final String TEST_CURRENCY = "BTC";
    private static final String TEST_EXCHANGE_NAME = "Coinbase";

    private CoinbaseTrader coinbaseTrader;
    private static ExchangeCredentials credentials;

    @BeforeAll
    static void setUpAll() throws IOException {
        credentials = ExchangeCredentials.loadFromFile(Path.of("src/test/resources/config/ec_private_key.json").toAbsolutePath());
    }

    @BeforeEach
    public void setUp() throws IOException {
        coinbaseTrader = new CoinbaseTrader(TEST_CURRENCY, credentials);
    }

    @AfterEach
    public void tearDown() {
        coinbaseTrader = null;
        credentials = null;
    }


    @Test
    void testPreInitialize() {
        assert coinbaseTrader != null;
    }
}
