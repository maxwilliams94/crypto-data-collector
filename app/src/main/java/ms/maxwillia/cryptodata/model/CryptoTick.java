package ms.maxwillia.cryptodata.model;

public record CryptoTick(
    String symbol,
    double price,      // Price in USD
    double volume_24_h,
    double best_bid,
    double best_bid_quantity,
    double best_ask,
    double best_ask_quantity,
    double timestamp,
    double nokPrice,   // Original NOK price
    double usdcRate    // USDC-NOK rate used for conversion
) {
    // Constructor overload for exchanges that deal directly in USD
    public CryptoTick(String symbol, double price, double volume_24_h, double best_bid, double best_bid_quantity, double best_ask, double best_ask_quantity, long timestamp) {
        this(symbol, price, volume_24_h, best_bid, best_bid_quantity, best_ask, best_ask_quantity, timestamp, -1, -1);
    }
    // Constructor overload for exchanges which are in NOK
    public CryptoTick(String symbol, double volume_24_h, double best_bid, double best_bid_quantity, double best_ask, double best_ask_quantity, long timestamp, double nokPrice, double usdcRate) {
        this(symbol, nokPrice / usdcRate, volume_24_h, best_bid, best_bid_quantity, best_ask, best_ask_quantity, timestamp, nokPrice, usdcRate);
    }

    public static String header() {
        return "symbol,price,volume_24_h,best_bid,best_bid_quantity,best_ask,best_ask_quantity,timestamp,nokPrice,usdcRate";
    }

    public static String headerFormat() {
        return "%s,%.8f,%.8f,%.8f,%.8f,%.8f,%.8f,%.8f,%.8f,%.8f";
    }
}
