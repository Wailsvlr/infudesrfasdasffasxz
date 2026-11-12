#!/bin/bash
set -e
cd "$(dirname "$0")"
mkdir -p out
javac --release 8 -d out src/LimpInjector.java src/LimpAgent.java
# Embed the mod JAR as a resource inside the launcher JAR
cp resources/legitclient-1.0.0.jar out/
cd out
cat > MANIFEST.MF << 'MEOF'
Main-Class: LimpInjector
Agent-Class: LimpAgent
Can-Retransform-Classes: true
Can-Redefine-Classes: true
MEOF
jar cfm ../LimpInjector.jar MANIFEST.MF *.class legitclient-1.0.0.jar
cd ..
rm -rf out
echo "Built LimpInjector.jar (mod embedded + agent)"
ls -lh LimpInjector.jar
