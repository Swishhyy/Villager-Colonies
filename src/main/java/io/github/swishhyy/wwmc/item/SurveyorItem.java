package io.github.swishhyy.wwmc.item;

import io.github.swishhyy.wwmc.block.StationBlock;
import io.github.swishhyy.wwmc.event.StationPreviewEvent;
import io.github.swishhyy.wwmc.settlement.SettlementService;
import io.github.swishhyy.wwmc.settlement.SettlementData;
import io.github.swishhyy.wwmc.settlement.ForestryService;
import io.github.swishhyy.wwmc.settlement.GuardService;
import io.github.swishhyy.wwmc.core.StructureRole;
import net.minecraft.tags.BlockTags;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.context.UseOnContext;

/** Keeps the old item ID/recipe usable while replacing manual selection with station inspection. */
public final class SurveyorItem extends Item {
    public SurveyorItem(Properties p) { super(p); }
    @Override public InteractionResult useOn(UseOnContext context) {
        var player=context.getPlayer();
        if(player==null) return InteractionResult.PASS;
        var level=context.getLevel();
        var pos=context.getClickedPos();
        if(level instanceof ServerLevel server && !(level.getBlockState(pos).getBlock() instanceof StationBlock)
                && GuardService.select(server,player,pos.relative(context.getClickedFace()))) return InteractionResult.SUCCESS;
        if(level.getBlockState(pos).getBlock() instanceof StationBlock block) {
            StationPreviewEvent.show(level,pos,player);
            if(level instanceof ServerLevel server) {
                SettlementService.registerStation(server,player,pos,block.role());
                if(block.role()==StructureRole.GUARD && player.isShiftKeyDown()) GuardService.begin(server,player,pos);
                else SettlementService.inspectStation(server,player,pos);
            }
            return InteractionResult.SUCCESS;
        }
        if(level instanceof ServerLevel server && level.getBlockState(pos).is(BlockTags.LOGS)) {
            var town=SettlementData.get(server).at(pos);
            if(SettlementService.owns(player,town)) {
                int count=ForestryService.protectConnectedLogs(server,town,pos);
                SettlementService.tell(player,"Protected "+count+" connected logs from your workers.");
            } else SettlementService.tell(player,"Protect existing log structures inside your own town.");
            return InteractionResult.SUCCESS;
        }
        if(level instanceof ServerLevel) SettlementService.tell(player,"Aim at a station to see its 7x7x7 range. Stations find nearby blocks automatically.");
        return InteractionResult.SUCCESS;
    }
}
