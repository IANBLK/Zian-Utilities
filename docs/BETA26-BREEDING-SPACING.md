# Utilities beta.26 — breeding job spacing

Client screenshot feedback revealed that beta.25 placed the currency rate/balance immediately below the acceleration buttons and only ten logical pixels above the helper text. The job panel now places the rate and balance before those actions, with explicit gaps between the selector, rate, actions, helper text and footer.

The job panel is 306 logical pixels high. Its minimum viewport is 440 by 410, leaving 12 pixels after the acceleration button row before helper text, space for two helper lines, and a separate footer row. Confirmation retains its 270-pixel panel. The existing responsive transform applies the same geometry to both rendering and input at GUI scales 3/4. No transaction, genetics, cooldown or packet changes.

Visual acceptance remains a client check; compilation and existing checks validate packaging and code, not the actual screenshot.
