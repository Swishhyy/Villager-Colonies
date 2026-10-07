package io.github.swishhyy.wwmc.settlement;

import io.github.swishhyy.wwmc.Config;
import io.github.swishhyy.wwmc.core.StructureRole;
import java.util.HashMap;
import java.util.Map;
import java.util.WeakHashMap;
import java.util.function.Function;
import java.util.function.Predicate;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.EmptyBlockGetter;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.common.Tags;

/**
 * A mine station placed near an exposed ore turns that ore into an endless vein: its miner collects the ore's normal drops
 * over and over without removing the block. Players mining it break it as usual. Rarer ores replenish more slowly.
 */
public final class OreVeins {
    /** Game time each vein can yield again; a restart simply lets every vein yield once more. */
    private static final Map<ServerLevel,Map<BlockPos,Long>> READY=new WeakHashMap<>();
    /** How many blocks from its station, along each axis, a vein may be. */
    public static final int REACH=2;
    private OreVeins() {}
    /**
     * The exposed ore within {@link #REACH} blocks of a mine station along each axis, nearest first. Ore buried on every
     * side does not count, so an older mine next to hidden ore keeps digging its tunnels.
     */
    public static BlockPos find(ServerLevel level,Settlement town,Station station) { return find(level::hasChunkAt,level::getBlockState,town,station); }
    public static BlockPos find(Predicate<BlockPos> loaded,Function<BlockPos,BlockState> blocks,Settlement town,Station station) {
        if(station.role()!=StructureRole.MINE) return null;
        BlockPos center=station.position(),best=null;
        for(BlockPos pos:BlockPos.betweenClosed(center.offset(-REACH,-REACH,-REACH),center.offset(REACH,REACH,REACH))) {
            if(pos.equals(center) || !town.contains(pos) || !loaded.test(pos) || !CaveMining.ore(blocks.apply(pos)) || !exposed(loaded,blocks,pos)) continue;
            if(best==null || pos.distSqr(center)<best.distSqr(center)) best=pos.immutable();
        }
        return best;
    }
    /** At least one side opens onto a block with no collision, such as air, a torch or water. */
    private static boolean exposed(Predicate<BlockPos> loaded,Function<BlockPos,BlockState> blocks,BlockPos ore) {
        for(Direction side:Direction.values()) {
            BlockPos next=ore.relative(side);
            if(loaded.test(next) && blocks.apply(next).getCollisionShape(EmptyBlockGetter.INSTANCE,next).isEmpty()) return true;
        }
        return false;
    }
    /** How many times longer than common ores a vein takes to replenish. */
    public static int rarity(BlockState ore) {
        if(ore.is(Tags.Blocks.ORES_NETHERITE_SCRAP)) return 8;
        if(ore.is(Tags.Blocks.ORES_DIAMOND) || ore.is(Tags.Blocks.ORES_EMERALD)) return 6;
        return ore.is(Tags.Blocks.ORES_GOLD) ? 2 : 1;
    }
    public static int interval(BlockState ore,int seconds) { return seconds*20*rarity(ore); }
    public static long readyAt(ServerLevel level,BlockPos vein) {
        Map<BlockPos,Long> veins=READY.get(level);
        return veins==null ? 0 : veins.getOrDefault(vein,0L);
    }
    public static void worked(ServerLevel level,BlockPos vein,BlockState ore) {
        Map<BlockPos,Long> veins=READY.computeIfAbsent(level,l -> new HashMap<>());
        veins.values().removeIf(time -> time<=level.getGameTime());
        veins.put(vein.immutable(),level.getGameTime()+interval(ore,Config.ORE_VEIN_SECONDS.get()));
    }
    /** "Iron Ore" for a vein's block. */
    public static String name(BlockState ore) { return ore.getBlock().getName().getString(); }
}
