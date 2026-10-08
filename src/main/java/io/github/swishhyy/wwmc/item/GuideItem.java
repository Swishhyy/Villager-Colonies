package io.github.swishhyy.wwmc.item;

import io.github.swishhyy.wwmc.menu.WwmcNetwork;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.Level;
import net.neoforged.neoforge.network.PacketDistributor;

/** Existing guide items keep their registry id and now open the illustrated handbook. */
public final class GuideItem extends Item {
    public GuideItem(Properties properties) { super(properties); }
    @Override public InteractionResult use(Level level,Player player,InteractionHand hand) {
        if(player instanceof ServerPlayer server) {
            PacketDistributor.sendToPlayer(server,new WwmcNetwork.GuidePayload("start"));
            io.github.swishhyy.wwmc.settlement.TutorialProgress.award(server,"read_guide");
        }
        return InteractionResult.SUCCESS;
    }
}
