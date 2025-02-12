package ms.maxwillia.cryptodata.client.trader;

import ms.maxwillia.cryptodata.client.BaseExchangeClient;
import ms.maxwillia.cryptodata.config.ExchangeCredentials;
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

public abstract class BaseExchangeTrader extends BaseExchangeClient implements ExchangeTrader {
    protected final Logger logger = LoggerFactory.getLogger(getClass());
    private boolean canTrade = false;
    protected final ExchangeCredentials credentials;
    protected ECPrivateKey ecPrivateKey;
    protected volatile boolean isConnected = false;

    public BaseExchangeTrader(String exchangeName, String currency, ExchangeCredentials credentials) {
        super(exchangeName, currency);
        this.credentials = credentials;
    }

    public void enableTrading() {
        canTrade = true;
    }

    public void disableTrading() {
        canTrade = false;
    }

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
}
