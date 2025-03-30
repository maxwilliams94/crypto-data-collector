# TODO

## Blocking
- [X] Unit tests for CoinbaseWebSocketClient handling of ticker events
- [X] Unit tests for CoinbaseWebSocketClient handling of subscription events
- [X] Handling of subscription events

## Critical
- [X] Checking/fix saving of ticker data to file
- [X] Properly parse command line arguments for symbols
- [X] Coinbase websocket implementation
- [X] Coinbase websocket - testing
- [X] Create ExchangeClient and BaseExchangeClient classes to support both REST and WebSocket clients
- [X] Firi REST api implementation
    - [x] Get Ticker order book
    - [X] Convert order book to best_ask/best_bid data
- [X] Firi REST api - unittesting
- [X] Firi REST api - testing
- [ ] Better price logging

### Trader
- [X] Booking/trading interfaces and parent classes
- [X] Rework Transaction fields (combined request and execution price fields + logging)
- [X] Redefine BaseExchangeClient to properly define currency pairs as well as a native currency (if relevant)

#### Coinbase
- [X] MarketBuy
- [X] MarketSell
- [X] Authentication
- [X] MarketBuy mocking
- [X] correct order request
- [X] Preview order request for testing
- [X] handle successful order
- [X] handle failed order
- [ ] ~~requestMapper~~
- [X] responseMapper
  - [X] market orders
- [ ] get order fill details post execution for accurate accounting
- [ ] CLI/test bed for coinbase trader
- [ ] Wallet withdrawal

#### CLI
- [ ] Show transactions
- [ ] Save transactions to file

#### Firi
- [ ] MarketBuy with intermediate sell
- [ ] MarketSell with intermediate buy
- [ ] Authentication
- [ ] Preview order request for testing
- [ ] handle successful order
- [ ] handle failed order
- [ ] ~~requestMapper~~
- [ ] responseMapper
- [ ] get order fill details post execution for accurate accounting


### Arbitrage
- [ ] Design for detecting arbitrage opportunities
- [ ] Springboot integration
- [X] Data collection interfaces and parent classes split into distinct collection classes
- [ ] Make sure that new interfaces are being used
- [ ] `offerTick` compatibility with `BlockingQueue` and `AtomicDouble`
- [ ] Arbitrage runner class launching collection instances and empty booking instances
- [ ] arbitrage detection
- [ ] Test runs with empty trading empty arbitrage opportunities
- [X] Coinbase Authentication

## Features
- [ ] Allow choice of exchange(s) to be defined at runtime
- [ ] Coinbase websocket - exponential backoff on reconnection
- [ ] ERROR state handling for Coinbase websocket
- [X] Allow choice of symbols to be defined at runtime
- [X] File name for CSV storage from serial time to ISO time

## Clean-up/Refactor
- [ ] use Money class for all currency values
- [ ] Reconnection logic / implementation 
- [ ] States/ClientStatus needs cleaning up
- [ ] Events can be stored within the client or logged without updating the status
- [ ] Subscription separation from websocket connection/onOpen
- [ ] Stale USD rate handling -> stop data collection
- [ ] CoinbaseWebSocketClient - allow websocket URL to be configurable with sensible default
- [X] OpenAPI specification for Coinbase
- [X] OpenAPI specification for Firi
- [ ] Implement WebAPI from openapi generated classes