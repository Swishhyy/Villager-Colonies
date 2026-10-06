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
        "Workshop recipes\n\nCraftsman Station:\nCrafting table + 8 planks\n\nBlacksmith Station:\nIron ingot + 8 planks\n\nCourier Station:\nBarrel + 8 planks\n\nBlacksmiths also need a separate anvil nearby.",
        "Pocket recipes\n\nStation Inspector:\n2 paper + 1 stick\n\nSettlement Guide:\n1 book + 1 blue dye\n\nBoth are shapeless. Stations and books also appear in the World War MC creative tab.",
        "Founding your town\n\nPlace and right-click a Settlement Banner in the Overworld. One town per player. New claims extend at least 240 blocks on X and Z. Red corner banners appear as those chunks load.",
        "Station ranges\n\nPlace stations inside your own claim. Each scans 7x7x7 blocks: 3 blocks each way. Holding or placing a station shows its range. Right-click it empty-handed to open its screen: status, crew and storage.",
        "Beds and people\n\nPut complete beds near Housing or Barracks Stations. Both halves must fit. Run /wwmc recruit or /wwmc recruit 3. Each citizen needs a housing bed; default population cap is 32.",
        "Building purpose\n\nThe station defines the room. Beds near a Hospital Station are patient beds and cannot recruit people. Medical treatment is planned. In overlapping ranges, the nearest matching station owns the resource.",
        "Supply the warehouse\n\nPut chests or barrels within 3 blocks of a Warehouse Station. Stock cooked food, axes, pickaxes, saplings and building blocks. Workers walk here to collect supplies and deliver goods in batches.",
        "Citizen screen\n\nRight-click your citizen empty-handed to see their job, activity, health, next meal and equipment above their 36-slot bag. Add food or gear directly. Sneak-right-click releases their job for reassignment.",
        "Food and healing\n\nCitizens eat carried food for meals and when injured. Food heals by its nutrition, at most once per 5 seconds. Right-click an injured citizen with food to feed them. Raw meat and fish are left for cooks.",
        "Farms\n\nPrepare farmland and plant wheat, carrots, potatoes or beetroot inside the Farm Station range. One farmer tends each farm. Farmers harvest ripe crops and replant. Wheat needs a cook to become bread. Carrots work as meals.",
        "Lumberjacks\n\nSupply axes and saplings. Workers fell complete natural trees, clear obstructing natural leaves, and replant on clear soil. They can plant when no tree is reachable. Placed logs and buildings are protected.",
        "Ore veins\n\nPlace a Mine Station touching an ore with an open side: any of the 26 blocks around it. One miner works that ore forever, collecting its normal drops while the block stays. Rarer ores refill slower. Supply a fitting pickaxe.",
        "Tunnel mines\n\nA Mine Station with no exposed ore beside it digs instead. Its arrow sets the direction. Workers build a staircase, then branch tunnels at a saved random Y from -30 to 10. Supply pickaxes and floor blocks.",
        "Quarries\n\nThe arrow points to the chunk excavated by a Quarry Station. It digs toward the configured bottom, keeps spiral stairs, and skips bedrock and protected blocks. The whole chunk must fit your town. Keep the stairs clear.",
        "Smelters\n\nPlace a furnace or blast furnace within the Smeltery Station range. Put raw iron, copper, gold or ore and real fuel in the warehouse. Smelters load the appliance and collect its actual output.",
        "Cooks\n\nPut a smoker or lit campfire within the Cook Station range. Supply raw food; smokers also need fuel. Cooks collect cooked results and make bread from 3 wheat, up to 32 bread. The Cook Station screen switches bread on or off.",
        "Craftsmen\n\nOpen the Craftsman Station. Click its Teach slot holding any item, or shift-click an item, and craftsmen learn its crafting table recipe. You keep the item. Each order's slider sets how many to keep in town; zero pauses it.",
        "Craft orders\n\nOrders run top first; the arrow raises one, the cross forgets it. Craftsmen use real materials from their barrels or the warehouse. Planks orders take any wood. Tools, torches, ladders and sticks are learned already.",
        "Job barrels\n\nPut a barrel within 3 blocks of a work station, outside any warehouse range. Workers take tools and supplies from it first. With a courier station in town, or no warehouse, they also leave their goods there.",
        "Couriers\n\nA Courier Station employs couriers. They carry goods from job barrels to the warehouse and keep smeltery and cook barrels stocked with ore, raw food, wheat and fuel. Workers then rarely walk to the warehouse.",
        "Guard crews\n\nEach Guard Station has 2 slots by default, with 1 guard active per station. Day and night crews rotate. A station with only 1 guard keeps them active. During an alarm every assigned guard wakes.",
        "Shared armor\n\nPut equipped armor stands within 3 blocks of a Guard Station. Outgoing guards return armor before sleeping; incoming guards check for upgrades. A day/night pair can share one set. Keep stands and beds accessible.",
        "Guard weapons\n\nSupply swords or spears, bows and arrows in the warehouse or stand hands. Guards choose stronger melee weapons and keep up to 32 arrows. They also scavenge usable dropped gear. Attacks and work reach 4 blocks with sight.",
        "Posts and alarms\n\nSneak-use the Inspector on a Guard Station. Right-click clear ground for the day post, then the night post. Ring a town bell or use /wwmc alarm to alert all guards. /wwmc wave calls a test attack.",
        "Worn equipment\n\nBelow 25% durability, armor goes to an empty matching stand slot, and weapons/tools go to the warehouse. Worn gear is not taken back into use. If racks are full, armor falls back to warehouse storage.",
        "Blacksmith workshop\n\nPut an anvil within 3 blocks of a Blacksmith Station. Stock repair materials in the warehouse. Blacksmiths repair damaged warehouse gear and worn armor from Guard Station stands, returning real items afterward.",
        "Repair materials\n\nIron gear: iron ingots\nGold gear: gold ingots\nDiamond: diamonds\nNetherite: netherite ingots\nLeather: leather\nOther gear uses its normal repair material. Each unit repairs up to 25% of maximum durability.",
        "Town screen\n\nRight-click your banner for the town screen: overview, citizens and stations. Its buttons change the work priority, sound the alarm or all-clear, and recruit a citizen when beds are free.",
        "Town commands\n\n/wwmc status\nInspect your town.\n/wwmc citizens\nSee jobs and activities.\n/wwmc priority food\nPrefer food work. Also use materials or balanced.\n/wwmc name <name>\nRename your town.",
        "Help when work stops\n\nInspect the station. Check range, loaded chunks, a clear route, warehouse space and supplies. Citizens never load distant chunks. A worker stuck for 30 seconds returns to the banner if safe ground is available.",
        "This playable alpha\n\nThe town works while its chunks are ticking on a running server. Distant simulation, other countries, convoys, player raids and diplomacy are planned. They are not active yet. /wwmc guide gives another copy."
    );
    public static WrittenBookContent content() {
        return new WrittenBookContent(Filterable.passThrough("Settlement Guide"),"World War MC",0,
                pages().stream().map(s -> Filterable.<Component>passThrough(Component.literal(s))).toList(),true);
    }
    /** Conservative widths fit the native 114-pixel page; chapters can continue onto another page. */
    public static List<String> pages() {
        List<String> pages=new ArrayList<>();
        for(String chapter:PAGES) {
            List<String> lines=new ArrayList<>();
            for(String paragraph:chapter.split("\n",-1)) {
                String rest=paragraph;
                while(rest.length()>18) {
                    int cut=rest.lastIndexOf(' ',18); if(cut<=0) cut=18;
                    lines.add(rest.substring(0,cut)); rest=rest.substring(cut).stripLeading();
                }
                lines.add(rest);
            }
            for(int start=0;start<lines.size();start+=13) pages.add(String.join("\n",lines.subList(start,Math.min(lines.size(),start+13))));
        }
        return List.copyOf(pages);
    }
}
