package ms.maxwillia.cryptodata.client.mapper.coinbase;

import com.fasterxml.jackson.databind.JsonNode;
import ms.maxwillia.cryptodata.client.mapper.AbstractResponseMapper;
import ms.maxwillia.cryptodata.client.exception.MappingException;
import ms.maxwillia.cryptodata.model.Transaction;
import ms.maxwillia.cryptodata.model.TransactionStatus;

import java.util.HashMap;
import java.util.Map;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.StreamSupport;

/**
 * Base mapper for Coinbase API responses
 * Handles common error detection and extraction
 */
public abstract class CoinbaseResponseMapper<R> extends AbstractResponseMapper<JsonNode, R> {

    @Override
    public boolean isError(JsonNode response) {
        if (response == null) {
            return true;
        }

        // Check for error field in Coinbase API response
        if (response.has("error_details")) {
            return true;
        }

        // Check for errors array in Coinbase API response
        if (response.has("errs") && response.get("errs").isArray() && !response.get("errs").isEmpty()) {
            return true;
        }

        // Check for success field, if present
        if (response.has("success") && response.get("success").isBoolean()) {
            return !response.get("success").asBoolean();
        }

        return false;
    }

    public boolean isWarning(JsonNode response) {
        if (response == null) {
            return false;
        }

        // Check for warning field in Coinbase API response
        return response.has("warning") && response.get("warning").isArray() && !response.get("warning").isEmpty();
    }

    @Override
    public String getErrorDetails(JsonNode response) {
        if (response == null) {
            return "NULL_RESPONSE";
        }

        // Extract from error field
        if (response.has("error_details")) {
            return response.get("error_details").asText();
        }

        // Extract from errors array (preview trades)
        if (response.has("errs") && response.get("errs").isArray() && !response.get("errs").isEmpty()) {
            return StreamSupport.stream(response.get("errs").spliterator(), false)
                    .map(JsonNode::asText)
                    .collect(Collectors.joining(","));
        }

        return "UNKNOWN_ERROR";
    }
}
