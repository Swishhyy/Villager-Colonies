package io.github.swishhyy.wwmc.settlement;

import io.github.swishhyy.wwmc.WWMC;
import io.github.swishhyy.wwmc.block.SettlementBannerBlock;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.level.block.BannerBlock;
import net.minecraft.world.level.levelgen.Heightmap;

/** Four physical corner flags. Only loaded terrain is inspected; existing buildings are never replaced. */
public final class TownBorders {
    private static final int[] PALETTE={11,14,13,10,1,9,5,2,4,3};
    private TownBorders() {}
    public static DyeColor color(Settlement town) {
        return DyeColor.byId(town.progress.color<0 ? PALETTE[Math.floorMod(town.id.hashCode(),PALETTE.length)] : town.progress.color);
    }
    public static String title(Settlement town) { return color(town).getName().replace('_',' '); }
    public static List<BlockPos> corners(Settlement town) {
        return List.of(town.center.offset(-town.radius,0,-town.radius),town.center.offset(town.radius,0,-town.radius),
                town.center.offset(-town.radius,0,town.radius),town.center.offset(town.radius,0,town.radius));
    }
    public static void update(ServerLevel level,Settlement town) {
        DyeColor color=color(town);
        if(level.hasChunkAt(town.center)) {
            var flag=level.getBlockState(town.center);
            if(flag.is(WWMC.BANNER.get()) && flag.getValue(SettlementBannerBlock.COLOR)!=color)
                level.setBlockAndUpdate(town.center,flag.setValue(SettlementBannerBlock.COLOR,color));
        }
        var banner=BuiltInRegistries.BLOCK.getValue(Identifier.withDefaultNamespace(color.getName()+"_banner")).defaultBlockState();
        for(BlockPos corner:corners(town)) {
            if(!level.hasChunkAt(corner)) continue;
            BlockPos saved=town.borderBanners.stream().filter(p -> p.getX()==corner.getX() && p.getZ()==corner.getZ()).findFirst().orElse(null);
            if(saved!=null) {
                var state=level.getBlockState(saved);
                if(state.getBlock() instanceof BannerBlock) {
                    if(state.getBlock()!=banner.getBlock()) level.setBlockAndUpdate(saved,banner.setValue(BannerBlock.ROTATION,state.getValue(BannerBlock.ROTATION)));
                    continue;
                }
                town.borderBanners.remove(saved); SettlementData.get(level).setDirty();
            }
            int y=level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES,corner.getX(),corner.getZ());
            BlockPos pos=new BlockPos(corner.getX(),y,corner.getZ());
            if(y>=level.getMaxY() || !level.getBlockState(pos).isAir() || !level.getFluidState(pos).isEmpty() || !banner.canSurvive(level,pos)) continue;
            if(level.setBlock(pos,banner,3)) { town.borderBanners.add(pos); SettlementData.get(level).setDirty(); }
        }
    }
    public static String choose(ServerLevel level,Settlement town,java.util.UUID player,int color) {
        if(!TownAccess.owner(town,player) || color<0 || color>15) return "Only the town owner can choose its color.";
        town.progress.color=color; SettlementData.get(level).setDirty(); update(level,town);
        return "Town color: "+title(town)+". Unloaded corner flags update when their terrain loads.";
    }
}
