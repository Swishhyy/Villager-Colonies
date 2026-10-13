package io.github.swishhyy.vc.settlement;

import io.github.swishhyy.vc.Config;
import io.github.swishhyy.vc.VC;
import io.github.swishhyy.vc.block.StationBlock;
import io.github.swishhyy.vc.core.StructureRole;
import java.util.*;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.Container;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BedPart;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import net.minecraft.world.level.levelgen.Heightmap;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import net.neoforged.neoforge.event.tick.LevelTickEvent;

/** Small neutral towns are discovered in loaded terrain and built in bounded, restartable server-thread batches. */
public final class NpcSettlements {
    public static final int BUILD_BUDGET=128,EXTENT=NpcVillageLayout.EXTENT;
    /** Citizens a city is founded with and has room for. */
    public static final int CITY_POPULATION=40;
    public record Placement(BlockPos pos,BlockState state,boolean protect) {}
    private static final Map<ServerLevel,Map<UUID,List<Placement>>> PLANS=new WeakHashMap<>();
    private int playerCursor,buildCursor,searchCursor;
    @SubscribeEvent public void tick(LevelTickEvent.Post event) {
        if(!(event.getLevel() instanceof ServerLevel level) || !level.dimension().equals(Level.OVERWORLD)) return;
        SettlementData data=SettlementData.get(level);
        var pending=data.settlements.stream().filter(t -> t.trading.npc && t.trading.buildIndex>=0 && loaded(level,t.center)).toList();
        if(!pending.isEmpty()) { build(level,pending.get(Math.floorMod(buildCursor++,pending.size()))); return; }
        var players=level.players().stream().filter(p -> !(p instanceof net.neoforged.neoforge.common.util.FakePlayer) && p.isAlive() && !p.isSpectator()).toList();
        if(level.getGameTime()%100==0 && mayFoundNewTown(level.getGameTime()) && !players.isEmpty()
                && data.settlements.stream().filter(t -> t.trading.npc).count()<Config.MAX_NPC_TOWNS.get()) {
            var player=players.get(Math.floorMod(playerCursor++,players.size()));
            var plots=NpcTownPlan.nearbyPlots(level.getSeed(),player.blockPosition(),Config.NPC_SPACING.get());
            int inspected=0;
            for(int n=0;n<plots.size();n++) {
                var plot=plots.get(Math.floorMod(searchCursor++,plots.size()));
                if(!loaded(level,plot.site()) || NpcWorldData.get(level).generated.contains(plot.candidate().region())
                        || player.blockPosition().distSqr(new BlockPos(plot.site().getX(),player.getBlockY(),plot.site().getZ()))<96*96) continue;
                if(discover(level,plot.candidate(),List.of(plot.site()))!=null) return;
                if(++inspected>=4) break;
            }
        }
        // Founding still recruits a starting crew. Later growth uses PopulationGrowth's happy parents and real babies.
    }
    public static boolean mayFoundNewTown(long elapsedTicks) {
        return Config.RANDOM_TOWNS.get() && elapsedTicks>=Config.NPC_FOUNDING_DAYS.get()*24000L;
    }
    public static boolean loaded(ServerLevel level,BlockPos center) { return loaded(level,center,EXTENT); }
    /** Whether every chunk of land reaching this far from the center is loaded; a city reaches further than a village. */
    public static boolean loaded(ServerLevel level,BlockPos center,int extent) {
        for(long chunk:chunks(center,extent)) { var p=new net.minecraft.world.level.ChunkPos((int)chunk,(int)(chunk >> 32)); if(!level.hasChunk(p.x(),p.z())) return false; }
        return true;
    }
    /** Every chunk a village centred here may build in: twenty-five for a walled village centred in its chunk. */
    public static Set<Long> chunks(BlockPos center) { return chunks(center,EXTENT); }
    public static Set<Long> chunks(BlockPos center,int extent) {
        Set<Long> result=new LinkedHashSet<>();
        for(int x=Math.floorDiv(center.getX()-extent,16);x<=Math.floorDiv(center.getX()+extent,16);x++)
            for(int z=Math.floorDiv(center.getZ()-extent,16);z<=Math.floorDiv(center.getZ()+extent,16);z++)
                result.add(new net.minecraft.world.level.ChunkPos(x,z).pack());
        return result;
    }
    private boolean discover(ServerLevel level,NpcTownPlan.Candidate candidate) {
        return discover(level,candidate,NpcTownPlan.sites(candidate))!=null;
    }
    public static Settlement discover(ServerLevel level,NpcTownPlan.Candidate candidate,List<BlockPos> sites) {
        NpcWorldData regions=NpcWorldData.get(level);
        if(regions.generated.contains(candidate.region()) || !Config.RANDOM_TOWNS.get()) return null;
        var data=SettlementData.get(level); int radius=Config.SETTLEMENT_RADIUS.get();
        if(data.settlements.stream().filter(t -> t.trading.npc).count()>=Config.MAX_NPC_TOWNS.get()) return null;
        NpcVillageTerrain.Site terrain=null;
        for(BlockPos site:sites) {
            if(!loaded(level,site) || data.settlements.stream().anyMatch(t -> t.overlaps(site,radius))) continue;
            terrain=NpcVillageTerrain.inspect(level,site);
            if(terrain!=null) break;
        }
        if(terrain==null) return null;
        Settlement town=createTown(candidate,terrain,radius);
        data.settlements.add(town); data.setDirty(); regions.generated(candidate.region());
        return town;
    }
    public static Settlement createTown(NpcTownPlan.Candidate candidate,NpcVillageTerrain.Site terrain,int radius) {
        Settlement town=new Settlement(candidate.id(),candidate.id(),candidate.name().strip(),terrain.center(),radius,List.of(),List.of(),
                candidate.specialty().equals("farming") ? "food" : "materials");
        town.populationLevel=0; town.trading.npc=true; town.trading.specialty=candidate.specialty(); town.trading.buildIndex=0;
        town.trading.layout=terrain.layout();
        town.trading.development.enabled=true;
        // Room from the first day for a citizen at every workstation, the gate sentries included.
        int posts=(int)stations(town).stream().filter(s -> s.role().providesWork()).count();
        while(town.populationLevel<8 && SettlementService.populationLimitAt(town,town.populationLevel)<posts) town.populationLevel++;
        // A city has room for its full population from its first day, short of the world's own ceiling.
        if(NpcVillagePlan.city(town)) while(town.populationLevel<16 && SettlementService.populationLimitAt(town,town.populationLevel)<CITY_POPULATION
                && SettlementService.populationLimitAt(town,town.populationLevel+1)>SettlementService.populationLimitAt(town,town.populationLevel)) town.populationLevel++;
        switch(candidate.specialty()) {
            case "farming" -> town.trading.exports.add(new TradeSettings.Export("minecraft:carrot",64,32));
            case "timber" -> town.trading.exports.add(new TradeSettings.Export("minecraft:oak_log",16,32));
            case "mining" -> town.trading.exports.add(new TradeSettings.Export("minecraft:iron_ingot",16,16));
        }
        return town;
    }
    private void build(ServerLevel level,Settlement town) {
        List<Placement> plan=PLANS.computeIfAbsent(level,l -> new HashMap<>()).computeIfAbsent(town.id,id -> blueprint(town));
        // The trees standing on the land go before the first block, so nothing is built around a trunk.
        if(town.trading.buildIndex==0 && NpcVillagePlan.walled(town)) VillageClearing.clear(level,town,plan);
        int end=Math.min(plan.size(),town.trading.buildIndex+BUILD_BUDGET);
        WorldWorkData protection=WorldWorkData.get(level);
        while(town.trading.buildIndex<end) {
            Placement p=plan.get(town.trading.buildIndex);
            // A player may have placed something during construction; pause instead of replacing it.
            if(protection.protectedBlocks.contains(p.pos()) && !level.getBlockState(p.pos()).equals(p.state())) {
                town.trading.status="Construction paused by a changed block"; return;
            }
            level.setBlock(p.pos(),p.state(),2);
            if(p.protect()) protection.protect(p.pos());
            town.trading.buildIndex++;
        }
        SettlementData.get(level).setDirty();
        if(end<plan.size()) return;
        finishFounding(level,town);
        PLANS.get(level).remove(town.id); SettlementData.get(level).setDirty();
    }
    /** Native worldgen supplies the blocks; only stock, saved AI and citizens are initialized here. */
    public static void finishFounding(ServerLevel level,Settlement town) {
        for(Station station:stations(town)) if(town.station(station.position())==null) town.stations.add(station);
        stock(level,town);
        town.trading.buildIndex=-1;
        SettlementService.recruit(level,town,startingPopulation(town));
        assignCrew(town);
        town.trading.status="Neutral town: ready to accept a supply route";
        NeighbourTrade.refreshRequests(town);
        CampaignService.journal(level,town,"Our settlement is ready for neighbours and trade.");
        SettlementData.get(level).setDirty();
    }
    /** A founding village staffs its workstations, and a city fills its houses, subject to the world's population cap. */
    public static int startingPopulation(Settlement town) {
        if(NpcVillagePlan.city(town)) return SettlementService.populationLimit(town);
        return Math.min((int)town.stations.stream().filter(s -> s.role().providesWork()).count(),SettlementService.populationLimit(town));
    }
    public static void assignCrew(Settlement town) {
        var available=town.stations.stream().filter(s -> s.role().providesWork() && town.jobs.level(s.role())>0)
                .sorted(town.jobs.openOrder()).toList();
        for(UUID id:town.citizens) if(town.jobs.home(id)==null && !town.progress.children.contains(id)) {
            Station job=available.stream().filter(s -> town.jobs.assigned(s.position())<SettlementService.workerLimit(town,s)).findFirst().orElse(null);
            if(job!=null) town.jobs.assign(id,job.position());
        }
    }
    private static Station station(Settlement t,int x,int z,StructureRole role) { return new Station(t.center.offset(x,0,z),role,Direction.NORTH); }
    public static List<Station> stations(Settlement t) {
        if(t.trading.layout!=null) return NpcVillagePlan.stations(t);
        return List.of(station(t,-8,-8,StructureRole.HOUSING),station(t,4,0,StructureRole.WAREHOUSE),
                station(t,0,-3,StructureRole.TRADER),station(t,0,8,StructureRole.GUARD),station(t,4,4,StructureRole.COURIER),
                station(t,7,-8,StructureRole.COOK),station(t,9,8,StructureRole.FARM),station(t,-9,8,StructureRole.LUMBER),
                station(t,-10,0,StructureRole.MINE),station(t,-6,0,StructureRole.SMELTERY));
    }
    public static List<Placement> blueprint(Settlement town) {
        if(town.trading.layout!=null) return NpcVillagePlan.blueprint(town);
        // Last placement wins before construction starts; no intermediate scaffolding is left in the saved plan.
        Map<BlockPos,Placement> map=new LinkedHashMap<>();
        class Builder {
            void put(int x,int y,int z,BlockState state,boolean protect) { BlockPos pos=town.center.offset(x,y,z); map.put(pos,new Placement(pos,state,protect)); }
            void block(int x,int y,int z,Block block) { put(x,y,z,block.defaultBlockState(),true); }
            void floor(int x,int z,Block material) {
                for(int y=-3;y<-1;y++) block(x,y,z,Blocks.DIRT);
                block(x,-1,z,material);
                for(int y=0;y<=4;y++) put(x,y,z,Blocks.AIR.defaultBlockState(),false);
            }
        }
        Builder b=new Builder();
        for(int x=-13;x<=13;x++) for(int z=-1;z<=1;z++) b.floor(x,z,Blocks.DIRT_PATH);
        for(int z=-13;z<=13;z++) for(int x=-1;x<=1;x++) b.floor(x,z,Blocks.DIRT_PATH);
        for(int x=-4;x<=-1;x++) for(int z=-9;z<=-7;z++) b.floor(x,z,Blocks.DIRT_PATH);
        // House: 12 beds fit wholly within the housing station's 7x7x7 detection range.
        for(int x=-12;x<=-4;x++) for(int z=-12;z<=-4;z++) {
            b.floor(x,z,Blocks.OAK_PLANKS); b.block(x,3,z,Blocks.OAK_PLANKS);
            if(x==-12 || x==-4 || z==-12 || z==-4) { b.block(x,0,z,Blocks.OAK_PLANKS); b.block(x,1,z,Blocks.OAK_PLANKS); b.block(x,2,z,Blocks.OAK_PLANKS); }
        }
        var door=Blocks.OAK_DOOR.defaultBlockState().setValue(DoorBlock.FACING,Direction.EAST);
        b.put(-4,0,-8,door.setValue(DoorBlock.HALF,DoubleBlockHalf.LOWER),true);
        b.put(-4,1,-8,door.setValue(DoorBlock.HALF,DoubleBlockHalf.UPPER),true);
        for(int x:new int[]{-11,-9,-7}) for(int z:new int[]{-11,-9,-7,-5}) {
            var bed=net.minecraft.core.registries.BuiltInRegistries.BLOCK.getValue(net.minecraft.resources.Identifier.withDefaultNamespace("yellow_bed")).defaultBlockState().setValue(BedBlock.FACING,Direction.EAST);
            b.put(x,0,z,bed.setValue(BedBlock.PART,BedPart.FOOT),true);
            b.put(x+1,0,z,bed.setValue(BedBlock.PART,BedPart.HEAD),true);
        }
        for(Station s:stations(town)) {
            int x=s.position().getX()-town.center.getX(),z=s.position().getZ()-town.center.getZ();
            if(s.role()!=StructureRole.HOUSING) b.floor(x,z,Blocks.COBBLESTONE);
            b.put(x,0,z,VC.STATIONS.get(s.role()).get().defaultBlockState(),true);
        }
        // Warehouse and the job barrels are real block entities, discovered by the normal station scans.
        for(int x:new int[]{3,4,5}) { b.floor(x,-2,Blocks.OAK_PLANKS); b.block(x,0,-2,Blocks.BARREL); }
        for(int[] p:new int[][]{{6,-8},{12,8},{-12,8},{-10,-2},{-6,-2}}) { b.floor(p[0],p[1],Blocks.COBBLESTONE); b.block(p[0],0,p[1],Blocks.BARREL); }
        b.floor(8,-8,Blocks.COBBLESTONE); b.block(8,0,-8,Blocks.SMOKER);
        b.floor(-6,1,Blocks.COBBLESTONE); b.block(-6,0,1,Blocks.FURNACE);
        // Adjacent renewable iron uses the existing ore-vein mechanics, rather than excavating the houses.
        b.put(-10,0,1,Blocks.IRON_ORE.defaultBlockState(),false);
        for(int x=7;x<=11;x++) for(int z=6;z<=10;z++) if(x!=9 || z!=8) {
            b.floor(x,z,Blocks.DIRT); b.put(x,-1,z,Blocks.FARMLAND.defaultBlockState().setValue(net.minecraft.world.level.block.state.properties.BlockStateProperties.MOISTURE,7),true);
            b.put(x,0,z,((CropBlock)Blocks.CARROTS).getStateForAge(7),false);
        }
        b.put(9,-1,8,Blocks.WATER.defaultBlockState(),false);
        for(int[] p:new int[][]{{-9,5},{-9,11},{-6,8}}) { b.floor(p[0],p[1],Blocks.GRASS_BLOCK); b.put(p[0],0,p[1],Blocks.OAK_SAPLING.defaultBlockState(),false); }
        b.block(0,0,0,VC.BANNER.get()); b.floor(0,9,Blocks.COBBLESTONE); b.block(0,0,9,Blocks.BELL);
        for(int[] p:new int[][]{{2,-4},{2,6},{-3,2},{6,2}}) { b.floor(p[0],p[1],Blocks.COBBLESTONE); b.block(p[0],0,p[1],Blocks.TORCH); }
        return List.copyOf(map.values());
    }
    private static void stock(ServerLevel level,Settlement town) {
        var containers=SettlementService.storage(level,town);
        List<ItemStack> starter=new ArrayList<>(List.of(new ItemStack(Items.BREAD,64),new ItemStack(Items.BREAD,64),new ItemStack(Items.CARROT,64),
                new ItemStack(Items.BAKED_POTATO,32),new ItemStack(Items.APPLE,32),
                new ItemStack(Items.COAL,64),new ItemStack(Items.OAK_SAPLING,16),new ItemStack(Items.COBBLESTONE,64),new ItemStack(Items.STONE_AXE),
                new ItemStack(Items.STONE_AXE),new ItemStack(Items.STONE_PICKAXE),new ItemStack(Items.STONE_PICKAXE),new ItemStack(Items.IRON_SWORD),
                new ItemStack(Items.LEATHER_HELMET),new ItemStack(Items.LEATHER_CHESTPLATE),new ItemStack(Items.LEATHER_LEGGINGS),new ItemStack(Items.LEATHER_BOOTS)));
        // Every guard post of a walled village beyond the first has its own sword.
        if(NpcVillagePlan.walled(town)) {
            long guards=stations(town).stream().filter(s -> s.role()==StructureRole.GUARD).count();
            for(int n=1;n<guards;n++) starter.add(new ItemStack(Items.IRON_SWORD));
        }
        // A city has food and fuel for its larger population from its first day.
        if(NpcVillagePlan.city(town)) starter.addAll(List.of(new ItemStack(Items.BREAD,64),new ItemStack(Items.BREAD,64),new ItemStack(Items.CARROT,64),
                new ItemStack(Items.BAKED_POTATO,32),new ItemStack(Items.APPLE,32),new ItemStack(Items.COAL,64),new ItemStack(Items.OAK_SAPLING,16),
                new ItemStack(Items.STONE_AXE),new ItemStack(Items.STONE_PICKAXE)));
        for(ItemStack offered:starter) for(Container box:containers) { offered=InventoryOps.insert(box,offered); if(offered.isEmpty()) break; }
    }
    @SubscribeEvent public void stopped(ServerStoppedEvent event) { PLANS.keySet().removeIf(l -> l.getServer()==event.getServer()); }
}
