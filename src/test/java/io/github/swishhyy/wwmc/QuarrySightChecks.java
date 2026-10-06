package io.github.swishhyy.wwmc;

import io.github.swishhyy.wwmc.settlement.ExcavationService;
import java.util.HashMap;
import java.util.Map;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import net.neoforged.testframework.junit.EphemeralTestServerProvider;
import static org.junit.jupiter.api.Assertions.*;

public final class QuarrySightChecks {
    /** A small block view exercises Minecraft's actual collision tracing without modifying a server world. */
    private static final class Terrain implements BlockGetter {
        final Map<BlockPos,BlockState> blocks=new HashMap<>();
        @Override public BlockEntity getBlockEntity(BlockPos pos) { return null; }
        @Override public BlockState getBlockState(BlockPos pos) { return blocks.getOrDefault(pos,Blocks.AIR.defaultBlockState()); }
        @Override public FluidState getFluidState(BlockPos pos) { return getBlockState(pos).getFluidState(); }
        @Override public int getHeight() { return 384; }
        @Override public int getMinY() { return -64; }
    }
    @Test @ExtendWith(EphemeralTestServerProvider.class)
    void prospectiveQuarryPositionTracesWithoutAnEntity(MinecraftServer server) {
        BlockPos stand=new BlockPos(0,64,0),target=new BlockPos(4,64,0);
        Vec3 eye=new Vec3(0.5,65.6,0.5);
        assertThrows(NullPointerException.class,() -> new ClipContext(eye,Vec3.atCenterOf(target),
                ClipContext.Block.COLLIDER,ClipContext.Fluid.NONE,(Entity)null),"The old call reproduces the reported Minecraft 26.2 crash");

        Terrain terrain=new Terrain(); terrain.blocks.put(target,Blocks.STONE.defaultBlockState());
        var sight=ExcavationService.quarrySight(stand,target);
        var clear=terrain.clip(sight);
        assertEquals(HitResult.Type.BLOCK,clear.getType());
        assertEquals(target,clear.getBlockPos(),"An unobstructed quarry face remains visible without a worker entity");

        BlockPos obstacle=new BlockPos(2,65,0); terrain.blocks.put(obstacle,Blocks.STONE.defaultBlockState());
        assertEquals(obstacle,terrain.clip(sight).getBlockPos(),"Walls still obstruct the quarry visibility check");
        terrain.blocks.clear();
        assertEquals(HitResult.Type.MISS,terrain.clip(sight).getType(),"Empty terrain produces a miss without crashing");
    }
}
