# Multiplayer campaign — 0.13.0-alpha

The playable loop is to specialize towns, stock an expedition, lead real guards alongside friends, clear an occupied site, and build a supplied outpost. Production, treatment, contracts and travel use real items. Install the same 0.13.0-alpha JAR on the server and every client. Existing towns keep their owners, stations and inventories; new campaign data starts empty. Every job block has one citizen except quarries, which keep their configured crew and upgrades. Surplus workers from old crews take other open jobs.

## Playing together

1. Found a settlement and establish housing, food production, warehouses and courier-serviced job barrels as before.
2. Open **Relationships** at its banner, then **Players**. Click a friend's permission button to invite them as a builder or steward. They right-click that town's flag, open **Invitations**, and click **Accept**.
3. A **steward** can manage jobs, citizens, upgrades, supply goals, projects and squads. A **builder** can place and remove stations. The owner alone changes membership and approves alliances. Invitations grant no access before acceptance; revocation closes management/inventory access.
4. Alternatively, each player founds a separate town. Both owners approve an alliance on **Relationships -> Settlements**. An alliance permits extra supply routes; it does not grant access to another town's controls or claim.
5. Configure one town for meals and forestry, and the other for mining and equipment. On **Supply**, choose an industry and set stock targets.

Industry choices improve only their matching work: farming harvests, fishing catches, forestry actions or mining actions. Matching jobs work 10% faster; a terrain match raises the speed bonus to 25%. Balanced uses normal production speed. Rivers help fishing, fertile surface helps farming, forest cover helps timber, and high/rocky ground helps mining. Other industries remain available. Bonuses never generate extra items per harvest.

## Relationships, claims and flags

Player towns deny interaction to everyone except their owner and accepted builders or stewards. This applies throughout the saved square claim, at every height, including existing towns. Visitors may walk through, but cannot mine or place blocks, open containers, use doors or other blocks, use buckets, trample crops, or interact with or attack entities there. Pistons cannot move blocks across a claim border. TNT ignited by an unauthorized player cannot destroy the claimed blocks. Natural hostiles still threaten ordinary buildings and citizens; flags survive explosions. NPC towns retain their existing neutral trading and combat rules.

The Relationships **Players** tab shows online players plus accepted members and pending invitations, retaining saved names for offline members. Its permission button cycles Denied, Builder and Steward. New access waits for acceptance on **Invitations**; changing an accepted member's role applies immediately. The × button revokes access and cancels invitations. Revocation also closes that player's current container. The owner alone changes permissions and alliances. Pending invitees can use only the flag's invitation screen until acceptance.

On **Settlements**, propose, accept, decline or cancel alliances. Mutual consent is required, and same-owner towns are already allied. NPC entries show existing goodwill. On **Town**, the owner types a name of up to 48 characters and clicks **Save name**. A small notice above the hotbar says **You are now entering <town name>** when a player crosses into the claim, including their first arrival after login. It appears once per entry and uses the current saved name. Red corner banners and the displayed X/Z limits identify the exact borders.

Settlement flags have high blast resistance, are removed from explosion destruction lists, and cannot be pushed by pistons. Owners can still deliberately remove an empty town's flag. An occupied town's flag remains its fixed rally point.

If a flag disappeared in an older build, its occupied town keeps the original identity and claim. Place a Settlement Banner at the saved flag coordinates and right-click it to reopen the same town. Alternatively, right-click another Settlement Banner inside that claim to open Relationships, read the original coordinates on **Town**, and use **Restore flag** while carrying one replacement in your inventory. Recovery consumes that item only after success, never overwrites another block, and preserves population, stations, assignments, memberships and routes. `/wwmc town recover` remains an optional shortcut while standing inside that claim.

## Stock targets and routes

Use the Supply board's +/- controls, or `/wwmc request minecraft:bread 32`. A target of zero removes the request. Tools change by one, stackable targets by sixteen; commands allow any target from 0 to 4096 and up to 24 requested item types.

The trader sends goods toward the destination's shortage, using last known stock when its warehouses are unloaded. Loaded warehouses refresh the snapshot before departure. Goods already on their way reserve the shortage across all allied carriers. Partial deliveries update that reservation; a returning or killed carrier releases it. The source's own requested stock and configured export reserves are retained. A deliberately paused export remains paused.

