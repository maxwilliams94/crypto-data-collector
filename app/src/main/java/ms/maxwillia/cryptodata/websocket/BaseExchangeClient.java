package ms.maxwillia.cryptodata.websocket;

import java.util.List;
import java.util.ArrayList;
import java.util.concurrent.BlockingQueue;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import ms.maxwillia.cryptodata.model.CryptoTick;

// Abstract base implementation
public abstract class BaseExchangeClient implements ExchangeWebSocketClient {
    protected final Logger logger = LoggerFactory.getLogger(getClass());
    protected final BlockingQueue<CryptoTick> dataQueue;
    protected final List<String> symbols;
    protected volatile ConnectionStatus status;
    protected final String exchangeName;

    protected BaseExchangeClient(String exchangeName, List<String> symbols, BlockingQueue<CryptoTick> dataQueue) {
        this.exchangeName = exchangeName;
        this.symbols = symbols;
        this.dataQueue = dataQueue;
        this.status = ConnectionStatus.DISCONNECTED;
    }

    @Override
    public String getExchangeName() {
        return exchangeName;
    }

    @Override
    public List<String> getSubscribedSymbols() {
        return new ArrayList<>(symbols);
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

    protected abstract void subscribeToSymbols();

    public static ExchangeWebSocketClient forSymbols(BlockingQueue<CryptoTick> dataQueue, String... symbols) {
        throw new UnsupportedOperationException("Factory method must be implemented by concrete exchange clients");
    }

}
