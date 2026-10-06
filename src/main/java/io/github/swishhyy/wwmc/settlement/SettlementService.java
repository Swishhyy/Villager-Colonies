package io.github.swishhyy.wwmc.settlement;

import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import io.github.swishhyy.wwmc.Config;
import io.github.swishhyy.wwmc.WWMC;
import io.github.swishhyy.wwmc.block.StationBlock;
import io.github.swishhyy.wwmc.core.ReservationBook;
import io.github.swishhyy.wwmc.core.WorkforceBook;
import io.github.swishhyy.wwmc.core.MiningLayout;
import io.github.swishhyy.wwmc.core.CitizenNames;
import io.github.swishhyy.wwmc.core.RoomBounds;
import io.github.swishhyy.wwmc.core.ShiftClock;
import io.github.swishhyy.wwmc.core.StructureRole;
import io.github.swishhyy.wwmc.entity.CitizenEntity;
import java.util.*;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Container;
import net.minecraft.world.clock.WorldClocks;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BedBlock;
import net.minecraft.world.level.block.state.properties.BedPart;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import net.neoforged.neoforge.event.level.block.BreakBlockEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import net.neoforged.neoforge.event.tick.LevelTickEvent;

public final class SettlementService {
    private static final Map<ServerLevel,ReservationBook<BlockPos>> RESERVATIONS=new WeakHashMap<>();
    private static final Map<ServerLevel,WorkforceBook<BlockPos>> WORKFORCE=new WeakHashMap<>();
    public static WorkforceBook<BlockPos> workers(ServerLevel level) { return WORKFORCE.computeIfAbsent(level,l -> new WorkforceBook<>()); }
    public static int workerLimit(Station station) { return station.role()==StructureRole.QUARRY ? Config.QUARRY_WORKERS.get() : station.role()==StructureRole.GUARD ? Config.GUARD_WORKERS.get() : Config.STATION_WORKERS.get(); }
    public static ReservationBook<BlockPos> reservations(ServerLevel level) {
        return RESERVATIONS.computeIfAbsent(level, l -> new ReservationBook<>());
    }
    public static void tell(Player player,String text) {
        if(player instanceof ServerPlayer serverPlayer) serverPlayer.sendSystemMessage(Component.literal(text));
    }
    public static boolean owns(Player player,Settlement settlement) { return settlement!=null && settlement.owner.equals(player.getUUID()); }
    public static boolean night(ServerLevel level) {
        return ShiftClock.night(level.clockManager().getTotalTicks(level.registryAccess().getOrThrow(WorldClocks.OVERWORLD)));
    }

