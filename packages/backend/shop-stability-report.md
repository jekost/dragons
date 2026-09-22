# Shop Stability

_Generated 2026-09-22._

Started **15** fresh games and compared the shop's full `(id, cost)` signature across them. Distinct layouts: **1**.

➡️ Prices and inventory are **fixed** — identical in every game, so the solver can read the shop once per game and trust it.

| Layout signature | Games |
| --- | ---: |
| `ch:300,cs:100,gas:100,hpot:50,iron:300,mtrix:300,rf:300,tricks:100,wax:100,wingpot:100,wingpotmax:300` | 15 |

Re-run with `npm run characterize:shop`.
