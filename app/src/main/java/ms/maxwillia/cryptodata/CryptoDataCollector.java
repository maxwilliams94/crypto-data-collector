package ms.maxwillia.cryptodata;

import java.io.IOException;
import java.util.*;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import ms.maxwillia.cryptodata.client.BaseExchangeCollector;
import ms.maxwillia.cryptodata.client.ExchangeCollector;
import ms.maxwillia.cryptodata.client.rest.FiriRestCollector;
import ms.maxwillia.cryptodata.client.websocket.CoinbaseWebSocketCollector;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import ms.maxwillia.cryptodata.model.CryptoTick;
import ms.maxwillia.cryptodata.storage.CsvStorage;

public class CryptoDataCollector {
    private static final Logger logger = LoggerFactory.getLogger(CryptoDataCollector.class);
    private static final int QUEUE_CAPACITY = 10000;
    private static final int BATCH_SIZE = 100;
    private static final int FLUSH_INTERVAL_MS = 1000;

    private final Map<String, BlockingQueue<CryptoTick>> dataQueues;
    private final Map<String, CsvStorage> storages;
    private final Map<String, QueueMetrics> queueMetrics;
    private final List<ExchangeCollector> clients;
    private volatile boolean running = true;
    private final List<Thread> processors;

    private static class QueueMetrics {
        private long totalReceived = 0;
        private long totalProcessed = 0;
        private long totalDropped = 0;
        private final AtomicInteger queueSize = new AtomicInteger(0);

        void recordReceived(boolean accepted) {
            totalReceived++;
            if (!accepted) {
                totalDropped++;
            }
        }

        void recordProcessed() {
            totalProcessed++;
        }

        int getCurrentQueueSize() {
            return queueSize.get();
        }

        void updateQueueSize(int size) {
            queueSize.set(size);
        }

        double getDropRate() {
            return totalReceived == 0 ? 0 : (double) totalDropped / totalReceived * 100;
        }
    }

    public CryptoDataCollector(String[] symbols) throws IOException {
        this.dataQueues = new ConcurrentHashMap<>();
        this.storages = new HashMap<>();
        this.queueMetrics = new HashMap<>();
        this.clients = new ArrayList<>();
        this.processors = new ArrayList<>();

        // Create all necessary components for each symbol
        createExchangeClientsAndComponents(symbols);

        // Start metrics reporting thread
        Thread metricsThread = new Thread(this::reportMetrics, "MetricsReporter");
        metricsThread.setDaemon(true);
        metricsThread.start();
    }

    static String generateKey(String exchange, String currency) {
        return String.format("%s_%s", exchange, currency);
    }

    private void createExchangeClientsAndComponents(String[] currencies) throws IOException {
        BaseExchangeCollector client;
        String key;
        for (String currency : currencies) {
            for (String exchange : Arrays.asList("Coinbase", "Firi")) {
                key = generateKey(exchange, currency);
                LinkedBlockingQueue<CryptoTick> queue = new LinkedBlockingQueue<>(QUEUE_CAPACITY);
                dataQueues.put(key, queue);
                queueMetrics.put(key, new QueueMetrics());

                // Setup storage
                String timestamp = java.time.LocalDateTime.now()
                        .format(java.time.format.DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss"));
                String filename = String.format("%s%s_%s.csv",
                        System.getProperty("java.io.tmpdir"),
                        key,
                        timestamp);
                CsvStorage storage = new CsvStorage(filename);
                logger.info("Using CSV file: {}", storage.getFilename());
                storages.put(key, storage);

                // Create client
                if (exchange.equals("Coinbase")) {
                    client = new CoinbaseWebSocketCollector(currency, queue);
                } else if (exchange.equals("Firi")) {
                    client = new FiriRestCollector(currency, queue);
                } else {
                    throw new IllegalArgumentException("Unknown exchange: " + exchange);
                }
                clients.add(client);
            }
        }

    }


    private void processData(String symbol) {
        BlockingQueue<CryptoTick> queue = dataQueues.get(symbol);
        CsvStorage storage = storages.get(symbol);
        QueueMetrics metrics = queueMetrics.get(symbol);
        List<CryptoTick> batch = new ArrayList<>(BATCH_SIZE);
        long lastFlushTime = System.currentTimeMillis();

        while (running) {
            try {
                // Update queue size metric
                metrics.updateQueueSize(queue.size());

                // Try to fill batch
                while (batch.size() < BATCH_SIZE) {
                    CryptoTick tick = queue.poll(100, TimeUnit.MILLISECONDS);
                    if (tick != null) {
                        batch.add(tick);
                        metrics.recordProcessed();
                    } else {
                        break;
                    }
                }

                // Process batch if we have data or enough time has passed
                long currentTime = System.currentTimeMillis();
                if (!batch.isEmpty() &&
                        (batch.size() >= BATCH_SIZE || currentTime - lastFlushTime >= FLUSH_INTERVAL_MS)) {

                    // Write all ticks in batch
                    for (CryptoTick tick : batch) {
                        storage.storeTick(tick);
                    }

                    // Flush after writing batch
                    storage.flush();
                    lastFlushTime = currentTime;
                    batch.clear();
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            } catch (Exception e) {
                logger.error("Error processing batch for {}", symbol, e);
            }
        }
    }

    private void reportMetrics() {
        while (running) {
            try {
                Thread.sleep(5000);  // Report every 5 seconds
                for (Map.Entry<String, QueueMetrics> entry : queueMetrics.entrySet()) {
                    QueueMetrics metrics = entry.getValue();
                    logger.info("{} - Queue size: {}, Drop rate: {}%, " +
                                    "Received: {}, Processed: {}, Dropped: {}",
                            entry.getKey(),
                            metrics.getCurrentQueueSize(),
                            metrics.getDropRate(),
                            metrics.totalReceived,
                            metrics.totalProcessed,
                            metrics.totalDropped);
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            }
        }
    }

    public void start() {
        // Start all clients
        for (ExchangeCollector client : clients) {
            logger.info("Starting data collection for {}", client.toString());
            client.initialize();
            client.startDataCollection();

            // Create and start processor thread for this symbol
            Thread processor = new Thread(
                    () -> processData(generateKey(client.getExchangeName(), client.getCurrency())),
                    "DataProcessor-%s-%s".formatted(client.getExchangeName(), client.getSymbol()));
            processors.add(processor);
            processor.start();
        }
    }

    public void stop() {
        logger.info("Stopping data collection");

        for (ExchangeCollector client : clients) {
            client.stopDataCollection();
        }

        running = false;

        // Close all storage files
        for (CsvStorage storage : storages.values()) {
            storage.close();
        }

        // Wait for all processors to finish
        for (Thread processor : processors) {
            try {
                processor.join(5000); // Wait up to 5 seconds for each processor
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                logger.warn("Interrupted while waiting for processor to finish");
            }
        }
    }

    public static void main(String[] args) throws IOException {
        if (args.length == 0) {
            logger.error("No symbols provided. Please provide at least one symbol.");
            return;
        }

        logger.info("Starting collector with symbols: {}", String.join(", ", args));
        CryptoDataCollector collector = new CryptoDataCollector(args);

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