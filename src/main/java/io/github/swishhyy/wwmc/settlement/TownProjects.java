package io.github.swishhyy.wwmc.settlement;

import io.github.swishhyy.wwmc.core.StructureRole;
import java.util.*;
import java.util.function.Predicate;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.Container;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/** Completed infrastructure projects unlock capabilities and permanently spend materials, never an abstract XP currency. */
public final class TownProjects {
    public record Cost(String name,int count,Predicate<ItemStack> material) {}
    public record Project(String id,String title,String benefit,List<Cost> costs,List<StructureRole> stations,List<String> prerequisites) {}
    private static Cost iron(int n) { return new Cost("iron ingots",n,s -> s.is(Items.IRON_INGOT)); }
    private static Cost wood(int n) { return new Cost("planks",n,s -> s.is(ItemTags.PLANKS)); }
    private static Cost bread(int n) { return new Cost("bread",n,s -> s.is(Items.BREAD)); }
    public static final List<Project> ALL=List.of(
        new Project("armory","Armory","Lead squads of up to 4 guards; equip the real guards before departure",List.of(iron(24),wood(32)),List.of(StructureRole.BARRACKS,StructureRole.GUARD,StructureRole.BLACKSMITH),List.of()),
        new Project("hospital","Field Hospital","A hospital medic treats wounded citizens with food and paper",List.of(iron(12),new Cost("paper",16,s -> s.is(Items.PAPER)),bread(16)),List.of(StructureRole.HOSPITAL),List.of()),
        new Project("depot","Transport Depot","Double shipment capacity; add allied supply routes and convoy escorts",List.of(iron(32),wood(64),new Cost("leather",16,s -> s.is(Items.LEATHER))),List.of(StructureRole.WAREHOUSE,StructureRole.COURIER,StructureRole.TRADER),List.of()),
        new Project("frontier","Frontier Charter","Claim cleared expedition sites as supplied production outposts",List.of(iron(32),new Cost("cobblestone",64,s -> s.is(Items.COBBLESTONE)),bread(16)),List.of(StructureRole.WAREHOUSE),List.of("armory","depot")),
        new Project("training","Officer School","Raise each player's squad limit from 4 to 6",List.of(iron(48),new Cost("gold ingots",24,s -> s.is(Items.GOLD_INGOT)),bread(32)),List.of(StructureRole.BARRACKS),List.of("armory"))
    );
    private TownProjects() {}
    public static Project byId(String key) { return ALL.stream().filter(p -> p.id().equals(key)).findFirst().orElse(null); }
    public static String missing(ServerLevel level,Settlement town,Project project) {
        for(String required:project.prerequisites()) if(!town.campaign.projects.contains(required)) return "Complete "+byId(required).title()+" first";
        for(StructureRole role:project.stations()) if(town.stations.stream().noneMatch(s -> s.role()==role && SettlementService.active(level,s))) return "Needs a loaded "+role.title()+" Station";
        if(project.id().equals("hospital") && town.stations.stream().filter(s -> s.role()==StructureRole.HOSPITAL && SettlementService.active(level,s))
                .noneMatch(s -> !SettlementService.beds(level,town,s).isEmpty() && !SettlementService.jobStorage(level,town,s).isEmpty())) return "Hospital needs patient beds and a barrel in range";
        for(Cost cost:project.costs()) if(InventoryOps.count(SettlementService.storage(level,town),cost.material())<cost.count()) return "Needs "+cost.count()+" "+cost.name()+" in the warehouse";
        return "";
    }
    public static boolean pay(List<Container> stock,List<Cost> costs) {
        // Each definition uses distinct materials, so these prechecks make the withdrawal atomic on the server thread.
        for(Cost cost:costs) if(InventoryOps.count(stock,cost.material())<cost.count()) return false;
        for(Cost cost:costs) for(int n=0;n<cost.count();n++) InventoryOps.takeOne(stock,cost.material());
        return true;
    }
    public static String build(ServerLevel level,Settlement town,String id) {
        Project project=byId(id); if(project==null) return "Unknown project.";
        if(town.campaign.projects.contains(id)) return "Already completed.";
        String missing=missing(level,town,project); if(!missing.isEmpty()) return missing;
        if(!pay(SettlementService.storage(level,town),project.costs())) return "Materials changed; check warehouse stock.";
        town.campaign.projects.add(id); CampaignService.record(level,town,"Completed "+project.title()+". "+project.benefit());
        if(id.equals("hospital")) SettlementService.setJobLevel(level,town,StructureRole.HOSPITAL,JobBoard.HIGH);
        return project.title()+" completed.";
    }
}
