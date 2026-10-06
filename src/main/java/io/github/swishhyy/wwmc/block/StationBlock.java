package io.github.swishhyy.wwmc.block;
import io.github.swishhyy.wwmc.core.StructureRole;
import io.github.swishhyy.wwmc.event.StationPreviewEvent;
import io.github.swishhyy.wwmc.settlement.SettlementService;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.InteractionHand;
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
    @Override protected InteractionResult useItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos, Player player, InteractionHand hand, BlockHitResult hit) {
        // Let held tools reach Item.useOn; only an empty hand inspects this station.
        return stack.isEmpty() ? InteractionResult.TRY_WITH_EMPTY_HAND : InteractionResult.PASS;
    }
    @Override public void setPlacedBy(Level level, BlockPos pos, BlockState state, LivingEntity placer, ItemStack stack) {
        if(placer instanceof Player player) {
            if(level instanceof ServerLevel server) SettlementService.registerStation(server,player,pos,role);
            StationPreviewEvent.show(level,pos,player);
        }
    }
    @Override protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
        StationPreviewEvent.show(level,pos,player);
        if (level instanceof ServerLevel server) {
            SettlementService.registerStation(server,player,pos,role);
            SettlementService.inspectStation(server,player,pos);
        }
        return InteractionResult.SUCCESS;
    }
}
