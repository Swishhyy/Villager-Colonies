# World War MC (WWMC)

A first-person Minecraft settlement and warfare mod. You live among your citizens, direct the town's priorities, and take part in its work and battles. Citizens handle routine work themselves. The long-term world contains independent settlements, countries, and trade convoys.

Inspired by the first-person colony management of [Colony Survival](https://store.steampowered.com/app/366090/Colony_Survival/), with original Minecraft systems and an eventual emphasis on warfare.

**Target:** Minecraft Java 26.2 · NeoForge 26.2.0.88 · Java 25 · MIT license.

## Current build: 0.2.0-alpha

This is the first settlement foundation, not the completed warfare game.

Implemented:

- Persistent named settlements, owners, non-overlapping claims, and town priorities.
- Settlement banner and eight role stations, with survival crafting recipes and a creative tab.
- A default 150-block claim radius and red banners at the four claim corners.
- Automatic 7×7×7 station detection and live updates when nearby furniture/resources change.
- A placement range outline and a Station Inspector for checking existing stations.
- Deterministic ownership of overlapping beds, storage, and same-job work targets.
- Housing and barracks beds count toward recruitment; hospital beds remain patient capacity.
- Recruitable citizens using placeholder vanilla villager visuals and custom job AI.
- Autonomous harvesting and replanting of existing wheat, carrot, potato, and beetroot crops.
- Whole-tree felling, player-placement protection, and planting from actual saplings in storage.
- Descending mine access tunnels, branch mining, and a separate full-chunk quarry.
- Real tool withdrawal, tool durability, carried cargo, warehouse delivery, and food consumption.
- Shared station crews with exclusive work-target reservations, nighttime rest, and avoidance of nearby monsters.
- Save/reload support for settlement data, player block protection, replanting sites, excavation progress, cargo, tools, and meal timers.

Planned: automatic housing construction, hauling specialists, medical treatment, soldiers, raids, independent AI settlements, distant simulation, technology progression, convoys, and countries. A barracks station identifies troop housing in this build; it does not train soldiers yet. A hospital station identifies patient beds; it does not heal NPCs yet.

## Try the first build

Install the same mod JAR on the NeoForge 26.2 client and server. Use a new test world for this alpha.

1. Craft or obtain a **Settlement Banner**, place it on solid ground with open space around it, and right-click it with an empty hand to found your town. New towns extend **150 blocks in each horizontal direction**, a 301×301 block footprint including the center. Red banners appear at the four corners when those chunks are loaded and the ground can support a banner. The mod does not load distant chunks to place them or replace obstructing blocks.
2. Build a small camp with beds. Place a **Housing Station** or **Barracks Station** inside it.
3. Beds are detected automatically within **three blocks of the station on every axis**: a **7×7×7 cube**, including the station block. Both halves of each bed must fit inside the cube and your claim. No corner selection is required.
4. Place a **Warehouse Station** within that same range of your chests or barrels. It detects multiple containers, including trapped and double chests. Stock food, axes, appropriate pickaxes, saplings, and cobblestone or other tunnel floor supplies. You can add or remove storage later without registering it again.
5. Place **Farm** and **Lumber Stations** with crops or tree roots inside their **7×7×7** ranges. A farm, lumber station, or mine supports **four workers** by default; a quarry supports **eight**. Citizens reserve individual trees or excavation positions so a shared crew cannot harvest the same target twice. Housing, hospital, barracks, and warehouse ranges protect their structures from harvesting.
6. Prepare farmland and plant crops yourself. Carrots or potatoes supply both food and replanting stock; wheat is collected but is not automatically baked into bread. Put a lumber station by natural trees or accessible clear soil. It fells the connected tree, collects real leaf drops, and replants when storage has enough saplings. If no tree is accessible, it can plant a new one instead. Trees grow at Minecraft's normal rate.
7. For mining, place a **Mine Station** facing into the intended descent, with open walking space in front. It digs a staircase to Y −48, then a main tunnel with eight 24-block side branches. Alternatively, place a **Quarry Station** facing the neighboring chunk you want excavated. The quarry removes that complete 16×16 chunk from the surface down to Y −64, preserving bedrock. Keep the station and an accessible crew platform outside the target chunk. The whole plan must fit inside the town claim.
8. Run `/wwmc recruit 3`. Recruitment is limited by loaded housing beds and the configured population cap. Citizens choose available crew slots, obtain supplies, work, and deliver cargo in batches.
9. Right-click a citizen with an empty hand to inspect their activity. Sneak-right-click your citizen to release their current job. Use `/wwmc status` to inspect the settlement, and look at your mine/quarry station while running `/wwmc depth <y>` to change its target depth.

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

Changing `/wwmc depth` replaces that station's plan. Already removed blocks are recognized as air and do not award resources again. Completed work and outstanding parallel cuts are persisted across restarts.

### Commands

| Command | Purpose |
| --- | --- |
| `/wwmc status` | Inspect your town's population, loaded beds, stations, and priority. |
| `/wwmc recruit [1-8]` | Recruit citizens up to the housing/population limit. Defaults to one. |
| `/wwmc name <name>` | Rename your town, up to 48 characters. |
| `/wwmc priority balanced` | Idle citizens prefer the nearest available job. |
| `/wwmc priority food` | Idle citizens prefer available farm stations. |
| `/wwmc priority materials` | Idle citizens prefer lumber/mining stations. |
| `/wwmc depth <y>` | Set the target Y of the mine or quarry station you are looking at. |

Commands affect your own settlement. A prototype supports one settlement per owner in the Overworld. Claims do not implement general-purpose land protection; station removal is owner-restricted. An occupied settlement's banner is its fixed rally point and cannot be mined normally.

### Job blocks define building purpose

Beds alone do not determine what a structure is. A **role station and nearby furniture** provide that meaning. Housing, barracks, hospital, warehouse, farm, and lumber stations scan a fixed 7×7×7 cube centered on themselves, from offsets −3 through +3 on each axis. Mines and quarries use explicit excavation plans. Furniture/resource changes are picked up on the next inspection or worker scan.

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
| Mine | Descending staircase and branch tunnels at a selected Y level. | Reinforcement, lighting, richer excavation plans. |
| Quarry | Full neighboring chunk excavation, layer by layer. | Machinery, dedicated haulage, liquid management. |

Marker blocks use vanilla textures as placeholder visuals. Stations declare use; this build does not infer enclosed rooms, roofs, or architectural quality. Work validates supplies, protection, reservations, loaded terrain, and access before changing blocks.

Saves from 0.1.0-alpha keep their towns and stations, but old selected room bounds are ignored in favor of the fixed range. Reposition stations or furniture if an earlier selected room extended farther than three blocks. Existing surveyor items become Station Inspectors and retain the `wwmc:surveyor` ID and recipe.

Existing towns retain their saved claim radius; the 150 default applies to newly founded towns. Older mine stations default to facing north and now use tunnel plans, so inspect or reposition them before assigning workers. New excavation settings apply when a plan is created; `/wwmc depth` creates a replacement plan using the current settings.

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

The Station Inspector is a shapeless recipe with two paper and one stick. Tools consumed to craft stations are separate from tools supplied to workers.

### Server configuration

The generated WWMC server config controls these defaults:

| Setting | Default | Meaning |
| --- | --- | --- |
| `settlementRadius` | 150 | Horizontal radius of new towns. Saved towns retain their radius. |
| `maxCitizens` | 32 | Population cap, also limited by available housing beds. |
| `stationWorkers` | 4 | Crew slots per farm, lumber station, or mine. |
| `quarryWorkers` | 8 | Crew slots per quarry. |
| `mineTargetY` | −48 | Target depth when a new mine plan is created. |
| `quarryTargetY` | −64 | Bottom depth when a new quarry plan is created. |
| `branchLength` | 24 | Length of each mine side branch. |
| `branchPairs` | 4 | Paired side-branch junctions along the mine spine. |

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

`build` runs regression checks for ranges, beds/storage, overlap ownership, shared crews, atomic target claims, tunnel/quarry geometry, construction-aware tree recognition, sapling conservation, inventory transfers, save round-trips, legacy migration, and recipe/drop decoding with Minecraft's codecs. GitHub Actions builds with Java 25 and uploads the mod JAR as **wwmc-mc26.2**. Local JARs appear in `build/libs/`.

Automated checks do not replace an in-game playtest. Check previews, border placement, shared station crews, protected player logs, large-tree felling/replanting, tunnel pathfinding/floor support, quarry obstructions, tool breakage, bed use, and save/restart behavior before using this alpha in an important world.

## Development stages

1. **Settlement foundation — this build:** claims, role blocks, shared crews, real inventory, crop/tree cycles, tunnel mines, and quarries.
2. **Self-sustaining small town:** dedicated hauling, food processing, approved housing construction, robust room validation, and migration.
3. **Living neighboring world:** persisted AI settlements, weighted distant events, history, player-distance generation, and mode handoff.
4. **Military foundation:** soldiers, equipment, squads, defense, wounded citizens, and hospital treatment.
5. **Raids and trade:** independent targets, physical convoys, scouting, cargo loss, and supply disruption.
6. **Countries and progression:** multiple towns, territory, diplomacy, sieges, varied faction technology, and conquest rules.

Keep the first playable scope small, preserve real resource accounting, and make every existing feature explicit before broadening the world.

## License and attribution

Original mod code is [MIT licensed](LICENSE). The generated NeoForge starter's notice remains in [TEMPLATE_LICENSE.txt](TEMPLATE_LICENSE.txt). Inspiration is a gameplay reference; this project does not include Colony Survival code or assets.
