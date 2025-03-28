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
        double nativePrice,   // Original native currency price
        double usdRate    // USD rate used for conversion from native currency
) {
    // Constructor overload for exchanges that deal directly in USD
    public CryptoTick(String symbol, double price, double volume_24_h, double best_bid, double best_bid_quantity, double best_ask, double best_ask_quantity, long timestamp) {
        this(symbol, price, volume_24_h, best_bid, best_bid_quantity, best_ask, best_ask_quantity, timestamp, price, -1);
    }
    // Constructor overload for exchanges which are in nonUSD
    public CryptoTick(String symbol, double volume_24_h, double best_bid, double best_bid_quantity, double best_ask, double best_ask_quantity, long timestamp, double nativePrice, double usdRate) {
        this(symbol, nativePrice / usdRate, volume_24_h, best_bid, best_bid_quantity, best_ask, best_ask_quantity, timestamp, nativePrice, usdRate);
    }

    public static String header() {
        return "symbol,priceUSD,volume_24_h,best_bid,best_bid_quantity,best_ask,best_ask_quantity,timestamp,nativePrice,usdRate";
    }

    public static String headerFormat() {
        return "%s,%.8f,%.8f,%.8f,%.8f,%.8f,%.8f,%.8f,%.8f,%.8f";
    }
}
