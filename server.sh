#!/bin/bash
rmiregistry &
# Navigate to the bin directory where the compiled .class files are located

sleep 2
cd bin
java Server
