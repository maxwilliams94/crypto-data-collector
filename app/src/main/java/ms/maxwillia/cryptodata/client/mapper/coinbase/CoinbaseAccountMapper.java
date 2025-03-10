package ms.maxwillia.cryptodata.client.mapper.coinbase;

import java.util.Map;
import java.util.HashMap;
import com.fasterxml.jackson.databind.JsonNode;

import ms.maxwillia.cryptodata.client.exception.MappingException;

public class CoinbaseAccountMapper extends CoinbaseResponseMapper<Map<String, Double>> {
    @Override
    protected Map<String, Double> doMap(JsonNode response) throws MappingException {
        try {
            Map<String, Double> balances = new HashMap<>();

            // Check if we have accounts in the response
            if (!response.has("accounts")) {
                throw new MappingException("Response does not contain accounts");
            }

            JsonNode accounts = response.get("accounts");
            if (!accounts.isArray()) {
                throw new MappingException("Accounts is not an array");
            }

            // Extract balance for each account
            for (JsonNode account : accounts) {
                if (account.has("currency") && account.has("available_balance")) {
                    String currency = account.get("currency").asText();
                    JsonNode balance = account.get("available_balance");

                    if (balance.has("value")) {
                        double value = balance.get("value").asDouble();
                        balances.put(currency, value);
                    }
                }
            }

            return balances;

        } catch (Exception e) {
            if (e instanceof MappingException) {
                throw e;
            }
            throw new MappingException("Failed to map account response: " + e.getMessage(), e);
        }
    }
}
