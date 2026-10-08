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
    public static String schematicTitle(String id) {
        return switch(id) { case "armor" -> "Armorer's schematic"; case "signals" -> "Signal tower schematic"; case "mining" -> "Deep mining schematic"; default -> id; };
    }
    public static String missing(net.minecraft.server.level.ServerLevel level,Settlement town,Tech tech) {
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
