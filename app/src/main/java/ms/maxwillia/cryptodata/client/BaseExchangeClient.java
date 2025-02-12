package ms.maxwillia.cryptodata.client;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public abstract class BaseExchangeClient {
    protected final Logger logger = LoggerFactory.getLogger(getClass());
    protected final String exchangeName;
    protected volatile ClientStatus status;
    protected String symbol;
    protected String currency;

    public BaseExchangeClient(String exchangeName, String currency) {
        this.status = null;
        this.exchangeName = exchangeName;
        this.currency = currency;
    }

    public String toString() {
        return String.format("%s: %s", exchangeName, getSymbol());
    }

    public String getExchangeName() {
        return exchangeName;
    }

    public String getSymbol() {
        return this.symbol;
    }

    public String getCurrency() {
        return this.currency;
    }

    abstract protected void setSymbolFromCurrency(String currency);

    public ClientStatus getStatus() {
        return status;
    }

    protected void setStatus(ClientStatus newStatus) {
        ClientStatus oldStatus = this.status;
        this.status = newStatus;
        logger.info("{}: Status changed from {} to {}", exchangeName, oldStatus, newStatus);
    }
}
