package ms.maxwillia.cryptodata.client;

import lombok.Getter;
import lombok.Setter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import javax.money.CurrencyUnit;
import javax.money.Monetary;

public abstract class BaseExchangeClient {
    protected final Logger logger = LoggerFactory.getLogger(getClass());
    @Getter
    private final String exchangeName;
    @Getter
    private volatile ClientStatus status;

    @Getter
    @Setter
    private CurrencyUnit settlementCurrency = Monetary.getCurrency("USDC");

    @Getter
    private final CurrencyUnit assetCurrency;

    @Getter
    private final CurrencyUnit intermediateCurrency;



    public BaseExchangeClient(String exchangeName, String assetCurrency, String intermediateCurrency) {
        this.status = null;
        this.exchangeName = exchangeName;
        this.assetCurrency = Monetary.getCurrency(assetCurrency);
        if (intermediateCurrency != null) {
            this.intermediateCurrency = Monetary.getCurrency(intermediateCurrency);
        } else {
            this.intermediateCurrency = null;
        }
    }

    protected void setStatus(ClientStatus newStatus) {
        ClientStatus oldStatus = this.status;
        this.status = newStatus;
        logger.info("{}: Status changed from {} to {}", exchangeName, oldStatus, newStatus);
    }

    static public String getTradePair(String assetCurrency, String settlementCurrency) {
        return String.format("%s%s", assetCurrency, settlementCurrency);
    }

    public String getTradePair() {
        return "%s%s".formatted(assetCurrency.getCurrencyCode(), settlementCurrency.getCurrencyCode());
    }

    public String getIntermediatePair() {
        return "%s%s".formatted(assetCurrency.getCurrencyCode(), intermediateCurrency.getCurrencyCode());
    }

}
