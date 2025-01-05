# TODO

## Blocking
- [X] Unit tests for CoinbaseWebSocketClient handling of ticker events
- [X] Unit tests for CoinbaseWebSocketClient handling of subscription events
- [X] Handling of subscription events

## Critical
- [ ] Checking/fix saving of ticker data to file
- [ ] Properly parse command line arguments for symbols
- [X] Coinbase websocket implementation
- [ ] Coinbase websocket - testing
- [ ] Firi REST api implementation
- [ ] Firi REST api - testing

## Features
- [ ] Coinbase websocket - exponential backoff on reconnection
- [ ] ERROR state handling for Coinbase websocket
- [ ] Allow choice of exchange to be defined at runtime
- [ ] Allow choice of symbols to be defined at runtime

## Clean-up/Refactor
- [ ] Reconnection logic / implementation 