# World War MC (WWMC)

A first-person Minecraft settlement and warfare mod. You live among your citizens, direct the town's priorities, and take part in its work and battles. Citizens handle routine work themselves. The long-term world contains independent settlements, countries, and trade convoys.

Inspired by the first-person colony management of [Colony Survival](https://store.steampowered.com/app/366090/Colony_Survival/), with original Minecraft systems and an eventual emphasis on warfare.

**Target:** Minecraft Java 26.2 · NeoForge 26.2.0.88 · Java 25 · MIT license.

## Current build: 0.11.0-alpha

0.11.0 adds **hunters, fishermen, animal keepers and butchers**, with a whole-carcass → raw-portions → cooked-meals food chain. **Couriers are now the only haulers within a town**: production workers use their own job barrels, and wait for deliveries or free storage. Every mine has exactly **one miner**, including older upgraded mines. Regular meals are three times less frequent by default (six loaded minutes), healing meals have a 30-second cooldown, and scarce food is shared with priority for hungry citizens who were fed least recently. Hire couriers and add local barrels when updating an existing town.

0.10.0 makes citizens **keep their jobs** and adds **job priorities**. Each citizen has its own station and goes back to it every morning and after errands, instead of taking whichever station had the smallest crew at that moment. The banner's new **Jobs** tab sets each job to Off, Low, Normal or High: open places in higher-priority jobs fill first and draw citizens from lower ones, and Off frees a job's crew. See [Jobs and priorities](#jobs-and-priorities). Pathfinding is fixed: citizens find ordinary routes across a town and around buildings again, which they often gave up on in 0.9.2, while still preferring nearby roads and keeping to bridges over water. A Mine Station now works an exposed ore up to two blocks away as a vein, and its miner walks over and mines an ore touching the station instead of reporting it out of reach.

0.9.2 makes every citizen job favor paved paths and solid bridges over grass shortcuts. Citizens on land do not plan swims through open water, and follow bridge bends without cutting corners. Citizens already in water can still get out. Traders also look for narrow bridge decks between their usual waypoints, so wide rivers do not require swimming. Roads work automatically with dirt paths, gravel, common stone paving, planks, slabs, and stairs.

0.9.1 fixes traders stopping on walkable routes beneath roofs and tree overhangs. They choose reachable ground near their feet, try shorter and sideways legs, and retry stalled paths without teleporting or losing cargo. A headless world regression checks real delivery and return over 640 blocks, beneath an overhang and around a wall, with no nearby player.

0.9.0 adds **multiple owned towns, Trader Blocks, physical supply routes, and small neutral NPC towns**. Each town has one trader checkpoint and one partner. Set exports with Keep and Send controls; traders carry real goods between warehouses, preserving cargo through full storage and restarts. Towns owned by different players require both owners to choose the route. Neutral NPC towns have farming, timber, or mining specialties and use the existing citizen jobs, food, beds, and storage. See [Trading and other settlements](#trading-and-other-settlements).

0.8.1 makes the in-game server config readable: six sections, short setting names, and hover explanations with units and tick-to-time conversions. Existing config keys, values and defaults remain unchanged.

0.8.0 adds **emerald upgrades**. A station's screen sells a wider range or more crew slots, three levels each, and the town screen sells room for more citizens: a new town holds 10, and each population upgrade adds 5, costs more emeralds than the last and makes every enemy wave larger and tougher, bringing pillagers and then vindicators. Farms, craftsmen and the new enchanters always have exactly one worker. A new **Enchanter Station** enchants unenchanted gear and books with lapis at a nearby enchanting table: slowly (about five minutes per item, longer for rarer gear) and never above level 25. Citizens who spot a hostile **call the guards**, who send up to two guards on duty to deal with it; wave attackers **glow**, and any still alive a minute after a wave arrives are reported so the guards hunt them down. Every station and the banner have a new detailed model, turned to face whoever placed it. Existing towns keep their citizens: a town from an earlier build counts as having bought enough population upgrades for everyone it already has.

0.7.0 replaces chat read-outs with screens: right-click the settlement banner for a town overview (citizens, stations, food, storage, alarm and waves, with buttons for work priority, the alarm and recruiting), a station for its status, crew and storage, or a citizen for their job, health, meals and equipment above their bag. Craftsmen now learn any crafting-table recipe: click the Craftsman Station's teach slot with an item, then set how many to keep with its slider. Barrels near work stations become job storage, and a new **Courier Station** employs couriers who carry finished goods to the warehouse and keep smelters' and cooks' barrels stocked. A Mine Station touching an ore works it as an endless vein. Farms take one farmer each by default. Short notices now appear above the hotbar instead of in chat. Existing towns keep their stations and progress; their craftsman orders become learned orders.

0.6.0 adds a craftable in-game Settlement Guide, shared armor between guard shifts, better armor selection, retirement of equipment below 25% durability, Blacksmith Stations that repair real equipment using warehouse materials, and healing from real meals or food given by the owner. Existing towns and inventories remain compatible.

0.5.2 gives citizens four-block work and melee reach, measured from their eyes to block faces or enemy hitboxes. Lumberjacks choose clear standing spots beside trees and cut unprotected natural leaves that block the trunk or trap their body, keeping real drops and consuming axe durability. The tree’s original natural recognition is saved after access clearing, so work can resume after a restart while all player-block and building checks still apply.

0.5.1 makes citizens return their previous job's armor, weapons and tools when they change jobs, moves citizens who stay stuck for 30 seconds onto the settlement banner, stops citizens shoving each other off quarry stairs, and only sends quarry crews down a staircase that is intact, rebuilding collapsed steps. Citizens now open doors, craftsmen deliver the tools they make, smelters and cooks no longer burn equipment, and new mines and cave mining stay out of quarry chunks.

0.5.0 adds Smeltery and Cook Stations, makes guard shifts independent for each station, and activates every assigned guard when a town bell rings. It retains the staggered citizen updates, shared resource scans, bounded path probes, and quarry crash fix from 0.4.2. Existing worlds, inventories, and quarry progress remain compatible.

This is the first settlement foundation, not the completed warfare game.

Implemented:

- Persistent named settlements, owners, non-overlapping claims, and job priorities.
- Citizens who keep their own station, and job priorities from Off to High that decide which open places fill first.
- Settlement banner and twenty role stations, each with its own detailed model, survival crafting recipes and a creative tab.
- Emerald upgrades: wider station ranges, more crew slots, and room for more citizens at the price of larger enemy waves.
- Screens for the town, every station, the Craftsman's orders and each citizen, refreshed every second.
- A 240-block minimum claim radius and red banners at the four claim corners.
- Automatic 7×7×7 station detection and live updates when nearby furniture/resources change.
  Bed and warehouse locations refresh within one second; inspection and recruitment refresh them immediately. Workers still check detected beds and storage against the live world before using them.
- A placement range outline and a Station Inspector for checking existing stations.
- Deterministic ownership of overlapping beds, storage, and same-job work targets.
- Housing and barracks beds count toward recruitment; hospital beds remain patient capacity.
- Recruitable citizens with individual saved names, personal inventories, and custom job AI.
- Guards with day/night posts, town patrols, visible shared shift armor, equipment upgrades, and swords, spears, or bows they find themselves.
- A craftable Settlement Guide with every block recipe and instructions for the playable systems.
- Blacksmiths who repair tools, weapons, and protective armor at actual anvils with matching materials delivered by couriers.
- Enchanters who slowly enchant unenchanted gear and books with lapis at an enchanting table, up to level 25.
- Nutrition-based citizen healing from carried food and direct feeding, with a 30-second cooldown and fair access to scarce meals.
- Bell alarms: manual, projectile, redstone, and guard bell rings activate every assigned guard; civilians take cover until the all-clear. Guards also run to the bell when citizens sight a large hostile force.
- Civilians who call the guards about hostiles they spot, and guards who go and deal with them.
- Population-scaled hostile waves that arrive at night while the owner is home, glow, and are hunted down if they linger.
- Autonomous harvesting and replanting of existing wheat, carrot, potato, and beetroot crops.
- Hunters, fishermen and animal keepers supply whole carcasses; butchers prepare raw portions for cooks. Keepers feed real breeding pairs and preserve babies and adult breeders.
- Whole-tree felling, player-placement protection, and planting from actual saplings in storage.
- Endless ore veins for mine stations placed near an exposed ore, automatic mine depth selection between Y −30 and 10, descending tunnels, accessible cave ore gathering, and full-chunk quarries that crews enter by a spiral staircase.
- Craftsmen who learn any crafting-table recipe from an example item and keep the amount you choose in stock.
- Job barrels at work stations, and couriers who move goods between them and the warehouse.
- Smelters who supply nearby furnaces/blast furnaces with courier-delivered raw metals, ores, and fuel, and collect their real results.
- Cooks who supply smokers or lit campfires with raw food, collect cooked food, and make bread from three wheat.
- Openable 36-slot personal inventories, saved overflow, local supplies, one spare meal when abundant, and real tool durability.
- Shared station crews, exclusive resource reservations, civilian nighttime rest, and guard duty through the night.
- Save/reload support for settlement data, player block protection, replanting sites, excavation progress, cargo, tools, and meal timers.

Planned: automatic housing construction, medical treatment, military squads, raids by rival settlements, independent AI settlements, distant simulation, technology progression, convoys, and countries. A barracks station identifies troop housing in this build; it does not train soldiers yet. A hospital station identifies patient beds; it does not provide medical treatment yet. Citizens can heal by eating food.

## Try the first build

Install the same mod JAR on the NeoForge 26.2 client and server. Use a new test world for this alpha. Craft a **Settlement Guide** from one book and one blue dye, or run `/wwmc guide`, then right-click it to read the instructions.

1. Craft or obtain a **Settlement Banner**, place it on solid ground with open space around it, and right-click it with an empty hand to found your town. New towns extend **240 blocks in each horizontal direction**, a 481×481 block footprint including the center. Red banners appear at the four corners when those chunks are loaded and the ground can support a banner. The mod does not load distant chunks to place them or replace obstructing blocks.
2. Build a small camp with beds. Place a **Housing Station** or **Barracks Station** inside it.
3. Beds are detected automatically within **three blocks of the station on every axis**: a **7×7×7 cube**, including the station block. Both halves of each bed must fit inside the cube and your claim. No corner selection is required.
4. Place a **Warehouse Station** within that same range of your chests or barrels. It detects multiple containers, including trapped and double chests. Stock food, axes, appropriate pickaxes, saplings, and cobblestone or other tunnel floor supplies. You can add or remove storage later without registering it again.
5. Place **Farm** and **Lumber Stations** with crops or tree roots inside their **7×7×7** ranges. Put barrels in their ranges and add a **Courier Station** to deliver tools and collect goods. A farm always takes **one farmer**; a lumber station supports **four workers**, each mine **one miner**, and a quarry **eight**. Crew upgrades add more where supported, but mines stay solo (see [Station upgrades](#station-upgrades)). Citizens reserve individual trees or excavation positions so a shared crew cannot harvest the same target twice. Housing, hospital, barracks, and warehouse ranges protect their structures from harvesting.
6. Prepare farmland and plant crops yourself. Carrots or potatoes supply both food and replanting stock; wheat is collected, and a cook makes it into bread. Put a lumber station by natural trees or accessible clear soil. It fells the connected tree, collects real leaf drops, and replants when storage has enough saplings. If no tree is accessible, it can plant a new one instead. Trees grow at Minecraft's normal rate.
7. For mining, the simplest choice is a **Mine Station placed within two blocks of an exposed ore** (an ore with at least one open side): its miner works that ore as an endless vein. See [Ore veins](#ore-veins). A Mine Station with no ore beside it digs tunnels instead: place it facing into the intended descent, with open walking space in front. It chooses and saves a random depth between **Y −30 and 10**, then digs a staircase and eight 24-block side branches. Near that depth, workers also walk to accessible exposed cave ores they can reach. Alternatively, place a **Quarry Station** facing the neighboring chunk you want excavated. The quarry removes that complete 16×16 chunk from the surface down to Y −64, preserving bedrock. Keep the station and level, walkable ground outside the target chunk, in line with the station, where the crew's staircase begins. The whole plan must fit inside the town claim.
8. Run `/wwmc recruit 3`. Recruitment is limited by loaded housing beds and the town's population limit: 10 citizens at first, raised with emeralds on the town screen (see [Population](#population)). Each citizen takes the open job of highest priority and keeps it (see [Jobs and priorities](#jobs-and-priorities)), obtains supplies, works, and delivers cargo in batches.
9. Right-click your citizen with an empty hand to open their screen: job, current activity, health, next meal and equipment above their **36-slot bag**. Add food, spare tools, saplings, or guard gear directly. Sneak-right-click releases their job, and they take another open place; holding an item while interacting shows their status above the hotbar. Right-click the banner for the town screen. Mine depths are automatic.
10. Place a **Guard Station**, supply armor stands in its local range, and configure its day/night posts as described below. Up to two citizens become guards automatically; defense slots fill before production jobs.
11. Place a **Craftsman Station** near your warehouse and teach it what to make (see [Craftsmen](#craftsmen)). Add a **Smeltery Station** with a furnace or blast furnace in range, and a **Cook Station** with a smoker or lit campfire in range. Stock raw ores, raw food, wheat, and fuel in the warehouse. See [Smelters and cooks](#smelters-and-cooks).
12. Add a **Blacksmith Station** with an anvil and job barrel within three blocks on each axis. Stock repair materials in the warehouse for couriers to deliver. Blacksmiths repair damaged tools/weapons and worn guard armor, retaining names and enchantments.
13. Hang a **bell** inside the town so guards can raise the alarm, and prepare for the first enemy wave once the town has three citizens. See [Alarms and enemy waves](#alarms-and-enemy-waves).
14. Every production station needs **barrels** in range and **couriers** to move goods and supplies. Add hunters or keepers, a butcher, and a cook for meat, or a fisherman beside suitable water. See [Animal food jobs](#animal-food-jobs) and [Job barrels and couriers](#job-barrels-and-couriers).
15. Place an **Enchanter Station** within five blocks of an enchanting table surrounded by bookshelves, and stock lapis lazuli. See [Enchanters](#enchanters).
16. Spend spare emeralds on the stations that matter most and on room for more citizens. See [Station upgrades](#station-upgrades).

**Range preview:** hold any station block and aim at a block face to see a blue outline at its prospective placement position. The outline accounts for replaceable grass/snow. It turns red when the placement context is blocked. Placing a station displays a green outline for about three seconds. Right-click an existing station with an empty hand to open its screen and briefly show its range. The **Station Inspector** also previews an existing station while you aim at it and opens its screen when right-clicked.

Ordinary stations show their local 7×7×7 area, or their upgraded range; an Enchanter Station shows 11×11×11; a quarry shows the neighboring chunk's footprint. The quarry outline indicates its horizontal target, not the complete depth. Mines extend beyond the local outline along their planned tunnels. Inspection reports facing, depth, progress, and active crew size.

Keep doors and paths accessible. Stations are solid blocks; citizens need to reach a neighboring block. A full warehouse, missing tools, inaccessible resources, or missing food produces a visible worker status instead of creating supplies out of thin air.

### Forestry and construction protection

Workers mine, harvest, plant, and use stations from up to **four blocks away**, while still requiring a clear view of the work. Lumberjacks approach clear ground with headroom and firm footing outside the canopy. If natural leaves obstruct a selected tree or intersect the worker, they cut one reachable leaf at a time before felling the tree, rather than trying to walk into its trunk. Decorative/persistent leaves, player-placed blocks, protected furniture, and other citizens’ footing remain protected. Walls still block work and melee attacks. The previous 30-second stuck rescue remains a fallback.

Player-placed solid blocks are recorded from this version onward, even before a town is founded. Lumberjacks reject a tree if any connected log is recorded as player-placed, if logs enter a protected building range, or if the tree touches construction such as planks or a block entity. Recognition also requires rooted trunks and non-decorative leaves. Felling follows the complete connected trunk and branches beyond the local detection cube, within bounded size, claim, and loaded-chunk limits. Unloaded or ambiguous trees are skipped rather than partly cut.

Placement history cannot be recovered for buildings made before this feature was installed. For an older build, **right-click its logs with the Station Inspector** to protect the connected logs in your claim. Decorative trees deliberately built by the player are protected too.

Replanting uses one real sapling for a small tree or four in a 2×2 plot for a large tree. The plot must be clear and accessible within the lumber station's local range. Keep spare saplings in storage: leaf drops are random, so a harvest does not guarantee enough to replant. Axes need sufficient remaining durability for the whole tree. Lumberjacks do not create saplings or speed up growth.

### Tunnel mines and quarries

A mine creates a three-block-high descending staircase, followed by two-block-high tunnels at its target Y. By default the spine has four junctions three blocks apart, each with one 24-block branch on either side. Workers approach each cut from the previous cleared step and use actual building stock to fill missing tunnel floor support. The descent has a single working front; more of the crew can work in parallel once the branches are accessible.

A quarry targets the **adjacent chunk in the direction you faced when placing it**, rather than the chunk containing its station. Crews walk into the pit and dig the blocks around them, one horizontal layer at a time, with actual pickaxe durability and drops. Mining speed follows the block's hardness and the pickaxe, as for a player: about a second per stone block with a stone pickaxe, longer for harder blocks or weaker tools. Tunnel and cave mining use the same timing.

**Getting in and out.** The pit keeps a one-block-wide **spiral staircase** around its edge: one block per layer is left standing, each a step down and over from the last, starting level with the ground just outside the chunk edge nearest the station. Citizens walk it down to the working layer and back up for meals, deliveries, rest, and alarms, a few steps at a time. The staircase is checked from the rim down on every work search, and crews only go down while every step is solid with two clear blocks above it. Where a cave removed a step, or sand or gravel collapsed, a worker standing on the step above rebuilds it with cobblestone, stone, or dirt from storage. While a step is missing, flooded, or blocked, and for quarries planned before staircases existed, crews work from the control block instead, so the quarry keeps going. Villager pathfinding cannot climb ladders reliably, so the pit uses stairs rather than ladders.

**Safety.** Citizens never shove each other, so crews can pass on the one-block stairs. A citizen who falls inside their own town's quarry takes no fall damage. Hostile mobs can still spawn in a dark pit; keep it lit.

**Nothing stalls a layer.** Quarries fell natural trees and leaves in their chunk. Blocks they must not remove are left standing, and the layer carries on around them: anything beside water or lava, containers and other block entities, player-placed or Inspector-protected blocks, planks, and unbreakable blocks. A quarry planned before this version keeps its progress; if its pit is already below the surrounding ground it has no staircase, and its crew works from the control block. Inspection reports the working layer and how the crew gets in.

A new mine plan is refused if any of its tunnels would cross a quarry's chunk, and miners never dig cave ore inside one, so the pit floor stays level. Mines planned before this version keep their tunnels; where one runs through a quarry chunk, the quarry digs down through it and the old tunnels show as trenches in the floor.

Both jobs preserve player-placed blocks, stations, protected furnishing ranges, containers/block entities, and living entities' footing. Mines also skip logs and planks. In a mine, water, lava, protected blocks, or an unsuitable tool can block a tunnel until cleared. Drain or clear obstructions yourself and inspect the worker's status. These jobs do not pump liquids, place lighting, or guarantee safe unsupported terrain. Work only runs in loaded chunks and never forces chunks to load.

Each mine chooses its depth once when its plan is created and saves the result. There is no depth command. The entrance must be above at least part of the configured depth band, and the planned descent must fit the claim. Already removed blocks grant no resources again. Completed work and parallel cuts persist across restarts.

When miners encounter caves near the selected depth, they scan a bounded nearby area for exposed ore. They walk to reachable targets and use the correct actual pickaxe. They resume planned tunnels when no cave ore is accessible. This is local cave work; systematic exploration of an entire cave network remains future work.

### Commands

| Command | Purpose |
| --- | --- |
| `/wwmc guide` | Receive a readable Settlement Guide with crafting recipes and instructions. |
| `/wwmc status` | Inspect your town's population, loaded beds, stations, and priority. |
| `/wwmc recruit [1-8]` | Recruit citizens up to the housing/population limit. Defaults to one. |
| `/wwmc name <name>` | Rename your town, up to 48 characters. |
| `/wwmc priority balanced\|food\|materials` | Set every job's priority from a preset: balanced puts guards and traders first, food also puts farms and cooks first, and materials puts farms and cooks last. |
| `/wwmc job <job> <off\|low\|normal\|high>` | Set one job's priority, for example `/wwmc job farm high`. |
| `/wwmc citizens` | List each loaded citizen with their job and what they are doing. |
| `/wwmc craft` | List craftsman orders with town stock and targets; change them on the Craftsman Station screen. |
| `/wwmc craft bread on\|off` | Switch the cooks' bread order, also available on the Cook Station screen. |
| `/wwmc alarm` | Sound the alarm yourself, or call the all-clear early while it rings. |
| `/wwmc wave` | Bring the next enemy wave forward to now, even in daylight. |

Commands affect your own town at your current position. You can own several towns in the Overworld, each with its own population limit. If you own several and stand outside them, use a banner screen or enter the town you want to manage. Claims do not implement general-purpose land protection; station removal is owner-restricted. An occupied settlement's banner is its fixed rally point and cannot be mined normally.

### Job blocks define building purpose

Beds alone do not determine what a structure is. A **role station and nearby furniture** provide that meaning. Housing, barracks, hospital, warehouse, farm, lumber, and guard supply stations scan a fixed 7×7×7 cube centered on themselves, from offsets −3 through +3 on each axis. Mines and quarries use explicit excavation plans. Furniture/resource changes are picked up on the next inspection or worker scan.

Ranges can overlap. A complete bed belongs to the nearest housing, barracks, or hospital station that contains both halves; hospital-owned beds do not recruit citizens. Each chest/barrel block belongs to the nearest warehouse, and work targets belong to the nearest station of that job. Equal distances use station coordinates (X, then Y, then Z) as a stable tie-breaker. A double chest's two physical inventories are each included once.

Only loaded blocks inside the settlement claim count. Scanning never loads chunks. A known station in an unloaded chunk retains ownership of its nearby furniture until it is loaded and validated; its own production/capacity stays paused.

| Station | Current interpretation | Later role |
| --- | --- | --- |
| Housing | Residential beds, recruiting capacity, and rest. | Families, migration, approved housing expansion. |
| Barracks | Camp/troop beds, currently usable as housing. | Recruiting, training, and organizing military units. |
| Hospital | Patient beds excluded from housing capacity. | Treatment, medical supplies, casualty evacuation. |
| Warehouse | Chests, trapped chests, and barrels within its 7×7×7 range. | Reserves, convoy loading. |
| Farm | Mature supported crops within its 7×7×7 range. | Planting expansions, varied crops, food processing. |
| Lumber | Whole trees rooted in range, real sapling planting, and replanting. | Larger forestry areas and better species/terrain handling. |
| Mine | One miner working an endless vein or digging a staircase, branches and accessible cave ores. | Cave exploration, reinforcement, lighting. |
| Quarry | Full neighboring chunk excavation, layer by layer, entered by a spiral staircase. | Machinery, dedicated haulage, liquid management. |
| Craftsman | Learned crafting-table recipes kept at chosen stock levels from real materials. | Stonecutter and smithing orders. |
| Smeltery | Warehouse ores/raw metals smelted in nearby furnaces or blast furnaces. | Specialized metallurgy and technology. |
| Cook | Raw food cooked in smokers or lit campfires; three wheat become bread. | More meals and food orders. |
| Blacksmith | Repairs courier-delivered equipment at nearby anvils, leaving it in the job barrel. | Repair orders and specialized smithing. |
| Guard | Day/night posts, patrols, armor and weapons from stands or storage, melee and archery, bell alarms. | Squad orders, training. |
| Courier | The only town hauler: moves job outputs, tools and inputs through the warehouse. | Convoys between towns. |
| Enchanter | Enchants unenchanted gear and books with lapis at an enchanting table within 5 blocks, up to level 25. | Enchanting orders and libraries. |

Each station has its own small model built from vanilla textures, a workbench, a watchtower or a tent for example, turned to face the player who placed it. A mine's tunnel entrance and a quarry's red flag point the way they dig. Stations declare use; this build does not infer enclosed rooms, roofs, or architectural quality. Work validates supplies, protection, reservations, loaded terrain, and access before changing blocks.

Saves from 0.1.0-alpha keep their towns and stations, but old selected room bounds are ignored in favor of the fixed range. Reposition stations or furniture if an earlier selected room extended farther than three blocks. Existing surveyor items become Station Inspectors and retain the `wwmc:surveyor` ID and recipe.

Existing towns smaller than 240 blocks widen to the 240 minimum automatically unless the larger square would reach another town, in which case they keep their saved radius. A widened town gets new corner banners; the old ones stay as ordinary blocks you may remove. Older mine stations default to facing north and now use tunnel plans, so inspect or reposition them before assigning workers. Mine plans from 0.2.0-alpha receive a one-time automatic-depth replacement when used; existing excavated blocks remain air and grant no duplicate drops. New automatic plans keep their chosen depth across reloads. Quarry plans retain their existing progress. Legacy numbered citizen labels receive personal names when their citizens load; custom names are preserved. Old nine-slot cargo saves expand into the new inventory. Server configs with a `settlementRadius` below 240 are corrected to 240.

### Crafting

All markers use eight planks around a center item in a crafting table.

| Marker | Center item |
| --- | --- |
| Settlement Banner | Blue wool |
| Housing Station | Oak door |
| Barracks Station | Iron sword |
| Hospital Station | Paper |
| Warehouse Station | Chest |
| Farm Station | Wheat seeds |
| Lumber Station | Stone axe |
| Mine Station | Stone pickaxe |
| Quarry Station | Iron pickaxe |
| Craftsman Station | Crafting table |
| Smeltery Station | Furnace |
| Cook Station | Smoker |
| Hunter Station | Leather |
| Fisherman Station | Fishing rod |
| Animal Keeper Station | Hay bale |
| Butcher Station | Iron axe |
| Guard Station | Iron helmet |
| Blacksmith Station | Iron ingot |
| Courier Station | Barrel |
| Enchanter Station | Book |

The Station Inspector is a shapeless recipe with two paper and one stick. The **Settlement Guide** is a shapeless recipe with one book and one blue dye; right-click it to open the native book screen. `/wwmc guide` gives another copy. Tools consumed to craft stations are separate from tools supplied to workers.

### Trading and other settlements

Craft a **Trader Block** with a compass surrounded by eight planks. Place one inside each town; a second Trader Block in the same claim is rejected without consuming its item. One citizen works as the trader. Trader Blocks have no crew or range upgrades.

1. Found a second town outside the first claim and give both towns housing, citizens, and warehouses. Population limits belong to each town separately.
2. Open the Trader Block empty-handed and select the other town on **Routes**. Your own towns connect immediately; another player's town must select yours to accept. Each town supports one partner at a time.
3. Click the example slot with an item, or shift-click one from your inventory, to add it to **Exports**. The example stays with you. Up to six items can be listed.
4. Set **Keep** to the amount that must remain in this town's warehouse and **Send** to the maximum carried per trip. For example, Keep 64 / Send 32 sends up to 32 carrots above a 64-carrot reserve. Send 0 pauses that item. Configure the other town's exports separately.
5. The citizen loads goods at home, visits the home checkpoint, walks to the other checkpoint, deposits goods in its warehouse, and returns. These are supply routes, with no automatic price or payment.

Goods in transit remain separate from meals and ordinary work supplies. Full destination storage keeps the remaining load on the citizen. **Pause** stops new departures; **Disconnect** returns undelivered goods. Traders follow reachable ground waypoints, including beneath roofs, and retry stalled legs with shorter and sideways alternatives. Citizens favor paving and solid bridge decks, and traders search nearby for narrow bridge approaches. A genuinely blocked route waits for a clear path; roads, bridges, and open doors help. Citizens on land do not plan to swim across a river; citizens pushed into water can still swim out. Traders do not teleport, use portals, sail boats, or build roads. A dead trader drops its actual goods and the town can assign another citizen.

**Road materials:** dirt paths, gravel, cobblestone, common stone/brick paving, planks, slabs, and stairs are preferred automatically by all citizen jobs. Solid bridge decks over water also receive a preference. Grass and dirt remain usable when a road is unavailable. Keep bridges connected to both banks, with room for a citizen to stand and walk. Datapacks can extend the `wwmc:paved_paths` block tag for other paving materials.

Each active trader maintains a moving **3×3 chunk window**. The route does not keep every intervening chunk loaded. The default server limit is **8 active town traders**, and the maximum route length is **8,192 blocks**. Travel continues on a running server when an owner logs off. Ordinary workers still require ticking chunks; distant abstract town simulation is future work. Install matching mod versions on server and clients because the screen protocol changes in 0.9.0.

Small NPC towns appear as you explore suitable **loaded Overworld terrain**. Deterministic regions usually put candidate towns about **1,000–2,000 blocks apart**, with larger gaps where terrain or claims prevent building. Sites must be dry, gently sloped, clear of block entities and recorded player blocks, and outside every existing town claim. Construction is saved and proceeds in batches of 128 block placements per tick. Revisiting a region cannot duplicate its town; destroying it does not cause a respawn.

NPC towns start neutral with a house, 12 beds, warehouse, trader checkpoint, guard, farm, lumber operation, renewable iron vein, smelter, kitchen, and courier. They begin with six named citizens (subject to the population cap) and starter supplies, then can recruit up to ten using real surplus food and available housing while ticking. Small crews take one worker per station. Farmers export carrots, timber towns oak logs, and mining towns iron ingots; their own reserves are protected. A free neutral town accepts a proposed route automatically. Deliveries build goodwill; attacking its citizens ends your route, makes it refuse further trade, and its guards defend the town. Countries, sieges, conquest, negotiated prices, route networks, carts and escorts remain future work.

| Setting | Default | Meaning |
| --- | --- | --- |
| `maxActiveTraders` | 8 | Town traders with a moving 9-chunk window in one dimension. |
| `tradeRouteDistance` | 8192 | Longest banner-to-banner trader route, in blocks. |
| `randomSettlements` | true | Discover new neutral NPC towns; disabling leaves existing towns intact. |
| `npcTownSpacing` | 1408 | Region size for future candidate towns, in blocks. |
| `maxNpcTowns` | 48 | Maximum automatically generated NPC towns in the Overworld. |

### Server configuration

Open **Mods → WWMC → Config** while your single-player world is loaded. Settings are grouped into **Settlements & Upgrades**, **Worker Crews**, **Mining & Quarries**, **Work & Food**, **Enchanting**, **Defense & Waves**, and **Trade & Other Towns**. Hover a label or control for its explanation, valid range and units. The native Undo, Reset and Done controls still apply; Reset affects only the open section. Return to the category menu and press Done to save. Existing TOML keys stay in their original locations, so earlier settings carry over. Multiplayer server configuration remains controlled by the server.

The generated WWMC server config controls these defaults:

| Setting | Default | Meaning |
| --- | --- | --- |
| `settlementRadius` | 240 | Horizontal radius of new towns; 240 is also the minimum. |
| `maxCitizens` | 64 | Hard ceiling on citizens per town, whatever its population upgrades; housing beds also limit recruiting. |
| `basePopulation` | 10 | Citizen limit of a town before population upgrades. |
| `populationPerUpgrade` | 5 | Extra citizens each population upgrade allows. |
| `populationUpgradeCost` | 8 | Emeralds for the first population upgrade; each later one costs this much more than the last. |
| `stationUpgradeCost` | 8 | Emeralds for a station's first range or crew upgrade; each further level costs twice the last. |
| `stationWorkers` | 4 | Crew slots per lumber station before crew upgrades. Farms, mines, craftsmen and enchanters always have one. |
| `courierWorkers` | 2 | Couriers per Courier Station before crew upgrades. |
| `oreVeinSeconds` | 15 | Seconds between yields of a common ore vein; gold ×2, diamond and emerald ×6, ancient debris ×8. |
| `quarryWorkers` | 8 | Crew slots per quarry before crew upgrades. |
| `processingWorkers` | 2 | Crew slots per Smeltery or Cook Station before crew upgrades. |
| `animalWorkers` | 2 | Workers per hunter, fisherman, animal keeper or butcher station before crew upgrades. |
| `rationTicks` | 2400 | Base loaded ticks between regular meals, multiplied by `mealIntervalMultiplier`. |
| `mealIntervalMultiplier` | 3 | Regular meal interval multiplier; the default gives six loaded minutes between meals. |
| `fishingSeconds` | 30 | Working seconds on a dry bank per whole-fish catch. |
| `animalBreeders` | 4 | Adult animals of each species a keeper preserves before harvesting surplus. |
| `blacksmithWorkers` | 2 | Crew slots per Blacksmith Station before crew upgrades. |
| `guardWorkers` | 2 | Guard crew slots per Guard Station before crew upgrades. |
| `enchantMinutes` | 5 | Minutes an enchanter spends on a book or common item; iron and gold ×1.3, diamond ×1.6, netherite ×2, plus more for uncommon, rare and epic items. |
| `enchanterMaxLevel` | 25 | Highest enchanting level enchanters reach (at most 29); level 30 is the player's alone. |
| `alarmThreshold` | 10 | Hostiles citizens must sight at once before a guard runs to ring the bell. |
| `enemyWaves` | true | Send hostile waves against towns while their owner is home. |
| `waveMinPopulation` | 3 | Citizens a town needs before waves are scheduled. |
| `waveIntervalDays` | 2 | Average in-game days between waves, ±25%. |
| `waveBaseMobs` | 2 | Hostiles in every wave before population scaling. |
| `waveMobsPerCitizen` | 0.5 | Extra hostiles per citizen, rounded up. |
| `waveMaxMobs` | 40 | Largest possible wave before population upgrades. |
| `waveMobsPerUpgrade` | 2 | Extra hostiles per population upgrade, also above `waveMaxMobs`. |
| `mineMinY` | −30 | Lower endpoint for a new mine's randomly chosen depth. |
| `mineMaxY` | 10 | Upper endpoint for a new mine's randomly chosen depth. |
| `quarryTargetY` | −64 | Bottom depth when a new quarry plan is created. |
| `mineBranchLength` | 24 | Length of each mine side branch. |
| `mineBranchPairs` | 4 | Paired side-branch junctions along the mine spine. |

### Personal inventories

Citizens keep resources in a persistent **36-slot bag**. Their owner can open it with an empty-hand right-click within eight blocks; the screen also shows the citizen's job, activity, health, next meal and equipment. Work pauses while the inventory is open; guards continue defending during an alarm. Menus close when the citizen dies, you move out of range, or ownership is no longer valid.

Workers use carried supplies before collecting replacements from their own job barrels. Deliveries leave finished goods and unused equipment in those barrels for couriers. Citizens keep at most **one spare meal** when shared stock is plentiful; low stock stays in the pantry for hungry citizens. Production supplies and goods never become a worker's warehouse trip.

**Changing jobs.** Old gear is put away and returned to the new job's barrel for a courier to collect. Unloaded, full or missing storage keeps the items in the bag. Guards still share armor through their station's stands and defend before returning gear during alarms. Equipment below 25% durability is carried for repair. Smelters and cooks never burn bows or tools.

**Getting unstuck.** Citizens open doors on their way. A citizen who keeps trying to walk somewhere but stays within a block and a half of the same spot for 30 seconds is moved on top of the settlement banner, or onto clear, firm ground right beside it, and drops the trip that trapped them: a quarry worker carries on from the control block, other workers try a different station for a while. Sleeping citizens are never moved, and nobody is moved into an unloaded part of town. A full warehouse leaves the remainder in the citizen's bag. An unusually large tree harvest has a saved backlog that moves into the bag when space opens; citizens wait for space instead of dropping overflow on the ground. On death, actual carried items and equipped gear drop normally.

### Food and healing

Regular meals default to **7,200 loaded ticks / six minutes**, three times farther apart than before. `rationTicks` keeps its existing saved base value; the new `mealIntervalMultiplier` defaults to 3, so the slowdown also applies to existing worlds without overwriting custom values.

Injured citizens can eat a safe meal for healing at most once every **600 ticks / 30 seconds**. Each real meal heals by nutrition (bread up to five health points, steak eight), capped at maximum health. Bowls and bottles are retained. The owner can right-click an injured citizen with food under the same cooldown.

When storage holds fewer than two meals per town citizen, nobody takes spare food. Among loaded hungry citizens with no carried meal, those fed least recently get priority. Every healthy citizen waits until its next meal; an injured citizen cannot keep taking healing meals while other hungry citizens wait. With ten hungry citizens and ten loaves, each can eat one loaf. With abundant stock a citizen may carry **one** spare. Farmers and cooks return their food outputs instead of keeping reserves.

Citizens may walk to the communal pantry to eat; this does not transfer production goods or tools. Couriers deliver food loads intact, including a single meal, and eat from communal stock instead of claiming meals from their cargo. Raw meat, raw fish and whole carcasses are not meals. Rotten flesh, spider eyes and poisonous food are rejected.

### Animal food jobs

Craft each station from the center item listed above surrounded by eight planks. Add a job barrel and couriers; each new animal job starts with two crew slots (`animalWorkers`). The food priority preset includes all four roles.

| Job | Setup and behavior | Output |
| --- | --- | --- |
| Hunter | Sword or axe in its barrel. Searches for adult cows, pigs, sheep, chickens and rabbits within 24 blocks by default. Named, leashed and animals in keeper ranges are protected. | One whole carcass per animal; real leather, wool and feathers are preserved. |
| Fisherman | Fishing rod, a dry reachable bank, open sky and two-block-deep water in station range. Works for 30 seconds per catch by default. | One whole cod or salmon carcass; fishing does not require entering water. |
| Animal Keeper | Fenced pen and pairs in range, normal breeding feed, plus a sword or axe for surplus adults. Uses real breeding and growth. Keeps four adults per species and breeds up to a bounded herd. | Carcasses from surplus adults; babies, named animals and mating animals are preserved. |
| Butcher | Axe and carcasses in its barrel. The station is its cutting table. | Raw portions for cooks: cow/pig 4, sheep 3, chicken/rabbit/cod/salmon 2. |

The chain is **producer barrel → courier → warehouse → courier → butcher → courier → warehouse → courier → cook → courier → pantry**. Carcasses cannot be eaten or placed directly in cooking appliances. Without couriers, production remains in job barrels. Supply each station's tools and keep the kitchen fuelled. Keepers use wheat for cows/sheep, seeds for chickens, roots for pigs and carrots/dandelions for rabbits; couriers avoid taking scarce ready-to-eat crops for breeding.

`fishingSeconds` controls catch time and `animalBreeders` controls the breeding group. Hunters affect only their own supported game targets; ordinary player kills retain vanilla drops.

### Guard stations and posts

1. Craft a **Guard Station** from eight planks around an iron helmet and place it in your claim. Two citizens take its guard slots by default; `guardWorkers` changes that capacity. **Each staffed station keeps one guard on duty**, with its own day/night rotation. The other guard rests, keeps their station assignment, and wakes for their shift. A station with one guard keeps that guard on duty through both shifts. Empty stations receive a guard before existing crews receive extra members; spare guards can transfer to a newly placed empty station. An unstaffed station still needs citizens to recruit.
2. Put equipped **armor stands within three blocks of the station on each axis**. On duty, guards take usable protective armor for empty slots and upgrade to pieces with higher armor/toughness attributes. An upgrade exchanges the real old and new pieces on the stand. Guards also check their bags and local job barrels, preserving durability, names and enchantments. Gear below 25% durability is never taken back into service. Equipped armor is visible.
3. Guards **look for weapons** themselves: one melee weapon (a **sword** or **spear**), one **bow**, and up to 32 **arrows** for it. They check their own bag, items held in the hands of armor stands in the station range (stands double as weapon racks), their job barrels, and loose weapons or arrows that have lain on the ground in town for five seconds, such as a fallen skeleton's bow. They take the strongest melee weapon available and swap up when they find a better one, returning the weaker weapon to storage. Loose items they cannot reach are skipped for a minute.
4. In combat, a guard with a bow and arrows shoots enemies 5–24 blocks away when no citizen or player stands in the line of fire; each shot uses one real arrow and bow durability, and arrows are not recoverable. Closer in, they switch to their sword or spear; melee attacks with swords, spears, or fists reach up to four blocks from the guard’s eyes to the target hitbox, with line of sight required. A guard without a melee weapon puts the bow away and fights unarmed. Weapons lose durability in use. Guards defend against nearby visible hostile monsters inside the town claim and prioritize combat over supply trips.
5. **Sneak-right-click the Guard Station with the Station Inspector.** Then right-click clear ground for the **day post**, followed by clear ground for the **night post**. Both positions need dry footing, headroom, and a location inside the same claim. The pair is saved together. Sneak-click ground during selection cancels it. Until configured, both posts default to the station.
6. At a shift change the outgoing guard returns armor to empty matching slots on accessible stands before sleeping. The incoming guard wakes, checks for better gear, and gives the outgoing guard up to 20 seconds to return a shared set before beginning an ordinary patrol. Enemies and alarms take priority over this wait. Day/night partners can share one set: six stations normally need six sets, rather than twelve. A full or unreachable rack sends armor to the local guard barrel; if no storage accepts it, the citizen keeps it in their bag and sleeps without wearing it. During an alarm all twelve guards may be active, so six sets will equip only six of them. On-duty guards roam among reachable town stations and nearby paths, and revisit the post periodically. Day duty runs from tick 23000 through 12999; night duty from 13000 through 22999. Post inspection reports both positions and crew size. Unloaded posts/terrain are never force-loaded.

Citizens are drawn with the villager head, robe, and skin on a humanoid body with free arms, so armor, weapons, and tools are visible. Biome and profession clothing overlays are not drawn. Patrolling does not yet include formation orders or player/faction warfare.

### Worn equipment and blacksmiths

Equipment with **less than 25% durability remaining** retires from use. Guards return armor to shared stands, or their local barrel when no rack accepts it; couriers collect retired rack gear. Workers leave worn tools in job barrels.

Craft a Blacksmith Station from an **iron ingot surrounded by eight planks**, and place an anvil and a barrel in its range. Couriers deliver damaged tools, weapons and protective armor plus their matching repair material. The smith repairs the original item, preserving names and enchantments, and returns it to the local barrel for courier pickup. Worn armor on stands in the smith's own range can also be serviced.

Each material repairs up to 25% of maximum durability: iron gear uses iron ingots, gold gold ingots, diamond diamonds, netherite netherite ingots, leather armor leather, stone tools cobblestone and wooden tools planks. Missing supplies or full storage pauses the job; equipment is never copied or discarded.

### Smelters and cooks

Smelters supply actual furnaces or blast furnaces in range, using raw metals, ores and fuel delivered to their job barrels. Cooks use smokers or lit campfires, with ingredients and smoker fuel delivered to their own barrels. Appliance progress, recipes and output remain vanilla.

Cooks turn prepared raw meat or fish into cooked meals and make one bread from **three wheat**. The Cook Station screen controls the bread order, which aims for 32 bread in town stock. A whole carcass must first pass through a butcher. Player-harvested vanilla raw meat can still be cooked normally.

Workers collect finished items into their bags and leave them in local job barrels; couriers bring them to the warehouse. Two workers share each processing station by default. Missing appliances, ingredients or fuel pauses work locally; a full barrel keeps products in the worker's saved inventory.

### Craftsmen

A **Craftsman Station** (eight planks around a crafting table) employs one craftsman, always; place more stations for more craftsmen. Right-click it to open its order screen.

**Teaching.** Click the **Teach** slot while holding any item, or shift-click an item in your inventory, and the craftsmen learn the crafting-table recipe that makes it. You keep the item. Any shaped or shapeless recipe works, including recipes added by other mods and datapacks; special recipes such as dyeing armor, copying maps or fireworks cannot be taught. A town knows up to 27 orders. Teaching any planks makes a **planks (any wood)** order that uses whichever logs the town holds.

**Amounts.** Each order has a slider for how many to **keep in town**, from 0 to 256; 0 pauses it. Stackable items start at 16 and tools at 1. Stock counts the warehouse and every job barrel. The ▲ button raises an order's priority and ✕ forgets it. Each row shows the town's stock and whether the order is stocked, ready to craft, or missing materials.

**Work.** A craftsman takes the highest order below its target that has materials, from the station's own barrels, supplied by couriers, carries up to eight batches of real items to the bench and crafts them there. Each batch is checked against Minecraft's recipe before it is made, and container items such as milk buckets come back empty. When two orders make each other, such as iron ingots and iron blocks, each only uses the other's stock above its target, so they never convert back and forth. When nothing is short or materials are missing, the craftsman takes other work for a while.

New towns, and towns from earlier builds, start with these orders: stone pickaxe 2, stone axe 2, stone sword 2, bow 1, arrows 64, torches 32, ladders 32, sticks 32 and planks (any wood) 64. Orders switched off in an earlier build start at 0. Cooks still bake one bread from three wheat up to 32 bread; the Cook Station screen or `/wwmc craft bread off` switches it off.

### Settlement screens

Right-click with an empty hand to open:

- **Settlement banner:** the town overview (population, limit and beds, population upgrades, food, warehouse fill, job barrels, claim, job priorities, alarm and waves), every job with its priority and who holds its places, every loaded citizen with their job, activity and health, and every station with its crew and status. Buttons apply a priority preset, sound the alarm or the all-clear, recruit a citizen when housing beds are free, and grow the population limit; the Jobs tab's - and + buttons change one job's priority.
- **Any station:** its detected resources and job status, the citizens assigned to it and what each is doing, the contents of its barrels (or the warehouse's containers), and its upgrades. Work stations have a button for their job's priority, the Cook Station a bread switch, the Guard Station a button to choose its posts, and stations that can be upgraded buttons for range and crew upgrades. Hover a button to see exactly what it does and costs.
- **Craftsman Station:** the order screen described above.
- **Citizen:** their job, activity, health, next meal and equipment above their bag.

Screens refresh every second and close when you move more than eight blocks away. Only the town's owner can open them; other players see a one-line notice. Short notices, including alarms and waves, appear above the hotbar instead of in chat.

### Jobs and priorities

Every citizen has its **own station**. It goes back there each morning, after deliveries, meals and alarms, and waits beside it when there is no work, rather than taking another station. Crews stay the same from day to day; a station's Crew tab lists the citizens assigned to it, including those asleep or out of range.

Each job has a **priority**: Off, Low, Normal or High. Set it with the - and + buttons in the banner's **Jobs** tab, the priority button on a station's screen, or `/wwmc job`.

- A citizen without a job, such as a new recruit, takes the open place of highest priority. Among equal priorities, guard posts fill first, then the trader, then the station with the fewest workers, then the nearest.
- About every half minute, and at once after you change a priority, a citizen moves to an open place in a job of **higher** priority than its own. Equal priorities never trade workers, so a new Normal station waits for a recruit, a free citizen or a raised priority.
- **Off** frees everyone in that job, and nobody takes it until you raise it again.
- Guards and traders start at High and every other job at Normal. The preset button on the banner, or `/wwmc priority`, sets every job at once: balanced, food (all food jobs High) or materials (all food jobs Low). Both production presets keep couriers at High so deliveries retain their staff.
- An open guard post draws a citizen from a lower-priority job at once, day or night.
- Sneak-right-click a citizen to release it from its job: it takes another open place and avoids that station for a minute.

A citizen part-way through an enchantment, a repair or a trade run finishes it before moving to another job. Towns from earlier versions take their old priority preset, and each citizen keeps the station it was working at when the update loads.

### Job barrels and couriers

Every production station needs a **barrel within its range**, normally three blocks on each axis, outside Warehouse Station ranges. Overlapping job ranges assign each barrel to the nearest station. Guards and blacksmiths now use job barrels too; couriers and traders use warehouses.

**Only couriers haul within a town.** Workers collect their own tools and ingredients, work locally, and leave goods in their barrels. They do not fetch production inputs from a warehouse or deliver their output there. Missing, unreachable, empty or full barrels stop the affected part of production until supplied or cleared. Citizens can visit the pantry for a meal, and traders keep their routes between towns.

A Courier Station employs **two couriers** by default, with crew upgrades available. Couriers move finished goods to the warehouse and restock job barrels with tools, saplings, floor blocks, smelting and cooking inputs, fuel, animal feed, carcasses, repair inputs, learned crafting materials, books and lapis. They preserve the supplies each job uses. Retired armor from guard racks goes through the same repair chain. Small food-chain loads take priority while the pantry is low; other small loads are collected when larger errands are finished. Only one courier serves a particular job's barrels at a time.

A mine always has **one miner**, including an older mine with crew upgrades. Build more Mine Stations for more miners; quarries keep their separate crew setting. Start with barrels and couriers before expanding production, then balance supply and pickup rates against workers and storage.

### Ore veins

A **Mine Station placed within two blocks of an exposed ore**, on every axis, works that ore as an **endless vein**. The ore needs at least one open side, such as air, a torch or a ladder; ore buried on every side does not count, so older mines beside hidden ore keep digging their tunnels. Its miner walks to the station first, then to a standing spot with a clear view of the ore, mines it with a pickaxe able to harvest it, and collects the ore's normal drops, including Fortune, while the block stays in place. Only citizens get endless drops; a player who mines the ore breaks it normally. Each Mine Station has one miner, including old upgraded stations; excess crew members take other jobs.

A vein replenishes every 15 seconds (`oreVeinSeconds`). Gold takes twice as long, diamond and emerald six times, and ancient debris eight times. Any block in the `c:ores` tag counts, including other mods' ores. The nearest exposed ore comes first. If the ore is removed, the station looks for another one within two blocks, and a station with no exposed ore that close digs tunnels as described above. The miner needs open standing room within reach of the ore and a clear view of it; the station's screen says when it has none.

### Enchanters

An **Enchanter Station** (eight planks around a book) needs an **enchanting table within five blocks on each axis**, an 11×11×11 range, larger than other stations'. Surround the table with bookshelves as you would for yourself: the bookshelves set the enchanting level exactly as for a player, but an enchanter never goes above level **25** (`enchanterMaxLevel`); level 30 stays yours. One enchanter works each station.

Stock **lapis lazuli** and **unenchanted gear or books** in the warehouse for couriers to deliver, or directly in the station's barrel. The enchanter takes one item at a time, its own barrel first, armor and weapons before tools and books, and the rarest first. Each item costs one, two or three lapis by level, like the table's rows, and takes a long time: about **five minutes** for a book or a common item (`enchantMinutes`), 1.3 times as long for iron or gold gear, 1.6 for diamond and twice as long for netherite, with uncommon, rare and epic items taking longer still. Work only happens during the working day, so a busy enchanter finishes a few items each day. Progress is saved; the station screen shows the table's level, the lapis in stock, the items waiting and how far along the current item is. Finished items go back to the job barrel for couriers to collect. Nearly broken gear waits for the blacksmith first.

### Station upgrades

Every station that can use them sells upgrades on its screen, paid in **emeralds from your inventory**; emerald blocks count as nine, with change given back. Creative players pay nothing.

- **Range**, up to three levels: each level widens the station's cube by a block in every direction, from 7×7×7 to 9×9×9, 11×11×11 and 13×13×13 (an enchanter goes from 11 up to 17). Wider ranges reach more beds, chests, crops, trees, furnaces, anvils, armor stands and barrels. Quarries, mines, couriers and craftsmen have no range upgrades.
- **Crew**, up to three levels: each adds a worker slot. Farms, craftsmen and enchanters always have exactly one worker.

Each level costs twice the one before: 8, 16, then 32 emeralds by default (`stationUpgradeCost`). A broken station's item keeps its upgrades and shows them in its tooltip, so you can move an upgraded station without paying again; plain stations still stack with freshly crafted ones. The range preview shows the upgraded range.

### Population

A new town holds up to **10 citizens** (`basePopulation`). The town screen's **Grow** button raises the limit by **5** (`populationPerUpgrade`) for emeralds: 8 for the first upgrade, then 16, 24 and so on (`populationUpgradeCost`), up to the server's ceiling (`maxCitizens`, 64). Housing beds still limit recruiting as before.

A bigger town draws bigger attacks. Each population upgrade adds **2 attackers** to every wave (`waveMobsPerUpgrade`), even beyond `waveMaxMobs`; from the first upgrade a tenth of each wave per upgrade are **pillagers**, and from the third upgrade **vindicators** join them. Towns from earlier builds count as having bought enough upgrades for the citizens they already have.

### Alarms and enemy waves

**Noticing a threat.** Every second, the town counts the hostile monsters its citizens can see inside the claim: guards watch out to 24 blocks (32 during an alarm), other citizens only notice hostiles within 8 blocks. One or two monsters are left to the guards. When at least `alarmThreshold` (default **10**) are in sight at once, the guard closest to a **bell** within 96 blocks runs to ring it. Bells must be inside the claim and loaded. If that guard is killed, cannot find a path, or takes longer than a minute, another guard is sent. Without a reachable bell, you receive a warning instead and the town is not alerted.

**The alarm.** Any bell rung inside the town—by a player, projectile, redstone, or a guard—raises the alarm and **wakes every assigned guard at every station**, including resting reserves. Guard-raised alarms also briefly make nearby hostiles glow and tell you how many were sighted. While it rings, civilians stop working, flee hostiles from 20 blocks away instead of 12, and **duck and cover** at the nearest housing or barracks station (the banner if there is none). Alarms at night find civilians in their beds. A citizen may still volunteer for an empty guard slot. Guards stay at their posts instead of making supply trips unless they have no weapon, patrol faster, and engage from farther away. After **30 seconds** without a sighted hostile, the bell rings again for the **all-clear**, everyone returns to work, and guards resume their station shifts. That automatic all-clear ring does not raise a second alarm. `/wwmc alarm` raises the alarm without a runner, or calls the all-clear early.

**Enemy waves.** Once a town has `waveMinPopulation` (default **3**) citizens, a wave is scheduled about every `waveIntervalDays` (default **2**) in-game days. It arrives after sunset, only while you are online and within 64 blocks of the claim, and never while the previous wave's attackers are still alive. Waves contain `waveBaseMobs + waveMobsPerCitizen × population` hostiles, rounded up and capped at `waveMaxMobs`: four for a three-citizen town, 12 for 20 citizens, 18 for 32. Small towns face zombies; from 6 citizens a quarter of each wave are skeletons, and from 10 citizens 15% are spiders. The wave gathers 40–64 blocks from the banner on loaded open ground inside the claim, away from stations and at least 24 blocks from you, then marches on the banner and attacks citizens on sight. You are told its size and compass direction, and again when it has been repelled. Wave mobs do not despawn and remember their town across restarts. `/wwmc status` shows the alarm state and the next wave; `/wwmc wave` calls the next wave immediately. Peaceful difficulty prevents waves. Population upgrades make every wave larger and bring pillagers and vindicators; see [Population](#population).

**Glowing attackers.** Every wave attacker glows through walls, so you can find them. If any are still alive a minute after the wave arrives, the town reports them to the guards, who hunt them down, and you are told how many remain.

**Calling the guards.** Citizens who see a hostile within 16 blocks of them inside the claim, near their work for example, call the guards. Up to two guards on duty answer each call: they walk to the hostile, wherever it has gone in town, and fight it as soon as they see it. A guard who cannot reach it within 90 seconds leaves it to the others for two minutes. Calm endermen and other neutral mobs that are not angry are not reported. The citizen's screen shows the call for a few seconds, and the town screen's alarm row shows how many hostiles are reported.

## Player direction and citizen autonomy

The player decides job priorities, approved structures, work sites, and eventually military objectives. Citizens take the open job of highest priority and keep it, and decide how to get to work, when to collect supplies, and when to rest or flee a nearby monster.

Production moves real Minecraft items. Farmer replanting reserves one seed or crop from the harvest; lumber and mining consume tool durability. Workers carry drops home. Furnished residential areas, warehouse supplies, and accessible work sites are the initial town-management loop.

There is no generative AI or external service dependency. Initial decisions use bounded job searches, priorities, reservations, and goal states. Later personalities and faction decisions will build on the same deterministic simulation state.

## Offline and distant settlement design

**Implemented behavior:** loaded citizens continue working when the owner logs off, as long as the server is running and their chunks remain loaded. Unloaded citizens pause and retain their state. This alpha does not force-load towns, simulate unloaded production, spawn rival towns, or calculate progress while the server is shut down.

**Next simulation phase:** distant towns should use state-based event checks instead of running every citizen physically. See [the roadmap](docs/ROADMAP.md) for the proposed generator and asynchronous simulation contract.

- Generate starter AI settlements within a configurable distance band around explored player areas. Use seeded cells and persisted identifiers so returning to an area does not repeatedly create settlements.
- An event check considers population, food, materials, technology, relations, nearby routes, recent events, and cooldowns. Examples include a harvest, supply shortage, migration, construction proposal, or convoy departure.
- Randomness chooses among eligible events. It cannot bypass resource costs, prerequisites, or cooldowns. Persist the random step and event history so a restart does not reroll outcomes.
- Snapshot the settlement on the server thread, calculate an immutable result asynchronously, then validate its revision and apply it on the server thread. Never read or mutate Minecraft worlds, entities, or inventories from the calculation worker.
- Transfer authority between detailed and abstract simulation explicitly. A resource or convoy must never be produced by both modes.
- Offline balance should preserve daily autonomy and restrict new major attacks against offline owners by default. The exact war rules, combat logout handling, and technology ceiling remain design decisions.

## Architecture

| Area | Responsibility |
| --- | --- |
| `core` | Minecraft-independent bounds, workforce leases, atomic target reservations, and tunnel/quarry geometry. |
| `settlement` | Claims, block ownership/protection, forestry, saved excavation plans, commands, inventory transfers, guard weapons, bell alarms, and enemy waves. |
| `block` / `item` | Banner, automatic role stations with their upgrade levels in the block state, and station inspection. |
| `entity` | Citizen goals, harvesting, supply trips, food, rest, and entity persistence. |
| `client` / `WWMCClient` | Citizen model/renderer and transient range outlines; dedicated servers do not load rendering classes. |
| `src/main/resources` | Block/item models, language, drops (which keep station upgrades), and recipes. |
| `tools/station_models.py` | Generates the station and banner models from vanilla block textures; rerun it after editing a model. |

Settlement records are dimension SavedData under `wwmc:settlements`. Placement provenance, planting sites, and excavation progress use a separate `wwmc:world_work` record so older settlement saves remain readable. Normal world saves persist both; temporary crew and target reservations expire and are reconstructed after reload. All current gameplay changes happen on the logical server thread. Persistent IDs keep future diplomacy and military systems independent from entity instances.

## Build and verify

Use a **Java 25 JDK**, not just a Java runtime. The Gradle wrapper and ModDevGradle versions are pinned in the repository.

```bash
./gradlew build
./gradlew runGameTestServer
./gradlew runClient
```

On Windows, use `gradlew.bat build` and `gradlew.bat runClient`. Development servers use `./gradlew runServer`.

`runGameTestServer` checks paved detours, narrow bridge bends, unbridged river rejection, and escape from water, a 43-block walk across open grass, a detour through the only gap in a long wall, and a 72-block walk in several legs. Miners must walk across town and work ore veins touching their station and two blocks away, and a farmer with no crops must keep its job beside an open mine until mining is raised to High. Food tests run real hunting, every courier transfer, butchery and vanilla cooking; fishing from a dry bank with an obstructing station; keeper culling with four breeders preserved and a real newborn; production waiting without a courier; and ten hungry citizens sharing ten loaves without stockpiling. It also runs a 640-block trader delivery and return on dirt paths, beneath a roof, around a wall, and over a wide river on a waterlogged slab bridge, with no nearby players, then checks the live warehouse inventories after chunk reload. The test mod in `src/gameTest` is excluded from the release JAR. GitHub Actions runs these world tests after `build`.

`build` runs regression checks for ranges, beds/storage, overlap ownership, shared crews, atomic target claims, tunnel/quarry geometry, random depth/shift boundaries, name uniqueness, construction-aware tree recognition, sapling conservation, local inventory conservation/backlog, armor transfers, weapon classification/ranking, quarry staircase geometry and persistence, quarry collision tracing, four-block surface/vertical reach, blocked work rays, clear tree approach candidates, persisted natural-tree proof after access clearing, role-specific crafting orders, appliance recipe eligibility, furnace slot/component conservation, independent station guard shifts and alarm rosters, alarm thresholds and all-clear timing, wave sizes/composition/timing, claim widening, save round-trips, legacy migration, recipe/drop decoding with Minecraft's codecs, shared shift armor, full-bag overflow armor returns and occupied-slot conservation, armor upgrades, the exact durability cutoff, material-funded repairs with preserved names/enchantments, healing meal/bowl conservation and cooldowns, guide-page bounds and anvil eligibility, learned crafting against the server's real recipes (shaped layout, any-wood planks, container remainders, paused and stocked orders, ingot/block cycle protection, legacy order migration), job barrel collection rules and courier supply loads, ore vein detection and rarity pacing, screen data network round trips, upgrade prices, emerald payments with change, upgraded ranges and fixed crews, wave threat from population upgrades, enchanting rarity timing, lapis costs, the level cap and item priority against the server's real enchantments, job priority presets, job assignments, crew trimming and their save round-trip, staggered 30-citizen updates, bounded reachability probes, and shared resource scan expiry/invalidation. GitHub Actions builds with Java 25 and uploads the mod JAR as **wwmc-mc26.2**. Local JARs appear in `build/libs/`.

Automated checks do not replace an in-game playtest. Check previews, border placement, shared station crews, protected player logs, large-tree felling/replanting, tunnel/cave pathfinding, quarry staircase descent and climb-out, quarry obstructions and skipped blocks, craftsman/smelter/cook trips, fuel use and appliance output collection, guard shift changes and shared armor returns, blacksmith pickup/repair/return trips, every screen and its buttons, teaching orders and their sliders, courier trips and job barrel use, ore vein mining, station and population upgrades with emeralds, the Jobs tab and citizens keeping their jobs across days, the new station models, enchanter trips and enchanting, guards answering calls and hunting glowing wave stragglers, guide crafting/reading, meal healing and direct feeding, armor upgrades/patrol/combat, weapon scavenging and archery, bell runs and civilian cover, wave spawning, inventory menus, tool breakage, bed use, and save/restart behavior before using this alpha in an important world.

## Development stages

1. **Settlement foundation — this build:** claims, role blocks, shared crews, real inventory, crop/tree cycles, automatic tunnel/cave mining, quarries, named citizens, inventories, armed guards, bell alarms, the first enemy waves, and emerald upgrades.
2. **Self-sustaining small town:** more food processing, approved housing construction, robust room validation, and migration. Craftsmen, couriers and enchanters are the first steps.
3. **Living neighboring world:** persisted AI settlements, weighted distant events, history, player-distance generation, and mode handoff.
4. **Military foundation:** build on town guards with trained soldiers, squad orders, wounded citizens, and hospital treatment.
5. **Raids and trade:** independent targets, physical convoys, scouting, cargo loss, and supply disruption.
6. **Countries and progression:** territory, deeper diplomacy, sieges, varied faction technology, and conquest rules. Multiple towns and the first neutral settlements and trader routes are playable in 0.9.0.

Keep the first playable scope small, preserve real resource accounting, and make every existing feature explicit before broadening the world.

## License and attribution

Original mod code is [MIT licensed](LICENSE). The generated NeoForge starter's notice remains in [TEMPLATE_LICENSE.txt](TEMPLATE_LICENSE.txt). Inspiration is a gameplay reference; this project does not include Colony Survival code or assets.
