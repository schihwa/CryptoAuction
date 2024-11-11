#!/bin/bash

# Create the bin directory if it doesn't exist
mkdir -p bin

# Compile all .java files and place the .class files in the bin directory
javac -d bin *.java

