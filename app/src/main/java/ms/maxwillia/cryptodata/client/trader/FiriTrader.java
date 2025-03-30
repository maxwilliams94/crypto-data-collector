package ms.maxwillia.cryptodata.client.trader;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.JWSSigner;
import com.nimbusds.jose.crypto.ECDSASigner;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import ms.maxwillia.cryptodata.client.ClientStatus;
import ms.maxwillia.cryptodata.client.mapper.coinbase.CoinbaseOrderMapper;
import ms.maxwillia.cryptodata.config.ExchangeCredentials;
import ms.maxwillia.cryptodata.model.Transaction;
import ms.maxwillia.cryptodata.model.TransactionSide;
import ms.maxwillia.cryptodata.model.TransactionStatus;
import ms.maxwillia.cryptodata.model.TransactionType;
import okhttp3.*;
import org.jetbrains.annotations.Nullable;

import javax.money.Monetary;
import javax.money.MonetaryAmount;
import java.io.IOException;
import java.net.URI;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

import ms.maxwillia.cryptodata.apis.firi.v2.invoker.ApiClient;
import ms.maxwillia.cryptodata.apis.firi.v2.invoker.auth.ApiKeyAuth;


public class FiriTrader extends BaseExchangeTrader {
        private final ApiClient apiClient;

    public FiriTrader(String assetCurrency, String intermediateCurrency, ExchangeCredentials credentials) {
        super("Firi", assetCurrency, intermediateCurrency, credentials, false);
        setSettlementCurrency(Monetary.getCurrency("NOK"));
        apiClient = new ApiClient();
    }

    public void setApiRoot(HttpUrl apiRoot) {
        this.apiClient.setBasePath(apiRoot.toString());
    }

    protected String generateJWT(String requestMethod, String requestUrl) throws Exception {
        // create header object
        Map<String, Object> header = new HashMap<>();
        header.put("alg", "ES256");
        header.put("typ", "JWT");
        header.put("kid", credentials.getName());
        header.put("nonce", String.valueOf(Instant.now().getEpochSecond()));

        // create uri string for current request
        String uri = requestMethod + " " + requestUrl;

        // create data object
        Map<String, Object> data = new HashMap<>();
        data.put("iss", "cdp");
        data.put("nbf", Instant.now().getEpochSecond());
        data.put("exp", Instant.now().getEpochSecond() + 120);
        data.put("sub", credentials.getName());
        data.put("uri", uri);

        // create JWT
        JWTClaimsSet.Builder claimsSetBuilder = new JWTClaimsSet.Builder();
        for (Map.Entry<String, Object> entry : data.entrySet()) {
            claimsSetBuilder.claim(entry.getKey(), entry.getValue());
        }
        JWTClaimsSet claimsSet = claimsSetBuilder.build();

        JWSHeader jwsHeader = new JWSHeader.Builder(JWSAlgorithm.ES256).customParams(header).build();
        SignedJWT signedJWT = new SignedJWT(jwsHeader, claimsSet);

        JWSSigner signer = new ECDSASigner(ecPrivateKey);
        signedJWT.sign(signer);

        return signedJWT.serialize();
    }

    @Override
    public void disconnect() {
        isConnected = false;
        setStatus(ClientStatus.STOPPED);
    }

    public Transaction marketBuy(double targetPrice, double quantity, String clientOrderId) {
        return executeOrder(TransactionType.MARKET, TransactionSide.BUY, targetPrice, quantity, clientOrderId);
    }

    @Override
    public Transaction marketBuy(double targetPrice, double quantity) {
        return executeOrder(TransactionType.MARKET, TransactionSide.BUY, targetPrice, quantity, generateClientOrderId());
    }

    public Transaction marketSell(double targetPrice, double quantity, String clientOrderId) {
        return executeOrder(TransactionType.MARKET, TransactionSide.SELL, targetPrice, quantity, clientOrderId);
    }

    @Override
    public Transaction marketSell(double targetPrice, double quantity) {
        return executeOrder(TransactionType.MARKET, TransactionSide.SELL, targetPrice, quantity, generateClientOrderId());
    }

    @Override
    @Nullable
    public Transaction limitBuy(double targetPrice, double quantity) { return null; }

    @Override
    @Nullable
    public Transaction limitSell(double targetPrice, double quantity) { return null; }

    public String getExchangeTradePair() {
        return "%s%s".formatted(getAssetCurrency().getCurrencyCode(), getSettlementCurrency().getCurrencyCode());
    }

    @Override
    public String getExchangeIntermediatePair() {
        return "%s%s".formatted(getAssetCurrency().getCurrencyCode(), getIntermediateCurrency().getCurrencyCode());
    }

}