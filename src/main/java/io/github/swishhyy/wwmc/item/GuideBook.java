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
        "Supply routes\n\nOpen your Trader Block empty-handed. On Routes choose another town. Your own towns connect immediately. Another player's town must choose yours to accept. A free neutral NPC town accepts automatically. Each town has one partner.",
        "Choosing exports\n\nClick the Trader Block's example slot with an item, or shift-click one, to add an export. You keep the item. List up to six goods. Keep sets the warehouse reserve; Send sets the maximum per trip. Zero Send pauses that item.",
        "Trader journeys\n\nThe trader takes only goods above your reserve, visits your checkpoint, walks to the other town's checkpoint, and unloads at its warehouse. The load is separate from meals. Deliveries use real items, with no payment required.",
        "Trade problems\n\nA full destination keeps the goods on the trader. Pause stops new departures; Disconnect sends remaining goods home. Blocked traders wait for a clear path. Build roads over difficult terrain. Death drops the real trade load.",
        "Other settlements\n\nExplore to discover small neutral farming, timber and mining towns. Candidates are usually 1000 to 2000 blocks apart; terrain can leave larger gaps. Towns have houses, guards, farms and real storage. Their workers keep them supplied while ticking.",
        "Town relations\n\nSupplying an NPC town builds goodwill. Attacking its citizens makes it hostile to you, stops your route, and its guards defend it. Countries, conquest and deeper diplomacy are future systems.",
        "Station ranges\n\nPlace stations inside your own claim. Each scans 7x7x7 blocks: 3 blocks each way. Range upgrades widen that. Holding or placing a station shows its range. Right-click it empty-handed to open its screen: status, crew, storage and upgrades.",
        "Station upgrades\n\nA station's screen sells upgrades for emeralds from your inventory: a wider range or more crew slots, 3 levels each. Each level costs twice the last, 8, 16 then 32 emeralds by default. Emerald blocks count as nine. A broken station's item keeps its upgrades.",
        "Crew sizes\n\nMines, farms, craftsmen, enchanters and traders each take one worker. Lumber starts with 4, quarries 8, couriers, guards, smelters, cooks, blacksmiths and animal jobs 2. Shared jobs can buy crew slots. Mine crew upgrades do not add miners.",
        "Beds and people\n\nPut complete beds near Housing or Barracks Stations. Both halves must fit. Run /wwmc recruit or /wwmc recruit 3. Each citizen needs a housing bed. A new town holds up to 10 citizens.",
        "Population\n\nThe town screen's Grow button buys room for 5 more citizens with emeralds. Each upgrade costs 8 more than the last. Word of a bigger town spreads: every wave grows by 2 attackers, brings pillagers, and from the third upgrade vindicators too.",
        "Building purpose\n\nThe station defines the room. Beds near a Hospital Station are patient beds and cannot recruit people. Medical treatment is planned. In overlapping ranges, the nearest matching station owns the resource.",
        "Supply the warehouse\n\nPut chests or barrels within 3 blocks of a Warehouse Station. Stock cooked food, axes, pickaxes, saplings and building blocks. Workers walk here to collect supplies and deliver goods in batches.",
        "Citizen screen\n\nRight-click your citizen empty-handed to see their job, activity, health, next meal and equipment above their 36-slot bag. Add food or gear directly. Sneak-right-click releases their job; they take another open place.",
        "Food and healing\n\nRegular meals default to every 6 loaded minutes, three times less often. Injured citizens heal from a meal at most every 30 seconds. Scarce meals stay shared; hungry citizens least recently fed get priority. Citizens carry at most one spare when food is plentiful.",
        "Farms\n\nPrepare farmland and plant wheat, carrots, potatoes or beetroot inside the Farm Station range. One farmer tends each farm. Farmers harvest ripe crops and replant. Wheat needs a cook to become bread. Carrots work as meals.",
        "Lumberjacks\n\nSupply axes and saplings. Workers fell complete natural trees, clear obstructing natural leaves, and replant on clear soil. They can plant when no tree is reachable. Placed logs and buildings are protected.",
        "Ore veins\n\nPlace a Mine Station within 2 blocks of an ore with an open side. Its miner walks over and works that ore forever, collecting its normal drops while the block stays. Rarer ores refill slower. Supply a fitting pickaxe.",
        "Tunnel mines\n\nA Mine Station with no exposed ore beside it digs instead. Its arrow sets the direction. Workers build a staircase, then branch tunnels at a saved random Y from -30 to 10. Supply pickaxes and floor blocks.",
        "Quarries\n\nThe arrow points to the chunk excavated by a Quarry Station. It digs toward the configured bottom, keeps spiral stairs, and skips bedrock and protected blocks. The whole chunk must fit your town. Keep the stairs clear.",
        "Smelters\n\nPut a furnace or blast furnace in range. Couriers bring raw metals, ores and fuel from the warehouse to the job barrel. Smelters supply appliances and leave finished goods in that barrel for couriers.",
        "Cooks\n\nPut a smoker or lit campfire in range, with a job barrel. Couriers deliver raw food, wheat and smoker fuel. Cooks leave meals for couriers to collect. Bread uses 3 wheat. Whole carcasses need a butcher first.",
        "Craftsmen\n\nOpen the Craftsman Station. Click its Teach slot holding any item, or shift-click an item, and craftsmen learn its crafting table recipe. You keep the item. Each order's slider sets how many to keep in town; zero pauses it.",
        "Craft orders\n\nOrders run top first; the arrow raises one, the cross forgets it. Craftsmen use real materials in their own barrels, delivered by couriers. Planks orders take any wood. Tools, torches, ladders and sticks are learned already.",
        "Job barrels\n\nEvery production station needs a barrel in its range, outside warehouse ranges. Workers take supplies from their own barrels and leave goods there. They do not haul between stations. An empty or full barrel pauses work until supplied or cleared.",
        "Couriers\n\nOnly couriers haul within a town. They take job goods to the warehouse and bring tools, raw materials, animal feed, carcasses, repair inputs and lapis to job barrels. Add enough couriers to keep the production chain moving. Traders still carry exports between towns.",
        "Enchanters\n\nPut an enchanting table within 5 blocks of an Enchanter Station, with bookshelves around it as for a player. Stock lapis lazuli and unenchanted gear or books in the warehouse or the station's barrel. Gear in its barrel goes first.",
        "Enchanting time\n\nOne enchanter works one item at a time: about 5 minutes for a book or common item, longer for iron, diamond and netherite. Bookshelves set the level as for players, up to 25; level 30 is yours alone. Each item uses 1 to 3 lapis.",
        "Guard crews\n\nEach Guard Station has 2 slots by default, with 1 guard active per station. Day and night crews rotate. A station with only 1 guard keeps them active. During an alarm every assigned guard wakes.",
        "Shared armor\n\nPut equipped armor stands within 3 blocks of a Guard Station. Outgoing guards return armor before sleeping; incoming guards check for upgrades. A day/night pair can share one set. Keep stands and beds accessible.",
        "Guard weapons\n\nSupply swords or spears, bows and arrows in the warehouse or stand hands. Guards choose stronger melee weapons and keep up to 32 arrows. They also scavenge usable dropped gear. Attacks and work reach 4 blocks with sight.",
        "Posts and alarms\n\nSneak-use the Inspector on a Guard Station. Right-click clear ground for the day post, then the night post. Ring a town bell or use /wwmc alarm to alert all guards. /wwmc wave calls a test attack.",
        "Calling the guards\n\nCitizens who spot a hostile near their work call the guards. Up to two guards on duty go and deal with it. Wave attackers glow, and any still alive a minute after a wave arrives are reported, so the guards hunt them down.",
        "Worn equipment\n\nBelow 25% durability, armor goes to an empty matching stand slot, and weapons/tools go to the warehouse. Worn gear is not taken back into use. If racks are full, armor falls back to warehouse storage.",
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
        "This playable alpha\n\nTowns work while their chunks tick on a running server. Traders travel even when an owner logs off, within the server's active trader limit. Distant event simulation and countries are future work. /wwmc guide gives another copy."
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
