# Cycling Power protocol map

Normative baseline: Cycling Power Service 1.1.1 and Cycling Power Profile 1.1.1, checked against CPS.TS p9 / CPS.ICS p8 and CPP.TS p14 / CPP.ICS p9 in GATTBased TCRL package 104.

## GATT surface

| Characteristic | UUID | Standard requirement | Properties | Simulator control |
| --- | --- | --- | --- | --- |
| Cycling Power Measurement | 0x2A63 | Mandatory | Notify; optional Broadcast | All optional fields/status flags, Broadcast property, CCCD/SCCD, and per-client mask state |
| Cycling Power Vector | 0x2A64 | Optional | Notify | Characteristic presence, crank data, first angle, context, direction, and magnitude array |
| Cycling Power Feature | 0x2A65 | Mandatory | Read; conditional Indicate | Full 32-bit declaration separated from actual behavior |
| Sensor Location | 0x2A5D | Mandatory | Read | Presence can be removed intentionally for non-standard tests; value and location set are configurable |
| Cycling Power Control Point | 0x2A66 | Conditional | Write, Indicate | Presence and all 16 procedures are independently selectable |

## Measurement

The simulator implements the mandatory sint16 Instantaneous Power plus Pedal Power Balance, Accumulated Torque, Wheel/Crank Revolution Data, context-correct Extreme Force or Torque Magnitudes, packed 12-bit Extreme Angles, Top/Bottom Dead Spot Angles, Accumulated Energy, and Offset Compensation Indicator. Content Masking state is maintained per connection and reset on a new connection.

If a logical Measurement record does not fit the negotiated ATT payload, it is split only at complete optional-field boundaries. Every continuation is independently decodable, repeats the mandatory flags/power header, and advertises only the fields actually included in that notification.

## Vector

Vector notifications implement Crank Revolution Data, First Crank Measurement Angle, mutually exclusive force/torque arrays, and all four direction values. Each packet contains at most nine magnitudes. At the default ATT MTU, a packet with crank data and first angle carries at most six values; continuations omit the first angle and carry the remaining samples.

## Feature word

All defined bits 0–19 and the two-bit Distributed System Support field at bits 20–21 are represented. Actual force/torque context and distributed-system state are kept distinct from an independently editable declared feature value.

## Control Point

All procedures 0x01–0x10 are implemented: cumulative wheel value, sensor location set/request, crank length set/request, chain length set/request, chain weight set/request, span length set/request, offset compensation, Measurement content mask, sampling rate, factory calibration Date Time, and enhanced offset compensation with success and structured failure parameters. Responses use opcode 0x20. Parameter lengths, RFU mask bits, supported locations, procedure collision, CCCD state, indication completion, and MTU limits on variable response data are validated.

## Broadcast

When enabled in the simulator surface, Measurement exposes the Broadcast property and Server Characteristic Configuration descriptor. Enabling broadcast starts a separate non-connectable Android advertising set containing Cycling Power Service Data and a compact valid Measurement payload; it is refreshed with the live measurement.
