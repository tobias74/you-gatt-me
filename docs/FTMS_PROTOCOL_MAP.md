# FTMS Protocol Map

Source of truth: Bluetooth SIG Fitness Machine Service 1.0.1.
Reviewed against a local working copy of the official specification; the specification file itself is intentionally kept outside this repository.

The implementation is also checked against the current Bluetooth SIG GATT Specification Supplement data definitions and the FTMS 1.0.1 integrated errata set (`9555`, `10187`, `10194`, `16264`, `16265`, `18751`, `18972`, `22596`, `23316`). Erratum `10194` is the reason the 1.0.1 resistance range, Control Point target, and Status target use the corrected `UINT8` representation rather than the legacy `SINT16` representation.

This simulator keeps FTMS concepts separated by characteristic role.

## Indoor Bike Data

Characteristic: `0x2AD2`.

This is notification measurement data from the server to a client. For the indoor bike path, this includes the data record fields such as instantaneous speed, cadence, distance, current resistance level, measured power, energy, heart rate, metabolic equivalent, elapsed time, and remaining time.

Rules:

- Treat these as produced/current measurement values.
- Do not put Indoor Bike Simulation Parameters here.
- Do not treat a Control Point target write as proof that the measured value changed.
- Encode the corrected Resistance Level field as `UINT8` with 0.1 resolution.
- Split oversized Data Records at the negotiated ATT MTU. Preliminary notifications set More Data; mandatory fields occur only in the final notification.

## Fitness Machine Control Point

Characteristic: `0x2AD9`.

This is the write/indicate control procedure characteristic. Target procedures store requested targets and produce Control Point responses. They may also lead to Fitness Machine Status notifications.

Rules:

- Store client-written targets in target state.
- Do not directly overwrite measurement data from target writes unless a later explicit simulator policy models physical convergence.
- Indoor Bike Simulation Parameters belong here as a procedure payload, not in Indoor Bike Data.
- Keep a procedure in progress until the client confirms the response indication.
- Defer resulting Status and Training Status notifications until that confirmation.
- FTMS 1.0.1 Set Target Resistance Level uses a one-octet `UINT8` parameter with 0.1 resolution.

## Fitness Machine Status

Characteristic: `0x2ADA`.

This is event/status notification data. It reports target changes, indoor bike simulation parameter changes, wheel circumference changes, spin-down status, and permission/state events.

Indoor Bike Simulation Parameters are represented as:

- Wind speed, in meters per second.
- Grade, in percent.
- Crr, the coefficient of rolling resistance.
- Cw, the wind resistance coefficient.

Rules:

- Show these separately from measurement data in the UI.
- When a client writes the simulation-parameter procedure, report the full parameter set, not only grade.
- FTMS 1.0.1 Target Resistance Level Changed uses a one-octet `UINT8` parameter with 0.1 resolution.

## Supported Range Characteristics

Characteristics: `0x2AD4` through `0x2AD8`.

These are read characteristics that tell a client valid target ranges and increments for controllable values such as speed, inclination, resistance, heart rate, and power.

Rules:

- Use these to validate Control Point target writes.
- Do not confuse supported target ranges with current measurement values.
- Expose each range characteristic only when its corresponding declared target-setting feature is supported.
- The corrected Supported Resistance Level Range is three `UINT8` fields (minimum, maximum, increment), each with 0.1 resolution.

## Simulator UI model

- **Live measurements** are editable server-produced values and show the logical Data Record plus a 20-byte ATT payload preview.
- **Remote control state** is read-only client-owned Control Point target state.
- **Protocol events** contain Control Point, Fitness Machine Status, and Training Status traffic with direction and exact hexadecimal payload.
- **Protocol configuration** controls characteristic presence, declared capabilities, actual procedures, and optional measurement fields.
