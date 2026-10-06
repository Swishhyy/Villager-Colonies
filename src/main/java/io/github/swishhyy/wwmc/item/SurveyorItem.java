package io.github.swishhyy.wwmc.item;

import io.github.swishhyy.wwmc.block.StationBlock;
import io.github.swishhyy.wwmc.event.StationPreviewEvent;
import io.github.swishhyy.wwmc.settlement.SettlementService;
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
        if(level.getBlockState(pos).getBlock() instanceof StationBlock block) {
            StationPreviewEvent.show(level,pos,player);
            if(level instanceof ServerLevel server) {
                SettlementService.registerStation(server,player,pos,block.role());
                SettlementService.inspectStation(server,player,pos);
            }
            return InteractionResult.SUCCESS;
        }
        if(level instanceof ServerLevel) SettlementService.tell(player,"Aim at a station to see its 7x7x7 range. Stations find nearby blocks automatically.");
        return InteractionResult.SUCCESS;
    }
}