    public static void foundOrInspect(ServerLevel level,Player player,BlockPos pos) {
        if(!level.dimension().equals(Level.OVERWORLD)) { tell(player,"Settlements currently belong in the Overworld."); return; }
        SettlementData data=SettlementData.get(level);
        Settlement present=data.at(pos);
        if(present!=null) { tell(player,status(level,present)); return; }
        int radius=Config.SETTLEMENT_RADIUS.get();
        if(data.settlements.stream().anyMatch(s -> s.overlaps(pos,radius))) { tell(player,"Move your banner farther away: settlement claims cannot overlap."); return; }
        if(data.settlements.stream().anyMatch(s -> s.owner.equals(player.getUUID()))) { tell(player,"You already own a settlement. Multiple towns are planned for a later build."); return; }
        Settlement settlement=new Settlement(UUID.randomUUID(),player.getUUID(),player.getName().getString()+"'s settlement",pos,radius,List.of(),List.of(),"balanced");
        data.settlements.add(settlement); data.setDirty();
        placeBorders(level,settlement);
        tell(player,"Founded "+settlement.name+". Place housing, warehouse, and work stations inside the "+radius+"-block claim.");
    }
    public static void registerStation(ServerLevel level,Player player,BlockPos pos,StructureRole role) {
        SettlementData data=SettlementData.get(level);
        Settlement settlement=data.at(pos);
        if(!owns(player,settlement)) { tell(player,"Place stations inside your own settlement claim."); return; }
        if(settlement.station(pos)==null) {
            var state=level.getBlockState(pos);
            Station station=new Station(pos,role,state.getValue(StationBlock.FACING));
            if(role==StructureRole.QUARRY) {
                var bounds=MiningLayout.quarry(pos.getX(),pos.getZ(),station.facing().getStepX(),station.facing().getStepZ(),pos.getY(),pos.getY());
                if(!settlement.contains(new BlockPos(bounds.minX(),pos.getY(),bounds.minZ())) || !settlement.contains(new BlockPos(bounds.maxX(),pos.getY(),bounds.maxZ()))) {
                    tell(player,"The full chunk in front of this quarry must fit inside your town claim. Move or turn the station."); return;
                }
            }
            settlement.stations.add(station); data.setDirty();
            tell(player,"Registered "+role.id()+" station."+(role.excavates() ? " Excavation plans stay inside your claim." : " Nearby blocks are detected automatically in its 7x7x7 range."));
            inspectStation(level,player,pos);
        }
    }
    public static boolean active(ServerLevel level,Station station) {
        return level.hasChunkAt(station.position()) && level.getBlockState(station.position()).getBlock() instanceof StationBlock block && block.role()==station.role();
    }
    private static boolean availableCell(ServerLevel level,Settlement town,BlockPos pos) {
        return pos.getY()>=level.getMinY() && pos.getY()<level.getMaxY() && town.contains(pos) && level.hasChunkAt(pos);
    }
    private static boolean knownStation(ServerLevel level,Station station) {
        // Retain ownership across chunk boundaries; never load an absent chunk just to scan it.
        return !level.hasChunkAt(station.position()) || active(level,station);
    }
    public static Iterable<BlockPos> cells(Station station) {
        RoomBounds r=station.area();
        return BlockPos.betweenClosed(r.minX(),r.minY(),r.minZ(),r.maxX(),r.maxY(),r.maxZ());
    }
    public static boolean ownsBlock(ServerLevel level,Settlement town,Station station,BlockPos pos) {
        Station owner=town.nearestStation(pos,s -> s.role()==station.role() && knownStation(level,s));
        return station.equals(owner);
    }
    public static List<BlockPos> beds(ServerLevel level,Settlement town,Station station) {
        List<BlockPos> result=new ArrayList<>();
        if(!station.role().detectsBeds() || !active(level,station)) return result;
        for(BlockPos pos:cells(station)) {
            if(!availableCell(level,town,pos)) continue;
            var head=level.getBlockState(pos);
            if(!(head.getBlock() instanceof BedBlock) || head.getValue(BedBlock.PART)!=BedPart.HEAD) continue;
            BlockPos foot=pos.relative(head.getValue(BedBlock.FACING).getOpposite());
            if(!availableCell(level,town,foot) || !station.contains(foot)
                    || !StationDetection.completeBed(head,level.getBlockState(foot))) continue;
            // Hospital, barracks, and housing compete for the whole bed, rather than counting each half.
            Station owner=town.nearestStation(pos,s -> s.role().detectsBeds() && s.contains(foot) && knownStation(level,s));
            if(station.equals(owner)) result.add(pos.immutable());
        }
        return result;
    }
    public static List<BlockPos> housingBeds(ServerLevel level,Settlement settlement) {
        Set<BlockPos> result=new LinkedHashSet<>();
        for(Station station:settlement.stations) if(station.role().providesHousing()) result.addAll(beds(level,settlement,station));
        return new ArrayList<>(result);
    }
    private static int workBlocks(ServerLevel level,Settlement town,Station station) {
        int count=0;
        for(BlockPos pos:cells(station)) if(availableCell(level,town,pos) && !protectedFurniture(town,pos)
                && StationDetection.workBlock(station.role(),level.getBlockState(pos)) && ownsBlock(level,town,station,pos)) count++;
        return count;
    }
    public static void inspectStation(ServerLevel level,Player player,BlockPos pos) {
        Settlement town=SettlementData.get(level).at(pos);
        if(town==null || town.station(pos)==null) return;
        Station station=town.station(pos);
        String found=switch(station.role()) {
            case HOUSING,BARRACKS -> beds(level,town,station).size()+" housing beds";
            case HOSPITAL -> beds(level,town,station).size()+" patient beds (medical treatment is planned)";
            case WAREHOUSE -> storageAt(level,town,pos).size()+" chest/barrel storage blocks";
            case FARM -> workBlocks(level,town,station)+" mature crops";
            case LUMBER -> "natural trees + sapling planting sites";
            case MINE,QUARRY -> ExcavationService.status(level,town,station);
            case GUARD -> GuardService.status(level,station);
        };
        tell(player,station.role().id()+" station: "+found+(station.role().excavates() ? ". Facing "+station.facing().name().toLowerCase(Locale.ROOT)+"." : " in its 7x7x7 range.")+
                (station.role().providesWork() ? " Crew: "+workers(level).count(pos,level.getGameTime())+"/"+workerLimit(station)+"." : "")+
                " Only loaded blocks inside the claim count.");
    }
    public static List<Container> storage(ServerLevel level,Settlement settlement) {
        return storageAt(level,settlement,null);
    }
    public static List<Container> storageAt(ServerLevel level,Settlement settlement,BlockPos selectedWarehouse) {
        Set<BlockPos> positions=new LinkedHashSet<>();
        for(Station station:settlement.stations) {
            if(station.role()!=StructureRole.WAREHOUSE || !active(level,station)) continue;
            if(selectedWarehouse!=null && !station.position().equals(selectedWarehouse)) continue;
            for(BlockPos pos:cells(station)) {
                if(availableCell(level,settlement,pos) && StationDetection.storageBlock(level.getBlockState(pos))
                        && ownsBlock(level,settlement,station,pos)) positions.add(pos.immutable());
            }
        }
        List<Container> containers=new ArrayList<>();
        // Each chest half contributes its actual block inventory once, including double chests.
        for(BlockPos pos:positions) if(level.getBlockEntity(pos) instanceof Container container) containers.add(container);
        return containers;
    }
    public static BlockPos warehouse(ServerLevel level,Settlement settlement,BlockPos from) {
        return settlement.stations.stream().filter(s -> s.role()==StructureRole.WAREHOUSE && active(level,s))
                .filter(s -> !storageAt(level,settlement,s.position()).isEmpty())
                .min(Comparator.comparingDouble(s -> s.position().distSqr(from))).map(Station::position).orElse(null);
    }
    public static boolean protectedFurniture(Settlement settlement,BlockPos pos) {
        return settlement.center.equals(pos) || settlement.borderBanners.contains(pos) || settlement.stations.stream().anyMatch(s -> s.position().equals(pos) ||
                (!s.role().providesWork() && s.contains(pos)));
    }
    private static void placeBorders(ServerLevel level,Settlement town) {
        var banner=BuiltInRegistries.BLOCK.getValue(Identifier.fromNamespaceAndPath("minecraft","red_banner")).defaultBlockState();
        for(int x:new int[]{-town.radius,town.radius}) for(int z:new int[]{-town.radius,town.radius}) {
            int cornerX=town.center.getX()+x,cornerZ=town.center.getZ()+z;
            if(town.borderBanners.stream().anyMatch(p -> p.getX()==cornerX && p.getZ()==cornerZ)) continue;
            BlockPos chunkCheck=new BlockPos(cornerX,town.center.getY(),cornerZ);
            if(!level.hasChunkAt(chunkCheck)) continue;
            int y=level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES,cornerX,cornerZ);
            BlockPos pos=new BlockPos(cornerX,y,cornerZ);
            if(y>=level.getMaxY() || !level.getBlockState(pos).isAir() || !level.getFluidState(pos).isEmpty() || !banner.canSurvive(level,pos)) continue;
            if(level.setBlock(pos,banner,3)) { town.borderBanners.add(pos); SettlementData.get(level).setDirty(); }
        }
    }
    public static String citizenName(ServerLevel level,Settlement town,UUID citizen) {
        String saved=town.citizenNames.get(citizen);
        if(saved!=null) return saved;
        List<String> used=new ArrayList<>(town.citizenNames.values());
        for(UUID id:town.citizens) {
            var entity=level.getEntity(id);
            if(entity!=null && entity.getCustomName()!=null && !id.equals(citizen)) used.add(entity.getCustomName().getString());
        }
        String name=CitizenNames.choose(citizen,used);
        town.citizenNames.put(citizen,name); SettlementData.get(level).setDirty(); return name;
    }
    public static String status(ServerLevel level,Settlement settlement) {
        return settlement.name+": "+settlement.citizens.size()+" citizens / "+housingBeds(level,settlement).size()+
                " loaded housing beds, "+settlement.stations.size()+" stations, priority: "+settlement.priority+". Claim radius: "+settlement.radius+
                ". Defense: "+DefenseService.status(settlement)+"; "+WaveService.status(level,settlement)+".";
    }
    private static Settlement owned(CommandSourceStack source) throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        ServerPlayer player=source.getPlayerOrException();
        return SettlementData.get(source.getLevel()).settlements.stream().filter(s -> s.owner.equals(player.getUUID())).findFirst().orElse(null);
    }
    private static int recruit(CommandSourceStack source,int count) throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        Settlement settlement=owned(source);
        if(settlement==null) { source.sendFailure(Component.literal("Right-click a settlement banner to found a town first.")); return 0; }
        ServerLevel level=source.getLevel();
        List<BlockPos> beds=housingBeds(level,settlement);
        int limit=Math.min(beds.size(),Config.MAX_CITIZENS.get());
        int added=0;
        for(int i=0;i<count && settlement.citizens.size()<limit;i++) {
            CitizenEntity citizen=WWMC.CITIZEN.get().create(level,EntitySpawnReason.COMMAND);
            if(citizen==null) break;
            BlockPos spawn=null;
            for(int x=-3;x<=3 && spawn==null;x++) for(int z=-3;z<=3;z++) {
                BlockPos trial=settlement.center.offset(x,0,z);
                if(!level.hasChunkAt(trial)) continue;
                citizen.setPos(trial.getX()+0.5,trial.getY(),trial.getZ()+0.5);
                if(level.noCollision(citizen) && !level.getBlockState(trial.below()).isAir()) { spawn=trial; break; }
            }
            if(spawn==null) break;
            citizen.join(settlement.id);
            citizen.setCustomName(Component.literal(citizenName(level,settlement,citizen.getUUID())));
            if(level.addFreshEntity(citizen)) { settlement.citizens.add(citizen.getUUID()); added++; }
        }
        SettlementData.get(level).setDirty();
        final int result=added;
        source.sendSuccess(() -> Component.literal("Recruited "+result+" citizens. "+status(level,settlement)+" Supply food and tools in the warehouse."),false);
        return added;
    }
    @SubscribeEvent public void commands(RegisterCommandsEvent event) {
        event.getDispatcher().register(Commands.literal("wwmc")
            .then(Commands.literal("status").executes(c -> {
                Settlement s=owned(c.getSource());
                if(s==null) { c.getSource().sendFailure(Component.literal("You do not own a settlement here.")); return 0; }
                c.getSource().sendSuccess(() -> Component.literal(status(c.getSource().getLevel(),s)),false); return 1;
            }))
            .then(Commands.literal("recruit").executes(c -> recruit(c.getSource(),1))
                .then(Commands.argument("count",IntegerArgumentType.integer(1,8)).executes(c -> recruit(c.getSource(),IntegerArgumentType.getInteger(c,"count")))))
            .then(Commands.literal("name").then(Commands.argument("name",StringArgumentType.greedyString()).executes(c -> {
                Settlement s=owned(c.getSource()); if(s==null) return 0;
                String name=StringArgumentType.getString(c,"name").strip();
                if(name.isEmpty() || name.length()>48) { c.getSource().sendFailure(Component.literal("Use a town name of 1 to 48 characters.")); return 0; }
                s.name=name; SettlementData.get(c.getSource().getLevel()).setDirty(); return 1;
            })))
            .then(Commands.literal("priority").then(Commands.argument("priority",StringArgumentType.word())
                .suggests((c,b) -> { for(String p:List.of("balanced","food","materials")) b.suggest(p); return b.buildFuture(); })
                .executes(c -> {
                    Settlement s=owned(c.getSource()); if(s==null) return 0;
                    String p=StringArgumentType.getString(c,"priority");
                    if(!List.of("balanced","food","materials").contains(p)) { c.getSource().sendFailure(Component.literal("Choose balanced, food, or materials.")); return 0; }
                    s.priority=p; SettlementData.get(c.getSource().getLevel()).setDirty();
                    c.getSource().sendSuccess(() -> Component.literal("Town priority set to "+p+". Idle workers will prefer those stations."),false); return 1;
                })))
            .then(Commands.literal("alarm").executes(c -> {
                Settlement s=owned(c.getSource());
                if(s==null) { c.getSource().sendFailure(Component.literal("You do not own a settlement here.")); return 0; }
                DefenseService.toggle(c.getSource().getLevel(),s); return 1;
            }))
            .then(Commands.literal("wave").executes(c -> {
                Settlement s=owned(c.getSource());
                if(s==null) { c.getSource().sendFailure(Component.literal("You do not own a settlement here.")); return 0; }
                int spawned=WaveService.callNow(c.getSource().getLevel(),s);
                if(spawned==0) c.getSource().sendFailure(Component.literal("No wave could gather: it needs loaded open ground 40-64 blocks from the banner, away from stations and from you, and a difficulty above peaceful."));
                return spawned;
            })));
    }
    @SubscribeEvent public void breakStation(BreakBlockEvent event) {
        if(!(event.getLevel() instanceof ServerLevel level)) return;
        SettlementData data=SettlementData.get(level);
        Settlement settlement=data.at(event.getPos());
        if(settlement==null) return;
        boolean banner=settlement.center.equals(event.getPos()) && event.getState().is(WWMC.BANNER.get());
        boolean station=event.getState().getBlock() instanceof StationBlock;
        if(!banner && !station) return;
        if(!owns(event.getPlayer(),settlement)) { event.setCanceled(true); event.setNotifyClient(true); tell(event.getPlayer(),"Only this town's owner can remove its stations."); return; }
        if(banner && !settlement.citizens.isEmpty()) {
            event.setCanceled(true); event.setNotifyClient(true); tell(event.getPlayer(),"This banner belongs to an occupied settlement. Keep it as your town's rally point."); return;
        }
        // Reconcile after the block is actually gone; another event listener may still cancel the break.
    }
    @SubscribeEvent public void tick(LevelTickEvent.Post event) {
        if(!(event.getLevel() instanceof ServerLevel level) || level.getGameTime()%200!=0) return;
        reservations(level).prune(level.getGameTime());
        workers(level).prune(level.getGameTime());
        SettlementData data=SettlementData.get(level);
        if(data.settlements.removeIf(s -> s.citizens.isEmpty() && level.hasChunkAt(s.center) && !level.getBlockState(s.center).is(WWMC.BANNER.get()))) data.setDirty();
        for(Settlement s:data.settlements) {
            if(s.widenTo(Settlement.MIN_RADIUS,data.settlements)) {
                // Old corner banners are no longer the border; they stay in the world as ordinary blocks.
                s.borderBanners.clear(); data.setDirty();
                ServerPlayer owner=level.getServer().getPlayerList().getPlayer(s.owner);
                if(owner!=null) tell(owner,s.name+"'s claim now extends "+s.radius+" blocks from its banner.");
            }
            placeBorders(level,s);
            if(s.stations.removeIf(station -> level.hasChunkAt(station.position()) && !active(level,station))) data.setDirty();
        }
    }
    @SubscribeEvent public void stopped(ServerStoppedEvent event) {
        RESERVATIONS.keySet().removeIf(level -> level.getServer()==event.getServer());
        WORKFORCE.keySet().removeIf(level -> level.getServer()==event.getServer());
    }
}
