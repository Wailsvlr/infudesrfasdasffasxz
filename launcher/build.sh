#!/bin/bash
set -e
cd "$(dirname "$0")"
mkdir -p out
javac -d out src/LimpInjector.java
cd out
echo "Main-Class: LimpInjector" > MANIFEST.MF
jar cfm ../LimpInjector.jar MANIFEST.MF *.class
cd ..
rm -rf out
echo "Built LimpInjector.jar"
