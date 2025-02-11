package ms.maxwillia.cryptodata.config;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Path;

public class ExchangeCredentialsTest {

    private static ExchangeCredentials credentials;

    @BeforeAll
    static void setUpAll() throws IOException {
        // Set up credentials
        credentials = ExchangeCredentials.loadFromFile(Path.of("src/test/resources/config/ec_private_key.json").toAbsolutePath());
    }

    @Test
    void testLoadFromFile() {
        // Test loading credentials from a file
        try {
            assert credentials.getName() != null;
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    @Test
    void testGetPrivateKey() {
        // Test getting the private key from the credentials
        try {
            assert credentials.getPrivateKey() != null;
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
