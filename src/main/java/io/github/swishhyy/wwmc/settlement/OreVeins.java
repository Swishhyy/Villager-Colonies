package io.github.swishhyy.wwmc.settlement;

import io.github.swishhyy.wwmc.Config;
import io.github.swishhyy.wwmc.core.StructureRole;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.function.Function;
import java.util.function.Predicate;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.EmptyBlockGetter;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.common.Tags;

/**
 * A mine station placed near an exposed ore turns that ore into an endless vein: its miner collects the ore's normal drops
 * over and over without removing the block. Players mining it break it as usual. Rarer ores replenish more slowly.
 */
public final class OreVeins {
    /** How many blocks from its station, along each axis, a vein may be. */
    public static final int REACH=2;
    private OreVeins() {}
    /**
     * The exposed ore within {@link #REACH} blocks of a mine station along each axis, nearest first. Ore buried on every
     * side does not count, so an older mine next to hidden ore keeps digging its tunnels.
     */
    public static BlockPos find(ServerLevel level,Settlement town,Station station) { return find(level::hasChunkAt,level::getBlockState,town,station); }
    public static BlockPos find(Predicate<BlockPos> loaded,Function<BlockPos,BlockState> blocks,Settlement town,Station station) {
        return candidates(loaded,blocks,town,station).stream().findFirst().orElse(null);
    }
    /** Every exposed ore in the mine's bounded vein area, ordered consistently by distance. */
    public static List<BlockPos> candidates(Predicate<BlockPos> loaded,Function<BlockPos,BlockState> blocks,Settlement town,Station station) {
        if(station.role()!=StructureRole.MINE) return List.of();
        BlockPos center=station.position(); int reach=REACH+station.range();
        List<BlockPos> found=new ArrayList<>();
        for(BlockPos pos:BlockPos.betweenClosed(center.offset(-reach,-reach,-reach),center.offset(reach,reach,reach))) {
            if(pos.equals(center) || !town.contains(pos) || !loaded.test(pos) || !CaveMining.ore(blocks.apply(pos)) || !exposed(loaded,blocks,pos)) continue;
            found.add(pos.immutable());
        }
        found.sort(Comparator.comparingDouble(p -> p.distSqr(center)));
        return List.copyOf(found);
    }
    public static BlockPos ready(ServerLevel level,Settlement town,Station station,Predicate<BlockPos> usable) {
        return candidates(level::hasChunkAt,level::getBlockState,town,station).stream()
                .filter(p -> readyAt(level,p)<=level.getGameTime() && usable.test(p)).findFirst().orElse(null);
    }
    public static boolean valid(ServerLevel level,Settlement town,Station station,BlockPos pos) {
        int reach=REACH+station.range(); BlockPos center=station.position();
        return station.role()==StructureRole.MINE && !pos.equals(center) && town.contains(pos) && level.hasChunkAt(pos)
                && Math.abs(pos.getX()-center.getX())<=reach && Math.abs(pos.getY()-center.getY())<=reach && Math.abs(pos.getZ()-center.getZ())<=reach
                && CaveMining.ore(level.getBlockState(pos)) && exposed(level::hasChunkAt,level::getBlockState,pos);
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
    /** Stone is the baseline. Square-root scaling and a cap keep rare ores and faster modded tools bounded. */
    public static int interval(BlockState ore,int seconds,ItemStack pickaxe) {
        double speed=pickaxe.isEmpty() ? 4 : Math.max(1,pickaxe.getDestroySpeed(ore));
        double boost=Math.clamp(Math.sqrt(speed/4.0),0.75,1.5);
        return (int)Math.ceil(interval(ore,seconds)/boost);
    }
    public static long readyAt(ServerLevel level,BlockPos vein) {
        return WorldWorkData.get(level).veins.getOrDefault(vein,0L);
    }
    public static void worked(ServerLevel level,BlockPos vein,BlockState ore,ItemStack pickaxe) { worked(level,vein,ore,pickaxe,false); }
    /** Deep Mining research makes the vein ready a quarter sooner. */
    public static void worked(ServerLevel level,BlockPos vein,BlockState ore,ItemStack pickaxe,boolean deep) {
        WorldWorkData data=WorldWorkData.get(level);
        data.veins.values().removeIf(time -> time<=level.getGameTime());
        int wait=interval(ore,Config.ORE_VEIN_SECONDS.get(),pickaxe);
        data.veins.put(vein.immutable(),level.getGameTime()+(deep ? wait*3/4 : wait)); data.setDirty();
    }
    /** "Iron Ore" for a vein's block. */
    public static String name(BlockState ore) { return ore.getBlock().getName().getString(); }
}
