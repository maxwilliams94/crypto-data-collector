package ms.maxwillia.cryptodata;

import java.io.IOException;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import ms.maxwillia.cryptodata.model.CryptoTick;
import ms.maxwillia.cryptodata.storage.CsvStorage;
import ms.maxwillia.cryptodata.websocket.ExchangeWebSocketClient;
import ms.maxwillia.cryptodata.websocket.CoinbaseWebSocketClient;

public class CryptoDataCollector {
    private static final Logger logger = LoggerFactory.getLogger(CryptoDataCollector.class);
    
    private final BlockingQueue<CryptoTick> dataQueue;
    private final CsvStorage storage;
    private final ExchangeWebSocketClient client;
    private volatile boolean running = true;

    public CryptoDataCollector(String[] symbols) throws IOException {
        this.dataQueue = new LinkedBlockingQueue<>(1000);
        this.storage = new CsvStorage(System.getProperty("java.io.tmpdir") + "crypto_data.csv");

        this.client = new CoinbaseWebSocketClient(symbols[0], dataQueue);
    }

    public void start() {
        logger.info("Starting data collection for {}", client.getSubscribedSymbol());
        
        // Start WebSocket connections
        client.connect();

        // Start processing thread
        Thread processor = new Thread(this::processData, "DataProcessor");
        processor.start();
    }

    private void processData() {
        int errorCount = 0;
        while (running && errorCount < 3) {
            try {
                CryptoTick tick = dataQueue.poll(1, TimeUnit.SECONDS);
                if (tick != null) {
                    storage.storeTick(tick);
                }
                errorCount = 0;  // Reset error count on successful processing
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            } catch (Exception e) {
                logger.error("Error processing tick", e);
                errorCount++;
                if (errorCount >= 3) {
                    logger.error("Too many consecutive errors, stopping processor");
                }
            }
        }
    }

    public void stop() {
        logger.info("Stopping data collection");
        running = false;
        client.disconnect();
        storage.close();
    }

    public static void main(String[] args) throws IOException {
        logger.debug("args: {}", args.toString());
        String[] symbols = args.length > 0 ? args : new String[]{};
        if (symbols.length == 0) {
            logger.error("No symbols provided. Please provide at least one symbol.");
            return;
        }
        CryptoDataCollector collector = new CryptoDataCollector(symbols);
        
        // Add shutdown hook for graceful shutdown
        Runtime.getRuntime().addShutdownHook(new Thread(collector::stop));
        
        collector.start();
        
        // Keep running until interrupted
        try {
            Thread.sleep(Long.MAX_VALUE);
        } catch (InterruptedException e) {
            logger.info("Main thread interrupted");
        }
    }
}

