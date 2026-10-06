# World War MC (WWMC)

A first-person Minecraft settlement and warfare mod. You live among your citizens, direct the town's priorities, and take part in its work and battles. Citizens handle routine work themselves. The long-term world contains independent settlements, countries, and trade convoys.

Inspired by the first-person colony management of [Colony Survival](https://store.steampowered.com/app/366090/Colony_Survival/), with original Minecraft systems and an eventual emphasis on warfare.

**Target:** Minecraft Java 26.2 · NeoForge 26.2.0.88 · Java 25 · MIT license.

## Current build: 0.3.0-alpha

This is the first settlement foundation, not the completed warfare game.

Implemented:

- Persistent named settlements, owners, non-overlapping claims, and town priorities.
- Settlement banner and nine role stations, with survival crafting recipes and a creative tab.
- A default 150-block claim radius and red banners at the four claim corners.
- Automatic 7×7×7 station detection and live updates when nearby furniture/resources change.
- A placement range outline and a Station Inspector for checking existing stations.
- Deterministic ownership of overlapping beds, storage, and same-job work targets.
- Housing and barracks beds count toward recruitment; hospital beds remain patient capacity.
- Recruitable citizens with individual saved names, personal inventories, and custom job AI.
- Guards with day/night posts, town patrols, melee defense, and real armor-stand equipment transfers.
- Autonomous harvesting and replanting of existing wheat, carrot, potato, and beetroot crops.
- Whole-tree felling, player-placement protection, and planting from actual saplings in storage.
- Automatic mine depth selection between Y −30 and 10, descending tunnels, accessible cave ore gathering, and full-chunk quarries.
- Openable 36-slot personal inventories, saved overflow, local supplies/rations, real tool durability, and warehouse deliveries.
- Shared station crews, exclusive resource reservations, civilian nighttime rest, and guard duty through the night.
- Save/reload support for settlement data, player block protection, replanting sites, excavation progress, cargo, tools, and meal timers.

Planned: automatic housing construction, hauling specialists, medical treatment, military squads, raids, independent AI settlements, distant simulation, technology progression, convoys, and countries. A barracks station identifies troop housing in this build; it does not train soldiers yet. A hospital station identifies patient beds; it does not heal NPCs yet.

## Try the first build

Install the same mod JAR on the NeoForge 26.2 client and server. Use a new test world for this alpha.

1. Craft or obtain a **Settlement Banner**, place it on solid ground with open space around it, and right-click it with an empty hand to found your town. New towns extend **150 blocks in each horizontal direction**, a 301×301 block footprint including the center. Red banners appear at the four corners when those chunks are loaded and the ground can support a banner. The mod does not load distant chunks to place them or replace obstructing blocks.
2. Build a small camp with beds. Place a **Housing Station** or **Barracks Station** inside it.
3. Beds are detected automatically within **three blocks of the station on every axis**: a **7×7×7 cube**, including the station block. Both halves of each bed must fit inside the cube and your claim. No corner selection is required.
4. Place a **Warehouse Station** within that same range of your chests or barrels. It detects multiple containers, including trapped and double chests. Stock food, axes, appropriate pickaxes, saplings, and cobblestone or other tunnel floor supplies. You can add or remove storage later without registering it again.
5. Place **Farm** and **Lumber Stations** with crops or tree roots inside their **7×7×7** ranges. A farm, lumber station, or mine supports **four workers** by default; a quarry supports **eight**. Citizens reserve individual trees or excavation positions so a shared crew cannot harvest the same target twice. Housing, hospital, barracks, and warehouse ranges protect their structures from harvesting.
6. Prepare farmland and plant crops yourself. Carrots or potatoes supply both food and replanting stock; wheat is collected but is not automatically baked into bread. Put a lumber station by natural trees or accessible clear soil. It fells the connected tree, collects real leaf drops, and replants when storage has enough saplings. If no tree is accessible, it can plant a new one instead. Trees grow at Minecraft's normal rate.
7. For mining, place a **Mine Station** facing into the intended descent, with open walking space in front. It chooses and saves a random depth between **Y −30 and 10**, then digs a staircase and eight 24-block side branches. Near that depth, workers also walk to accessible exposed cave ores they can reach. Alternatively, place a **Quarry Station** facing the neighboring chunk you want excavated. The quarry removes that complete 16×16 chunk from the surface down to Y −64, preserving bedrock. Keep the station and an accessible crew platform outside the target chunk. The whole plan must fit inside the town claim.
8. Run `/wwmc recruit 3`. Recruitment is limited by loaded housing beds and the configured population cap. Citizens choose available crew slots, obtain supplies, work, and deliver cargo in batches.
9. Right-click your citizen with an empty hand to open their **36-slot inventory** and see their activity. Add food, spare tools, saplings, or guard gear directly. Sneak-right-click releases their job; holding an item while interacting reports status. Use `/wwmc status` to inspect the town. Mine depths are automatic.
10. Place a **Guard Station**, supply armor stands in its local range, and configure its day/night posts as described below. Up to two citizens become guards automatically; defense slots fill before production jobs.

**Range preview:** hold any station block and aim at a block face to see a blue outline at its prospective placement position. The outline accounts for replaceable grass/snow. It turns red when the placement context is blocked. Placing a station displays a green outline for about three seconds. Right-click an existing station with an empty hand to inspect its detected blocks and briefly show its range. The **Station Inspector** also previews an existing station while you aim at it and reports its contents when right-clicked.

Ordinary stations show their local 7×7×7 area; a quarry shows the neighboring chunk's footprint. The quarry outline indicates its horizontal target, not the complete depth. Mines extend beyond the local outline along their planned tunnels. Inspection reports facing, depth, progress, and active crew size.

Keep doors and paths accessible. Stations are solid blocks; citizens need to reach a neighboring block. A full warehouse, missing tools, inaccessible resources, or missing food produces a visible worker status instead of creating supplies out of thin air.

### Forestry and construction protection

Player-placed solid blocks are recorded from this version onward, even before a town is founded. Lumberjacks reject a tree if any connected log is recorded as player-placed, if logs enter a protected building range, or if the tree touches construction such as planks or a block entity. Recognition also requires rooted trunks and non-decorative leaves. Felling follows the complete connected trunk and branches beyond the local detection cube, within bounded size, claim, and loaded-chunk limits. Unloaded or ambiguous trees are skipped rather than partly cut.

Placement history cannot be recovered for buildings made before this feature was installed. For an older build, **right-click its logs with the Station Inspector** to protect the connected logs in your claim. Decorative trees deliberately built by the player are protected too.

Replanting uses one real sapling for a small tree or four in a 2×2 plot for a large tree. The plot must be clear and accessible within the lumber station's local range. Keep spare saplings in storage: leaf drops are random, so a harvest does not guarantee enough to replant. Axes need sufficient remaining durability for the whole tree. Lumberjacks do not create saplings or speed up growth.

### Tunnel mines and quarries

A mine creates a three-block-high descending staircase, followed by two-block-high tunnels at its target Y. By default the spine has four junctions three blocks apart, each with one 24-block branch on either side. Workers approach each cut from the previous cleared step and use actual building stock to fill missing tunnel floor support. The descent has a single working front; more of the crew can work in parallel once the branches are accessible.

A quarry targets the **adjacent chunk in the direction you faced when placing it**, rather than the chunk containing its station. Crews operate from the accessible control block outside the pit, removing one real block per work cycle with actual pickaxe durability and drops. Each horizontal layer finishes before the next begins. This control-block operation is a prototype; physical machinery and haulage animations are future work.

Both jobs preserve player-placed blocks, stations, protected furnishing ranges, containers/block entities, and living entities' footing. Mining also skips logs and planks; clear trees from a quarry first. Water, lava, protected blocks, or an unsuitable tool can block progress. Drain or clear obstructions yourself and inspect the worker's status. These jobs do not pump liquids, place lighting, or guarantee safe unsupported terrain. Work only runs in loaded chunks and never forces chunks to load.

Each mine chooses its depth once when its plan is created and saves the result. There is no depth command. The entrance must be above at least part of the configured depth band, and the planned descent must fit the claim. Already removed blocks grant no resources again. Completed work and parallel cuts persist across restarts.

When miners encounter caves near the selected depth, they scan a bounded nearby area for exposed ore. They walk to reachable targets and use the correct actual pickaxe. They resume planned tunnels when no cave ore is accessible. This is local cave work; systematic exploration of an entire cave network remains future work.

### Commands

| Command | Purpose |
| --- | --- |
| `/wwmc status` | Inspect your town's population, loaded beds, stations, and priority. |
| `/wwmc recruit [1-8]` | Recruit citizens up to the housing/population limit. Defaults to one. |
| `/wwmc name <name>` | Rename your town, up to 48 characters. |
| `/wwmc priority balanced` | Idle citizens prefer the nearest available job. |
| `/wwmc priority food` | Idle citizens prefer available farm stations. |
| `/wwmc priority materials` | Idle citizens prefer lumber/mining stations. |

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
| Quarry | Full neighboring chunk excavation, layer by layer. | Machinery, dedicated haulage, liquid management. |
| Guard | Day/night posts, settlement patrols, armor stand equipment, melee defense. | Squad orders, ranged combat, training. |

Marker blocks use vanilla textures as placeholder visuals. Stations declare use; this build does not infer enclosed rooms, roofs, or architectural quality. Work validates supplies, protection, reservations, loaded terrain, and access before changing blocks.

Saves from 0.1.0-alpha keep their towns and stations, but old selected room bounds are ignored in favor of the fixed range. Reposition stations or furniture if an earlier selected room extended farther than three blocks. Existing surveyor items become Station Inspectors and retain the `wwmc:surveyor` ID and recipe.

Existing towns retain their saved claim radius; the 150 default applies to newly founded towns. Older mine stations default to facing north and now use tunnel plans, so inspect or reposition them before assigning workers. Mine plans from 0.2.0-alpha receive a one-time automatic-depth replacement when used; existing excavated blocks remain air and grant no duplicate drops. New automatic plans keep their chosen depth across reloads. Quarry plans retain their existing progress. Legacy numbered citizen labels receive personal names when their citizens load; custom names are preserved. Old nine-slot cargo saves expand into the new inventory. Existing server configs may need `settlementRadius=150` to use the new default.

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
| Guard Station | Iron helmet |

The Station Inspector is a shapeless recipe with two paper and one stick. Tools consumed to craft stations are separate from tools supplied to workers.

### Server configuration

The generated WWMC server config controls these defaults:

| Setting | Default | Meaning |
| --- | --- | --- |
| `settlementRadius` | 150 | Horizontal radius of new towns. Saved towns retain their radius. |
| `maxCitizens` | 32 | Population cap, also limited by available housing beds. |
| `stationWorkers` | 4 | Crew slots per farm, lumber station, or mine. |
| `quarryWorkers` | 8 | Crew slots per quarry. |
| `guardWorkers` | 2 | Guard crew slots per Guard Station. |
| `mineMinY` | −30 | Lower endpoint for a new mine's randomly chosen depth. |
| `mineMaxY` | 10 | Upper endpoint for a new mine's randomly chosen depth. |
| `quarryTargetY` | −64 | Bottom depth when a new quarry plan is created. |
| `mineBranchLength` | 24 | Length of each mine side branch. |
| `mineBranchPairs` | 4 | Paired side-branch junctions along the mine spine. |

### Personal inventories

Citizens keep resources in a persistent **36-slot bag**. Their owner can open it with an empty-hand right-click within eight blocks. Work pauses while the inventory is open. Menus close when the citizen dies, you move out of range, or ownership is no longer valid.

Workers use carried food, spare tools, saplings, and floor supplies before requesting replacements. Deliveries retain spare tools, supplies for the current task, and up to eight food items while sending surplus production to the warehouse. A full warehouse leaves the remainder in the citizen's bag. An unusually large tree harvest has a saved backlog that moves into the bag when space opens; citizens wait for space instead of dropping overflow on the ground. On death, actual carried items and equipped gear drop normally.

### Guard stations and posts

1. Craft a **Guard Station** from eight planks around an iron helmet and place it in your claim. Two citizens take its guard slots by default; `guardWorkers` changes that capacity. Guards stay on duty overnight while civilians rest.
2. Put equipped **armor stands within three blocks of the station on each axis**. Guards approach accessible stands and take pieces for empty armor slots. Each actual piece disappears from the stand, keeps its durability/components, and can equip only one guard. Guards leave spare pieces for others once equipped. You can also put armor directly in a guard's inventory.
3. Supply swords in the warehouse or personal inventory. Guards obtain a real sword when stock is available, consume weapon durability in combat, and fight unarmed if they lack one. Guards defend against nearby visible hostile monsters inside the town claim. They prioritize combat over supply trips.
4. **Sneak-right-click the Guard Station with the Station Inspector.** Then right-click clear ground for the **day post**, followed by clear ground for the **night post**. Both positions need dry footing, headroom, and a location inside the same claim. The pair is saved together. Sneak-click ground during selection cancels it. Until configured, both posts default to the station.
5. Guards return to the active post when the shift changes, roam among reachable town stations and nearby paths, and revisit the post periodically. Day duty runs from tick 23000 through 12999; night duty from 13000 through 22999. Post inspection reports both positions and crew size. Unloaded posts/terrain are never force-loaded.

Citizen visuals currently use vanilla villagers. Equipped guard armor affects the entity's actual equipment and defenses, but a dedicated guard model displaying the complete armor set is future work. Patrolling does not yet include formation orders, ranged weapons, or player/faction warfare.

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
| `settlement` | Claims, block ownership/protection, forestry, saved excavation plans, commands, and inventory transfers. |
| `block` / `item` | Banner, automatic role stations, and station inspection. |
| `entity` | Citizen goals, harvesting, supply trips, food, rest, and entity persistence. |
| `client` / `WWMCClient` | Client renderer registration and transient range outlines; dedicated servers do not load rendering classes. |
| `src/main/resources` | Block/item models, language, drops, and recipes. |

Settlement records are dimension SavedData under `wwmc:settlements`. Placement provenance, planting sites, and excavation progress use a separate `wwmc:world_work` record so older settlement saves remain readable. Normal world saves persist both; temporary crew and target reservations expire and are reconstructed after reload. All current gameplay changes happen on the logical server thread. Persistent IDs keep future diplomacy and military systems independent from entity instances.

## Build and verify

Use a **Java 25 JDK**, not just a Java runtime. The Gradle wrapper and ModDevGradle versions are pinned in the repository.

```bash
./gradlew build
./gradlew runClient
```

On Windows, use `gradlew.bat build` and `gradlew.bat runClient`. Development servers use `./gradlew runServer`.

`build` runs regression checks for ranges, beds/storage, overlap ownership, shared crews, atomic target claims, tunnel/quarry geometry, random depth/shift boundaries, name uniqueness, construction-aware tree recognition, sapling conservation, local inventory conservation/backlog, armor transfers, save round-trips, legacy migration, and recipe/drop decoding with Minecraft's codecs. GitHub Actions builds with Java 25 and uploads the mod JAR as **wwmc-mc26.2**. Local JARs appear in `build/libs/`.

Automated checks do not replace an in-game playtest. Check previews, border placement, shared station crews, protected player logs, large-tree felling/replanting, tunnel/cave pathfinding, quarry obstructions, guard armor transfers/patrol/combat, inventory menus, tool breakage, bed use, and save/restart behavior before using this alpha in an important world.

## Development stages

1. **Settlement foundation — this build:** claims, role blocks, shared crews, real inventory, crop/tree cycles, automatic tunnel/cave mining, quarries, named citizens, inventories, and guards.
2. **Self-sustaining small town:** dedicated hauling, food processing, approved housing construction, robust room validation, and migration.
3. **Living neighboring world:** persisted AI settlements, weighted distant events, history, player-distance generation, and mode handoff.
4. **Military foundation:** build on town guards with trained soldiers, squad orders, ranged weapons, wounded citizens, and hospital treatment.
5. **Raids and trade:** independent targets, physical convoys, scouting, cargo loss, and supply disruption.
6. **Countries and progression:** multiple towns, territory, diplomacy, sieges, varied faction technology, and conquest rules.

Keep the first playable scope small, preserve real resource accounting, and make every existing feature explicit before broadening the world.

## License and attribution

Original mod code is [MIT licensed](LICENSE). The generated NeoForge starter's notice remains in [TEMPLATE_LICENSE.txt](TEMPLATE_LICENSE.txt). Inspiration is a gameplay reference; this project does not include Colony Survival code or assets.
