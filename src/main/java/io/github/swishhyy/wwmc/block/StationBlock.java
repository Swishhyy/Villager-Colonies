package io.github.swishhyy.wwmc.block;
import io.github.swishhyy.wwmc.core.StructureRole;
import io.github.swishhyy.wwmc.core.Upgrades;
import io.github.swishhyy.wwmc.event.StationPreviewEvent;
import io.github.swishhyy.wwmc.settlement.SettlementService;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.world.phys.BlockHitResult;

/**
 * A station block. Its range and crew upgrade levels are also kept in the block state, so the client can draw the
 * upgraded range and a broken station's item keeps the upgrades for wherever it is placed next.
 */
public final class StationBlock extends Block {
    public static final EnumProperty<Direction> FACING=BlockStateProperties.HORIZONTAL_FACING;
    public static final IntegerProperty RANGE=IntegerProperty.create("range",0,Upgrades.MAX_STATION_LEVEL);
    public static final IntegerProperty CREW=IntegerProperty.create("crew",0,Upgrades.MAX_STATION_LEVEL);
    private final StructureRole role;
    public StationBlock(StructureRole role, Properties properties) {
        super(properties); this.role=role;
        registerDefaultState(stateDefinition.any().setValue(FACING,Direction.NORTH).setValue(RANGE,0).setValue(CREW,0));
    }
    @Override protected void createBlockStateDefinition(StateDefinition.Builder<Block,BlockState> builder) { builder.add(FACING,RANGE,CREW); }
    @Override public BlockState getStateForPlacement(BlockPlaceContext context) {
        if(role==StructureRole.TRADER && context.getLevel() instanceof ServerLevel server && context.getPlayer()!=null) {
            var town=io.github.swishhyy.wwmc.settlement.SettlementData.get(server).at(context.getClickedPos());
            if(!SettlementService.owns(context.getPlayer(),town)) { SettlementService.notify(context.getPlayer(),"Place the Trader Block inside your own town."); return null; }
            if(!io.github.swishhyy.wwmc.settlement.TradeRoutes.uniqueCheckpoint(server,town,context.getClickedPos())) {
                SettlementService.notify(context.getPlayer(),"Each town can have only one Trader Block."); return null;
            }
        }
        return defaultBlockState().setValue(FACING,context.getHorizontalDirection());
    }
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
