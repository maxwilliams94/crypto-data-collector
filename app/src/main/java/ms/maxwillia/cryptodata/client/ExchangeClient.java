package ms.maxwillia.cryptodata.client;

public interface ExchangeClient {
    /**
     * Get a string representation of the client
     *
     * @return string representation
     */
    String toString();

    /**
     * Initialize the client connection
     *
     * @return true if initialization was successful
     */
    boolean initialize();

    /**
     * Configure REST client parameters
     *
     * @return true if configuration successful
     */
    boolean configure();

    /**
     * Get the exchange name
     *
     * @return exchange name
     */
    String getExchangeName();

    /**
     * Get the currency symbol (standard format)
     *
     * @return currency
     */
    String getCurrency();

    /**
     * Get the subscribed symbol (format for the exchange)
     *
     * @return symbol
     */
    String getExchangeSymbol();

    /**
     * Get client status
     *
     * @return current status
     */
    ClientStatus getStatus();
}
