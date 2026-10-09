# Defenses and traps

Available on the **0.1.1.0 test branch**. Build traps near gates, roads and narrow approaches, then let guards finish attackers slowed by your defenses.

## Choose a trap

Damage is measured in hearts before armor. Each activation spends one use.

| Trap | Research | Effect | Uses | Reset |
| --- | --- | --- | --- | --- |
| Wooden Spikes | Stone Age | 1 heart and a short slow | 12 | Automatically after 2 seconds |
| Tangle Net | Stone Age | Strong slow for 4 seconds | 4 | Rearm with 1 string |
| Bronze Snare | Bronze Age | 1.5 hearts and a 2-second hold | 6 | Rearm with 1 string |
| Bronze Caltrops | Bronze Age | Half a heart and a 5-second slow | 12 | Automatically after 3 seconds |
| Iron Spring Trap | Iron Age | 3 hearts and a short slow | 8 | Automatically after 5 seconds |

Players, citizens, animals, pets and calm neutral monsters pass safely. Traps target hostile monsters. An attacker can activate at most one trap per second, preventing stacked traps from hitting it simultaneously.

## Craft and place

Recipes work in a crafting table; craftsmen can also learn them as ordinary stock orders.

| Recipe | Ingredients | Output |
| --- | --- | --- |
| Wooden Spikes | 5 sticks over 3 planks | 2 |
| Tangle Net | 4 string at the corners, 4 sticks between them | 1 |
| Bronze Snare | 2 bronze ingots at the top corners, string in the center, 3 planks along the bottom | 1 |
| Bronze Caltrops | 1 bronze ingot above a row of 3 bronze ingots | 4 |
| Iron Spring Trap | 2 iron ingots at the top corners; iron, plank, iron in the middle; 3 planks along the bottom | 1 |

Place traps on solid ground inside a town where you have building permission. That town needs the trap's age research. They remain walkable and have visible armed, triggered and broken forms. Green markers indicate armed defenses; gray markers indicate broken ones.

## Rearm and repair

Right-click with the required material. Empty-hand right-click shows the remaining uses and the current requirement above the hotbar.

| Worn-out trap | Full repair cost |
| --- | --- |
| Wooden Spikes | 1 plank of any kind |
| Tangle Net | 2 string |
| Bronze Snare or Bronze Caltrops | 1 bronze ingot |
| Iron Spring Trap | 2 iron ingots |

Rearming a net or snare retains its wear. A fully broken mechanism needs its repair material instead. Breaking and replacing a trap also retains its wear. Trap positions, cooldowns and block states survive saving.

After an alarm ends, craftsmen can maintain loaded traps within **128 blocks of their station**. Give the craftsman a reachable job barrel and a clear route. Couriers deliver needed materials from the warehouse; the craftsman collects the supplies, walks to the trap and works before spending the repair cost. Blocked routes are skipped and retried later.

The banner's **Overview** reports ready and loaded traps. **Needs → Show** locates defenses needing maintenance. These notices do not create chat messages on each activation.

## Wave approaches

Waves gather outside the footprint of your active stations and recorded traps, while staying in safe ticking terrain inside the wider claim. They avoid constructed roofs and platforms, water and furnished work areas. If no safe approach is loaded, the wave waits and retries; the banner explains the delay and rate-limited diagnostics report it in the server console.

The server setting **maxSettlementTraps** defaults to **256** per settlement. Trap checks do not load distant chunks. Keep reachable entrances and guards near approaches; ranged attackers can still shoot from a distance.
