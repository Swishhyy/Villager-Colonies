package io.github.swishhyy.wwmc.settlement;

import java.util.List;
import io.github.swishhyy.wwmc.entity.CitizenEntity;
import io.github.swishhyy.wwmc.WWMC;
import io.github.swishhyy.wwmc.core.StructureRole;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.item.Items;

/**
 * Technologies a town studies by spending real warehouse goods, many of them regional: emeralds from highland outposts,
 * gold from the drylands, copper from the coast, lapis from the jungle and redstone from the savanna. The most advanced
 * also need a schematic recovered from a fortified bandit captain.
 */
public final class Research {
    public enum WorkState { IDLE,WORKING,WAITING,PAUSED }
    /** A current observation, never saved and never used to advance or charge a project. */
    public record Status(WorkState state,String detail) {
        public boolean paused() { return state==WorkState.PAUSED; }
        public String title() {
            return switch(state) { case IDLE -> "Research"; case WORKING -> "Research working"; case WAITING -> "Research waiting"; case PAUSED -> "Research paused"; };
        }
        private int rank() { return state==WorkState.WORKING ? 2 : state==WorkState.WAITING ? 1 : 0; }
        public static Status paused(String detail) { return new Status(WorkState.PAUSED,detail); }
        public static Status waiting(String detail) { return new Status(WorkState.WAITING,detail); }
        public static Status working() { return new Status(WorkState.WORKING,"Researcher is working at the lectern."); }
    }
    public record Tech(String id,String title,String benefit,List<TownProjects.Cost> costs,String schematic,String prerequisite,int ticks) {
        public Tech(String id,String title,String benefit,List<TownProjects.Cost> costs,String schematic) {
            this(id,title,benefit,costs,schematic,"",3600);
        }
    }
    private static TownProjects.Cost cost(String name,int count,net.minecraft.world.item.Item item) { return new TownProjects.Cost(name,count,s -> s.is(item)); }
    public static final List<Tech> ALL=List.of(
        new Tech("bronze_age","Bronze Age","Unlocks copper and bronze equipment, bronze alloying and quarry stations for settlement members",
                List.of(cost("copper ingots",24,Items.COPPER_INGOT),new TownProjects.Cost("tin ingots",8,s -> s.is(WWMC.TIN_INGOT.get())),
                        cost("coal",8,Items.COAL),cost("paper",8,Items.PAPER)),"","",3600),
        new Tech("iron_age","Iron Age","Unlocks iron and gold equipment, buckets, shields, anvils and blacksmith stations",
                List.of(new TownProjects.Cost("bronze ingots",16,s -> s.is(WWMC.BRONZE_INGOT.get())),cost("iron ingots",16,Items.IRON_INGOT),
                        cost("coal",16,Items.COAL),cost("paper",16,Items.PAPER)),"","bronze_age",7200),
        new Tech("gemcraft","Gemcraft","Unlocks diamond equipment and enchanting after the Iron Age",
                List.of(cost("diamonds",8,Items.DIAMOND),cost("lapis lazuli",24,Items.LAPIS_LAZULI),cost("paper",24,Items.PAPER)),"","iron_age",9600),
        new Tech("netherite_smithing","Netherite Smithing","Unlocks netherite equipment and upgrades after Gemcraft",
                List.of(cost("netherite scraps",4,Items.NETHERITE_SCRAP),cost("gold ingots",16,Items.GOLD_INGOT),cost("paper",32,Items.PAPER)),"","gemcraft",12000),
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
    public static boolean has(Settlement town,String id) {
        if(town==null) return false;
        int tier=switch(id) { case "bronze_age" -> 1; case "iron_age" -> 2; case "gemcraft" -> 3; case "netherite_smithing" -> 4; default -> 0; };
        return town.progress.research.contains(id) || tier>0 && town.progress.legacyGearTier>=tier;
    }
    public static String age(Settlement town) { return has(town,"iron_age") ? "Iron Age" : has(town,"bronze_age") ? "Bronze Age" : "Stone Age"; }
    public static Tech project(Settlement town) { return byId(town.progress.project); }
    public static String progress(Settlement town) {
        Tech tech=project(town);
        if(tech==null) return "Choose a project in Campaign / Research";
        int left=Math.max(0,tech.ticks()-town.progress.projectTicks);
        return tech.title()+": "+Math.min(100,town.progress.projectTicks*100/tech.ticks())+"%; "+(left+1199)/1200+" min of work left";
    }
    /** Read-only banner status. Loaded, available researchers take precedence over another worker's pause. */
    public static Status status(ServerLevel level,Settlement town) {
        if(project(town)==null) return new Status(WorkState.IDLE,"Choose a project in Campaign / Research.");
        if(town.jobs.level(StructureRole.RESEARCHER)==JobBoard.OFF)
            return Status.paused("Researcher jobs are off. Enable them on the Jobs tab.");
        List<Station> stations=town.stations.stream().filter(s -> s.role()==StructureRole.RESEARCHER).toList();
        if(stations.isEmpty()) return Status.paused("No Researcher Station. Add one with a lectern in range.");
        boolean active=false,desk=false,assigned=false,unloaded=false;
        Status best=null;
        boolean bestHasAi=false;
        for(Station station:stations) {
            if(!level.hasChunkAt(station.position())) { unloaded=true; continue; }
            if(!SettlementService.active(level,station)) continue;
            active=true;
            if(desks(level,town,station).isEmpty()) continue;
            desk=true;
            for(var id:town.jobs.crew(station.position())) {
                if(!town.citizens.contains(id) || !town.jobs.holdsPlace(id,station,SettlementService.workerLimit(town,station))) continue;
                assigned=true;
                if(!(level.getEntity(id) instanceof CitizenEntity citizen) || !citizen.isAlive() || citizen.town(level)!=town) continue;
                Status current=citizen.researchStatus(level,town,station);
                boolean hasAi=!citizen.isNoAi();
                if(best==null || current.rank()>best.rank() || current.rank()==best.rank() && hasAi && !bestHasAi) {
                    best=current; bestHasAi=hasAi;
                }
                if(best.state()==WorkState.WORKING) return best;
            }
        }
        if(best!=null) return best;
        if(!active) return Status.paused(unloaded ? "Researcher Station is unloaded. Keep its area loaded."
                : "Researcher Station is missing. Replace it or add another.");
        if(!desk) return Status.paused("No lectern in range. Place one beside a Researcher Station.");
        if(!assigned) return Status.paused("No researcher assigned. Recruit a citizen or raise Researcher priority on the Jobs tab.");
        return Status.paused("Assigned researcher is unloaded or missing. Check the station's Crew tab.");
    }
    /** Nearby desks are real, loaded lecterns. A researcher still has to reach and work at one. */
    public static List<BlockPos> desks(ServerLevel level,Settlement town,Station station) {
        var result=new java.util.ArrayList<BlockPos>();
        if(!SettlementService.active(level,station)) return result;
        for(BlockPos p:SettlementService.cells(station)) if(town.contains(p) && level.hasChunkAt(p)
                && level.getBlockState(p).is(Blocks.LECTERN) && SettlementService.ownsBlock(level,town,station,p)) result.add(p.immutable());
        return result;
    }
    public static int populationBonus(Settlement town) {
        return (has(town,"housing_plans") ? 10 : 0)+(has(town,"civic_planning") ? 15 : 0)+(has(town,"city_planning") ? 25 : 0);
    }
    public static String schematicTitle(String id) {
        return switch(id) { case "armor" -> "Armorer's schematic"; case "signals" -> "Signal tower schematic"; case "mining" -> "Deep mining schematic"; default -> id; };
    }
    public static String missing(net.minecraft.server.level.ServerLevel level,Settlement town,Tech tech) {
        String prerequisite=!tech.prerequisite().isEmpty() ? tech.prerequisite() : switch(tech.id()) {
            case "civic_planning" -> "housing_plans"; case "city_planning" -> "civic_planning";
            case "steel_tools","reinforced_armor","deep_mining" -> "iron_age"; case "bellows" -> "bronze_age"; default -> "";
        };
        if(!prerequisite.isEmpty() && !has(town,prerequisite)) return "Research "+byId(prerequisite).title()+" first";
        if(List.of("housing_plans","civic_planning","city_planning").contains(tech.id())
                && SettlementService.populationLimit(town)>=io.github.swishhyy.wwmc.Config.MAX_CITIZENS.get())
            return "Population is already at the server's configured maximum";
        if(!tech.schematic().isEmpty() && !town.progress.schematics.contains(tech.schematic()))
            return "Needs the "+schematicTitle(tech.schematic()).toLowerCase(java.util.Locale.ROOT)+", carried by fortified bandit captains";
        if(!town.progress.project.isEmpty()) return "Finish "+(project(town)==null ? "the current project" : project(town).title())+" first";
        if(town.stations.stream().noneMatch(s -> s.role()==StructureRole.RESEARCHER && SettlementService.active(level,s) && !desks(level,town,s).isEmpty()))
            return "Add a Researcher Station with a lectern in range";
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
        town.progress.project=id; town.progress.projectTicks=0;
        SettlementData.get(level).setDirty();
        CampaignService.journal(level,town,"Started "+tech.title()+". Supplies paid; a researcher must work at a lectern to finish it.");
        WWMC.LOGGER.info("[WWMC][research-start] town={} project={} workTicks={}",town.id,id,tech.ticks());
        return tech.title()+" started. Assign a researcher; progress pauses when they cannot work.";
    }
    /** A single town receives at most ten ticks of work each game tick, even with several researchers. */
    public static boolean work(ServerLevel level,Settlement town,Station station,BlockPos desk) {
        Tech tech=project(town);
        if(tech==null || has(town,tech.id()) || station.role()!=StructureRole.RESEARCHER || town.station(station.position())!=station
                || town.jobs.level(StructureRole.RESEARCHER)==JobBoard.OFF || !SettlementService.active(level,station)
                || !desks(level,town,station).contains(desk) || town.progress.lastResearchWork==level.getGameTime()) return false;
        town.progress.lastResearchWork=level.getGameTime();
        town.progress.projectTicks=Math.min(tech.ticks(),town.progress.projectTicks+10);
        SettlementData.get(level).setDirty();
        if(town.progress.projectTicks>=tech.ticks()) {
            town.progress.research.add(tech.id()); town.progress.project=""; town.progress.projectTicks=0;
            CampaignService.record(level,town,"Researched "+tech.title()+": "+tech.benefit()+".");
            WWMC.LOGGER.info("[WWMC][research-complete] town={} project={} age={}",town.id,tech.id(),age(town));
        }
        return true;
    }
}
