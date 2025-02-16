package ms.maxwillia.cryptodata.client;

import lombok.Getter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public abstract class BaseExchangeClient {
    protected final Logger logger = LoggerFactory.getLogger(getClass());
    @Getter
    protected final String exchangeName;
    @Getter
    protected volatile ClientStatus status;
    @Getter
    protected String symbol;
    @Getter
    protected String currency;

    public BaseExchangeClient(String exchangeName, String currency) {
        this.status = null;
        this.exchangeName = exchangeName;
        this.currency = currency;
        setSymbolFromCurrency(currency);
    }

    public String toString() {
        return String.format("%s: %s", exchangeName, getSymbol());
    }

    abstract protected void setSymbolFromCurrency(String currency);

    protected void setStatus(ClientStatus newStatus) {
        ClientStatus oldStatus = this.status;
        this.status = newStatus;
        logger.info("{}: Status changed from {} to {}", exchangeName, oldStatus, newStatus);
    }
}
