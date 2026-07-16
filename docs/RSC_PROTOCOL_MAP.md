# Running Speed and Cadence (RSC) protocol map

Normative baseline: Running Speed and Cadence Service 1.0.1 and Running Speed and Cadence Profile 1.0.1, checked against RSCS.TS p7 / RSCS.ICS p5 and RSCP.TS p10 / RSCP.ICS p9 in GATTBased TCRL package 104.

## GATT surface

| Characteristic | UUID | Standard requirement | Properties | Simulator control |
| --- | --- | --- | --- | --- |
| RSC Measurement | 0x2A53 | Mandatory | Notify | Mandatory surface; every optional field/status is independently selectable |
| RSC Feature | 0x2A54 | Mandatory | Read; conditional Indicate | Declared bits can mirror actual behavior or be edited independently |
| Sensor Location | 0x2A5D | Mandatory with Multiple Locations, otherwise optional | Read | Characteristic presence, current value, and supported-location set are independent |
| SC Control Point | 0x2A55 | Present iff at least one procedure is supported | Write, Indicate | Characteristic presence and every procedure are independent |

## Measurement

The encoded order is Flags, Instantaneous Speed (uint16, 1/256 m/s), Instantaneous Cadence (uint8, 1/min), optional Instantaneous Stride Length (uint16, 0.01 m), and optional Total Distance (uint32, 0.1 m). Flag bit 2 carries walking/running state and has no following field. RFU bits remain zero in standard configurations.

## Feature and behavior separation

Feature bits are stride length, total distance, walking/running status, sensor calibration, and multiple sensor locations. The simulator maintains a declared feature word separately from actual Measurement content and actual Control Point procedure support. Turning off mirroring therefore permits intentional contradictions for collector robustness testing.

## SC Control Point

Implemented procedures are Set Cumulative Value (0x01), Start Sensor Calibration (0x02), Update Sensor Location (0x03), and Request Supported Sensor Locations (0x04). Responses use 0x10 followed by request opcode and result. Invalid operands, unsupported opcodes, operation failure, ATT 0x80 Procedure Already in Progress, and ATT 0x81 CCCD Improperly Configured are handled. A procedure stays active through indication completion.
