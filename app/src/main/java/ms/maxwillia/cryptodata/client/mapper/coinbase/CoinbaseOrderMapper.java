package ms.maxwillia.cryptodata.client.mapper.coinbase;

import java.time.Instant;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;

import com.fasterxml.jackson.databind.JsonNode;
import kotlin.NotImplementedError;
import ms.maxwillia.cryptodata.client.exception.MappingException;
import ms.maxwillia.cryptodata.model.Transaction;

/**
 * Maps Coinbase order responses to Transaction domain objects
 */
public class CoinbaseOrderMapper extends CoinbaseResponseMapper<Transaction> {

    private final Transaction baseTransaction;

    public CoinbaseOrderMapper(Transaction baseTransaction) {
        this.baseTransaction = baseTransaction;
    }

    @Override
    protected Transaction doMap(JsonNode response) throws MappingException {
        try {
            // Start with the base transaction
            Transaction.TransactionBuilder builder = baseTransaction.toBuilder();

            double quoteSize;
            double baseSize;
            double price;

            if (response.get("order_id") != null) {
                builder.exchangeId(response.get("order_id").asText());
            } else if (response.get("preview_id") != null) {
                builder.exchangeId(response.get("preview_id").asText());
            } else {
                    builder.exchangeId("unknown");
                }

            if (response.has("commission_total")) {
                builder.fee(Double.parseDouble(response.get("commission_total").asText("0.0")));
            } else {
                builder.fee(0.0);
            }


            if (response.has("quote_size") && response.has("base_size")) {
                quoteSize = response.get("quote_size").asDouble(-1.0);
                baseSize = response.get("base_size").asDouble(-1.0);
            } else if (response.has("order_configuration")) {
                JsonNode orderConfiguration = response.get("order_configuration");
                /* Market order */
                if (orderConfiguration.has("market_market_ioc")) {
                    quoteSize = orderConfiguration.get("market_market_ioc").get("quote_size").asDouble(-1.0);
                    baseSize = orderConfiguration.get("market_market_ioc").get("base_size").asDouble(-1.0);
                } else {
                    throw new NotImplementedError("Only market orders are supported");
                }
            } else {
                throw new MappingException("Could not locate quote_size or base_size in response");
            }

            builder.quantity(baseSize);
            if (quoteSize < 0 || baseSize < 0) {
                builder.price(-1.0);
            } else {
                price = quoteSize / baseSize;
                builder.price(price);
            }

            return builder.build();

        } catch (Exception e) {
            throw new MappingException("Failed to map order response: " + e.getMessage(), e);
        }
    }
}
