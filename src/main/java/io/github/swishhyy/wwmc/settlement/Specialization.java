package io.github.swishhyy.wwmc.settlement;

import io.github.swishhyy.wwmc.core.StructureRole;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.level.levelgen.Heightmap;

/** Industry choices help the matching job without removing any town's ability to survive independently. */
public final class Specialization {
    public static final List<String> NAMES=List.of("balanced","farming","fishing","timber","mining");
    private Specialization() {}
    public static boolean matches(String specialty,StructureRole job) {
        return switch(specialty) { case "farming" -> job==StructureRole.FARM; case "fishing" -> job==StructureRole.FISHERMAN;
            case "timber" -> job==StructureRole.LUMBER; case "mining" -> job.excavates(); default -> false; };
    }
    public static boolean terrain(ServerLevel level,Settlement town,String specialty) {
        if(specialty.equals("mining") && town.center.getY()>=95) return true;
        int found=0;
        for(int x=-24;x<=24;x+=12) for(int z=-24;z<=24;z+=12) {
            BlockPos probe=town.center.offset(x,0,z); if(!level.hasChunkAt(probe)) continue;
            int y=level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES,probe.getX(),probe.getZ());
            var ground=level.getBlockState(new BlockPos(probe.getX(),y-1,probe.getZ()));
            if(specialty.equals("fishing") && !ground.getFluidState().isEmpty()
                    || specialty.equals("farming") && ground.is(BlockTags.DIRT)
                    || specialty.equals("timber") && level.getHeight(Heightmap.Types.WORLD_SURFACE,probe.getX(),probe.getZ())>y+2
                    || specialty.equals("mining") && (ground.is(BlockTags.BASE_STONE_OVERWORLD))) found++;
        }
        return found>=3;
    }
    public static int ticks(Settlement town,StructureRole role,int base) {
        return matches(town.campaign.specialty,role) ? Math.max(10,base*100/(town.campaign.projects.contains("terrain_bonus") ? 125 : 110)) : base;
    }
}
