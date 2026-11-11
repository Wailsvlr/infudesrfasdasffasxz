#!/bin/bash
set -e
cd "$(dirname "$0")"
mkdir -p out
javac --release 8 -d out src/LimpInjector.java
# Embed the mod JAR as a resource inside the launcher JAR
cp resources/legitclient-1.0.0.jar out/
cd out
echo "Main-Class: LimpInjector" > MANIFEST.MF
jar cfm ../LimpInjector.jar MANIFEST.MF *.class legitclient-1.0.0.jar
cd ..
rm -rf out
echo "Built LimpInjector.jar (mod embedded)"
ls -lh LimpInjector.jar
