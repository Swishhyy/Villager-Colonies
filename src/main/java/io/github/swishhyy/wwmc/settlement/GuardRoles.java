package io.github.swishhyy.wwmc.settlement;

import io.github.swishhyy.wwmc.core.StructureRole;
import io.github.swishhyy.wwmc.entity.CitizenEntity;
import io.github.swishhyy.wwmc.menu.Panels;
import java.util.*;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.levelgen.Heightmap;

/**
 * Guard roles and watchtowers. A Guard Station raised at least six blocks above the ground around it is a watchtower:
 * its guard sees hostiles from 48 blocks, and warns the town when they approach from outside the claim.
 */
public final class GuardRoles {
    public static final int TOWER_HEIGHT=6,TOWER_SIGHT=48,SIGNAL_SIGHT=64;
    private static final int WARNING_TICKS=2400,SIGNAL_WARNING_TICKS=1200;
    private static final Map<UUID,Long> WARNED=new HashMap<>();
    private GuardRoles() {}
    public static String role(ServerLevel level,BlockPos station) { return GuardService.posts(level,new Station(station,StructureRole.GUARD)).role(); }
    /** A station at least six blocks above the lowest ground eight blocks to each side. */
    public static boolean watchtower(ServerLevel level,BlockPos station) {
        int ground=Integer.MAX_VALUE;
        for(int[] offset:new int[][]{{8,0},{-8,0},{0,8},{0,-8}}) {
            BlockPos column=station.offset(offset[0],0,offset[1]);
            if(!level.hasChunkAt(column)) return false;
            ground=Math.min(ground,level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES,column.getX(),column.getZ()));
        }
        return station.getY()-ground>=TOWER_HEIGHT;
    }
    /** How far a watchtower of this town sees: farther with Signal Fires. */
    public static int towerSight(Settlement town) { return Research.has(town,"signal_fires") ? SIGNAL_SIGHT : TOWER_SIGHT; }
    /** Damage, in percent, a guard's role turns aside. */
    public static int protection(CitizenEntity guard) {
        if(!(guard.level() instanceof ServerLevel level)) return 0;
        Settlement town=guard.town(level);
        BlockPos station=town==null ? null : town.jobs.home(guard.getUUID());
        if(station==null || !GuardPosts.SHIELD.equals(role(level,station))) return 0;
        return guard.getOffhandItem().is(Items.SHIELD) ? 25 : 15;
    }
    /** Guards in watchtowers report hostiles closing on the claim from outside it, at most every two minutes. */
    public static void lookout(ServerLevel level,Settlement town,List<CitizenEntity> citizens) {
        long now=level.getGameTime();
        if(WARNED.getOrDefault(town.id,Long.MIN_VALUE)>now) return;
        for(CitizenEntity guard:citizens) {
            if(!guard.isGuard()) continue;
            BlockPos station=town.jobs.home(guard.getUUID());
            if(station==null || !watchtower(level,station)) continue;
            int sight=towerSight(town);
            List<Monster> seen=level.getEntitiesOfClass(Monster.class,guard.getBoundingBox().inflate(sight),
                    m -> m.isAlive() && !town.contains(m.blockPosition()) && guard.distanceToSqr(m)<=sight*sight && guard.hasLineOfSight(m));
            if(seen.isEmpty()) continue;
            Monster first=seen.stream().min(Comparator.comparingDouble(guard::distanceToSqr)).get();
            String text="Watchtower lookout "+guard.getName().getString()+" spotted "+seen.size()+(seen.size()==1 ? " hostile" : " hostiles")
                    +" approaching "+town.name+": "+Panels.directions(station,first.blockPosition())+" of the tower.";
            WARNED.put(town.id,now+(Research.has(town,"signal_fires") ? SIGNAL_WARNING_TICKS : WARNING_TICKS));
            CampaignService.record(level,town,text);
            for(ServerPlayer player:level.players()) if(TownAccess.manages(town,player.getUUID()) && town.contains(player.blockPosition()))
                SettlementService.notify(player,text);
            return;
        }
    }
    public static void forget() { WARNED.clear(); }
}
