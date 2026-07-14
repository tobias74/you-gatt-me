# Standard Profiles

This repository ships standard Bluetooth GATT profile implementations intended
for simulator and client-development use.

## Fitness Machine Service

Supported FTMS machine modes:

- Indoor Bike
- Treadmill
- Rower
- Cross Trainer
- Step Climber
- Stair Climber

Implemented FTMS areas include:

- Fitness Machine Feature
- Fitness Machine Status
- Fitness Machine Control Point
- Training Status
- Supported range characteristics
- Machine-specific measurement characteristics

The simulator UI separates emitted measurement fields from declared/readable
capabilities and Control Point support, so clients can be tested against
different feature combinations.

## Cycling Speed and Cadence

The CSC profile simulates wheel and crank revolution data using the shared
measurement simulator state.

## Cycling Power

The Cycling Power profile emits standard Cycling Power Measurement packets with
power and revolution data.

## Running Speed and Cadence

The RSC profile emits standard Running Speed and Cadence Measurement data.

## Battery and Device Information

Battery and Device Information profiles provide simple standard supporting
services for clients that expect common peripheral metadata.

## Test Coverage

Unit tests currently cover selected FTMS protocol state and packet encoding
behavior under:

```text
app/src/test/java/de/tobiga/yougattme/gatt/fitnessmachine/
```
