#!/bin/bash


# Allow a moment for the RMI registry to start
sleep 2

# Run the Auction Server
java -cp CryptoAuction/bin Server
