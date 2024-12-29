# Firi WebSocket Client Improvements

## 1. USDC-NOK Rate Management

### Rate Validation and Error Detection
- Implement sudden rate change detection
  - Track percentage changes between updates
  - Define maximum allowed rate change threshold (e.g., 5%)
  - Log warnings for suspicious rate movements
  - Option to pause conversions during extreme volatility

### Rolling Average Implementation
```java
public class RollingRateCalculator {
    private final int windowSize;
    private final LinkedList<Double> rates;
    private double sum;

    public RollingRateCalculator(int windowSize) {
        this.windowSize = windowSize;
        this.rates = new LinkedList<>();
        this.sum = 0.0;
    }

    public double addRate(double rate) {
        rates.addLast(rate);
        sum += rate;
        
        if (rates.size() > windowSize) {
            sum -= rates.removeFirst();
        }
        
        return getAverage();
    }

    public double getAverage() {
        return sum / rates.size();
    }
}
```

### Rate Update Throttling
```java
public class ThrottledRateUpdater {
    private final AtomicReference<Double> currentRate;
    private final long minUpdateIntervalMs;
    private volatile long lastUpdateTime;

    public ThrottledRateUpdater(long minUpdateIntervalMs) {
        this.currentRate = new AtomicReference<>();
        this.minUpdateIntervalMs = minUpdateIntervalMs;
        this.lastUpdateTime = 0;
    }

    public boolean updateRate(double newRate) {
        long now = System.currentTimeMillis();
        if (now - lastUpdateTime >= minUpdateIntervalMs) {
            currentRate.set(newRate);
            lastUpdateTime = now;
            return true;
        }
        return false;
    }
}
```

## 2. REST API Integration

### Initial Rate Fetching
```java
public class FiriRestClient {
    private static final String API_BASE_URL = "https://api.firi.com/v2";
    private final OkHttpClient httpClient;

    public CompletableFuture<Double> fetchUsdcNokRate() {
        return CompletableFuture.supplyAsync(() -> {
            Request request = new Request.Builder()
                .url(API_BASE_URL + "/markets/USDC-NOK/ticker")
                .build();

            try (Response response = httpClient.newCall(request).execute()) {
                if (!response.isSuccessful()) throw new IOException("Unexpected response " + response);
                
                String responseBody = response.body().string();
                JsonNode node = new ObjectMapper().readTree(responseBody);
                return node.get("last").asDouble();
            }
        });
    }
}
```

### Fallback Mechanism
```java
public class RateFallbackManager {
    private final FiriRestClient restClient;
    private final ScheduledExecutorService scheduler;
    
    public void initializeRate() {
        restClient.fetchUsdcNokRate()
            .thenAccept(rate -> {
                usdcNokRate.set(rate);
                logger.info("Initial USDC-NOK rate set to: {}", rate);
            })
            .exceptionally(ex -> {
                logger.error("Failed to fetch initial rate", ex);
                return null;
            });
    }

    public void schedulePeriodicRateCheck() {
        scheduler.scheduleAtFixedRate(
            this::validateAndUpdateRate,
            1, 1, TimeUnit.MINUTES
        );
    }
}
```

## 3. Price Conversion Strategies

### Multi-Source Price Conversion
```java
public class PriceConverter {
    private final Map<String, Double> stablecoinRates = new ConcurrentHashMap<>();
    
    // Support multiple stablecoins (USDC, USDT, DAI)
    public void updateStablecoinRate(String stablecoin, double nokRate) {
        stablecoinRates.put(stablecoin, nokRate);
    }
    
    public OptionalDouble convertToUsd(double nokPrice) {
        // Use weighted average of available stablecoin rates
        DoubleSummaryStatistics stats = stablecoinRates.values()
            .stream()
            .mapToDouble(rate -> nokPrice / rate)
            .summaryStatistics();
            
        return stats.getCount() > 0 
            ? OptionalDouble.of(stats.getAverage())
            : OptionalDouble.empty();
    }
}
```

### Price Sanity Checks
```java
public class PriceValidator {
    private final Map<String, PriceRange> expectedRanges;
    
    public boolean isValidPrice(String market, double price) {
        PriceRange range = expectedRanges.get(market);
        if (range == null) return true;
        
        return price >= range.min() && price <= range.max();
    }
    
    public record PriceRange(double min, double max) {
        public boolean isWithinRange(double price) {
            return price >= min && price <= max;
        }
    }
}
```

## 4. Error Recovery and Resilience

### Circuit Breaker Implementation
```java
public class ConversionCircuitBreaker {
    private final int errorThreshold;
    private final long resetTimeoutMs;
    private int errorCount;
    private long lastErrorTime;
    private boolean open;

    public boolean allowConversion() {
        if (!open) return true;
        
        if (System.currentTimeMillis() - lastErrorTime > resetTimeoutMs) {
            reset();
            return true;
        }
        return false;
    }

    public void recordError() {
        lastErrorTime = System.currentTimeMillis();
        if (++errorCount >= errorThreshold) {
            open = true;
        }
    }
}
```

### Rate Monitoring and Alerts
```java
public class RateMonitor {
    private final double maxRateChange;
    private final AlertService alertService;
    
    public void checkRateChange(double oldRate, double newRate) {
        double changePercent = Math.abs(newRate - oldRate) / oldRate * 100;
        
        if (changePercent > maxRateChange) {
            Alert alert = new Alert(
                AlertLevel.WARNING,
                String.format("USDC-NOK rate changed by %.2f%%", changePercent),
                LocalDateTime.now()
            );
            alertService.sendAlert(alert);
        }
    }
}
```

## Implementation Priority

1. **High Priority**
   - Rate validation and error detection
   - Initial rate fetching from REST API
   - Basic price sanity checks

2. **Medium Priority**
   - Rolling average implementation
   - Circuit breaker implementation
   - Rate update throttling

3. **Low Priority**
   - Multi-source price conversion
   - Advanced monitoring and alerts
   - Rate history tracking

## Testing Strategy

1. **Unit Tests**
   - Rate calculation accuracy
   - Price conversion edge cases
   - Circuit breaker behavior
   - Throttling logic

2. **Integration Tests**
   - REST API fallback
   - WebSocket reconnection
   - Rate update propagation

3. **Load Tests**
   - High frequency rate updates
   - Multiple market handling
   - Memory usage monitoring