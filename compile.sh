#!/bin/bash

# Create bin directory if it doesn't exist
mkdir -p CryptoAuction/bin

# Compile all Java files in the src directory, outputting to the bin directory
echo "Compiling Java files..."
javac -d CryptoAuction/bin CryptoAuction/src/*.java

# Check if the compilation was successful
if [ $? -eq 0 ]; then
  echo "Compilation successful. All classes are in 'CryptoAuction/bin'."
else
  echo "Compilation failed."
  exit 1
fi
