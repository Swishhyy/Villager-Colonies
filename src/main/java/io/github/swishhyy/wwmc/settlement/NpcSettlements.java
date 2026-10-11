package io.github.swishhyy.wwmc.settlement;

import io.github.swishhyy.wwmc.Config;
import io.github.swishhyy.wwmc.VC;
import io.github.swishhyy.wwmc.block.StationBlock;
import io.github.swishhyy.wwmc.core.StructureRole;
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
    public record Placement(BlockPos pos,BlockState state,boolean protect) {}
    private static final Map<ServerLevel,Map<UUID,List<Placement>>> PLANS=new WeakHashMap<>();
    private int playerCursor,buildCursor;
    @SubscribeEvent public void tick(LevelTickEvent.Post event) {
        if(!(event.getLevel() instanceof ServerLevel level) || !level.dimension().equals(Level.OVERWORLD)) return;
        SettlementData data=SettlementData.get(level);
        var pending=data.settlements.stream().filter(t -> t.trading.npc && t.trading.buildIndex>=0 && loaded(level,t.center)).toList();
        if(!pending.isEmpty()) { build(level,pending.get(Math.floorMod(buildCursor++,pending.size()))); return; }
        if(level.getGameTime()%100==0 && Config.RANDOM_TOWNS.get() && !level.players().isEmpty()
                && data.settlements.stream().filter(t -> t.trading.npc).count()<Config.MAX_NPC_TOWNS.get()) {
            var player=level.players().get(Math.floorMod(playerCursor++,level.players().size()));
            int spacing=Config.NPC_SPACING.get(),rx=Math.floorDiv(player.blockPosition().getX(),spacing),rz=Math.floorDiv(player.blockPosition().getZ(),spacing);
            for(int dx=-1;dx<=1;dx++) for(int dz=-1;dz<=1;dz++) {
                var candidate=NpcTownPlan.candidate(level.getSeed(),rx+dx,rz+dz,spacing);
                if(discover(level,candidate)) return;
            }
        }
        // Founding still recruits a starting crew. Later growth uses PopulationGrowth's happy parents and real babies.
    }
    public static boolean loaded(ServerLevel level,BlockPos center) {
        for(long chunk:TradeChunks.window(center)) { var p=new net.minecraft.world.level.ChunkPos((int)chunk,(int)(chunk >> 32)); if(!level.hasChunk(p.x(),p.z())) return false; }
        return true;
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
        Settlement town=new Settlement(candidate.id(),candidate.id(),candidate.name().strip(),terrain.center(),radius,List.of(),List.of(),
                candidate.specialty().equals("farming") ? "food" : "materials");
        town.populationLevel=0; town.trading.npc=true; town.trading.specialty=candidate.specialty(); town.trading.buildIndex=0;
        town.trading.layout=terrain.layout();
        town.trading.development.enabled=true;
        switch(candidate.specialty()) {
            case "farming" -> town.trading.exports.add(new TradeSettings.Export("minecraft:carrot",64,32));
            case "timber" -> town.trading.exports.add(new TradeSettings.Export("minecraft:oak_log",16,32));
            case "mining" -> town.trading.exports.add(new TradeSettings.Export("minecraft:iron_ingot",16,16));
        }
        data.settlements.add(town); data.setDirty(); regions.generated(candidate.region());
        return town;
    }
    private void build(ServerLevel level,Settlement town) {
        List<Placement> plan=PLANS.computeIfAbsent(level,l -> new HashMap<>()).computeIfAbsent(town.id,id -> blueprint(town));
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
        for(Station station:stations(town)) town.stations.add(station);
        stock(level,town);
        town.trading.buildIndex=-1;
        SettlementService.recruit(level,town,startingPopulation(town));
        assignCrew(town);
        town.trading.status="Neutral town: ready to accept a supply route";
        NeighbourTrade.refreshRequests(town);
        CampaignService.journal(level,town,"Our settlement is ready for neighbours and trade.");
        PLANS.get(level).remove(town.id); SettlementData.get(level).setDirty();
    }
    /** A founding village staffs its eight workstations, subject to the world's population cap. */
    public static int startingPopulation(Settlement town) {
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
        for(ItemStack offered:starter) for(Container box:containers) { offered=InventoryOps.insert(box,offered); if(offered.isEmpty()) break; }
    }
    @SubscribeEvent public void stopped(ServerStoppedEvent event) { PLANS.keySet().removeIf(l -> l.getServer()==event.getServer()); }
}
