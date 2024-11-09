#!/bin/bash

# Run the ClientTest class to perform all tests
echo "Running ClientTest..."
java -cp CryptoAuction/bin ClientTest

# Check if the test ran successfully
if [ $? -eq 0 ]; then
  echo "Client test completed successfully."
else
  echo "Client test encountered issues."
  exit 1
fi
