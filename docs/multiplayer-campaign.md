# Multiplayer campaign — 0.12.0-alpha

The playable loop is to specialize towns, stock an expedition, lead real guards alongside friends, clear an occupied site, and build a supplied outpost. Production, treatment, contracts and travel use real items. Install the same 0.12.0-alpha JAR on the server and every client. Existing towns keep their owners, stations, assignments and inventories; new campaign data starts empty.

## Playing together

1. Found a settlement and establish housing, food production, warehouses and courier-serviced job barrels as before.
2. Open **Campaign** at its banner, then **People**. Invite your friend as a steward. They accept with `/wwmc town accept`.
3. A **steward** can manage jobs, citizens, upgrades, supply goals, projects and squads. A **builder** can place and remove stations. The owner alone changes membership and approves alliances. Invitations grant no access before acceptance; revocation closes management/inventory access.
4. Alternatively, each player founds a separate town. Both owners approve an alliance on their People boards. An alliance permits extra supply routes; it does not grant access to another town's controls or bags.
5. Configure one town for meals and forestry, and the other for mining and equipment. On **Supply**, choose an industry and set stock targets.

Industry choices improve only their matching work: farming harvests, fishing catches, forestry actions or mining actions. Matching jobs work 10% faster; a terrain match raises the speed bonus to 25%. Balanced uses normal production speed. Rivers help fishing, fertile surface helps farming, forest cover helps timber, and high/rocky ground helps mining. Other industries remain available. Bonuses never generate extra items per harvest.

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

Supply and equip your normal guards first. The squad system borrows them; it does not create extra population, equipment or supplies. Their original station assignments are retained, and remaining guards cover shifts. Taking an entire station's crew away leaves its post unstaffed.

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

Occupied sites close to a trader can issue a 30-second ambush warning. Small convoy attacks launch only with a nearby player and respect the configured bandit cap. Clearing the camp stops that site's ambushes. Existing population-scaled settlement waves continue, now recognizing a present steward as well as the owner. Absent or distant towns retain their major-wave protection.

## Outposts and recovery

After a Frontier Charter, walk to a cleared, unclaimed site and use `/wwmc outpost claim`. Clear any blocks you added at the center or station positions first. The system does not overwrite your modifications. A new outpost keeps the parent's owner and current memberships, starts with housing, warehouse, mine, courier and trader stations, and recruits up to four citizens if its beds are still present. It has its own population limit.

A reciprocal extra route connects the outpost to its parent without replacing the parent's main trading partner. Requests start at 32 bread, two stone pickaxes and 16 oak planks; raw iron exports keep four at the outpost. Couriers remain the only internal haulers. Mining and other production wait when both local food stock and the worker's food bag are empty. Traders and couriers continue so a shortage can recover through deliveries. Players can expand or change the outpost like another town.

Complete Field Hospital and supply its barrel through couriers with **edible meals and paper dressings**. The project sets the hospital job to high priority. One medic works there. Citizens below 60% health seek and reserve patient beds, then remain in treatment until at least 95% health. A present medic spends one meal and one paper to restore four health per treatment. Medics can treat their own wounds and keep the bowl or bottle left by a meal. Hospital beds never count toward recruitment. Paper shortages, occupied beds or a missing medic are visible in citizen activity; scarce meals reduce hospital courier demand so other citizens still get food.

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

Town commands select the managed town you stand inside, then a town whose squad you lead, then your nearest managed town. Membership and alliance changes always require the immutable owner. Campaign board actions require being within eight blocks of the banner; field orders remain available away from it. All authority and item movement are checked on the server.

Expedition settings stay in the existing flat server config and its **World** section: `expeditionSites`, `maxExpeditionSites` (64), `maxExpeditionBandits` (48), and `convoyRaids`.
