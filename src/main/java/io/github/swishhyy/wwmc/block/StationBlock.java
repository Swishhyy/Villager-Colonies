package io.github.swishhyy.wwmc.block;
import io.github.swishhyy.wwmc.core.StructureRole;
import io.github.swishhyy.wwmc.settlement.SettlementService;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;

public final class StationBlock extends Block {
    private final StructureRole role;
    public StationBlock(StructureRole role, Properties properties) { super(properties); this.role=role; }
    public StructureRole role() { return role; }
    @Override public void setPlacedBy(Level level, BlockPos pos, BlockState state, LivingEntity placer, ItemStack stack) {
        if (level instanceof ServerLevel server && placer instanceof Player player) SettlementService.registerStation(server,player,pos,role);
    }
    @Override protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
        if (level instanceof ServerLevel server) {
            SettlementService.registerStation(server,player,pos,role);
            SettlementService.inspectStation(server,player,pos);
        }
        return InteractionResult.SUCCESS;
    }
}
