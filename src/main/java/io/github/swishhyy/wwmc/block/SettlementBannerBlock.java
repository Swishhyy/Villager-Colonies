package io.github.swishhyy.wwmc.block;
import io.github.swishhyy.wwmc.settlement.SettlementService;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;

public final class SettlementBannerBlock extends Block {
    public SettlementBannerBlock(Properties p) { super(p); }
    @Override protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
        if (level instanceof ServerLevel server) SettlementService.foundOrInspect(server,player,pos);
        return InteractionResult.SUCCESS;
    }
}
