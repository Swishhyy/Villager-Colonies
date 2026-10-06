# World War MC (WWMC)

A first-person Minecraft settlement and warfare mod. You live among your citizens, direct the town's priorities, and take part in its work and battles. Citizens handle routine work themselves. The long-term world contains independent settlements, countries, and trade convoys.

Inspired by the first-person colony management of [Colony Survival](https://store.steampowered.com/app/366090/Colony_Survival/), with original Minecraft systems and an eventual emphasis on warfare.

**Target:** Minecraft Java 26.2 · NeoForge 26.2.0.88 · Java 25 · MIT license.

## Current build: 0.1.0-alpha

This is the first settlement foundation, not the completed warfare game.

Implemented:

- Persistent named settlements, owners, non-overlapping claims, and town priorities.
- Settlement banner and seven role stations, with survival crafting recipes and a creative tab.
- A surveyor tool for explicitly assigning rooms and storage volumes.
- Housing and barracks beds count toward recruitment; hospital beds remain patient capacity.
- Recruitable citizens using placeholder vanilla villager visuals and custom job AI.
- Autonomous harvesting and replanting of existing wheat, carrot, potato, and beetroot crops.
- Lumber workers harvesting nearby logs with leaves and miners harvesting reachable exposed stone/ores.
- Real tool withdrawal, tool durability, carried cargo, warehouse delivery, and food consumption.
- Exclusive station/resource reservations, nighttime rest, and avoidance of nearby monsters.
- Save/reload support for settlement data, room roles, citizens' cargo, tools, and meal timers.

Planned: automatic housing construction, hauling specialists, medical treatment, soldiers, raids, independent AI settlements, distant simulation, technology progression, convoys, and countries. A barracks station identifies troop housing in this build; it does not train soldiers yet. A hospital station identifies patient beds; it does not heal NPCs yet.

## Try the first build

Install the same mod JAR on the NeoForge 26.2 client and server. Use a new test world for this alpha.

1. Craft or obtain a **Settlement Banner**, place it on solid ground with open space around it, and right-click it with an empty hand to found your town. The default claim extends 64 blocks in each horizontal direction.
2. Build a small camp with beds. Place a **Housing Station** or **Barracks Station** inside it.
3. Use the **Settlement Surveyor** to right-click two opposite block corners around the camp. Include the station and both halves of the beds. Right-click the station to assign that selected volume. Sneak-right-click with the surveyor clears the selection.
4. Place a **Warehouse Station** directly next to a chest or barrel. Alternatively, survey a warehouse room containing several containers. Fill it with food, axes, and pickaxes.
5. Place **Farm**, **Lumber**, and **Mine Stations** near appropriate work. Each station supports one active worker. Work searches remain inside your claim and loaded chunks.
6. Prepare farmland and plant crops yourself. Carrots or potatoes are the easiest self-supplying food source in this build. Wheat is collected but is not automatically baked into bread yet. Put a lumber station near natural trees and a mining station by reachable exposed stone or ore.
7. Run `/wwmc recruit 3`. Recruitment is limited by loaded housing beds and the configured population cap. Citizens choose available stations, obtain tools from storage, work, and bring resources back.
8. Right-click a citizen with an empty hand to inspect their activity. Sneak-right-click your citizen to release their current job so they can choose another. Use `/wwmc status` to inspect the settlement.

Keep doors and paths accessible. Stations are solid blocks; citizens need to reach a neighboring block. A full warehouse, missing tools, inaccessible resources, or missing food produces a visible worker status instead of creating supplies out of thin air.

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

Beds alone do not determine what a structure is. A **role station plus a selected volume** provides that meaning. Selected volumes cannot overlap and contain one role station, so furniture belongs to one registered structure.

| Station | Current interpretation | Later role |
| --- | --- | --- |
| Housing | Residential beds, recruiting capacity, and rest. | Families, migration, approved housing expansion. |
| Barracks | Camp/troop beds, currently usable as housing. | Recruiting, training, and organizing military units. |
| Hospital | Patient beds excluded from housing capacity. | Treatment, medical supplies, casualty evacuation. |
| Warehouse | Adjacent containers or containers in its selected room. | Dedicated haulers, reserves, convoy loading. |
| Farm | Crop work within the station's search radius. | Planting expansions, varied crops, food processing. |
| Lumber | Tree-log work within the station's search radius. | Replanting and sustainable forestry. |
| Mine | Exposed stone and ore within the station's search radius. | Approved shafts, excavation plans, underground safety. |

Marker blocks use vanilla textures as placeholder visuals. Surveyed volumes declare use; this build does not automatically infer enclosed rooms, roofs, or architectural quality.

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

The surveyor is a shapeless recipe with two paper and one stick. Tools consumed to craft stations are separate from tools supplied to workers.

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
| `core` | Minecraft-independent room bounds, role rules, and expiring reservations. |
| `settlement` | Saved settlement records, claims, room/storage interpretation, commands, and inventory transfers. |
| `block` / `item` | Banner, role station interactions, and the two-corner surveyor. |
| `entity` | Citizen goals, harvesting, supply trips, food, rest, and entity persistence. |
| `WWMCClient` | Client renderer registration; dedicated servers do not load client classes. |
| `src/main/resources` | Block/item models, language, drops, and recipes. |

Settlement records are dimension SavedData under the `wwmc:settlements` identifier. Mod state is saved by Minecraft's normal world saves; transient reservations expire and are reconstructed after reload. All current gameplay changes happen on the logical server thread. Persistent IDs keep future diplomacy and military systems independent from entity instances.

## Build and verify

Use a **Java 25 JDK**, not just a Java runtime. The Gradle wrapper and ModDevGradle versions are pinned in the repository.

```bash
./gradlew build
./gradlew runClient
```

On Windows, use `gradlew.bat build` and `gradlew.bat runClient`. Development servers use `./gradlew runServer`.

`build` runs regression checks for room bounds, exclusive reservations, inventory conservation, ownership, claim overlap, and settlement/room save round-trips. GitHub Actions builds with Java 25 and uploads the mod JAR as **wwmc-mc26.2**. Local JARs appear in `build/libs/`.

Automated checks do not replace an in-game playtest. Check pathfinding, client visuals, station removal, crop replanting, tool breakage, nighttime bed use, and a server restart before using an alpha with an important world.

## Development stages

1. **Settlement foundation — this build:** claims, role blocks, housing capacity, real inventory, and autonomous crop/wood/ore work.
2. **Self-sustaining small town:** dedicated hauling, food processing, forest replanting, approved housing construction, robust room validation, and migration.
3. **Living neighboring world:** persisted AI settlements, weighted distant events, history, player-distance generation, and mode handoff.
4. **Military foundation:** soldiers, equipment, squads, defense, wounded citizens, and hospital treatment.
5. **Raids and trade:** independent targets, physical convoys, scouting, cargo loss, and supply disruption.
6. **Countries and progression:** multiple towns, territory, diplomacy, sieges, varied faction technology, and conquest rules.

Keep the first playable scope small, preserve real resource accounting, and make every existing feature explicit before broadening the world.

## License and attribution

Original mod code is [MIT licensed](LICENSE). The generated NeoForge starter's notice remains in [TEMPLATE_LICENSE.txt](TEMPLATE_LICENSE.txt). Inspiration is a gameplay reference; this project does not include Colony Survival code or assets.
