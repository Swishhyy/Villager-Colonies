package io.github.swishhyy.wwmc.settlement;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.function.Predicate;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.common.Tags;

/** Work visible, reachable cave ore near a miner's current tunnel depth without loading terrain. */
public final class CaveMining {
    private CaveMining() {}
    public static boolean ore(BlockState state) { return state.is(Tags.Blocks.ORES); }
    public static BlockPos find(ServerLevel level,Settlement town,BlockPos miner,Predicate<BlockPos> accessible) {
        var candidates=new ArrayList<BlockPos>();
        for(BlockPos pos:BlockPos.betweenClosed(miner.offset(-8,0,-8),miner.offset(8,4,8))) {
            if(!town.contains(pos) || !level.hasChunkAt(pos) || !ore(level.getBlockState(pos))
                    || !ExcavationService.safeBlock(level,town,pos)) continue;
            boolean exposed=false;
            for(Direction face:Direction.values()) {
                BlockPos neighbor=pos.relative(face);
                if(level.hasChunkAt(neighbor) && level.getBlockState(neighbor).getCollisionShape(level,neighbor).isEmpty()) { exposed=true; break; }
            }
            if(exposed) candidates.add(pos.immutable());
        }
        candidates.sort(Comparator.comparingDouble(miner::distSqr));
        return candidates.stream().filter(accessible).findFirst().orElse(null);
    }
}
