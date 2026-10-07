package io.github.swishhyy.wwmc.settlement;

import io.github.swishhyy.wwmc.Config;
import io.github.swishhyy.wwmc.WWMC;
import io.github.swishhyy.wwmc.core.StructureRole;
import io.github.swishhyy.wwmc.entity.CitizenEntity;
import java.nio.charset.StandardCharsets;
import java.util.*;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.Container;
import net.minecraft.world.Difficulty;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.BedBlock;
import net.minecraft.world.level.block.state.properties.BedPart;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;
import net.neoforged.neoforge.event.entity.EntityLeaveLevelEvent;
import net.neoforged.neoforge.event.tick.LevelTickEvent;

/** Player-discovered sites use loaded, unclaimed natural terrain. Defenders and loot are finite and saved. */
public final class ExpeditionService {
    private int cursor;
    private static boolean nearby(ServerLevel level,BlockPos pos,int range) {
        return level.players().stream().anyMatch(p -> p.isAlive() && !p.isSpectator() && p.distanceToSqr(Vec3.atCenterOf(pos))<(double)range*range);
    }
    public static boolean clearSite(ServerLevel level,BlockPos pos) {
        return siteIssue(level,pos).isEmpty();
    }
    public static String siteIssue(ServerLevel level,BlockPos pos) {
        var data=SettlementData.get(level); var protection=WorldWorkData.get(level);
        if(data.settlements.stream().anyMatch(t -> t.overlaps(pos,Settlement.MIN_RADIUS+8))) return "too close to an existing claim";
        for(int x=-5;x<=5;x++) for(int z=-5;z<=5;z++) {
            BlockPos feet=pos.offset(x,0,z); if(!level.hasChunkAt(feet)) return "unloaded ground at "+feet.toShortString();
            int ground=level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES,feet.getX(),feet.getZ());
            if(Math.abs(ground-pos.getY())>2) return "uneven ground at "+feet.toShortString()+"; height "+ground;
            if(!level.getFluidState(new BlockPos(feet.getX(),ground-1,feet.getZ())).isEmpty()) return "wet ground at "+feet.toShortString();
            for(int y=-3;y<=4;y++) {
                BlockPos block=feet.offset(0,y,0); var state=level.getBlockState(block);
                if(protection.protectedBlocks.contains(block) || level.getBlockEntity(block)!=null) return "protected construction at "+block.toShortString();
                if(!state.isAir() && !state.is(Blocks.GRASS_BLOCK) && !state.is(BlockTags.DIRT) && !state.is(BlockTags.BASE_STONE_OVERWORLD) && !state.is(BlockTags.LEAVES)
                        && !state.is(BlockTags.LOGS) && !state.canBeReplaced()) return "non-natural block "+BuiltInRegistries.BLOCK.getKey(state.getBlock())+" at "+block.toShortString();
            }
        }
        return "";
    }
    public static ExpeditionData.Site discover(ServerLevel level,BlockPos probe,String kind,String region) {
        var data=ExpeditionData.get(level);
        if(data.sites.size()>=Config.MAX_EXPEDITIONS.get() || data.sites.stream().anyMatch(s -> s.region.equals(region) || s.pos.distSqr(probe)<384*384)) return null;
        if(!level.hasChunkAt(probe)) return null;
        BlockPos pos=new BlockPos(probe.getX(),level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES,probe.getX(),probe.getZ()),probe.getZ());
        if(!clearSite(level,pos)) return null;
        ExpeditionData.Site site=new ExpeditionData.Site(UUID.nameUUIDFromBytes((level.getSeed()+":"+region).getBytes(StandardCharsets.UTF_8)),pos,kind,region);
        build(level,site); data.sites.add(site); data.setDirty();
        for(Settlement town:SettlementData.get(level).settlements) if(!town.trading.npc && town.center.distSqr(pos)<4096.0*4096.0)
            CampaignService.record(level,town,"Scouts found a "+site.title()+" at "+pos.toShortString()+". Clear it for supplies and an outpost site.");
        return site;
    }
    private static void put(ServerLevel level,BlockPos pos,net.minecraft.world.level.block.state.BlockState state) {
        level.setBlock(pos,state,3); WorldWorkData.get(level).protect(pos);
    }
    private static void build(ServerLevel level,ExpeditionData.Site site) {
        BlockPos c=site.pos;
        for(int x=-4;x<=4;x++) for(int z=-4;z<=4;z++) {
            put(level,c.offset(x,-1,z),(site.kind.equals("fort") ? Blocks.STONE_BRICKS : Blocks.COBBLESTONE).defaultBlockState());
            for(int y=0;y<=3;y++) level.setBlock(c.offset(x,y,z),Blocks.AIR.defaultBlockState(),3);
        }
        for(int x:new int[]{-4,4}) for(int z:new int[]{-4,4}) {
            for(int y=0;y<3;y++) put(level,c.offset(x,y,z),(site.kind.equals("fort") ? Blocks.STONE_BRICKS : Blocks.OAK_FENCE).defaultBlockState());
            put(level,c.offset(x,3,z),Blocks.WOOL.red().defaultBlockState());
        }
        // Open aisles and two beds remain useful after capture. No later rebuild overwrites player alterations.
        for(int x:new int[]{-2,-1,1,2}) {
            var bed=Blocks.BED.red().defaultBlockState().setValue(BedBlock.FACING,Direction.NORTH);
            put(level,c.offset(x,0,-2),bed.setValue(BedBlock.PART,BedPart.FOOT));
            put(level,c.offset(x,0,-3),bed.setValue(BedBlock.PART,BedPart.HEAD));
        }
        put(level,c.east(3),Blocks.BARREL.defaultBlockState());
        put(level,c.west(3),Blocks.BARREL.defaultBlockState());
        var cache=(Container)level.getBlockEntity(c.east(3));
        cache.setItem(0,new ItemStack(Items.IRON_INGOT,site.kind.equals("fort") ? 24 : 12));
        cache.setItem(1,new ItemStack(Items.BREAD,16)); cache.setItem(2,new ItemStack(Items.EMERALD,4));
        cache.setItem(3,new ItemStack(Items.STONE_PICKAXE)); cache.setItem(4,new ItemStack(Items.PAPER,16)); cache.setChanged();
        if(site.kind.equals("mine")) for(int y=0;y<2;y++) level.setBlock(c.offset(-4,y,-1),Blocks.IRON_ORE.defaultBlockState(),3);
    }
    private static int aliveBudget(ServerLevel level) { return ExpeditionData.get(level).sites.stream().mapToInt(s -> s.guards.size()).sum(); }
    public static int spawn(ServerLevel level,ExpeditionData.Site site,BlockPos origin,int count) {
        if(level.getDifficulty()==Difficulty.PEACEFUL || !level.hasChunkAt(origin)) return 0;
        int made=0;
        for(int n=0;n<count && aliveBudget(level)<Config.MAX_BANDITS.get();n++) {
            BlockPos pos=origin.offset(n%3-1,0,2+n/3);
            if(!level.hasChunkAt(pos) || !level.getFluidState(pos).isEmpty()) continue;
            var entity=BuiltInRegistries.ENTITY_TYPE.getValue(Identifier.withDefaultNamespace(n%3==2 ? "vindicator" : "pillager")).create(level,EntitySpawnReason.EVENT);
            Mob bandit=entity instanceof Mob mob ? mob : null;
            if(bandit==null) continue;
            bandit.setPos(pos.getX()+0.5,pos.getY(),pos.getZ()+0.5);
            if(!level.noCollision(bandit)) continue;
            bandit.finalizeSpawn(level,level.getCurrentDifficultyAt(pos),EntitySpawnReason.EVENT,null);
            bandit.setPersistenceRequired(); bandit.addTag("wwmc_expedition_"+site.id);
            if(level.addFreshEntity(bandit)) { site.guards.add(bandit.getUUID()); made++; }
        }
        if(made>0) ExpeditionData.get(level).setDirty();
        return made;
    }
    public static String claim(ServerLevel level,Settlement parent,ServerPlayer player,UUID id) {
        var site=ExpeditionData.get(level).byId(id);
        if(!TownAccess.manages(parent,player.getUUID())) return "You need steward permission.";
        if(!parent.campaign.projects.contains("frontier")) return "Complete the Frontier Charter project first.";
        if(parent.campaign.extraRoutes.size()>=4) return "Disconnect an extra supply route before claiming another outpost.";
        if(site==null || !site.cleared || site.claimed!=null) return "Choose an unclaimed, cleared expedition site.";
        if(player.distanceToSqr(Vec3.atCenterOf(site.pos))>12*12) return "Walk to the cleared site to claim it; use /wwmc outpost claim there.";
        var data=SettlementData.get(level);
        if(data.settlements.stream().anyMatch(t -> t.overlaps(site.pos,Settlement.MIN_RADIUS))) return "This site overlaps a newer settlement claim.";
        for(var role:List.of(StructureRole.WAREHOUSE,StructureRole.TRADER,StructureRole.MINE,StructureRole.HOUSING,StructureRole.COURIER)) {
            BlockPos p=station(site.pos,role);
            if(!level.hasChunkAt(p) || !level.getBlockState(p).isAir()) return "Clear the center and station positions before claiming; the site was changed.";
        }
        if(!level.getBlockState(site.pos).isAir()) return "Clear the center block before claiming.";
        Settlement outpost=new Settlement(UUID.randomUUID(),parent.owner,parent.name+" "+site.title()+" Outpost",site.pos,Settlement.MIN_RADIUS,List.of(),List.of(),"materials");
        outpost.populationLevel=0; outpost.campaign.parent=parent.id; outpost.campaign.members.putAll(parent.campaign.members);
        outpost.jobs.setLevel(StructureRole.MINE,JobBoard.HIGH); outpost.priority=JobBoard.CUSTOM;
        outpost.campaign.requests.put("minecraft:bread",32); outpost.campaign.requests.put("minecraft:stone_pickaxe",2);
        outpost.campaign.requests.put("minecraft:oak_planks",16);
        outpost.trading.exports.add(new TradeSettings.Export("minecraft:raw_iron",4,32));
        put(level,site.pos,WWMC.BANNER.get().defaultBlockState());
        for(var role:List.of(StructureRole.WAREHOUSE,StructureRole.TRADER,StructureRole.MINE,StructureRole.HOUSING,StructureRole.COURIER)) {
            BlockPos p=station(site.pos,role); put(level,p,WWMC.STATIONS.get(role).get().defaultBlockState()); outpost.stations.add(new Station(p,role));
        }
        data.settlements.add(outpost); site.claimed=outpost.id; ExpeditionData.get(level).setDirty();
        parent.campaign.extraRoutes.add(outpost.id); outpost.campaign.extraRoutes.add(parent.id);
        SettlementService.recruit(level,outpost,4);
        CampaignService.record(level,parent,"Claimed "+outpost.name+" at "+site.pos.toShortString()+". Its trader requests food and tools from home.");
        CampaignService.record(level,outpost,"Founded as a supplied outpost of "+parent.name+". Recruit workers and keep the warehouse stocked.");
        return "Outpost claimed. Food and tools keep its mining industry working; a physical supply route now connects it to home.";
    }
    private static BlockPos station(BlockPos center,StructureRole role) {
        return switch(role) { case WAREHOUSE -> center.east(2); case TRADER -> center.south(2); case MINE -> center.west(2);
            case HOUSING -> center.north(); case COURIER -> center.south(4); default -> center; };
    }
    @SubscribeEvent public void tick(LevelTickEvent.Post event) {
        if(!(event.getLevel() instanceof ServerLevel level) || !level.dimension().equals(Level.OVERWORLD) || level.getGameTime()%100!=0 || !Config.EXPEDITIONS.get()) return;
        var data=ExpeditionData.get(level);
        if(level.getGameTime()%200==0 && !level.players().isEmpty() && data.sites.size()<Config.MAX_EXPEDITIONS.get()) {
            var player=level.players().get(Math.floorMod(cursor++,level.players().size()));
            int rx=Math.floorDiv(player.blockPosition().getX(),768),rz=Math.floorDiv(player.blockPosition().getZ(),768);
            Random random=new Random(level.getSeed()^((long)rx<<32)^rz);
            BlockPos probe=new BlockPos(rx*768+128+random.nextInt(512),0,rz*768+128+random.nextInt(512));
            discover(level,probe,List.of("camp","mine","fort").get(random.nextInt(3)),rx+":"+rz);
        }
        for(var site:data.sites) {
            if(!level.hasChunkAt(site.pos)) continue;
            if(!site.spawned && nearby(level,site.pos,96) && spawn(level,site,site.pos,site.kind.equals("fort") ? 6 : 3)>0) { site.spawned=true; data.setDirty(); }
            if(site.cleared) continue;
            for(UUID id:site.guards) if(level.getEntity(id) instanceof Mob bandit && bandit.isAlive()) {
                CitizenEntity soldier=level.getEntitiesOfClass(CitizenEntity.class,bandit.getBoundingBox().inflate(24),c -> c.isAlive() && c.isGuard()
                        && SquadService.assigned(c.town(level),c.getUUID())).stream().min(Comparator.comparingDouble(bandit::distanceToSqr)).orElse(null);
                if(soldier!=null && bandit.getTarget()==null) bandit.setTarget(soldier);
                else if(bandit.getTarget()==null && bandit.blockPosition().distSqr(site.pos)>32*32) bandit.getNavigation().moveTo(site.pos.getX()+0.5,site.pos.getY(),site.pos.getZ()+0.5,0.7);
            }
            ambush(level,site);
        }
    }
    private static void ambush(ServerLevel level,ExpeditionData.Site site) {
        if(!site.spawned || site.cleared || !Config.CONVOY_RAIDS.get()) return;
        if(site.ambush!=null) {
            if(level.getGameTime()<site.nextRaid) return;
            Settlement victim=site.victim==null ? null : SettlementData.get(level).byId(site.victim);
            CitizenEntity trader=victim!=null && victim.trading.runner!=null && level.getEntity(victim.trading.runner) instanceof CitizenEntity c ? c : null;
            if(trader!=null && trader.isAlive() && trader.tradeCargoCount()>0 && trader.blockPosition().distSqr(site.pos)<128*128
                    && nearby(level,trader.blockPosition(),96)) {
                BlockPos ground=ambushGround(level,trader.blockPosition()); if(ground!=null) spawn(level,site,ground,3);
            }
            site.ambush=null; site.victim=null; site.nextRaid=level.getGameTime()+12000; ExpeditionData.get(level).setDirty(); return;
        }
        if(level.getGameTime()<site.nextRaid) return;
        for(Settlement town:SettlementData.get(level).settlements) if(town.trading.runner!=null && town.trading.runnerPos!=null && town.trading.runnerPos.distSqr(site.pos)<128*128
                && nearby(level,town.trading.runnerPos,96)) {
            if(!(level.getEntity(town.trading.runner) instanceof CitizenEntity trader) || !trader.isAlive() || trader.tradeCargoCount()==0) continue;
            BlockPos ground=ambushGround(level,town.trading.runnerPos); if(ground==null) continue;
            site.ambush=ground; site.victim=town.id; site.nextRaid=level.getGameTime()+600;
            CampaignService.record(level,town,"Bandits are gathering near the trader at "+ground.toShortString()+". Escort the shipment or clear their camp.");
            ExpeditionData.get(level).setDirty(); return;
        }
    }
    private static BlockPos ambushGround(ServerLevel level,BlockPos trader) {
        BlockPos column=trader.offset(8,0,8); if(!level.hasChunkAt(column)) return null;
        BlockPos ground=new BlockPos(column.getX(),level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES,column.getX(),column.getZ()),column.getZ());
        return level.getFluidState(ground.below()).isEmpty() ? ground : null;
    }
    @SubscribeEvent public void died(LivingDeathEvent event) {
        if(!(event.getEntity().level() instanceof ServerLevel level)) return;
        removed(level,event.getEntity().getUUID());
    }
    @SubscribeEvent public void left(EntityLeaveLevelEvent event) {
        // Peaceful-mode removal is final; a chunk unload must retain its saved defender and budget slot.
        if(event.getLevel() instanceof ServerLevel level && event.getEntity().getRemovalReason()==Entity.RemovalReason.DISCARDED)
            removed(level,event.getEntity().getUUID());
    }
    private static void removed(ServerLevel level,UUID defender) {
        var data=ExpeditionData.get(level);
        for(var site:data.sites) if(site.guards.remove(defender)) {
            if(site.spawned && site.guards.isEmpty()) {
                site.cleared=true; site.ambush=null;
                for(Settlement town:SettlementData.get(level).settlements) if(!town.trading.npc && town.center.distSqr(site.pos)<4096.0*4096.0)
                    CampaignService.record(level,town,"The "+site.title()+" at "+site.pos.toShortString()+" is cleared. Recover its supplies or claim an outpost with a Frontier Charter.");
            }
            data.setDirty(); break;
        }
    }
}