Each town still has **one Trader Block and one trader**. The original primary route remains available. Completing a Transport Depot permits up to four extra routes to same-owner or reciprocally allied towns. Add or remove these on Supply; one carrier checks destinations in turn. Pauses, broken checkpoints, full warehouses and route cancellations retain real cargo instead of deleting it. A depot raises shipment slots from six to twelve and doubles configured per-item loads, up to 128. Contract rewards have their own saved return compartment and are never delivered back into the requesting town or eaten as rations.

## Material projects

All costs come from loaded warehouse containers. Materials are checked before any are removed. A completed project never charges twice. Required stations must be loaded and actually present.

| Project | Warehouse materials | Required infrastructure | Unlock |
| --- | --- | --- | --- |
| Armory | 24 iron ingots, 32 planks of any wood | Barracks, Guard, Blacksmith | Each player can muster up to 4 existing guards |
| Field Hospital | 12 iron ingots, 16 paper, 16 bread | Hospital with patient beds and a barrel | One medic treats wounded citizens |
| Transport Depot | 32 iron ingots, 64 planks, 16 leather | Warehouse, Courier, Trader | Larger shipments, extra allied routes and escorts |
| Frontier Charter | 32 iron ingots, 64 cobblestone, 16 bread | Warehouse; completed Armory and Depot | Claim cleared sites as supplied outposts |
| Officer School | 48 iron ingots, 24 gold ingots, 32 bread | Barracks; completed Armory | Squad limit rises to 6 |

## Squads and expeditions

Supply and equip your normal guards first. The squad system borrows them; it does not create extra population, equipment or supplies. Each Guard Station has one guard, active through both shifts. Original assignments are retained during deployment, so each guard you take leaves its post unstaffed. Build additional Guard Stations and leave some equipped guards home.

Open **Army** on Campaign or run `/wwmc squad` anywhere for field controls. Muster one, two or four nearby healthy, armed guards; Officer School also allows six. `/wwmc squad muster 2` works directly. One player can lead one squad per town, with up to eight deployed squads in a town.

| Order | Behavior |
| --- | --- |
| Follow | Follow the player in a loose formation and fight visible nearby hostiles |
| Hold | Hold the marked position; avoid chasing enemies |
| Defend | Fight hostiles within 24 blocks of the marked position |
| Retreat | Return to the town banner and await further orders |
| Escort | After a Depot, follow that town's travelling trader; the trader waits for separated escorts |
| Release | Walk home, then return to normal guard shifts |

Orders persist across restart. A missing, dead, disconnected or dimension-changing leader causes guards to walk home. Guards below 30% health fall back individually. Broken or below-25%-durability equipment also sends a guard home to resume the normal repair and shared-armor routine, even after a retreat order. Squads use the same road/bridge-aware travel and weapon combat as existing citizens. They do not force-load a new army corridor; a nearby player or trader window supplies loaded terrain.

Explore for **Bandit Camps**, **Occupied Mines** and **Ruined Forts**. Discovery uses loaded, dry, relatively level natural ground outside existing claims. It rejects player-protected blocks and block entities. Sites contain finite defenders, supplies, beds and useful storage; generation never rebuilds over later player changes. Site state, defender identities and cleared status persist across restart. Campaign -> Sites lists discovered coordinates and remaining defenders.

### Objectives and rewards

Every site found from 0.13.0 has an objective. Sites found earlier keep their plain "drive out the occupiers" goal.

| Site | Objective | Reward |
| --- | --- | --- |
| Bandit Camp | **Rescue**: two captive villagers wait unharmed in a fenced pen | They join your town once you stand among them after the fight. When you leave, the recall service brings them home. |
| Bandit Camp | **Recover**: a second barrel holds stolen iron, gold, leather, bread, emeralds and a pickaxe | The goods themselves; carry them home or claim the site |
| Ruined Fort | **Defeat the Bandit Captain**: a vindicator in iron armor with 60 health | Its armor and axe drop; your town gains a research schematic |
| Occupied Mine | **Drive out the occupiers** | An ore vein for an outpost |
| Neighbor raid | **Defend a neutral town** raided while a friendly player visits, at most once every two days per town | If a player or a player town's citizen killed a raider: goodwill (+150) and a volunteer citizen, if your town has room. A raid the neutral guards repel alone earns nothing. |

