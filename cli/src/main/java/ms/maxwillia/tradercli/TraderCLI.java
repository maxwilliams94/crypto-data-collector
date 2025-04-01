package ms.maxwillia.tradercli;

import ms.maxwillia.cryptodata.client.trader.ExchangeTrader;
import ms.maxwillia.cryptodata.client.trader.TraderFactory;
import ms.maxwillia.cryptodata.model.TransactionStatus;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Scanner;



/**
 * Example application showing how to use the CoinbaseTrader
 */
public class TraderCLI {
    private static final Logger logger = LoggerFactory.getLogger(TraderCLI.class);
    
    public static void main(String[] args) {
        if (args.length < 2) {
            logger.error("Usage: TradingExample <exchange> <currency> <intermediate> <api-key-path>");
            System.exit(1);
        }

        String exchange = args[0];
        String currency = args[1];
        String intermediate = args[2];
        String apiKeyPath = args[3];

        try {
            // Create trader
            ExchangeTrader trader = TraderFactory.createTrader(
                    exchange,
                    currency,
                    intermediate,
                    apiKeyPath);

            // Initialize and connect
            if (!trader.initialize()) {
                logger.error("Failed to initialize trader");
                System.exit(1);
            }

            if (!trader.connect()) {
                logger.error("Failed to connect to exchange");
                System.exit(1);
            }

            logger.info("Connected to {} for {}", trader.getExchangeName(), trader.getExchangeTradePair());

            // Interactive trading menu
            traderMenu(trader);
            
        } catch (IOException e) {
            logger.error(e.getMessage());
        }
    }
    
    private static void traderMenu(ExchangeTrader trader) {
        Scanner scanner = new Scanner(System.in);
        boolean running = true;
        
        while (running) {
            logger.info("========== Trader Menu ==========");
            logger.info("1. Enable Trading");
            logger.info("2. Disable Trading");
            logger.info("3. Enable Preview Trading");
            logger.info("4. Disable Preview Trading");
            logger.info("5. Get Balances");
            logger.info("6. Market Buy");
            logger.info("7. Market Sell");
            logger.info("8. Exit");
            System.out.print("Enter your choice: ");

            String choice = scanner.nextLine();

            try {
                switch (choice) {
                    case "1":
                        trader.enableTrading();
                        logger.info("Trading enabled");
                        break;

                    case "2":
                        trader.disableTrading();
                        logger.info("Trading disabled");
                        break;

                    case "3":
                        trader.enablePreviewTrading();
                        logger.info("Preview Trading enabled");
                        break;

                    case "4":
                        trader.disablePreviewTrading();
                        logger.info("Preview Trading disabled");
                        break;

                    case "5":
                        HashMap<String, Double> balances = trader.getBalances();
                        logger.info("Current Balances:");
                        for (Map.Entry<String, Double> entry : balances.entrySet()) {
                            logger.info("{}: {}", entry.getKey(), entry.getValue());
                        }
                        break;

                    case "6":
                        executeMarketBuy(trader, scanner);
                        break;

                    case "7":
                        executeMarketSell(trader, scanner);
                        break;

                    case "8":
                        running = false;
                        trader.disconnect();
                        logger.info("Disconnected from exchange");
                        break;

                    default:
                        logger.info("Invalid choice, please try again");
                }
            } catch (Exception e) {
                System.err.println("Error: " + e.getMessage());
            }
        }
        
        scanner.close();
    }
    
    private static void executeMarketBuy(ExchangeTrader trader, Scanner scanner) {
        try {
            System.out.print("Enter amount in USD: ");
            double amount = Double.parseDouble(scanner.nextLine());
            
            // For market buys, the price is approximate
            System.out.print("Enter approximate price per unit: ");
            double price = Double.parseDouble(scanner.nextLine());
            
            System.out.printf("Executing market buy for %.2f USD at ~%.2f per unit%n", amount, price);
            var transaction = trader.marketBuy(price, amount);
            if (trader.canTrade() && (transaction.getStatus().equals(TransactionStatus.EXECUTED) || transaction.getStatus().equals(TransactionStatus.PREVIEW_SUCCESS)) || !trader.canTrade()) {
                logger.info("Market buy executed successfully");
            } else {
                logger.info("Market buy failed");
            }
            logger.info(String.valueOf(transaction));
        } catch (NumberFormatException e) {
            logger.error("Invalid number format");
        }
    }
    
    private static void executeMarketSell(ExchangeTrader trader, Scanner scanner) {
        try {
            System.out.printf("Enter %s amount to sell: ", trader.getTradePair());
            double amount = Double.parseDouble(scanner.nextLine());
            
            // For market sells, the price is approximate
            System.out.print("Enter approximate price per unit: ");
            double price = Double.parseDouble(scanner.nextLine());
            
            System.out.printf("Executing market sell for %.8f %s at ~%.2f per unit%n", 
                    amount, trader.getTradePair(), price);
            var transaction = trader.marketSell(price, amount);

            if (transaction.getStatus().equals(TransactionStatus.EXECUTED) || transaction.getStatus().equals(TransactionStatus.PREVIEW_SUCCESS)) {
                logger.info("Market sell executed successfully");
            } else {
                logger.error("Market sell failed");
            }
            logger.info(transaction.toString());
        } catch (NumberFormatException e) {
            logger.error("Invalid number format");
        }
    }
}
