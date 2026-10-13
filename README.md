# PTX7 Cycle Count Scanner

A focused, site-specific app that replaces walking the floor with a laptop and typing
counts one-by-one. Scan a shelf label, eyeball the count, confirm, move on — then one
paste into the master sheet at the end.

## The loop

1. **Scan** the printed Code 128 PAC label with the PM86 trigger (keyboard-wedge).
2. The item screen shows its **photo**, UOM, Min/Max, and **last count**.
3. Set today's count on the **rolling wheel** (0.25 steps for small counts, whole numbers
   once large) or type it in **manual entry** with +/- steppers. Decimals supported.
4. **Review** — a big confirmation shows the number, `last → now`, and a red
   **BELOW MIN** warning if applicable.
5. **Save & Next** keeps the scan rhythm going. The home checklist counts down to zero.

## Two modes (shared count per PAC)

- **Daily Count** — the full sweep; complete when every PAC has been counted at least once.
- **Individual Update** — adjust any PAC anytime for inventory fluctuations.

## Export (no backend, no scripts)

Nothing is uploaded. All counts live on the phone (offline). On the Export screen:

- **COPY & OPEN SHEET** — copies the counts as a **vertical column** (blank for uncounted,
  in the sheet's fixed row order) and opens the master SharePoint sheet. Sign in if
  prompted, tap the new date column's first cell, and **paste**. Done.
- Fallbacks: **Copy column only**, **Share CSV** (backup).

The app matches the sheet's **fixed row order** ("we just add dates" — a new dated column
each count). Uncounted items are left blank for the inventory manager to call out.

## Site management

- **Photos:** captured in-app per PAC (take/retake/delete), stored locally, reference only.
- **Items:** add / edit / delete to keep the app's list aligned with the master sheet.
- **Sheet URL:** configurable under Manage (defaults to the provided SharePoint link).
- **New day:** gated on the prior day being exported, so an unfinished count is never lost.

## Build / deploy

Phone-only via GitHub Actions (no Android Studio). See `PHONE_DEPLOY_INSTRUCTIONS.md`.
Barcodes to print are in `labels/PTX7_PAC_Barcodes_Code128.pdf`.

## Reused from the proven PIMS scanner

- Point Mobile PM86 built-in scanner in keyboard-wedge mode (no camera, no ML Kit).
- GitHub Actions cloud build → sideload APK.
