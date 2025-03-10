package ms.maxwillia.cryptodata.client.mapper;

import ms.maxwillia.cryptodata.client.exception.MappingException;

import java.util.Optional;

/**
 * Generic interface for mapping API responses to domain objects
 * @param <T> Type of the raw response
 * @param <R> Type of the mapped domain object
 */
public interface ResponseMapper<T, R> {
    /**
     * Maps a raw response to a domain object
     * @param response The raw response from exchange API
     * @return The mapped domain object
     * @throws MappingException if the response cannot be properly mapped
     */
    R map(T response) throws MappingException;

    /**
     * Determines if the response contains an error
     * @param response The raw response to check
     * @return true if the response contains an error
     */
    boolean isError(T response);

    /**
     * Extracts error details from the response
     * @param response The raw response containing an error
     * @return ErrorDetails object with error information
     */
    String getErrorDetails(T response);

    /**
     * Attempts to map the response, returning an empty Optional if mapping fails
     * @param response The raw response
     * @return Optional containing the mapped object or empty if mapping failed
     */
    default Optional<R> mapSafely(T response) {
        try {
            return Optional.ofNullable(map(response));
        } catch (Exception e) {
            return Optional.empty();
        }
    }
}
