package ms.maxwillia.cryptodata.client;


/**
 * Core interface for all exchange clients (WebSocket, REST, etc.)
 */
public interface ExchangeClient {
    /**
     * Initialize the client connection
     * @return true if initialization was successful
     */
    boolean initialize();

    /**
     * Configure REST client parameters
     * @return true if configuration successful
     */
    boolean configure();

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
     * Get the exchange name
     * @return exchange name
     */
    String getExchangeName();

    /**
     * Get the subscribed symbol
     * @return symbol
     */
    String getSubscribedSymbol();

    /**
     * Check if client is actively collecting data
     * @return true if collecting
     */
    boolean isCollecting();

    /**
     * Get client status
     * @return current status
     */
    ClientStatus getStatus();
}