Rewards go to a town managed by the nearest player when the site is cleared. That player must be within 24 blocks of a site, or 96 blocks of a raided town. A main town is credited before an outpost, so schematics reach the town that does the research. Forts found before 0.13.0 also hold a captain if not yet entered; a fort cleared before the update gives its schematic the next time a manager visits it. Campaign -> Sites and the settlement map show each site's objective and its region.

Occupied sites close to a trader can issue a 30-second ambush warning. Small convoy attacks launch only with a nearby player and respect the configured bandit cap. Clearing the camp stops that site's ambushes. Existing population-scaled settlement waves continue, now recognizing a present steward as well as the owner. Absent or distant towns retain their major-wave protection.

## Outposts and recovery

After a Frontier Charter, walk to a cleared, unclaimed site and use `/wwmc outpost claim`. Clear any blocks you added at the center or station positions first. The system does not overwrite your modifications. A new outpost keeps the parent's owner and current memberships, starts with housing, warehouse, mine, courier and trader stations, and recruits up to four citizens if its beds are still present. It has its own population limit.

A reciprocal extra route connects the outpost to its parent without replacing the parent's main trading partner. Requests start at 32 bread, two stone pickaxes and 16 oak planks; raw iron exports keep four at the outpost. Couriers remain the only internal haulers. Mining and other production wait when both local food stock and the worker's food bag are empty. Traders and couriers continue so a shortage can recover through deliveries. Players can expand or change the outpost like another town.

### Regional resources

Each site records the land it stands on. An outpost claimed there places that region's ore beside its mine, so its miner works it as an endless vein. The outpost exports that ore's product home instead of raw iron.

| Region | Ore | Exported |
| --- | --- | --- |
| Highlands (mountains, hills) | Emerald | Emeralds |
| Drylands (badlands, desert) | Gold | Raw gold |
| Jungle | Lapis lazuli | Lapis lazuli |
| Coast and rivers | Copper | Raw copper |
| Savanna | Redstone | Redstone |
| Northern taiga | Coal | Coal |
| Lowlands, everywhere else | Iron | Raw iron |

### Research

Campaign -> Research spends real warehouse goods once, like projects. Several costs are regional, so a realm needs outposts in different lands.

| Research | Costs | Effect |
| --- | --- | --- |
| Steel Tools | 32 iron ingots, 16 coal, 8 gold ingots | Miners, quarry workers, lumberjacks, hunters, fishermen and butchers wear tools 15% less |
| Forge Bellows | 24 copper ingots, 16 coal, 8 leather | Cooks and smelters work 15% faster |
| Crop Rotation | 32 bone meal, 16 lapis lazuli, 32 wheat seeds | Farmers work 15% faster |
| Field Medicine | 24 paper, 8 gold ingots, 16 lapis lazuli | Hospital beds heal twice as fast |
| Reinforced Armor | 32 iron ingots, 16 emeralds, 8 leather; armorer's schematic | Guards take 10% less damage |
| Signal Fires | 32 coal, 16 redstone, 8 copper ingots; signal tower schematic | Watchtowers see 64 blocks and warn every minute |
| Deep Mining | 32 iron ingots, 16 redstone, 8 gold ingots; deep mining schematic | Ore veins replenish 25% faster |

Each defeated Bandit Captain gives one schematic the town does not yet hold.

### Recovery and supplies

Every injured citizen now rests in a reserved Hospital Station bed until full health. Beds heal 1 health every 5 loaded seconds without supplies. Fund the Field Hospital to unlock a medic, who spends one real meal and one paper dressing for 1 additional health per treatment. See [Production and recovery](production-recovery.md).

Hungry workers also visit the communal pantry while their station is idle. They approach from clear ground within four-block hand reach, including raised warehouses and stations beside storage barrels. Scarce meals remain shared one at a time; an empty pantry lets food producers continue working.

Couriers supply one usable tool per assigned worker and count tools already in workers' hands or bags. An equipped hunter therefore leaves the butcher's axe and other spare tools in shared stock; worn tools still trigger a replacement delivery.

## Shared map and pings

