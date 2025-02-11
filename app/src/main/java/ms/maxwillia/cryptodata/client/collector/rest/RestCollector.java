package ms.maxwillia.cryptodata.client.collector.rest;

import ms.maxwillia.cryptodata.client.collector.ExchangeCollector;

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