package ms.maxwillia.cryptodata.storage;

import ms.maxwillia.cryptodata.model.CryptoTick;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class CsvStorageTest {
    @TempDir
    Path tempDir;

    private Path csvFile;
    private CsvStorage storage;

    @BeforeEach
    void setUp() throws IOException {
        csvFile = tempDir.resolve("test_crypto_data.csv");
        storage = new CsvStorage(csvFile.toString());
    }

    @AfterEach
    void tearDown() {
        if (storage != null) {
            storage.close();
        }
    }

    @Test
    void testFileCreationAndHeader() throws IOException {
        assertTrue(Files.exists(csvFile), "CSV file should be created");
        List<String> lines = Files.readAllLines(csvFile);
        assertFalse(lines.isEmpty(), "File should not be empty");
        assertEquals(CryptoTick.header(), lines.getFirst(), "First line should be header");
    }

    @Test
    void testStoreSingleTickUSD() throws IOException {
        // Using the USD constructor
        CryptoTick tick = new CryptoTick(
                "BTC-USD",
                50000.0,
                1000.0,
                49999.0,
                1.5,
                50001.0,
                2.0,
                System.currentTimeMillis()
        );

        storage.storeTick(tick);
        storage.close(); // Ensure data is flushed

        List<String> lines = Files.readAllLines(csvFile);
        assertEquals(2, lines.size(), "Should have header and one data line");

        String dataLine = lines.get(1);
        assertTrue(dataLine.contains("BTC-USD"), "Data line should contain symbol");
        assertTrue(dataLine.contains("50000."), "Data line should contain price");
        assertTrue(dataLine.contains("1000."), "Data line should contain volume");
        assertTrue(dataLine.contains("-1."), "Data line should contain default NOK price");
        assertTrue(dataLine.contains("-1."), "Data line should contain default USD rate");
    }

    @Test
    void testStoreSingleTickNOK() throws IOException {
        // Using the NOK constructor
        double nokPrice = 500000.0;
        double USDRate = 10.0;
        CryptoTick tick = new CryptoTick(
                "BTC-NOK",
                1000.0,  // volume
                49999.0, // best bid
                1.5,     // best bid quantity
                50001.0, // best ask
                2.0,     // best ask quantity
                System.currentTimeMillis(),
                nokPrice,
                USDRate
        );

        storage.storeTick(tick);
        storage.close();

        List<String> lines = Files.readAllLines(csvFile);
        assertEquals(2, lines.size(), "Should have header and one data line");

        String dataLine = lines.get(1);
        assertTrue(dataLine.contains("BTC-NOK"), "Data line should contain symbol");
        assertTrue(dataLine.contains(String.valueOf(nokPrice / USDRate)), "Data line should contain converted USD price");
        assertTrue(dataLine.contains(String.valueOf(nokPrice)), "Data line should contain NOK price");
        assertTrue(dataLine.contains(String.valueOf(USDRate)), "Data line should contain USD rate");
    }

    @Test
    void testStoreMultipleTicks() throws IOException {
        long timestamp = System.currentTimeMillis();

        // Mix of USD and NOK ticks
        CryptoTick tickUSD = new CryptoTick(
                "BTC-USD",
                50000.0,
                1000.0,
                49999.0,
                1.5,
                50001.0,
                2.0,
                timestamp
        );

        CryptoTick tickNOK = new CryptoTick(
                "ETH-NOK",
                500.0,    // volume
                2999.0,   // best bid
                10.0,     // best bid quantity
                3001.0,   // best ask
                5.0,      // best ask quantity
                timestamp + 1000,
                30000.0,  // NOK price
                10.0      // USD rate
        );

        storage.storeTick(tickUSD);
        storage.storeTick(tickNOK);
        storage.close();

        List<String> lines = Files.readAllLines(csvFile);
        assertEquals(3, lines.size(), "Should have header and two data lines");
        assertTrue(lines.get(1).contains("BTC-USD"), "First data line should contain BTC-USD");
        assertTrue(lines.get(2).contains("ETH-NOK"), "Second data line should contain ETH-NOK");
        assertTrue(lines.get(2).contains("3000."), "Second line should contain converted USD price");
    }

    @Test
    void testInvalidFilePath() {
        assertThrows(IOException.class, () -> {
            new CsvStorage("/invalid/path/test.csv");
        }, "Should throw IOException for invalid file path");
    }

    @Test
    void testReopenExistingFile() throws IOException {
        // Create initial file with a USD tick
        CryptoTick tick1 = new CryptoTick(
                "BTC-USD",
                50000.0,
                1000.0,
                49999.0,
                1.5,
                50001.0,
                2.0,
                System.currentTimeMillis()
        );
        storage.storeTick(tick1);
        storage.close();

        // Reopen file and write a NOK tick
        CsvStorage newStorage = new CsvStorage(csvFile.toString());
        CryptoTick tick2 = new CryptoTick(
                "ETH-NOK",
                500.0,
                2999.0,
                10.0,
                3001.0,
                5.0,
                System.currentTimeMillis(),
                30000.0,
                10.0
        );
        newStorage.storeTick(tick2);
        newStorage.close();

        List<String> lines = Files.readAllLines(csvFile);
        assertTrue(lines.size() >= 3, "Should have header and at least two data lines");
        assertTrue(lines.get(1).contains("BTC-USD"), "Should contain USD tick");
        assertTrue(lines.stream().anyMatch(line -> line.contains("ETH-NOK")), "Should contain NOK tick");
    }

    @Test
    void testCloseMultipleTimes() {
        // Should not throw any exceptions when closed multiple times
        storage.close();
        storage.close();
    }
}