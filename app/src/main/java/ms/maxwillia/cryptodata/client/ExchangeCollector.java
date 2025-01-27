package ms.maxwillia.cryptodata.client;


/**
 * Core interface for all exchange clients (WebSocket, REST, etc.)
 */
public interface ExchangeCollector {

    /**
     * Get a string representation of the client
     * @return string representation
     */
    String toString();

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
     * Get the subscribed symbol (standard format)
     * @return symbol
     */
    String getSubscribedSymbol();

    /**
     * Get the currency symbol (standard format)
     * @return currency
     */
    String getCurrency();

    /**
     * Get the subscribed symbol (format for the exchange)
     * @return symbol
     */
    String getExchangeSymbol();

    /**
     * Get the USD-CCY rate for conversion purposes. Only applicable for exchanges that cannot use USD directly.
     */
    void updateUsdRate();


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