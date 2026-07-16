# Cycling Speed and Cadence (CSC) protocol map

Normative baseline: Cycling Speed and Cadence Service 1.0.1 and Cycling Speed and Cadence Profile 1.0.1, checked against CSCS.TS p9 / CSCS.ICS p7 and CSCP.TS p10 / CSCP.ICS p8 in GATTBased TCRL package 104.

## GATT surface

| Characteristic | UUID | Standard requirement | Properties | Simulator control |
| --- | --- | --- | --- | --- |
| CSC Measurement | 0x2A5B | Mandatory | Notify | Wheel and crank pairs independently selectable, including intentionally invalid empty content |
| CSC Feature | 0x2A5C | Mandatory | Read; conditional Indicate | Declared bits can mirror actual behavior or be edited independently |
| Sensor Location | 0x2A5D | Mandatory with Multiple Locations, otherwise optional | Read | Presence, value, and supported-location set are independent |
| SC Control Point | 0x2A55 | Present iff at least one procedure is supported | Write, Indicate | Presence and each standard CSC procedure are independent |

## Measurement

Wheel data is uint32 Cumulative Wheel Revolutions plus uint16 Last Wheel Event Time in 1/1024 second. Crank data is uint16 Cumulative Crank Revolutions plus uint16 Last Crank Event Time in 1/1024 second. Wheel cumulative value never rolls over; crank cumulative value and both event-time values do. Event time is updated only when a revolution occurs.

## SC Control Point

CSC implements Set Cumulative Value (wheel only, 0x01), Update Sensor Location (0x03), and Request Supported Sensor Locations (0x04). Opcode 0x02 is Sensor Calibration in the shared SC Control Point definition but is not used by CSC and returns Op Code Not Supported. Response and ATT error behavior matches the RSC service.
