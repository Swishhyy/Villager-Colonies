# World War MC (WWMC)

A first-person Minecraft settlement and warfare mod. You live among your citizens, direct the town's priorities, and take part in its work and battles. Citizens handle routine work themselves. The long-term world contains independent settlements, countries, and trade convoys.

Inspired by the first-person colony management of [Colony Survival](https://store.steampowered.com/app/366090/Colony_Survival/), with original Minecraft systems and an eventual emphasis on warfare.

**Target:** Minecraft Java 26.2 · NeoForge 26.2.0.88 · Java 25 · MIT license.

## Current build: 0.6.0-alpha

0.6.0 adds a craftable in-game Settlement Guide, shared armor between guard shifts, better armor selection, retirement of equipment below 25% durability, Blacksmith Stations that repair real equipment using warehouse materials, and healing from real meals or food given by the owner. Existing towns and inventories remain compatible.

0.5.2 gives citizens four-block work and melee reach, measured from their eyes to block faces or enemy hitboxes. Lumberjacks choose clear standing spots beside trees and cut unprotected natural leaves that block the trunk or trap their body, keeping real drops and consuming axe durability. The tree’s original natural recognition is saved after access clearing, so work can resume after a restart while all player-block and building checks still apply.

0.5.1 makes citizens return their previous job's armor, weapons and tools when they change jobs, moves citizens who stay stuck for 30 seconds onto the settlement banner, stops citizens shoving each other off quarry stairs, and only sends quarry crews down a staircase that is intact, rebuilding collapsed steps. Citizens now open doors, craftsmen deliver the tools they make, smelters and cooks no longer burn equipment, and new mines and cave mining stay out of quarry chunks.

0.5.0 adds Smeltery and Cook Stations, makes guard shifts independent for each station, and activates every assigned guard when a town bell rings. It retains the staggered citizen updates, shared resource scans, bounded path probes, and quarry crash fix from 0.4.2. Existing worlds, inventories, and quarry progress remain compatible.

This is the first settlement foundation, not the completed warfare game.

Implemented:

- Persistent named settlements, owners, non-overlapping claims, and town priorities.
- Settlement banner and thirteen role stations, with survival crafting recipes and a creative tab.
- A 240-block minimum claim radius and red banners at the four claim corners.
- Automatic 7×7×7 station detection and live updates when nearby furniture/resources change.
  Bed and warehouse locations refresh within one second; inspection and recruitment refresh them immediately. Workers still check detected beds and storage against the live world before using them.
- A placement range outline and a Station Inspector for checking existing stations.
- Deterministic ownership of overlapping beds, storage, and same-job work targets.
- Housing and barracks beds count toward recruitment; hospital beds remain patient capacity.
- Recruitable citizens with individual saved names, personal inventories, and custom job AI.
- Guards with day/night posts, town patrols, visible shared shift armor, equipment upgrades, and swords, spears, or bows they find themselves.
- A craftable Settlement Guide with every block recipe and instructions for the playable systems.
- Blacksmiths who repair tools, weapons, and protective armor at actual anvils with the matching warehouse materials.
- Nutrition-based citizen healing from carried food and direct feeding, with a five-second cooldown.
- Bell alarms: manual, projectile, redstone, and guard bell rings activate every assigned guard; civilians take cover until the all-clear. Guards also run to the bell when citizens sight a large hostile force.
- Population-scaled hostile waves that arrive at night while the owner is home.
- Autonomous harvesting and replanting of existing wheat, carrot, potato, and beetroot crops.
- Whole-tree felling, player-placement protection, and planting from actual saplings in storage.
- Automatic mine depth selection between Y −30 and 10, descending tunnels, accessible cave ore gathering, and full-chunk quarries that crews enter by a spiral staircase.
- Craftsmen who turn warehouse materials into planks, sticks, ladders, torches, stone tools, bows, and arrows.
- Smelters who supply nearby furnaces/blast furnaces with warehouse raw metals, ores, and fuel, and collect their real results.
- Cooks who supply smokers or lit campfires with raw food, collect cooked food, and make bread from three wheat.
- Openable 36-slot personal inventories, saved overflow, local supplies/rations, real tool durability, and warehouse deliveries.
- Shared station crews, exclusive resource reservations, civilian nighttime rest, and guard duty through the night.
- Save/reload support for settlement data, player block protection, replanting sites, excavation progress, cargo, tools, and meal timers.

Planned: automatic housing construction, hauling specialists, medical treatment, military squads, raids by rival settlements, independent AI settlements, distant simulation, technology progression, convoys, and countries. A barracks station identifies troop housing in this build; it does not train soldiers yet. A hospital station identifies patient beds; it does not provide medical treatment yet. Citizens can heal by eating food.

## Try the first build

Install the same mod JAR on the NeoForge 26.2 client and server. Use a new test world for this alpha. Craft a **Settlement Guide** from one book and one blue dye, or run `/wwmc guide`, then right-click it to read the instructions.

1. Craft or obtain a **Settlement Banner**, place it on solid ground with open space around it, and right-click it with an empty hand to found your town. New towns extend **240 blocks in each horizontal direction**, a 481×481 block footprint including the center. Red banners appear at the four corners when those chunks are loaded and the ground can support a banner. The mod does not load distant chunks to place them or replace obstructing blocks.
2. Build a small camp with beds. Place a **Housing Station** or **Barracks Station** inside it.
3. Beds are detected automatically within **three blocks of the station on every axis**: a **7×7×7 cube**, including the station block. Both halves of each bed must fit inside the cube and your claim. No corner selection is required.
4. Place a **Warehouse Station** within that same range of your chests or barrels. It detects multiple containers, including trapped and double chests. Stock food, axes, appropriate pickaxes, saplings, and cobblestone or other tunnel floor supplies. You can add or remove storage later without registering it again.
5. Place **Farm** and **Lumber Stations** with crops or tree roots inside their **7×7×7** ranges. A farm, lumber station, or mine supports **four workers** by default; a quarry supports **eight**. Citizens reserve individual trees or excavation positions so a shared crew cannot harvest the same target twice. Housing, hospital, barracks, and warehouse ranges protect their structures from harvesting.
6. Prepare farmland and plant crops yourself. Carrots or potatoes supply both food and replanting stock; wheat is collected, and a cook makes it into bread. Put a lumber station by natural trees or accessible clear soil. It fells the connected tree, collects real leaf drops, and replants when storage has enough saplings. If no tree is accessible, it can plant a new one instead. Trees grow at Minecraft's normal rate.
7. For mining, place a **Mine Station** facing into the intended descent, with open walking space in front. It chooses and saves a random depth between **Y −30 and 10**, then digs a staircase and eight 24-block side branches. Near that depth, workers also walk to accessible exposed cave ores they can reach. Alternatively, place a **Quarry Station** facing the neighboring chunk you want excavated. The quarry removes that complete 16×16 chunk from the surface down to Y −64, preserving bedrock. Keep the station and level, walkable ground outside the target chunk, in line with the station, where the crew's staircase begins. The whole plan must fit inside the town claim.
8. Run `/wwmc recruit 3`. Recruitment is limited by loaded housing beds and the configured population cap. Citizens choose available crew slots, obtain supplies, work, and deliver cargo in batches.
9. Right-click your citizen with an empty hand to open their **36-slot inventory** and see their activity. Add food, spare tools, saplings, or guard gear directly. Sneak-right-click releases their job; holding an item while interacting reports status. Use `/wwmc status` to inspect the town. Mine depths are automatic.
10. Place a **Guard Station**, supply armor stands in its local range, and configure its day/night posts as described below. Up to two citizens become guards automatically; defense slots fill before production jobs.
11. Place a **Craftsman Station** near your warehouse for tools and building materials. Add a **Smeltery Station** with a furnace or blast furnace in range, and a **Cook Station** with a smoker or lit campfire in range. Stock raw ores, raw food, wheat, and fuel in the warehouse. See [Smelters and cooks](#smelters-and-cooks).
12. Add a **Blacksmith Station** with an anvil within three blocks on each axis. Stock repair materials in the warehouse. Blacksmiths repair damaged tools/weapons and worn guard armor, retaining names and enchantments.
13. Hang a **bell** inside the town so guards can raise the alarm, and prepare for the first enemy wave once the town has three citizens. See [Alarms and enemy waves](#alarms-and-enemy-waves).

**Range preview:** hold any station block and aim at a block face to see a blue outline at its prospective placement position. The outline accounts for replaceable grass/snow. It turns red when the placement context is blocked. Placing a station displays a green outline for about three seconds. Right-click an existing station with an empty hand to inspect its detected blocks and briefly show its range. The **Station Inspector** also previews an existing station while you aim at it and reports its contents when right-clicked.

Ordinary stations show their local 7×7×7 area; a quarry shows the neighboring chunk's footprint. The quarry outline indicates its horizontal target, not the complete depth. Mines extend beyond the local outline along their planned tunnels. Inspection reports facing, depth, progress, and active crew size.

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
| `/wwmc priority balanced` | Idle citizens prefer the nearest available job. |
| `/wwmc priority food` | Idle citizens prefer available farm stations. |
| `/wwmc priority materials` | Idle citizens prefer lumber/mining stations. |
| `/wwmc citizens` | List each loaded citizen with their job and what they are doing. |
| `/wwmc craft` | Show craftsman orders with warehouse stock and targets. |
| `/wwmc craft <order> on\|off` | Switch a craftsman order on or off. |
| `/wwmc alarm` | Sound the alarm yourself, or call the all-clear early while it rings. |
| `/wwmc wave` | Bring the next enemy wave forward to now, even in daylight. |

Commands affect your own settlement. A prototype supports one settlement per owner in the Overworld. Claims do not implement general-purpose land protection; station removal is owner-restricted. An occupied settlement's banner is its fixed rally point and cannot be mined normally.

### Job blocks define building purpose

Beds alone do not determine what a structure is. A **role station and nearby furniture** provide that meaning. Housing, barracks, hospital, warehouse, farm, lumber, and guard supply stations scan a fixed 7×7×7 cube centered on themselves, from offsets −3 through +3 on each axis. Mines and quarries use explicit excavation plans. Furniture/resource changes are picked up on the next inspection or worker scan.

Ranges can overlap. A complete bed belongs to the nearest housing, barracks, or hospital station that contains both halves; hospital-owned beds do not recruit citizens. Each chest/barrel block belongs to the nearest warehouse, and work targets belong to the nearest station of that job. Equal distances use station coordinates (X, then Y, then Z) as a stable tie-breaker. A double chest's two physical inventories are each included once.

Only loaded blocks inside the settlement claim count. Scanning never loads chunks. A known station in an unloaded chunk retains ownership of its nearby furniture until it is loaded and validated; its own production/capacity stays paused.

| Station | Current interpretation | Later role |
| --- | --- | --- |
| Housing | Residential beds, recruiting capacity, and rest. | Families, migration, approved housing expansion. |
| Barracks | Camp/troop beds, currently usable as housing. | Recruiting, training, and organizing military units. |
| Hospital | Patient beds excluded from housing capacity. | Treatment, medical supplies, casualty evacuation. |
| Warehouse | Chests, trapped chests, and barrels within its 7×7×7 range. | Dedicated haulers, reserves, convoy loading. |
| Farm | Mature supported crops within its 7×7×7 range. | Planting expansions, varied crops, food processing. |
| Lumber | Whole trees rooted in range, real sapling planting, and replanting. | Larger forestry areas and better species/terrain handling. |
| Mine | Automatically chosen depth, staircase/branches, and accessible cave ores. | Cave exploration, reinforcement, lighting. |
| Quarry | Full neighboring chunk excavation, layer by layer, entered by a spiral staircase. | Machinery, dedicated haulage, liquid management. |
| Craftsman | Workbench that turns warehouse materials into tools and building goods. | Player-defined orders and more recipes. |
| Smeltery | Warehouse ores/raw metals smelted in nearby furnaces or blast furnaces. | Specialized metallurgy and technology. |
| Cook | Raw food cooked in smokers or lit campfires; three wheat become bread. | More meals and food orders. |
| Blacksmith | Repairs warehouse equipment and retired guard-stand armor at nearby anvils. | Repair orders and specialized smithing. |
| Guard | Day/night posts, patrols, armor and weapons from stands or storage, melee and archery, bell alarms. | Squad orders, training. |

Marker blocks use vanilla textures as placeholder visuals. Stations declare use; this build does not infer enclosed rooms, roofs, or architectural quality. Work validates supplies, protection, reservations, loaded terrain, and access before changing blocks.

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
| Guard Station | Iron helmet |
| Blacksmith Station | Iron ingot |

The Station Inspector is a shapeless recipe with two paper and one stick. The **Settlement Guide** is a shapeless recipe with one book and one blue dye; right-click it to open the native book screen. `/wwmc guide` gives another copy. Tools consumed to craft stations are separate from tools supplied to workers.

### Server configuration

The generated WWMC server config controls these defaults:

| Setting | Default | Meaning |
| --- | --- | --- |
| `settlementRadius` | 240 | Horizontal radius of new towns; 240 is also the minimum. |
| `maxCitizens` | 32 | Population cap, also limited by available housing beds. |
| `stationWorkers` | 4 | Crew slots per farm, lumber station, or mine. |
| `quarryWorkers` | 8 | Crew slots per quarry. |
| `craftsmanWorkers` | 2 | Crew slots per Craftsman Station. |
| `processingWorkers` | 2 | Crew slots per Smeltery or Cook Station. |
| `blacksmithWorkers` | 2 | Crew slots per Blacksmith Station. |
| `guardWorkers` | 2 | Guard crew slots per Guard Station. |
| `alarmThreshold` | 10 | Hostiles citizens must sight at once before a guard runs to ring the bell. |
| `enemyWaves` | true | Send hostile waves against towns while their owner is home. |
| `waveMinPopulation` | 3 | Citizens a town needs before waves are scheduled. |
| `waveIntervalDays` | 2 | Average in-game days between waves, ±25%. |
| `waveBaseMobs` | 2 | Hostiles in every wave before population scaling. |
| `waveMobsPerCitizen` | 0.5 | Extra hostiles per citizen, rounded up. |
| `waveMaxMobs` | 40 | Largest possible wave. |
| `mineMinY` | −30 | Lower endpoint for a new mine's randomly chosen depth. |
| `mineMaxY` | 10 | Upper endpoint for a new mine's randomly chosen depth. |
| `quarryTargetY` | −64 | Bottom depth when a new quarry plan is created. |
| `mineBranchLength` | 24 | Length of each mine side branch. |
| `mineBranchPairs` | 4 | Paired side-branch junctions along the mine spine. |

### Personal inventories

Citizens keep resources in a persistent **36-slot bag**. Their owner can open it with an empty-hand right-click within eight blocks. Work pauses while the inventory is open; guards continue defending during an alarm. Menus close when the citizen dies, you move out of range, or ownership is no longer valid.

Workers use carried food, spare tools, saplings, and floor supplies before requesting replacements. Deliveries retain the tools and gear the current job uses, supplies for the current task, and up to eight food items while sending everything else to the warehouse: a farmer hands in a spare axe, and a craftsman delivers the tools and weapons it makes.

**Changing jobs.** When a citizen moves to a different job, they first take off the previous job's armor and put away its weapons and tools, then carry them back to the nearest warehouse so the next guard, lumberjack, or miner can use them. If the town has no warehouse, the warehouse cannot be reached, or it is full, they drop the gear where they stand; if the warehouse is merely unloaded they keep it and hand it in with their next delivery. Guards drafted during an alarm defend first and return old gear afterwards. Smelters and cooks never burn bows, wooden tools, or other equipment. Citizens already wearing armor outside the guard job do the same.

**Getting unstuck.** Citizens open doors on their way. A citizen who keeps trying to walk somewhere but stays within a block and a half of the same spot for 30 seconds is moved on top of the settlement banner, or onto clear, firm ground right beside it, and drops the trip that trapped them: a quarry worker carries on from the control block, other workers try a different station for a while. Sleeping citizens are never moved, and nobody is moved into an unloaded part of town. A full warehouse leaves the remainder in the citizen's bag. An unusually large tree harvest has a saved backlog that moves into the bag when space opens; citizens wait for space instead of dropping overflow on the ground. On death, actual carried items and equipped gear drop normally.

### Food and healing

An injured citizen eats safe food from their own bag even if their normal ration timer has not expired. Each meal heals up to its nutrition value in health points, capped at maximum health: steak restores up to eight points (four hearts), bread up to five. Extra healing meals have a **100-tick / five-second cooldown**, saved across reloads. Ordinary scheduled meals also heal. Healing consumes one actual item; stew bowls and other use remainders are retained. Citizens leave raw meat/fish for cooks and reject rotten flesh, spider eyes and poisonous food.

The owner can **right-click an injured citizen with food** to feed them directly under the same cooldown. A healthy citizen or one still finishing their meal does not consume it. Normal right-click with an empty hand still opens the inventory. Put cooked food into the warehouse and citizens' bags to keep food available during work, patrols and rest.

### Guard stations and posts

1. Craft a **Guard Station** from eight planks around an iron helmet and place it in your claim. Two citizens take its guard slots by default; `guardWorkers` changes that capacity. **Each staffed station keeps one guard on duty**, with its own day/night rotation. The other guard rests, keeps their station assignment, and wakes for their shift. A station with one guard keeps that guard on duty through both shifts. Empty stations receive a guard before existing crews receive extra members; spare guards can transfer to a newly placed empty station. An unstaffed station still needs citizens to recruit.
2. Put equipped **armor stands within three blocks of the station on each axis**. On duty, guards take usable protective armor for empty slots and upgrade to pieces with higher armor/toughness attributes. An upgrade exchanges the real old and new pieces on the stand. Guards also check their bags and warehouse storage, preserving durability, names and enchantments. Gear below 25% durability is never taken back into service. Equipped armor is visible.
3. Guards **look for weapons** themselves: one melee weapon (a **sword** or **spear**), one **bow**, and up to 32 **arrows** for it. They check their own bag, items held in the hands of armor stands in the station range (stands double as weapon racks), the warehouse, and loose weapons or arrows that have lain on the ground in town for five seconds, such as a fallen skeleton's bow. They take the strongest melee weapon available and swap up when they find a better one, returning the weaker weapon to storage. Loose items they cannot reach are skipped for a minute.
4. In combat, a guard with a bow and arrows shoots enemies 5–24 blocks away when no citizen or player stands in the line of fire; each shot uses one real arrow and bow durability, and arrows are not recoverable. Closer in, they switch to their sword or spear; melee attacks with swords, spears, or fists reach up to four blocks from the guard’s eyes to the target hitbox, with line of sight required. A guard without a melee weapon puts the bow away and fights unarmed. Weapons lose durability in use. Guards defend against nearby visible hostile monsters inside the town claim and prioritize combat over supply trips.
5. **Sneak-right-click the Guard Station with the Station Inspector.** Then right-click clear ground for the **day post**, followed by clear ground for the **night post**. Both positions need dry footing, headroom, and a location inside the same claim. The pair is saved together. Sneak-click ground during selection cancels it. Until configured, both posts default to the station.
6. At a shift change the outgoing guard returns armor to empty matching slots on accessible stands before sleeping. The incoming guard wakes, checks for better gear, and gives the outgoing guard up to 20 seconds to return a shared set before beginning an ordinary patrol. Enemies and alarms take priority over this wait. Day/night partners can share one set: six stations normally need six sets, rather than twelve. A full or unreachable rack sends armor to the warehouse; if no storage accepts it, the citizen keeps it in their bag and sleeps without wearing it. During an alarm all twelve guards may be active, so six sets will equip only six of them. On-duty guards roam among reachable town stations and nearby paths, and revisit the post periodically. Day duty runs from tick 23000 through 12999; night duty from 13000 through 22999. Post inspection reports both positions and crew size. Unloaded posts/terrain are never force-loaded.

Citizens are drawn with the villager head, robe, and skin on a humanoid body with free arms, so armor, weapons, and tools are visible. Biome and profession clothing overlays are not drawn. Patrolling does not yet include formation orders or player/faction warfare.

### Worn equipment and blacksmiths

Equipment with **less than 25% durability remaining** retires from use. Exactly 25% is still usable. Guards return worn armor to an empty matching slot on a stand; spare/worn armor falls back to the warehouse when the rack is full. Worn weapons and worker tools are carried into warehouse storage. Guards look for replacements and leave unusable equipment for repair.

A **Blacksmith Station** uses eight planks around an **iron ingot**. Place a separate **anvil, chipped anvil or damaged anvil within three blocks on each axis**; a smithing table alone does not count. Two citizens can share the station (`blacksmithWorkers`). Keep its route and anvil accessible.

Blacksmiths choose damaged tools, weapons and protective armor in the nearest warehouse. They also collect armor below 25% from loaded, registered Guard Station stands inside the same town. They fetch the item's normal repair material from the warehouse, carry the original equipment to the anvil, and spend 40 working ticks per material. Each unit repairs up to one quarter of maximum durability, using Minecraft's repair-material component rather than guessed item names. Iron gear uses iron ingots, gold uses gold ingots, diamond uses diamonds, netherite uses netherite ingots and leather armor uses leather. Other supported gear follows its own component. Items without a repair material are left untouched.

A smith can perform a partial repair with one material and returns it when no more matching stock is available. Warehouse items return to storage. Stand armor returns to its original stand if the matching slot is empty; otherwise it goes to the warehouse. Full storage leaves the item with the blacksmith, and the in-progress original item is saved across restarts and drops on death. Repairs retain enchantments, custom names and other components, consume actual materials, and require no player XP. Blacksmiths do not create replacement equipment; craftsmen handle new tools.

### Smelters and cooks

A **Smeltery Station** is crafted from eight planks around a furnace. Place at least one **furnace or blast furnace within three blocks on each axis**. Smelters carry up to 16 raw metals or ore items and four fuel items from the nearest warehouse, load the actual appliance slots, collect its output, and deliver it to storage. Raw iron, copper, gold, and compatible ore items use the loaded Minecraft/mod recipes for the appliance. Only valid ingredients are inserted. Fuel and smelting times follow vanilla behavior; no ingots are created directly by a worker. Coal, charcoal, and ordinary consumed fuels work; bucket fuels can be loaded manually.

A **Cook Station** is crafted from eight planks around a smoker. It needs a **smoker or lit campfire/soul campfire within the same 7×7×7 range**. Cooks carry raw food from the warehouse, load the appliance, collect the result, and deliver it. Smokers require real fuel; lit campfires use normal campfire cooking without additional fuel. Beef becomes steak, other raw meats/fish become their cooked variants, potatoes become baked potatoes, and kelp dries when the appliance supports its recipe. Campfire results are collected from the actual dropped items. Keep the campfire lit and appliances reachable.

Cooks also make **one bread from three wheat**, carrying up to eight batches to a detected kitchen appliance and keeping a warehouse target of 32 bread. `/wwmc craft bread off` disables this order. Raw food is processed before starting a fresh bread trip; bread uses the normal three-wheat crafting ingredients and does not consume furnace fuel. Cooks can continue producing food when the pantry is empty, and deliver their food instead of reserving eight meals for themselves. Citizens leave raw meat and fish for cooking. Both new stations support two workers by default (`processingWorkers`).

Only loaded, owned appliances in range count. Overlapping stations assign each appliance to the nearest station of the same role. Appliance progress remains in its vanilla block entity across worker changes and world reloads. Missing appliances, fuel, or ingredients leave a visible status; full storage keeps products in workers’ inventories.

### Craftsmen

A **Craftsman Station** (eight planks around a crafting table) gives two citizens bench work by default (`craftsmanWorkers`). A craftsman checks the nearest warehouse for the first order below its stock target that storage has materials for, carries up to eight batches of real materials to the bench, crafts them there, and delivers the results. When nothing is short or materials are missing, the craftsman takes other work for a while.

| Order | Materials | Makes | Keeps in stock |
| --- | --- | --- | --- |
| `bread` (Cook Station) | 3 wheat | 1 bread | 32 |
| `stone_pickaxe` | 3 cobblestone, cobbled deepslate or blackstone + 2 sticks | 1 | 2 |
| `stone_axe` | 3 cobblestone (or equivalent) + 2 sticks | 1 | 2 |
| `stone_sword` | 2 cobblestone (or equivalent) + 1 stick | 1 | 2 |
| `bow` | 3 string + 3 sticks | 1 | 1 |
| `arrows` | flint + stick + feather | 4 | 64 |
| `torches` | coal or charcoal + stick | 4 | 32 |
| `ladders` | 7 sticks | 3 | 32 |
| `sticks` | 2 planks | 4 | 32 |
| `planks` | 1 log, stem, or wood of any type | 4 of that type | 64 |

Cooks handle the bread order; craftsmen check their orders from the top of the table, so worker tools come before building materials. Intermediate goods are made as needed: logs become planks, planks become sticks, and sticks become ladders, tools, torches, and arrows. Farmers' wheat becomes bread at Cook Stations, workers' broken tools are replaced, and guards find stone swords, bows, and arrows in the warehouse. `/wwmc craft` shows each order's stock against its target; `/wwmc craft <order> off` stops an order, for example to keep logs or coal for yourself.

### Alarms and enemy waves

**Noticing a threat.** Every second, the town counts the hostile monsters its citizens can see inside the claim: guards watch out to 24 blocks (32 during an alarm), other citizens only notice hostiles within 8 blocks. One or two monsters are left to the guards. When at least `alarmThreshold` (default **10**) are in sight at once, the guard closest to a **bell** within 96 blocks runs to ring it. Bells must be inside the claim and loaded. If that guard is killed, cannot find a path, or takes longer than a minute, another guard is sent. Without a reachable bell, you receive a warning instead and the town is not alerted.

**The alarm.** Any bell rung inside the town—by a player, projectile, redstone, or a guard—raises the alarm and **wakes every assigned guard at every station**, including resting reserves. Guard-raised alarms also briefly make nearby hostiles glow and tell you how many were sighted. While it rings, civilians stop working, flee hostiles from 20 blocks away instead of 12, and **duck and cover** at the nearest housing or barracks station (the banner if there is none). Alarms at night find civilians in their beds. A citizen may still volunteer for an empty guard slot. Guards stay at their posts instead of making supply trips unless they have no weapon, patrol faster, and engage from farther away. After **30 seconds** without a sighted hostile, the bell rings again for the **all-clear**, everyone returns to work, and guards resume their station shifts. That automatic all-clear ring does not raise a second alarm. `/wwmc alarm` raises the alarm without a runner, or calls the all-clear early.

**Enemy waves.** Once a town has `waveMinPopulation` (default **3**) citizens, a wave is scheduled about every `waveIntervalDays` (default **2**) in-game days. It arrives after sunset, only while you are online and within 64 blocks of the claim, and never while the previous wave's attackers are still alive. Waves contain `waveBaseMobs + waveMobsPerCitizen × population` hostiles, rounded up and capped at `waveMaxMobs`: four for a three-citizen town, 12 for 20 citizens, 18 for 32. Small towns face zombies; from 6 citizens a quarter of each wave are skeletons, and from 10 citizens 15% are spiders. The wave gathers 40–64 blocks from the banner on loaded open ground inside the claim, away from stations and at least 24 blocks from you, then marches on the banner and attacks citizens on sight. You are told its size and compass direction, and again when it has been repelled. Wave mobs do not despawn and remember their town across restarts. `/wwmc status` shows the alarm state and the next wave; `/wwmc wave` calls the next wave immediately. Peaceful difficulty prevents waves.

## Player direction and citizen autonomy

The player decides priorities, approved structures, work sites, and eventually military objectives. Citizens decide which available job to take, how to get to work, when to collect supplies, and when to rest or flee a nearby monster.

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
| `block` / `item` | Banner, automatic role stations, and station inspection. |
| `entity` | Citizen goals, harvesting, supply trips, food, rest, and entity persistence. |
| `client` / `WWMCClient` | Citizen model/renderer and transient range outlines; dedicated servers do not load rendering classes. |
| `src/main/resources` | Block/item models, language, drops, and recipes. |

Settlement records are dimension SavedData under `wwmc:settlements`. Placement provenance, planting sites, and excavation progress use a separate `wwmc:world_work` record so older settlement saves remain readable. Normal world saves persist both; temporary crew and target reservations expire and are reconstructed after reload. All current gameplay changes happen on the logical server thread. Persistent IDs keep future diplomacy and military systems independent from entity instances.

## Build and verify

Use a **Java 25 JDK**, not just a Java runtime. The Gradle wrapper and ModDevGradle versions are pinned in the repository.

```bash
./gradlew build
./gradlew runClient
```

On Windows, use `gradlew.bat build` and `gradlew.bat runClient`. Development servers use `./gradlew runServer`.

`build` runs regression checks for ranges, beds/storage, overlap ownership, shared crews, atomic target claims, tunnel/quarry geometry, random depth/shift boundaries, name uniqueness, construction-aware tree recognition, sapling conservation, local inventory conservation/backlog, armor transfers, weapon classification/ranking, quarry staircase geometry and persistence, quarry collision tracing, four-block surface/vertical reach, blocked work rays, clear tree approach candidates, persisted natural-tree proof after access clearing, role-specific crafting orders, appliance recipe eligibility, furnace slot/component conservation, independent station guard shifts and alarm rosters, alarm thresholds and all-clear timing, wave sizes/composition/timing, claim widening, save round-trips, legacy migration, recipe/drop decoding with Minecraft's codecs, shared shift armor and occupied-slot conservation, armor upgrades, the exact durability cutoff, material-funded repairs with preserved names/enchantments, healing meal/bowl conservation and cooldowns, guide-page bounds and anvil eligibility, staggered 30-citizen updates, bounded reachability probes, and shared resource scan expiry/invalidation. GitHub Actions builds with Java 25 and uploads the mod JAR as **wwmc-mc26.2**. Local JARs appear in `build/libs/`.

Automated checks do not replace an in-game playtest. Check previews, border placement, shared station crews, protected player logs, large-tree felling/replanting, tunnel/cave pathfinding, quarry staircase descent and climb-out, quarry obstructions and skipped blocks, craftsman/smelter/cook trips, fuel use and appliance output collection, guard shift changes and shared armor returns, blacksmith pickup/repair/return trips, guide crafting/reading, meal healing and direct feeding, armor upgrades/patrol/combat, weapon scavenging and archery, bell runs and civilian cover, wave spawning, inventory menus, tool breakage, bed use, and save/restart behavior before using this alpha in an important world.

## Development stages

1. **Settlement foundation — this build:** claims, role blocks, shared crews, real inventory, crop/tree cycles, automatic tunnel/cave mining, quarries, named citizens, inventories, armed guards, bell alarms, and the first enemy waves.
2. **Self-sustaining small town:** dedicated hauling, more food processing, approved housing construction, robust room validation, and migration. Craftsmen are the first step.
3. **Living neighboring world:** persisted AI settlements, weighted distant events, history, player-distance generation, and mode handoff.
4. **Military foundation:** build on town guards with trained soldiers, squad orders, wounded citizens, and hospital treatment.
5. **Raids and trade:** independent targets, physical convoys, scouting, cargo loss, and supply disruption.
6. **Countries and progression:** multiple towns, territory, diplomacy, sieges, varied faction technology, and conquest rules.

Keep the first playable scope small, preserve real resource accounting, and make every existing feature explicit before broadening the world.

## License and attribution

Original mod code is [MIT licensed](LICENSE). The generated NeoForge starter's notice remains in [TEMPLATE_LICENSE.txt](TEMPLATE_LICENSE.txt). Inspiration is a gameplay reference; this project does not include Colony Survival code or assets.
