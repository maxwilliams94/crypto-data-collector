package ms.maxwillia.cryptodata.client.collector;


import ms.maxwillia.cryptodata.client.ExchangeClient;

/**
 * Core interface for all exchange clients (WebSocket, REST, etc.)
 */
public interface ExchangeCollector extends ExchangeClient {

    /**
     * Start collecting data from the exchange
     * @return true if data collection started successfully
     */
    boolean startDataCollection();

    /**
     * Stop collecting data from the exchange
     */
    void stopDataCollection();

    /**
     * Get the USD-CCY rate for conversion purposes. Only applicable for exchanges that cannot use USD directly.
     */
    void updateIntemediateRate();


    /**
     * Check if client is actively collecting data
     * @return true if collecting
     */
    boolean isCollecting();

}