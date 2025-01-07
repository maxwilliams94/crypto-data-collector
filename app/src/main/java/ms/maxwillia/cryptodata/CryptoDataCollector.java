package ms.maxwillia.cryptodata;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import ms.maxwillia.cryptodata.model.CryptoTick;
import ms.maxwillia.cryptodata.storage.CsvStorage;
import ms.maxwillia.cryptodata.websocket.ExchangeWebSocketClient;
import ms.maxwillia.cryptodata.websocket.CoinbaseWebSocketClient;

public class CryptoDataCollector {
    private static final Logger logger = LoggerFactory.getLogger(CryptoDataCollector.class);
    private static final int QUEUE_CAPACITY = 10000;
    private static final int BATCH_SIZE = 100;
    private static final int FLUSH_INTERVAL_MS = 1000;

    private final Map<String, BlockingQueue<CryptoTick>> dataQueues;
    private final Map<String, CsvStorage> storages;
    private final Map<String, QueueMetrics> queueMetrics;
    private final List<ExchangeWebSocketClient> clients;
    private volatile boolean running = true;
    private final List<Thread> processors;

    // Inner class to track queue metrics
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

        for (String symbol : symbols) {
            // Create queue and metrics
            BlockingQueue<CryptoTick> queue = new LinkedBlockingQueue<>(QUEUE_CAPACITY);
            dataQueues.put(symbol, queue);
            queueMetrics.put(symbol, new QueueMetrics());

            // Setup storage
            String timestamp = java.time.LocalDateTime.now()
                    .format(java.time.format.DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss"));
            String filename = String.format("%s%s_%s.csv",
                    System.getProperty("java.io.tmpdir"),
                    symbol.replace("-", "_"),
                    timestamp);
            CsvStorage storage = new CsvStorage(filename);
            logger.info("Using CSV file: {}", storage.getFilename());
            storages.put(symbol, storage);

            // Create WebSocket client with just the queue
            ExchangeWebSocketClient client = new CoinbaseWebSocketClient(symbol, queue);
            clients.add(client);
        }

        // Start metrics reporting thread
        Thread metricsThread = new Thread(this::reportMetrics, "MetricsReporter");
        metricsThread.setDaemon(true);
        metricsThread.start();
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

    public void handleTick(String symbol, CryptoTick tick) {
        BlockingQueue<CryptoTick> queue = dataQueues.get(symbol);
        QueueMetrics metrics = queueMetrics.get(symbol);
        boolean accepted = queue.offer(tick);
        metrics.recordReceived(accepted);

        if (!accepted) {
            logger.warn("{}: Queue full, dropping tick", symbol);
        }
    }

    public void start() {
        // Start all WebSocket connections
        for (ExchangeWebSocketClient client : clients) {
            logger.info("Starting data collection for {}", client.getSubscribedSymbol());
            client.connect();

            // Create and start processor thread for this symbol
            Thread processor = new Thread(
                    () -> processData(client.getSubscribedSymbol()),
                    "DataProcessor-" + client.getSubscribedSymbol()
            );
            processors.add(processor);
            processor.start();
        }
    }

    public void stop() {
        logger.info("Stopping data collection");
        running = false;

        // Disconnect all clients
        for (ExchangeWebSocketClient client : clients) {
            client.disconnect();
        }

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