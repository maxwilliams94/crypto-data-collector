package ms.maxwillia.cryptodata.client.mapper;

import ms.maxwillia.cryptodata.client.exception.MappingException;

public abstract class AbstractResponseMapper<T, R> implements ResponseMapper<T, R> {

    @Override
    public R map(T response) throws MappingException {
        return doMap(response);
    }

    /**
     * Performs the actual mapping of a validated response
     * @param response The validated raw response
     * @return The mapped domain object
     * @throws MappingException if mapping fails
     */
    protected abstract R doMap(T response) throws MappingException;
}