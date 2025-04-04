package ms.maxwillia.cryptodata.client.trader;

import lombok.Getter;
import lombok.Setter;
import ms.maxwillia.cryptodata.client.BaseExchangeClient;
import ms.maxwillia.cryptodata.client.ClientStatus;
import ms.maxwillia.cryptodata.config.ExchangeCredentials;
import ms.maxwillia.cryptodata.model.CurrencyType;
import ms.maxwillia.cryptodata.model.Transaction;
import ms.maxwillia.cryptodata.model.TransactionSide;
import ms.maxwillia.cryptodata.model.TransactionType;
import org.bouncycastle.jce.provider.BouncyCastleProvider;
import org.bouncycastle.openssl.PEMParser;
import org.bouncycastle.openssl.jcajce.JcaPEMKeyConverter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.StringReader;
import java.security.KeyFactory;
import java.security.PrivateKey;
import java.security.Security;
import java.security.interfaces.ECPrivateKey;
import java.security.spec.PKCS8EncodedKeySpec;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.UUID;

public abstract class BaseExchangeTrader extends BaseExchangeClient implements ExchangeTrader {
    protected final Logger logger = LoggerFactory.getLogger(getClass());

    @Getter
    private final boolean isNative;
    @Getter
    private boolean canTrade = false;
    @Getter
    private boolean previewTrade = true;
    protected final ExchangeCredentials credentials;
    protected ECPrivateKey ecPrivateKey;
    protected volatile boolean isConnected = false;
    protected ArrayList<Transaction> transactions;
    @Getter
    private final HashMap<String, String> accountIds = new HashMap<>();
    @Getter
    private final HashMap<String, Double> accountBalances = new HashMap<>();
    @Getter
    @Setter
    private boolean orderSinceLastBalance = true;

    public BaseExchangeTrader(String exchangeName, String assetCurrency, String intermediateCurrency, ExchangeCredentials credentials, boolean isNative) {
        super(exchangeName, assetCurrency, intermediateCurrency);
        this.credentials = credentials;
        this.transactions = new ArrayList<>();
        this.isNative = isNative;
    }

    protected static String generateClientOrderId() {
        return UUID.randomUUID().toString();
    }

    public void enableTrading() {
        canTrade = true;
    }

    public void disableTrading() {
        canTrade = false;
    }

    public void disablePreviewTrading() { previewTrade = false;}

    public void enablePreviewTrading() { previewTrade = true;}

    public boolean canTrade() {
        return canTrade;
    }

    public boolean configure() {
        try {
            if (credentials == null || credentials.getName() == null || credentials.getPrivateKey() == null) {
                logger.error("Missing {} credentials", this.getExchangeName());
                return false;
            }
            initializeSecurity();
            return true;
        } catch (Exception e) {
            logger.error("Error configuring {} trader: {}", this.getExchangeName(), e.getMessage());
            return false;
        }
    }

    private void initializeSecurity() throws Exception {
        Security.addProvider(new BouncyCastleProvider());

        // Load private key
        PEMParser pemParser = new PEMParser(new StringReader(credentials.getPrivateKey()));
        JcaPEMKeyConverter converter = new JcaPEMKeyConverter().setProvider("BC");
        Object object = pemParser.readObject();
        PrivateKey privateKey;

        if (object instanceof PrivateKey) {
            privateKey = (PrivateKey) object;
        } else if (object instanceof org.bouncycastle.openssl.PEMKeyPair) {
            privateKey = converter.getPrivateKey(((org.bouncycastle.openssl.PEMKeyPair) object).getPrivateKeyInfo());
        } else {
            throw new Exception("Unexpected private key format");
        }
        pemParser.close();

        // Convert to ECPrivateKey
        KeyFactory keyFactory = KeyFactory.getInstance("EC");
        PKCS8EncodedKeySpec keySpec = new PKCS8EncodedKeySpec(privateKey.getEncoded());
        ecPrivateKey = (ECPrivateKey) keyFactory.generatePrivate(keySpec);
    }

    public void addTransaction(Transaction transaction) {
        transactions.add(transaction);
    }

    public List<Transaction> getTransactions() {
        return this.transactions;
    }

    public String toString() {
        return String.format("%s: (%s): %s %s",this.getClass(), this.getExchangeName(), this.getExchangeTradePair(), this.getIntermediateCurrency() != null ? this.getExchangeIntermediatePair() : "");
    }

    public String getTradePair() {
        return String.format("%s%s", this.getAssetCurrency().getCurrencyCode(), this.getSettlementCurrency().getCurrencyCode());
    }

    public String getIntermediatePair() {
        return String.format("%s%s", this.getIntermediateCurrency().getCurrencyCode(), this.getSettlementCurrency().getCurrencyCode());
    }

    public List<String> getCurrencies() {
        List<String> currencies = new ArrayList<>();
        currencies.add(this.getAssetCurrency().getCurrencyCode());
        currencies.add(this.getSettlementCurrency().getCurrencyCode());
        if (this.getIntermediateCurrency() != null) {
            currencies.add(this.getIntermediateCurrency().getCurrencyCode());
        }
        return currencies;
    }

    @Override
    public boolean initialize() {
        setStatus(ClientStatus.STARTING);
        try {
            if (!configure()) {
                return false;
            }
            setStatus(ClientStatus.INITIALIZED);
            logger.debug("Initialized {} wih credentials: {}", this.getClass().getName(), credentials.getName());
            return true;
        } catch (Exception e) {
            logger.error("Failed to initialize {}: {}", this.getClass().getName(), e.getMessage());
            setStatus(ClientStatus.ERROR);
            return false;
        }
    }

    abstract Transaction executeOrder(TransactionType orderType, TransactionSide side, CurrencyType currencyType, double price, double quantity, String clientOrderId);

    abstract String getAssetWalletAddress();

    abstract String getIntermediateWalletAddress();

}
