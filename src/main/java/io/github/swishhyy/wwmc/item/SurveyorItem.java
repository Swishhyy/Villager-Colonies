package io.github.swishhyy.wwmc.item;
import io.github.swishhyy.wwmc.block.StationBlock;
import io.github.swishhyy.wwmc.core.RoomBounds;
import io.github.swishhyy.wwmc.settlement.SettlementService;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.WeakHashMap;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.context.UseOnContext;

public final class SurveyorItem extends Item {
    private record Selection(ServerLevel level, BlockPos first, BlockPos second) {}
    private static final Map<MinecraftServer, Map<UUID,Selection>> SELECTIONS = new WeakHashMap<>();
    public SurveyorItem(Properties p) { super(p); }
    @Override public InteractionResult useOn(UseOnContext context) {
        if (!(context.getLevel() instanceof ServerLevel level) || context.getPlayer()==null) return InteractionResult.SUCCESS;
        Player player=context.getPlayer();
        Map<UUID,Selection> selections=SELECTIONS.computeIfAbsent(level.getServer(), s -> new HashMap<>());
        if (player.isShiftKeyDown()) {
            selections.remove(player.getUUID());
            player.displayClientMessage(Component.literal("Room selection cleared."), true);
            return InteractionResult.SUCCESS;
        }
        BlockPos pos=context.getClickedPos().immutable();
        Selection selection=selections.get(player.getUUID());
        if (selection!=null && selection.level()==level && selection.second()!=null && level.getBlockState(pos).getBlock() instanceof StationBlock) {
            RoomBounds room=RoomBounds.between(selection.first().getX(),selection.first().getY(),selection.first().getZ(),selection.second().getX(),selection.second().getY(),selection.second().getZ());
            if (SettlementService.bindRoom(level,player,pos,room)) selections.remove(player.getUUID());
            return InteractionResult.SUCCESS;
        }
        if (selection==null || selection.level()!=level || selection.second()!=null) {
            selections.put(player.getUUID(),new Selection(level,pos,null));
            player.displayClientMessage(Component.literal("First corner selected. Select the opposite corner, including beds and floor."),false);
        } else {
            selections.put(player.getUUID(),new Selection(level,selection.first(),pos));
            player.displayClientMessage(Component.literal("Second corner selected. Right-click the role station inside this area."),false);
        }
        return InteractionResult.SUCCESS;
    }
    public static void clear(MinecraftServer server, UUID player) { var map=SELECTIONS.get(server); if(map!=null) map.remove(player); }
    public static void clear(MinecraftServer server) { SELECTIONS.remove(server); }
}
