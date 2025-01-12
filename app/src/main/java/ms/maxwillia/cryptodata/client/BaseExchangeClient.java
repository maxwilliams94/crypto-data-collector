package ms.maxwillia.cryptodata.client;

import java.util.concurrent.BlockingQueue;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import ms.maxwillia.cryptodata.model.CryptoTick;

public abstract class BaseExchangeClient implements ExchangeClient {
    protected final Logger logger = LoggerFactory.getLogger(getClass());
    protected final BlockingQueue<CryptoTick> dataQueue;
    protected volatile ClientStatus status;
    protected final String exchangeName;
    protected long lastSequenceNumber = -1;
    protected String symbol;

    protected BaseExchangeClient(String exchangeName, String symbol, BlockingQueue<CryptoTick> dataQueue) {
        this.exchangeName = exchangeName;
        this.dataQueue = dataQueue;
        this.status = ClientStatus.INITIALIZED;
        setSymbolFromCurrency(symbol);
        logger.info("Created client for {} with symbol: {}", exchangeName, symbol);
    }

    @Override
    public String getExchangeName() {
        return exchangeName;
    }

    @Override
    public String getSubscribedSymbol() {
        return this.symbol;
    }

    abstract protected void setSymbolFromCurrency(String currency);

    @Override
    public ClientStatus getStatus() {
        return status;
    }

    protected void setStatus(ClientStatus newStatus) {
        ClientStatus oldStatus = this.status;
        this.status = newStatus;
        logger.info("{}: Status changed from {} to {}", exchangeName, oldStatus, newStatus);
    }

    protected void offerTick(CryptoTick tick) {
        if (!dataQueue.offer(tick)) {
            logger.warn("{}: Queue full, dropping tick for {}", exchangeName, tick.symbol());
        }
    }

    /**
     * Handle reconnection logic for the client
     */
    protected abstract void handleReconnect();

    /**
     * Initialize data collection parameters
     */
    protected abstract void initializeDataCollection();
}