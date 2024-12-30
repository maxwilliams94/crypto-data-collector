package ms.maxwillia.cryptodata;

import ms.maxwillia.cryptodata.model.CryptoTick;
import ms.maxwillia.cryptodata.storage.CsvStorage;
import ms.maxwillia.cryptodata.websocket.BaseExchangeClient;
import ms.maxwillia.cryptodata.websocket.BinanceWebSocketClient;
import ms.maxwillia.cryptodata.websocket.ExchangeWebSocketClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

public class CryptoDataCollector {
    private static final Logger logger = LoggerFactory.getLogger(CryptoDataCollector.class);
    
    private final BlockingQueue<CryptoTick> dataQueue;
    private final CsvStorage storage;
    private final List<ExchangeWebSocketClient> clients;
    private volatile boolean running = true;

    public CryptoDataCollector(String[] symbols) throws IOException {
        this.dataQueue = new LinkedBlockingQueue<>(1000);
        this.storage = new CsvStorage("crypto_data.csv");
        this.clients = new ArrayList<>();

        for (String symbol : symbols) {
            BinanceWebSocketClient client = BinanceWebSocketClient.forSymbols(dataQueue, symbol);
            clients.add(client);
        }
    }

    public void start() {
        logger.info("Starting data collection for {} symbols", clients.size());
        
        // Start WebSocket connections
        for (ExchangeWebSocketClient client : clients) {
            client.connect();
        }

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
        for (ExchangeWebSocketClient client : clients) {
            client.disconnect();
        }
        storage.close();
    }

    public static void main(String[] args) throws IOException {
        String[] symbols = {"btcusdt", "ethusdt"};
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

