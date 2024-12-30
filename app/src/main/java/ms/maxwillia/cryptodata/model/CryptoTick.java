package ms.maxwillia.cryptodata.model;

public record CryptoTick(
    String symbol,
    double price,      // Price in USD
    double volume,
    long timestamp,
    double nokPrice,   // Original NOK price
    double usdcRate    // USDC-NOK rate used for conversion
) {
    // Constructor overload for exchanges that deal directly in USD
    public CryptoTick(String symbol, double price, double volume, long timestamp) {
        this(symbol, price, volume, timestamp, price, 1.0);
    }
}
