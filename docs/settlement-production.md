# Settlement production

[Project overview](../README.md) · [All guides](README.md)

These features are in the development branch. The public version remains **0.1.0.0**.

## Start with a small town

1. Build housing, a warehouse, a farm and a courier. Keep ready-to-eat meals available.
2. Add a Researcher Station and a separate lectern. Set the job in **People → Jobs**.
3. Add a Gatherer Station near cane or bamboo. Give it a shovel and its own barrel. It also collects dry, exposed sand, gravel and clay, avoiding protected construction.
4. Set a paper target in **Production → Workshop**. Craftsmen follow the real crafting recipe; couriers move supplies and finished goods.
5. Stock ink sacs or charcoal. Smelters can make charcoal from logs, glass from sand, bricks from clay balls and terracotta from clay blocks in normal furnaces. Blast furnaces still accept their normal recipes.

## Research with scrolls

A researcher spends **2 paper + 1 ink sac or charcoal** and works at its lectern for **30 seconds** to write one Research Scroll. The default warehouse target is 32; set a different target in **Research**, or zero to pause new scrolls. A paid scroll still finishes if its target is lowered. Paid work survives saving, stopped workers and full storage.

Spend earned scrolls and the listed materials to unlock a discovery. Scrolls can be traded between towns. Research still belongs to the settlement: every accepted member shares its unlocks, while allies retain their own progress.

Bronze Age requires a staffed researcher and **3 citizens**. Iron Age requires Bronze Age and a **staffed blacksmith with an anvil and furnace**. Field Medicine requires a staffed hospital with a bed. Other prerequisites and expedition schematics remain visible in Research.

Existing unlocks are retained. Projects paid under the earlier system still finish their saved lectern work without charging new supplies or scrolls.

## Build a bronze smithy

Bronze Age unlocks the Blacksmith Station: **8 planks around 1 copper ingot**. Add a bronze anvil, a furnace and a job barrel within its range.

A bronze anvil uses **3 bronze ingots across the top, 1 copper ingot in the centre, and 3 copper ingots across the bottom**. Repairs, metallurgy and forging work **65% slower than iron**, at **35% of iron's speed**. Both use normal anvil wear: a 12% chance per completed operation to advance from fresh to chipped to damaged, then break. Moving an anvil preserves its wear, including anvils from existing saves. The station screen shows its condition and speed. Iron anvils can also support gem and netherite forging.

The smith repairs damaged equipment first, using the matching material and preserving the original item, name and enchantments. It then fills stock orders in **Production → Forge**:

| Work | Input | Output |
| --- | --- | --- |
| Bronze alloy | 3 copper ingots + 1 tin ingot | 4 bronze ingots |
| Raw metal refining | 4 raw copper, tin, iron or gold | 4 matching ingots |
| Equipment forging | The item's normal recipe ingredients | Its normal equipment result |
| Netherite upgrading | Template + original diamond gear + netherite ingot | Native netherite upgrade, preserving components |

Each completed batch also consumes **1 coal or charcoal**. With an **iron anvil**, refining/alloying takes 8 seconds; equipment takes 12–18 seconds; netherite upgrades take 20 seconds, before modest worker skill bonuses. Bronze-anvil work takes about **2.86 times as long**: an 8-second alloy batch takes about 23 seconds. These are active work times; walking and supplies add time. If work is interrupted, unused ingredients stay with the worker. Restarting can require redoing the unfinished work time.

Wood and stone gear remain player-crafted. Bronze, copper and later gear must be forged, including shields, buckets, shears, crossbows and maces. Blocked player recipes and vanilla redstone crafters leave their ingredients intact. Found gear becomes usable once its age is researched.

## Automate job blocks

**Production → Workshop** offers job blocks, the banner, furniture, paper and basic supplies. Set a target without needing an example item. The craftsman makes shortages using actual materials; zero pauses an order. You can still teach other ordinary crafting recipes by example at its station.

Stock targets count goods in town storage. Keep job barrels separate from warehouse storage and give couriers clear paths. Forge barrels retain working inputs and repair reserves; surplus alloys and finished equipment can be collected. Smeltery barrels retain a fuel reserve and release surplus charcoal.

## Explore ruined town halls

New expedition discoveries can include ruined town halls. They contain actual housing, warehouse, research, workshop, gatherer and blacksmith blocks, a bronze smithy, and finite scroll salvage. They demonstrate useful room layouts and can be salvaged. Discovery checks loaded natural terrain and protected construction; a saved ruin is never rebuilt or restocked.

## Find the right page

| Banner destination | Use it for |
| --- | --- |
| People | Citizens, jobs, housing, recruitment and population upgrades |
| Production | Warehouse requests, workshop orders, forging and station status |
| Research | Scroll targets, pause reasons, prerequisites and discoveries |
| Neighbours | Caravans, contracts, alliances and quiet town news |
| Needs / More | Problems; alarm, projects, expeditions, settings and map |
