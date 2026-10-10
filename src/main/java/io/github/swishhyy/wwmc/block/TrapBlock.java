package io.github.swishhyy.wwmc.block;

import io.github.swishhyy.wwmc.core.TrapKind;
import io.github.swishhyy.wwmc.settlement.*;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

/** Walkable defenses. Wear is copied into drops; moving a trap never repairs it. */
public final class TrapBlock extends Block {
    public static final BooleanProperty ARMED=BooleanProperty.create("armed");
    public static final IntegerProperty WEAR=IntegerProperty.create("wear",0,12);
    private static final VoxelShape OUTLINE=Block.box(0,0,0,16,4,16);
    private final TrapKind kind;
    public TrapBlock(TrapKind kind,Properties properties) {
        super(properties); this.kind=kind;
        registerDefaultState(stateDefinition.any().setValue(ARMED,true).setValue(WEAR,0));
    }
    public TrapKind kind() { return kind; }
    @Override protected void createBlockStateDefinition(StateDefinition.Builder<Block,BlockState> builder) { builder.add(ARMED,WEAR); }
    @Override protected VoxelShape getShape(BlockState state,BlockGetter level,BlockPos pos,CollisionContext context) { return OUTLINE; }
    @Override public BlockState getStateForPlacement(BlockPlaceContext context) {
        if(!context.getLevel().getBlockState(context.getClickedPos().below()).isFaceSturdy(context.getLevel(),context.getClickedPos().below(),Direction.UP)) return null;
        if(context.getLevel() instanceof ServerLevel server && context.getPlayer()!=null) {
            var town=SettlementData.get(server).at(context.getClickedPos());
            if(!TownAccess.builds(town,context.getPlayer().getUUID())) {
                SettlementService.notify(context.getPlayer(),"Place defenses inside a town where you can build."); return null;
            }
            if(!AgeProgression.allowed(town,context.getItemInHand())) { AgeProgression.notice(context.getPlayer(),context.getItemInHand()); return null; }
            if(!TrapService.hasRoom(town,context.getClickedPos())) {
                SettlementService.notify(context.getPlayer(),"This town has reached its trap limit."); return null;
            }
        }
        return defaultBlockState();
    }
    @Override public void setPlacedBy(Level level,BlockPos pos,BlockState state,LivingEntity placer,ItemStack stack) {
        if(level instanceof ServerLevel server) TrapService.register(server,pos);
    }
    @Override protected InteractionResult useItemOn(ItemStack stack,BlockState state,Level level,BlockPos pos,Player player,InteractionHand hand,BlockHitResult hit) {
        if(stack.isEmpty()) return InteractionResult.TRY_WITH_EMPTY_HAND;
        if(level instanceof ServerLevel server) {
            var town=SettlementData.get(server).at(pos);
            if(TownAccess.builds(town,player.getUUID()) && TrapService.maintainHeld(server,town,pos,stack,player.isCreative())) {
                SettlementService.notify(player,kind.title+" ready."); return InteractionResult.SUCCESS;
            }
        }
        return InteractionResult.PASS;
    }
    @Override protected InteractionResult useWithoutItem(BlockState state,Level level,BlockPos pos,Player player,BlockHitResult hit) {
        if(level instanceof ServerLevel server) SettlementService.notify(player,TrapService.describe(server,pos));
        return InteractionResult.SUCCESS;
    }
}
