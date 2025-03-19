package ms.maxwillia.cryptodata.client.trader;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

import ms.maxwillia.cryptodata.config.ExchangeCredentials;

/**
 * Factory for creating exchange trader instances
 */
public class TraderFactory {
    private static final Logger logger = LoggerFactory.getLogger(TraderFactory.class);
    
    /**
     * Creates an ExchangeTrader for the specified exchange and currency
     * @param exchangeName The name of the exchange (e.g., "Coinbase", "Firi")
     * @param currency The currency to trade (e.g., "BTC")
     * @param apiKeyPath Path to the API key file
     * @return ExchangeTrader instance
     * @throws IllegalArgumentException if the exchange is not supported
     * @throws IOException if the API keys cannot be loaded
     */
    public static ExchangeTrader createTrader(
            String exchangeName, 
            String currency, 
            String apiKeyPath) throws IllegalArgumentException, IOException {
        
        // Validate inputs
        if (exchangeName == null || exchangeName.isEmpty()) {
            throw new IllegalArgumentException("Exchange name cannot be empty");
        }
        
        if (currency == null || currency.isEmpty()) {
            throw new IllegalArgumentException("Currency cannot be empty");
        }
        
        // Normalize exchange name to handle case variations
        String normalizedExchange = exchangeName.toLowerCase();
        
        // Create appropriate trader based on exchange
        switch (normalizedExchange) {
            case "coinbase":
                return createCoinbaseTrader(currency, apiKeyPath);
            case "firi":
                throw new IllegalArgumentException("Firi trader not yet implemented");
            default:
                throw new IllegalArgumentException("Unsupported exchange: " + exchangeName);
        }
    }
    
    /**
     * Creates a Coinbase trader instance
     * @param currency The currency to trade
     * @param apiKeyPath Path to the API key file
     * @return CoinbaseTrader instance
     * @throws IOException if the API keys cannot be loaded
     */
    private static CoinbaseTrader createCoinbaseTrader(
            String currency, 
            String apiKeyPath) throws IOException {

        ExchangeCredentials credentials = ExchangeCredentials.loadFromFile(Path.of(apiKeyPath).toAbsolutePath());

        return new CoinbaseTrader(currency, credentials);
    }
    
    /**
     * Loads a key from a file
     * @param filePath Path to the key file
     * @return String containing the key
     * @throws IOException if the file cannot be read
     */
    private static String loadKeyFromFile(String filePath) throws IOException {
        Path path = Paths.get(filePath);
        if (!Files.exists(path)) {
            throw new IOException("Key file not found: " + filePath);
        }
        
        return Files.readString(path).trim();
    }
}
