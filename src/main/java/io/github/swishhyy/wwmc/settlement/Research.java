package io.github.swishhyy.wwmc.settlement;

import java.util.List;
import net.minecraft.world.item.Items;

/**
 * Technologies a town studies by spending real warehouse goods, many of them regional: emeralds from highland outposts,
 * gold from the drylands, copper from the coast, lapis from the jungle and redstone from the savanna. The most advanced
 * also need a schematic recovered from a fortified bandit captain.
 */
public final class Research {
    public record Tech(String id,String title,String benefit,List<TownProjects.Cost> costs,String schematic) {}
    private static TownProjects.Cost cost(String name,int count,net.minecraft.world.item.Item item) { return new TownProjects.Cost(name,count,s -> s.is(item)); }
    public static final List<Tech> ALL=List.of(
        new Tech("housing_plans","Housing Plans","Raises the town's population limit by 10; housing beds are still required",
                List.of(cost("paper",16,Items.PAPER),cost("cobblestone",64,Items.COBBLESTONE),cost("emeralds",8,Items.EMERALD)),""),
        new Tech("civic_planning","Civic Planning","Raises the population limit by another 15 after Housing Plans",
                List.of(cost("paper",32,Items.PAPER),cost("iron ingots",24,Items.IRON_INGOT),cost("emeralds",16,Items.EMERALD)),""),
        new Tech("city_planning","City Planning","Raises the population limit by another 25 after Civic Planning",
                List.of(cost("paper",64,Items.PAPER),cost("bricks",64,Items.BRICK),cost("gold ingots",32,Items.GOLD_INGOT),cost("emeralds",32,Items.EMERALD)),""),
        new Tech("steel_tools","Steel Tools","Miners, quarry workers, lumberjacks, hunters, fishermen and butchers wear their tools 15% less",
                List.of(cost("iron ingots",32,Items.IRON_INGOT),cost("coal",16,Items.COAL),cost("gold ingots",8,Items.GOLD_INGOT)),""),
        new Tech("bellows","Forge Bellows","Cooks and smelters work 15% faster",
                List.of(cost("copper ingots",24,Items.COPPER_INGOT),cost("coal",16,Items.COAL),cost("leather",8,Items.LEATHER)),""),
        new Tech("crop_rotation","Crop Rotation","Farmers work 15% faster",
                List.of(cost("bone meal",32,Items.BONE_MEAL),cost("lapis lazuli",16,Items.LAPIS_LAZULI),cost("wheat seeds",32,Items.WHEAT_SEEDS)),""),
        new Tech("field_medicine","Field Medicine","Hospital beds heal twice as fast",
                List.of(cost("paper",24,Items.PAPER),cost("gold ingots",8,Items.GOLD_INGOT),cost("lapis lazuli",16,Items.LAPIS_LAZULI)),""),
        new Tech("reinforced_armor","Reinforced Armor","Guards take 10% less damage",
                List.of(cost("iron ingots",32,Items.IRON_INGOT),cost("emeralds",16,Items.EMERALD),cost("leather",8,Items.LEATHER)),"armor"),
        new Tech("signal_fires","Signal Fires","Watchtowers see 64 blocks and warn of approaching hostiles every minute",
                List.of(cost("coal",32,Items.COAL),cost("redstone",16,Items.REDSTONE),cost("copper ingots",8,Items.COPPER_INGOT)),"signals"),
        new Tech("deep_mining","Deep Mining","Ore veins replenish 25% faster",
                List.of(cost("iron ingots",32,Items.IRON_INGOT),cost("redstone",16,Items.REDSTONE),cost("gold ingots",8,Items.GOLD_INGOT)),"mining")
    );
    /** Schematics a bandit captain may carry; each unlocks one technology. */
    public static final List<String> SCHEMATICS=List.of("armor","signals","mining");
    private Research() {}
    /** Extra work speed, in percent, research gives this job. */
    public static int speed(Settlement town,io.github.swishhyy.wwmc.core.StructureRole role) {
        if(role==null) return 0;
        if((role==io.github.swishhyy.wwmc.core.StructureRole.COOK || role==io.github.swishhyy.wwmc.core.StructureRole.SMELTERY) && has(town,"bellows")) return 15;
        return role==io.github.swishhyy.wwmc.core.StructureRole.FARM && has(town,"crop_rotation") ? 15 : 0;
    }
    /** Chance, in percent, research gives this job to spare a tool's durability. */
    public static int toolSaving(Settlement town,io.github.swishhyy.wwmc.core.StructureRole role) {
        return role!=null && CitizenSkill.wearsTools(role) && has(town,"steel_tools") ? 15 : 0;
    }
    public static Tech byId(String id) { return ALL.stream().filter(t -> t.id().equals(id)).findFirst().orElse(null); }
    public static boolean has(Settlement town,String id) { return town!=null && town.progress.research.contains(id); }
    public static int populationBonus(Settlement town) {
        return (has(town,"housing_plans") ? 10 : 0)+(has(town,"civic_planning") ? 15 : 0)+(has(town,"city_planning") ? 25 : 0);
    }
    public static String schematicTitle(String id) {
        return switch(id) { case "armor" -> "Armorer's schematic"; case "signals" -> "Signal tower schematic"; case "mining" -> "Deep mining schematic"; default -> id; };
    }
    public static String missing(net.minecraft.server.level.ServerLevel level,Settlement town,Tech tech) {
        String prerequisite=switch(tech.id()) { case "civic_planning" -> "housing_plans"; case "city_planning" -> "civic_planning"; default -> ""; };
        if(!prerequisite.isEmpty() && !has(town,prerequisite)) return "Research "+byId(prerequisite).title()+" first";
        if(List.of("housing_plans","civic_planning","city_planning").contains(tech.id())
                && SettlementService.populationLimit(town)>=io.github.swishhyy.wwmc.Config.MAX_CITIZENS.get())
            return "Population is already at the server's configured maximum";
        if(!tech.schematic().isEmpty() && !town.progress.schematics.contains(tech.schematic()))
            return "Needs the "+schematicTitle(tech.schematic()).toLowerCase(java.util.Locale.ROOT)+", carried by fortified bandit captains";
        for(TownProjects.Cost cost:tech.costs()) if(InventoryOps.count(SettlementService.storage(level,town),cost.material())<cost.count())
            return "Needs "+cost.count()+" "+cost.name()+" in the warehouse";
        return "";
    }
    public static String study(net.minecraft.server.level.ServerLevel level,Settlement town,String id) {
        Tech tech=byId(id);
        if(tech==null) return "Unknown research.";
        if(has(town,id)) return "Already researched.";
        String missing=missing(level,town,tech);
        if(!missing.isEmpty()) return missing;
        if(!TownProjects.pay(SettlementService.storage(level,town),tech.costs())) return "Materials changed; check warehouse stock.";
        town.progress.research.add(id);
        CampaignService.record(level,town,"Researched "+tech.title()+": "+tech.benefit()+".");
        return tech.title()+" researched.";
    }
}
