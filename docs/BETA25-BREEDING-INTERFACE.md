# Utilities beta.25 — breeding interface

Pending jobs now use a centered, fixed-height panel rather than expanding to fill the viewport. Navigation, currency selection, acceleration, claim and cancellation buttons share panel coordinates and remain inset from its edges. Currency name, rate and balance occupy separate lines. Known zero balances disable acceleration controls; the server remains authoritative for charging and eligibility.

Start confirmation displays the selected parents and the existing estimated offspring, including their hover details. Parent selection is locked during confirmation; the preview may cycle through possible species without changing the selected parents. Cancellation and acceleration retain their server-provided confirmation text and warnings. No packet, persistence, reward, genetics or cooldown changes.

The responsive canvas has a minimum logical size of 440 by 380; compact panels and native tooltips use the existing render and inverse-input transform. Buttons truncate oversized labels, and the selected job number follows the server-selected job.

Validation: full Gradle build and existing tests passed. Live server logs show trainer reward delivery and legendary unlock/delivery during the user's tests, along with Cobblemon entity-removal warnings. Client visual verification at GUI scales 3 and 4 remains necessary.
