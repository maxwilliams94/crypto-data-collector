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
     * Get the pair for trading
     *
     * @return currency pair
     */
    String getExchangeTradePair();

    /**
     * Get the symbol for the intermediate pair
     *
     * @return currency pair
     */
    String exchangeIntermediatePair();

    /**
     * Get client status
     *
     * @return current status
     */
    ClientStatus getStatus();
}
