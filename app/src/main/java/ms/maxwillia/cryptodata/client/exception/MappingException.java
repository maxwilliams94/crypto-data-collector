package ms.maxwillia.cryptodata.client.exception;

import lombok.Getter;

/**
 * Exception thrown when response mapping fails
 */
@Getter
public class MappingException extends Exception {

    public MappingException(String message) {
        super(message);
    }

    public MappingException(String message, Throwable cause) {
        super(message, cause);
    }
}
