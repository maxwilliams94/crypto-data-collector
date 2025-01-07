# TODO

## Blocking
- [X] Unit tests for CoinbaseWebSocketClient handling of ticker events
- [X] Unit tests for CoinbaseWebSocketClient handling of subscription events
- [X] Handling of subscription events

## Critical
- [X] Checking/fix saving of ticker data to file
- [X] Properly parse command line arguments for symbols
- [X] Coinbase websocket implementation
- [ ] Coinbase websocket - testing
- [ ] Firi REST api implementation
- [ ] Firi REST api - testing

## Features
- [ ] Coinbase websocket - exponential backoff on reconnection
- [ ] ERROR state handling for Coinbase websocket
- [ ] Allow choice of exchange to be defined at runtime
- [X] Allow choice of symbols to be defined at runtime
- [ ] File name for CSV storage from serial time to ISO time

## Clean-up/Refactor
- [ ] Reconnection logic / implementation 
- [ ] Subscription separation from websocket connection/onOpen