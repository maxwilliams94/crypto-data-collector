package ms.maxwillia.cryptodata.model;

public record CryptoTick(
        String symbol,
        double price,      // Price in settlement or intermediate currency
        double volume_24_h,
        double best_bid,
        double best_bid_quantity,
        double best_ask,
        double best_ask_quantity,
        double timestamp,
        double nativePrice,   // Original native currency price
        double intermediateRate    // USD rate used for conversion from native currency
) {
    // Constructor overload for exchanges that deal directly in USD
    public CryptoTick(String symbol, double price, double volume_24_h, double best_bid, double best_bid_quantity, double best_ask, double best_ask_quantity, long timestamp) {
        this(symbol, price, volume_24_h, best_bid, best_bid_quantity, best_ask, best_ask_quantity, timestamp, price, -1);
    }
    // Constructor overload for exchanges which are in non-native
    public CryptoTick(String symbol, double volume_24_h, double best_bid, double best_bid_quantity, double best_ask, double best_ask_quantity, long timestamp, double nativePrice, double intermediateRate) {
        this(symbol, nativePrice / intermediateRate, volume_24_h, best_bid / intermediateRate, best_bid_quantity, best_ask / intermediateRate, best_ask_quantity, timestamp, nativePrice, intermediateRate);
    }

    public static String header() {
        return "symbol,price,volume_24_h,best_bid,best_bid_quantity,best_ask,best_ask_quantity,timestamp,nativePrice,rate";
    }

    public static String headerFormat() {
        return "%s,%.8f,%.8f,%.8f,%.8f,%.8f,%.8f,%.8f,%.8f,%.8f";
    }
}