Campaign -> **Map**, or `/wwmc map`, opens a top-down map centered on you. It shows:

- Every claim, colored by its relation to you: yours, allied, neutral, hostile or another player's.
- The trade routes of your own and allied towns.
- Expedition sites and raids.
- Pings, and your own position.

Scroll or use - and + to zoom from 256 to 16,384 blocks across. N, S, E and W pan the map, and Me centers it on you. Hover anything for details.

Choose a ping kind with the Ping button: meet here, build a bridge, build here, danger or resources. Then click the map to place it. Right-click your own ping to remove it; a town's managers can remove any of its pings. `/wwmc ping <kind> [note]` marks where you stand.

A ping belongs to the managed town you stand in, or your nearest one. That town's managers and its allies' managers see it, and placing it is written in the town journal. Each town keeps its 16 newest pings.

## Neighbor opportunities and journal

Loaded NPC towns continue their existing real production and food-funded growth. They occasionally offer shortages as **supply contracts**, or report a free trade checkpoint. Offers escrow existing goods from their warehouse as payment. Accept on your Supply board, then deliver by a normal trader route. Only accepted goods physically inserted into the destination warehouse count; only the accepting town receives credit. Rewards return with the carrier and remain on the board if its return compartment cannot accept them. Accepted contracts have no offline deadline.

For personal deliveries or remaining reward collection, bring goods to the requesting town and run `/wwmc contract deliver <contract UUID>`. The accepting town's owner or steward must be there in person. The operation transfers inventory items into NPC storage; completed rewards go into that player's inventory or remain safely escrowed if it is full. Other players cannot claim the contract payment.

**Journal** records shipment arrivals/departures, contracts, projects, losses, cleared sites, outposts and alarms. `/wwmc journal` shows the latest twelve entries. Each town retains 64 entries. Ordinary workers advance only in ticking chunks on a running server; trader windows remain capped. Campaign opportunities do not simulate distant block access or spawn destructive offline assaults.

## Commands

| Command | Purpose |
| --- | --- |
| `/wwmc town invite <player> [builder\|steward]` | Owner invites a currently online player; default steward |
| `/wwmc town accept [town UUID]` | Accept an invitation; without a UUID chooses the nearest waiting invitation |
| `/wwmc town remove <player>` | Owner revokes membership/invitation; offline members can be removed on the board |
| `/wwmc town leave` | Leave a town you joined while standing inside it |
| `/wwmc town list` | Town names and IDs for alliance/route commands |
| `/wwmc town recover` | Optional shortcut to restore that claim's lost flag with one replacement item |
| `/wwmc town ally <town UUID>` | Propose or accept a reciprocal alliance |
| `/wwmc town unally <town UUID>` | End an alliance |
| `/wwmc request <item ID> <0–4096>` | Set a stock target |
| `/wwmc industry <balanced\|farming\|fishing\|timber\|mining>` | Choose an industry |
| `/wwmc project <armory\|hospital\|depot\|frontier\|training>` | Fund a project from warehouse materials |
| `/wwmc squad` | Open field orders |
| `/wwmc squad muster <1–6>` | Gather equipped guards within 64 blocks, subject to project limits |
| `/wwmc squad <follow\|hold\|defend\|retreat\|escort\|release>` | Give a squad order |
| `/wwmc route add/remove <town UUID>` | Manage an extra allied/same-owner route |
| `/wwmc outpost claim` | Claim the cleared site you are standing at |
| `/wwmc contract accept/deliver <contract UUID>` | Accept an offer or make a personal delivery/collect payment |
| `/wwmc journal` | Read recent town history |
| `/wwmc needs` | List the town's needs, most urgent first |
| `/wwmc map` | Open the shared settlement map |
| `/wwmc ping <kind> [note]` | Share a ping where you stand: meet, bridge, build, danger or resource |

Town commands select the managed town you stand inside, then a town whose squad you lead, then your nearest managed town. Membership and alliance changes always require the immutable owner. Campaign board actions require being within eight blocks of the banner; field orders remain available away from it. All authority and item movement are checked on the server.

Expedition settings stay in the existing flat server config and its **World** section: `expeditionSites`, `maxExpeditionSites` (64), `maxExpeditionBandits` (48), and `convoyRaids`.
