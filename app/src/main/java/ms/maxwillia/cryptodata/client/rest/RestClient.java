package ms.maxwillia.cryptodata.client.rest;

import ms.maxwillia.cryptodata.client.ExchangeClient;

/**
 * Interface for REST-specific client functionality
 */
public interface RestClient extends ExchangeClient {

    /**
     * Test REST API connectivity
     * @return true if connection test successful
     */
    boolean testConnection();
}