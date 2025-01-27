package ms.maxwillia.cryptodata.client.rest;

import ms.maxwillia.cryptodata.client.ExchangeCollector;

/**
 * Interface for REST-specific client functionality
 */
public interface RestCollector extends ExchangeCollector {

    /**
     * Test REST API connectivity
     * @return true if connection test successful
     */
    boolean testConnection();
}