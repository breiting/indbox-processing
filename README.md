# INDbox Processing Library

Minimal Processing 4 library to read INDbox serial data.

Expected serial line format (CSV):
b1,b2,pot,dist
0,1,723,42.7

Build release zip:
./gradlew buildReleaseArtifacts

Output:
release/indbox.zip

## Changelog

2026-04-21: Added filtering and send commands (requires new firmware v1.1)
