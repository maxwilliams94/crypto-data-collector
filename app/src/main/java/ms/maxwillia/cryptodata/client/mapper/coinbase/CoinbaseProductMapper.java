package ms.maxwillia.cryptodata.client.mapper.coinbase;

import com.fasterxml.jackson.databind.JsonNode;
import ms.maxwillia.cryptodata.client.exception.MappingException;

import java.util.ArrayList;
import java.util.List;

/**
 * Maps Coinbase product responses
 */
public class CoinbaseProductMapper extends CoinbaseResponseMapper<List<String>> {

    @Override
    protected List<String> doMap(JsonNode response) throws MappingException {
        try {
            List<String> products = new ArrayList<>();

            // Check if we have products in the response
            if (!response.has("products")) {
                throw new MappingException("Response does not contain products");
            }

            JsonNode productNodes = response.get("products");
            if (!productNodes.isArray()) {
                throw new MappingException("Products is not an array");
            }

            // Extract product IDs
            for (JsonNode product : productNodes) {
                if (product.has("product_id")) {
                    products.add(product.get("product_id").asText());
                }
            }

            return products;

        } catch (Exception e) {
            if (e instanceof MappingException) {
                throw e;
            }
            throw new MappingException("Failed to map products response: " + e.getMessage(), e);
        }
    }
}
