package io.github.swishhyy.wwmc.item;

import java.util.List;
import java.util.ArrayList;
import net.minecraft.network.chat.Component;
import net.minecraft.server.network.Filterable;
import net.minecraft.world.item.component.WrittenBookContent;

/** Native written-book pages: readable with right-click, including every survival recipe. */
public final class GuideBook {
    private GuideBook() {}
    public static final List<String> PAGES=List.of(
        "World War MC\nSettlement Guide\n\nBuild a town, supply its people, and defend it in person. Citizens choose available jobs and carry real items. This book covers the playable systems.",
        "Crafting stations\n\nUse a crafting table. Put the listed center item in the middle and any eight planks around it. Each recipe makes one station. The center item is consumed.",
        "Starting recipes\n\nSettlement Banner:\nBlue wool + 8 planks\n\nHousing Station:\nOak door + 8 planks\n\nWarehouse Station:\nChest + 8 planks",
        "Food and timber\n\nFarm Station:\nWheat seeds + 8 planks\n\nLumber Station:\nStone axe + 8 planks\n\nCook Station:\nSmoker + 8 planks",
        "Mining recipes\n\nMine Station:\nStone pickaxe + 8 planks\n\nQuarry Station:\nIron pickaxe + 8 planks\n\nSmeltery Station:\nFurnace + 8 planks",
        "Defense recipes\n\nGuard Station:\nIron helmet + 8 planks\n\nBarracks Station:\nIron sword + 8 planks\n\nHospital Station:\nPaper + 8 planks",
        "Workshop recipes\n\nCraftsman Station:\nCrafting table + 8 planks\n\nBlacksmith Station:\nIron ingot + 8 planks\n\nCourier Station:\nBarrel + 8 planks\n\nEnchanter Station:\nBook + 8 planks\n\nBlacksmiths also need a separate anvil nearby, and enchanters an enchanting table.",
        "Pocket recipes\n\nStation Inspector:\n2 paper + 1 stick\n\nSettlement Guide:\n1 book + 1 blue dye\n\nBoth are shapeless. Stations and books also appear in the World War MC creative tab.",
        "Founding your towns\n\nPlace and right-click a Settlement Banner in the Overworld. You can own several towns, each with its own population limit. Claims cannot overlap. New claims extend at least 240 blocks on X and Z. Red corner banners mark the borders.",
        "Trader recipe\n\nTrader Block:\nCompass + 8 planks\n\nOnly one per town, with one citizen as trader. Each route connects two trader checkpoints. Both towns need warehouses and walkable routes between them.",
        "Supply routes\n\nOpen your Trader Block empty-handed. On Routes choose another town. Your own towns connect immediately. Another player's town must choose yours to accept. A free neutral NPC town accepts automatically. A Depot unlocks extra allied routes on the Campaign board.",
        "Choosing exports\n\nClick the Trader Block's example slot with an item, or shift-click one, to add an export. You keep the item. List up to six goods. Keep sets the warehouse reserve; Send sets the maximum per trip. Zero Send pauses that item.",
        "Trader journeys\n\nThe trader takes only goods above your reserve, visits your checkpoint, walks to the other town's checkpoint, and unloads at its warehouse. The load is separate from meals. Deliveries use real items, with no payment required.",
        "Trade problems\n\nFull storage keeps cargo aboard. Pause stops departures; Disconnect brings goods home. Blocked traders need a clear road. A dead trader drops its real load.",
        "Trade routes\n\nTraders map the land between towns that players or traders have loaded, and plan over it: roads first, open ground before forest, water only by bridge. Fly over a new route once so they know it.",
        "Other settlements\n\nExplore for neutral farming, timber and mining towns, usually 1000 to 2000 blocks apart. They have houses, guards and working production. Terrain can leave wider gaps. Workers need ticking chunks.",
        "Town relations\n\nSupplying an NPC town builds goodwill. Attacking its citizens makes it hostile to you, stops your route, and its guards defend it. Countries, conquest and deeper diplomacy are future systems.",
        "Station ranges\n\nStations scan 7x7x7 blocks, 3 each way; upgrades widen it. Place them inside your claim. Holding a station shows its range. Right-click empty-handed for status, crew, storage and upgrades.",
        "Station upgrades\n\nRange and quarry crew upgrades cost 8, 16, 32 emeralds. Farm and mine yield upgrades cost 16, 32, 64 for +10%, +20%, +30% average produce or minerals. Broken stations keep upgrades.",
        "Crew sizes\n\nEvery job block takes one citizen, except quarries: 8 workers by default, plus crew upgrades. Build more stations for more workers. Old extra workers take open jobs; their names and bags stay. Range upgrades stay.",
        "Beds and people\n\nPut complete beds near Housing or Barracks Stations. Both halves must fit. Run /wwmc recruit or /wwmc recruit 3. Each citizen needs a housing bed. A new town holds up to 10 citizens.",
        "Population\n\nThe town screen's Grow button buys room for 5 more citizens with emeralds. Each upgrade costs 8 more than the last. Word of a bigger town spreads: every wave grows by 2 attackers, brings pillagers, and from the third upgrade vindicators too.",
        "Building purpose\n\nBeds near a Hospital Station are patient beds, never housing. Injured citizens stay there until full health. Fund Field Hospital for a medic who helps with meals and paper dressings.",
        "Supply the warehouse\n\nPut chests or barrels within 3 blocks of a Warehouse Station. Stock cooked food, tools, saplings and building blocks. Couriers bring supplies to job barrels and return goods. Citizens can visit the pantry to eat.",
        "Citizen screen\n\nRight-click your citizen empty-handed to see their job, activity, health, next meal and equipment above their 36-slot bag. Add food or gear directly. Sneak-right-click releases their job; they take another open place.",
        "Missing citizens\n\nA citizen stuck outside the loaded area is brought back beside its station while that station is loaded. One that cannot be found leaves the roster, freeing its job and place. It rejoins if it turns up.",
        "Meals and recovery\n\nMeals default to every 6 loaded minutes and satisfy hunger. Injuries heal in hospital beds. Scarce food is shared fairly. Workers carry at most one spare meal.",
        "Farms\n\nPlant wheat, carrots, potatoes or beetroot in range. One farmer harvests and replants. Yield upgrades add produce, after reserving the planting item. Seeds never multiply. Wheat needs a cook; carrots are meals.",
        "Lumberjacks\n\nSupply axes and saplings. Workers fell complete natural trees, clear obstructing natural leaves, and replant on clear soil. They can plant when no tree is reachable. Placed logs and buildings are protected.",
        "Ore veins\n\nPlace a Mine Station within 2 blocks of exposed ore. Supply a fitting pickaxe: better tools dig and replenish faster, capped at 1.5 times stone speed. Rare ores stay slower. Yield upgrades add minerals; Silk Touch blocks never multiply.",
        "Tunnel mines\n\nA Mine Station with no exposed ore beside it digs instead. Its arrow sets the direction. Workers build a staircase, then branch tunnels at a saved random Y from -30 to 10. Supply pickaxes and floor blocks.",
        "Quarries\n\nThe arrow points to the chunk excavated by a Quarry Station. It digs toward the configured bottom, keeps spiral stairs, and skips bedrock and protected blocks. The whole chunk must fit your town. Keep the stairs clear.",
        "Smelters\n\nPut a furnace or blast furnace in range. Couriers bring raw metals, ores and fuel from the warehouse to the job barrel. Smelters supply appliances and leave finished goods in that barrel for couriers.",
        "Cooks\n\nPut furnaces, smokers or lit campfires in range with a job barrel. Couriers deliver raw food, wheat and fuel. Keep clear walking space beside appliances and barrels. Cooks leave meals for couriers. Bread uses 3 wheat.",
        "Craftsmen\n\nOpen the Craftsman Station. Click its Teach slot holding any item, or shift-click an item, and craftsmen learn its crafting table recipe. You keep the item. Each order's slider sets how many to keep in town; zero pauses it.",
        "Craft orders\n\nOrders run top first; the arrow raises one, the cross forgets it. Craftsmen use real materials in their own barrels, delivered by couriers. Planks orders take any wood. Tools, torches, ladders and sticks are learned already.",
        "Job barrels\n\nEvery production station needs a barrel in its range, outside warehouse ranges. Workers take supplies from their own barrels and leave goods there. They do not haul between stations. An empty or full barrel pauses work until supplied or cleared.",
        "Couriers\n\nOnly couriers haul within a town. They take job goods to the warehouse and bring tools, raw materials, animal feed, carcasses, repair inputs and lapis to job barrels. Add enough couriers to keep the production chain moving. Traders still carry exports between towns.",
        "Enchanters\n\nPut an enchanting table within 5 blocks of an Enchanter Station, with bookshelves around it as for a player. Stock lapis lazuli and unenchanted gear or books in the warehouse or the station's barrel. Gear in its barrel goes first.",
        "Enchanting time\n\nOne enchanter works one item at a time: about 5 minutes for a book or common item, longer for iron, diamond and netherite. Bookshelves set the level as for players, up to 25; level 30 is yours alone. Each item uses 1 to 3 lapis.",
        "Guard crews\n\nEach Guard Station has one guard, active through both day and night. Build more posts for more guards. Taking a guard on an expedition leaves that post empty. Keep home defenders. Alarms call all assigned guards.",
        "Guard armor\n\nPut equipped armor stands within 3 blocks of each Guard Station. Its guard takes usable gear, checks for upgrades, and returns worn armor for repair. One set equips one guard. Keep stands accessible.",
        "Guard weapons\n\nSupply swords or spears, bows and arrows in the guard barrel or stand hands. Couriers bring warehouse gear. Guards choose stronger melee weapons and keep up to 32 arrows. They also scavenge usable dropped gear. Reach is 4 blocks with sight.",
        "Posts and alarms\n\nSneak-use the Inspector on a Guard Station. Right-click clear ground for the day post, then the night post. Ring a town bell or use /wwmc alarm to alert all guards. /wwmc wave calls a test attack.",
        "Calling the guards\n\nCitizens who spot a hostile near their work call the guards. Up to two guards on duty go and deal with it. Wave attackers glow, and any still alive a minute after a wave arrives are reported, so the guards hunt them down.",
        "Worn equipment\n\nBelow 25% durability, armor goes to an empty matching stand slot, and weapons/tools go to the job barrel. Couriers collect worn rack gear for repair. Worn gear stays out of use. If racks are full, armor returns to the guard barrel.",
        "Blacksmith workshop\n\nPut an anvil and a barrel in range. Couriers deliver damaged tools, weapons, armor and matching repair materials. The smith works locally and leaves repaired gear in the barrel for couriers. Nearby worn stand armor can be repaired too.",
        "Repair materials\n\nIron gear: iron ingots\nGold gear: gold ingots\nDiamond: diamonds\nNetherite: netherite ingots\nLeather: leather\nOther gear uses its normal repair material. Each unit repairs up to 25% of maximum durability.",
        "Town screen\n\nRight-click your banner for the town screen: overview, jobs, citizens and stations. Its buttons apply a priority preset, sound the alarm or all-clear, recruit a citizen when beds are free, and grow the population limit.",
        "Jobs\n\nEach citizen keeps its own station. It returns there every morning and after errands, and waits beside it when there is no work, instead of hopping to another station.",
        "Job priorities\n\nThe Jobs tab sets each job to Off, Low, Normal or High with its - and + buttons. Jobless citizens take the open place of highest priority. Citizens also move up to open places in higher-priority jobs. Off frees a job's crew.",
        "Town commands\n\n/wwmc status\nInspect your town.\n/wwmc citizens\nSee jobs and activities.\n/wwmc priority food\nFarms and cooks first. Also materials or balanced.\n/wwmc job farm high\nSet one job's priority.\n/wwmc name <name>\nRename your town.",
        "Help when work stops\n\nInspect the station. Check range, a clear route, storage space and supplies. Ordinary jobs need ticking chunks. Traders keep a moving 3x3 chunk window, limited by the server. Blocked convoys wait for a road instead of teleporting.",
        "Animal recipes\n\nHunter Station:\nLeather + 8 planks\n\nFisherman Station:\nFishing rod + 8 planks\n\nAnimal Keeper Station:\nHay bale + 8 planks\n\nButcher Station:\nIron axe + 8 planks",
        "Hunters\n\nSupply a sword or axe in the hunter barrel. Hunters seek adult cows, pigs, sheep, chickens and rabbits within 24 blocks by default. Range upgrades enlarge the hunt area. Named, leashed and keeper-protected animals are left alone.",
        "Fishermen\n\nPut the station beside open, two-block-deep water with a dry reachable bank. Supply a fishing rod. Each 30 seconds of actual fishing gives a whole cod or salmon carcass. Fishing pauses while walking or if the water is blocked.",
        "Animal keepers\n\nFence a pen within the station range and bring pairs of animals. Supply their normal breeding food in the barrel. Keepers feed real pairs, wait for growth, keep 4 adult breeders per species, and harvest surplus adults with a sword or axe. Babies are safe.",
        "Butchers\n\nSupply an axe and carcasses in the butcher barrel. The station is the cutting table. Cow or pig: 4 raw portions; sheep: 3; chicken, rabbit or fish: 2. Carcasses cannot be eaten or cooked directly.",
        "The meat chain\n\nHunter, fisherman or keeper barrel -> courier -> warehouse -> courier -> butcher barrel -> raw meat -> courier -> warehouse -> courier -> cook barrel -> cooked meals. Supply tools and fuel, and balance courier crews against production.",
        "Animal job commands\n\n/wwmc job hunter high\n/wwmc job fisherman high\n/wwmc job animal_keeper high\n/wwmc job butcher high\n\nThe food priority preset includes all four animal jobs.",
        "Relationships\n\nAt the flag: Players invites Builder or Steward and revokes access. Friends Accept on Invitations. Settlements handles alliances. Town saves a name. Entry notices use it. Claims deny outsiders. Builders interact; stewards also manage. Alliances grant no access.",
        "Settlement flags\n\nFlags survive explosions and resist pistons. Replace an old lost flag at its original location to reopen that town. Another flag in its claim shows the location and Restore flag on Relationships, costing one replacement in your bag.",
        "Industry and supply goals\n\nCampaign -> Supply: farming, fishing, timber or mining. Matching jobs work 10% faster, or 25% on matching terrain. Set stock targets with +/-; zero removes one. Traders protect home stock and reserve incoming loads.",
        "Town projects\n\nCampaign -> Projects spends actual warehouse materials once. Furnish the listed stations first. Armory: 24 iron + 32 planks, needs barracks, guards and blacksmith. It lets you lead up to 4 existing, equipped guards.",
        "Hospital project\n\nBeds heal 1 health every 5 loaded seconds. Injured citizens sleep until full; housing and alarms do not interrupt recovery. Funding 12 iron + 16 paper + 16 bread unlocks a medic: one meal and paper give 1 extra health per treatment.",
        "Transport and training\n\nDepot: 32 iron + 64 planks + 16 leather; needs warehouse, courier and trader stations. Doubles shipment slots and per-item loads; enables extra allied routes and escorts. Officer School: 48 iron + 24 gold + 32 bread; armory and barracks required; raises squad size to 6.",
        "Squad field orders\n\nEquip healthy guards before departure. /wwmc squad muster 2 borrows two nearby guards. /wwmc squad opens field orders anywhere. Follow, hold, defend or retreat. Release returns guards to normal shifts. A missing leader or badly wounded guard sends soldiers home.",
        "Convoy escorts\n\nAfter a Depot, muster a squad and choose escort while the town trader is travelling. Guards follow its physical load; the carrier waits for separated escorts. Nearby occupied camps can warn of an ambush 30 seconds ahead. Major convoy raids require a nearby player.",
        "Expedition sites\n\nExplore for bandit camps, occupied mines and ruined forts. Campaign -> Sites shows discovered coordinates and defenders. Fight in person with friends and squads. Loot is real and finite. Defenders and cleared status persist across restart. Clearing camps makes routes safer.",
        "Frontier outposts\n\nFrontier Charter: 32 iron + 64 cobblestone + 16 bread after Armory and Depot. At a cleared site use /wwmc outpost claim. It becomes your mining outpost with beds, storage, mine, courier and trader blocks. Its supply route requests meals and tools; production waits without food.",
        "Allied supply networks\n\nOne Trader Block and trader per town. A Depot adds four extra routes on Campaign -> Supply to your own or allied towns. The trader visits in turn. Broken alliances send cargo home safely.",
        "Neighbor contracts\n\nNPC orders pay real warehouse goods. Accept on Campaign -> Supply; send goods by trader, or use /wwmc contract deliver <UUID> at that town. Rewards return separately. Accepted orders have no offline deadline.",
        "Town journal\n\nCampaign -> Journal records shipments, projects, losses and victories. Present co-managers can defend waves. Away towns retain major-wave protection.",
        "This playable alpha\n\nTowns work while their chunks tick on a running server. Traders travel even when an owner logs off, within the server's active trader limit. Neighbor contracts and journals are live. Fully simulated distant countries are future work. /wwmc guide gives another copy."
    );
    public static WrittenBookContent content() {
        return new WrittenBookContent(Filterable.passThrough("Settlement Guide"),"World War MC",0,
                pages().stream().map(s -> Filterable.<Component>passThrough(Component.literal(s))).toList(),true);
    }
    /** Conservative widths fit the native 114-pixel page; chapters can continue onto another page. */
    public static List<String> pages() {
        List<String> pages=new ArrayList<>();
        List<String> current=new ArrayList<>();
        for(String chapter:PAGES) {
            // Keep room for a chapter heading and its opening, while sharing short chapter tails.
            if(current.size()>8) { pages.add(String.join("\n",current)); current.clear(); }
            if(!current.isEmpty()) current.add("");
            List<String> lines=new ArrayList<>();
            for(String paragraph:chapter.split("\n",-1)) {
                String rest=paragraph;
                while(rest.length()>18) {
                    int cut=rest.lastIndexOf(' ',18); if(cut<=0) cut=18;
                    lines.add(rest.substring(0,cut)); rest=rest.substring(cut).stripLeading();
                }
                lines.add(rest);
            }
            for(String line:lines) {
                current.add(line);
                if(current.size()==13) { pages.add(String.join("\n",current)); current.clear(); }
            }
        }
        if(!current.isEmpty()) pages.add(String.join("\n",current));
        return List.copyOf(pages);
    }
}
