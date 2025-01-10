package ms.maxwillia.cryptodata.client.rest;

import ms.maxwillia.cryptodata.client.BaseExchangeClient;
import ms.maxwillia.cryptodata.model.CryptoTick;
import ms.maxwillia.cryptodata.client.ClientStatus;

import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.Executors;

/**
 * Base implementation for REST clients
 */
public abstract class BaseRestClient extends BaseExchangeClient implements RestClient {
    protected final ScheduledExecutorService scheduler;

    protected BaseRestClient(String exchangeName, String symbol, BlockingQueue<CryptoTick> dataQueue) {
        super(exchangeName, symbol, dataQueue);
        this.scheduler = Executors.newSingleThreadScheduledExecutor();
    }

    @Override
    public boolean initialize() {
        return configure() && testConnection();
    }

    @Override
    public boolean startDataCollection() {
        if (status != ClientStatus.INITIALIZED) {
            return false;
        }
        initializeDataCollection();
        return true;
    }

    @Override
    public void stopDataCollection() {
        scheduler.shutdown();
        setStatus(ClientStatus.STOPPED);
    }

    @Override
    public boolean isCollecting() {
        return !scheduler.isShutdown() && status == ClientStatus.COLLECTING;
    }

    /**
     * Schedule periodic data collection
     */
    protected abstract void scheduleDataCollection();
}