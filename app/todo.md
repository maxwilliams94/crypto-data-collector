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

### Arbitrage
- [ ] Design for detecting arbitrage opportunities
- [ ] Data collection interfaces and parent classes split into distinct collection classes
- [ ] `offerTick` compatibility with `BlockingQueue` and `AtomicDouble`
- [ ] Booking/trading interfaces and parent classes
- [ ] Arbitrage runner class launching collection instances and empty booking instances
- [ ] arbitrage detection
- [ ] Test runs with empty trading empty arbitrage opportunities
- [ ] Coinbase Authentication
- [ ] Firi Authentication

## Features
- [ ] Allow choice of exchange(s) to be defined at runtime
- [ ] Coinbase websocket - exponential backoff on reconnection
- [ ] ERROR state handling for Coinbase websocket
- [X] Allow choice of symbols to be defined at runtime
- [X] File name for CSV storage from serial time to ISO time

## Clean-up/Refactor
- [ ] Reconnection logic / implementation 
- [ ] Subscription separation from websocket connection/onOpen
- [ ] Stale USD rate handling -> stop data collection
- [ ] CoinbaseWebSocketClient - allow websocket URL to be configurable with sensible default