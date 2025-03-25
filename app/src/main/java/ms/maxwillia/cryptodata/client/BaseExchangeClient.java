package ms.maxwillia.cryptodata.client;

import lombok.Getter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import javax.money.CurrencyUnit;
import javax.money.Monetary;

import java.util.ArrayList;

public abstract class BaseExchangeClient {
    protected final Logger logger = LoggerFactory.getLogger(getClass());
    @Getter
    private final String exchangeName;
    @Getter
    private volatile ClientStatus status;

    @Getter
    private final CurrencyUnit settlementCurrency;

    @Getter
    private final CurrencyUnit assetCurrency;

    @Getter
    private final CurrencyUnit intermediateCurrency;


    public BaseExchangeClient(String exchangeName, String settlementCurrency, String assetCurrency, String intermediateCurrency) {
        this.status = null;
        this.exchangeName = exchangeName;
        this.settlementCurrency = Monetary.getCurrency(settlementCurrency);
        this.assetCurrency = Monetary.getCurrency(assetCurrency);
        this.intermediateCurrency = Monetary.getCurrency(intermediateCurrency);
    }

    protected void setStatus(ClientStatus newStatus) {
        ClientStatus oldStatus = this.status;
        this.status = newStatus;
        logger.info("{}: Status changed from {} to {}", exchangeName, oldStatus, newStatus);
    }
}
