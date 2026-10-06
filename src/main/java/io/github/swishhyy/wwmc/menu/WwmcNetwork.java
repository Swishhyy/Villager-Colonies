package io.github.swishhyy.wwmc.menu;

import io.github.swishhyy.wwmc.WWMC;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/** Screen refreshes go to the client; button presses come back. The client registers its own handler for refreshes. */
public final class WwmcNetwork {
    private WwmcNetwork() {}
    /** A fresh view for the open screen with this container id. */
    public record ViewPayload(int containerId,PanelView view) implements CustomPacketPayload {
        public static final Type<ViewPayload> TYPE=new Type<>(Identifier.fromNamespaceAndPath(WWMC.MODID,"view"));
        public static final StreamCodec<RegistryFriendlyByteBuf,ViewPayload> STREAM_CODEC=StreamCodec.composite(
            ByteBufCodecs.VAR_INT,ViewPayload::containerId,PanelView.STREAM_CODEC,ViewPayload::view,ViewPayload::new);
        @Override public Type<ViewPayload> type() { return TYPE; }
    }
    /**
     * A button, slider or row control on an open screen. Row controls name their row's {@code key} (an order's item),
     * so a click made against a list that has since changed is ignored rather than applied to another row.
     */
    public record ActionPayload(int containerId,int action,int index,int value,String key) implements CustomPacketPayload {
        public static final Type<ActionPayload> TYPE=new Type<>(Identifier.fromNamespaceAndPath(WWMC.MODID,"action"));
        public static final StreamCodec<RegistryFriendlyByteBuf,ActionPayload> STREAM_CODEC=StreamCodec.composite(
            ByteBufCodecs.VAR_INT,ActionPayload::containerId,ByteBufCodecs.VAR_INT,ActionPayload::action,
            ByteBufCodecs.VAR_INT,ActionPayload::index,ByteBufCodecs.VAR_INT,ActionPayload::value,
            ByteBufCodecs.STRING_UTF8,ActionPayload::key,ActionPayload::new);
        @Override public Type<ActionPayload> type() { return TYPE; }
    }
    public static void register(RegisterPayloadHandlersEvent event) {
        var registrar=event.registrar("1");
        registrar.playToClient(ViewPayload.TYPE,ViewPayload.STREAM_CODEC);
        registrar.playToServer(ActionPayload.TYPE,ActionPayload.STREAM_CODEC,WwmcNetwork::action);
    }
    /** Only the screen the player actually has open, while it is still valid, receives the action. */
    private static void action(ActionPayload payload,IPayloadContext context) {
        if(context.player() instanceof ServerPlayer player && player.containerMenu.containerId==payload.containerId()
                && player.containerMenu instanceof ViewMenu menu && player.containerMenu.stillValid(player))
            menu.act(player,payload.action(),payload.index(),payload.value(),payload.key());
    }
}
