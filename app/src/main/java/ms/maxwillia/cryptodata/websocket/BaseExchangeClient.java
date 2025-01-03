package ms.maxwillia.cryptodata.websocket;

import java.util.concurrent.BlockingQueue;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import ms.maxwillia.cryptodata.model.CryptoTick;

// Abstract base implementation
public abstract class BaseExchangeClient implements ExchangeWebSocketClient {
    protected final Logger logger = LoggerFactory.getLogger(getClass());
    protected final BlockingQueue<CryptoTick> dataQueue;
    protected volatile ConnectionStatus status;
    protected final String exchangeName;
    protected long lastSequenceNumber = -1;
    protected String symbol;

    protected BaseExchangeClient(String exchangeName, String symbol, BlockingQueue<CryptoTick> dataQueue) {
        this.exchangeName = exchangeName;
        this.symbol = symbol;
        this.dataQueue = dataQueue;
        this.status = ConnectionStatus.DISCONNECTED;
    }

    @Override
    public String getExchangeName() {
        return exchangeName;
    }

    @Override
    public String getSubscribedSymbol() {
        return this.symbol;
    }

    @Override
    public ConnectionStatus getStatus() {
        return status;
    }

    protected void setStatus(ConnectionStatus newStatus) {
        ConnectionStatus oldStatus = this.status;
        this.status = newStatus;
        logger.info("{}: Status changed from {} to {}", exchangeName, oldStatus, newStatus);
    }

    protected void offerTick(CryptoTick tick) {
        if (!dataQueue.offer(tick)) {
            logger.warn("{}: Queue full, dropping tick for {}", exchangeName, tick.symbol());
        }
    }

    protected abstract void handleReconnect();

    protected abstract void subscribeToSymbol();

    public static ExchangeWebSocketClient forSymbols(BlockingQueue<CryptoTick> dataQueue, String... symbols) {
        throw new UnsupportedOperationException("Factory method must be implemented by concrete exchange clients");
    }

}
