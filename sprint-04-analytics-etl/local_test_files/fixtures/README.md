# Malformed fixture

The deliberately corrupted payload contains six defects:

1. `2025-01-03` has `high < low`.
2. `2025-01-04` is missing the `high` field.
3. `2025-01-05` has a non-numeric `open`.
4. `2025-01-06` has negative volume.
5. The payload includes a valid row after malformed rows, proving processing continues.
6. The fixture contains an invalid candle sequence that should not be loaded as a
   valid analytical row; the transform's row-level validation keeps malformed
   data out of the analytical table.

The transform policy is to quarantine malformed rows when a quarantine list is
provided and otherwise drop them. It never loads a row known to violate OHLCV
invariants.
