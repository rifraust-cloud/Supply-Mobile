# PTX7 Cycle Count Scanner — Test Plan

## Test 1 — Scan a known PAC
Scan a printed label, e.g. `PAC13180`.
Expected: correct item appears (description, UOM, Min/Max, last count). No added characters.

## Test 2 — Count via wheel
- Scroll the wheel to a value (try a fraction like 4.5).
- Tap REVIEW, then SAVE & NEXT.
Expected: confirm screen shows the big number and `last → now`; home progress +1; item shows ●.

## Test 3 — Count via manual + steppers
- On the count screen, type a value in Manual; tap + and − .
Expected: value changes by 0.25 (small) or 1 (≥20); wheel and manual stay consistent.

## Test 4 — Large count reachable
- Open an item like the syringes (last ~100).
Expected: wheel centers near 100 in whole-number steps; you can reach e.g. 94. Manual works too.

## Test 5 — Below minimum
- Enter a count below the item's Min.
Expected: confirm screen number is RED with "BELOW MIN n"; home row border/value red.

## Test 6 — Photo capture
- On an item, tap the photo area, take a picture.
Expected: photo shows on the item screen; "tap to retake" appears; persists after reopening.

## Test 7 — Offline
- Airplane mode. Count several items, force-close, reopen.
Expected: all counts and photos remain. Everything works with no connection.

## Test 8 — Unknown code / Add item
- Scan/type a PAC not in the list → "CODE NOT IN LIST".
- Tap ADD THIS PAC, fill fields, save.
Expected: item added (marked "added"), appears in checklist and Manage.

## Test 9 — Manage: edit / delete
- Manage → tap an item → edit Min/Max → save. Then delete a test item.
Expected: edits reflected; deleted item gone from checklist.

## Test 10 — Export: Copy & Open Sheet
- Count a few items, Export → COPY & OPEN SHEET.
Expected: counts column copied; master sheet opens in browser (sign in if asked);
pasting into the new date column fills top-to-bottom; uncounted rows are blank.
- Verify "Copy column only" and "Share CSV" too.

## Test 11 — New day gating
- Before exporting, try START NEW DAY.
Expected: blocked with "not exported yet". After exporting, it rolls over: today's counts
become last count, entries clear.

## Capture if anything fails
Which test/step, the exact PAC/value, and any on-screen message.
