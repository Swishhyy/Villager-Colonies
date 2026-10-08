package io.github.swishhyy.wwmc.menu;

import io.github.swishhyy.wwmc.WWMC;
import net.minecraft.core.BlockPos;
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
    /** Points out a block in the world for a few seconds, such as a station named on the town's Needs tab. */
    public record HighlightPayload(BlockPos pos,int seconds) implements CustomPacketPayload {
        public static final Type<HighlightPayload> TYPE=new Type<>(Identifier.fromNamespaceAndPath(WWMC.MODID,"highlight"));
        public static final StreamCodec<RegistryFriendlyByteBuf,HighlightPayload> STREAM_CODEC=StreamCodec.composite(
            BlockPos.STREAM_CODEC,HighlightPayload::pos,ByteBufCodecs.VAR_INT,HighlightPayload::seconds,HighlightPayload::new);
        @Override public Type<HighlightPayload> type() { return TYPE; }
    }
    /** The shared settlement map for this player; the client opens or refreshes its map screen. */
    public record MapPayload(MapData map) implements CustomPacketPayload {
        public static final Type<MapPayload> TYPE=new Type<>(Identifier.fromNamespaceAndPath(WWMC.MODID,"map"));
        public static final StreamCodec<RegistryFriendlyByteBuf,MapPayload> STREAM_CODEC=MapData.STREAM_CODEC.map(MapPayload::new,MapPayload::map);
        @Override public Type<MapPayload> type() { return TYPE; }
    }
    /** A click on the map: refresh it, place a ping of a kind at a column, or remove a ping by id. */
    public record MapActionPayload(int action,int x,int z,String kind,String id) implements CustomPacketPayload {
        public static final Type<MapActionPayload> TYPE=new Type<>(Identifier.fromNamespaceAndPath(WWMC.MODID,"map_action"));
        public static final StreamCodec<RegistryFriendlyByteBuf,MapActionPayload> STREAM_CODEC=StreamCodec.composite(
            ByteBufCodecs.VAR_INT,MapActionPayload::action,ByteBufCodecs.VAR_INT,MapActionPayload::x,ByteBufCodecs.VAR_INT,MapActionPayload::z,
            ByteBufCodecs.STRING_UTF8,MapActionPayload::kind,ByteBufCodecs.STRING_UTF8,MapActionPayload::id,MapActionPayload::new);
        @Override public Type<MapActionPayload> type() { return TYPE; }
    }
    public static void register(RegisterPayloadHandlersEvent event) {
        var registrar=event.registrar("4");
        registrar.playToClient(ViewPayload.TYPE,ViewPayload.STREAM_CODEC);
        registrar.playToClient(HighlightPayload.TYPE,HighlightPayload.STREAM_CODEC);
        registrar.playToClient(MapPayload.TYPE,MapPayload.STREAM_CODEC);
        registrar.playToServer(MapActionPayload.TYPE,MapActionPayload.STREAM_CODEC,(payload,context) -> {
            if(context.player() instanceof ServerPlayer player && payload.kind().length()<=16 && payload.id().length()<=64)
                io.github.swishhyy.wwmc.settlement.SettlementMap.act(player,payload.action(),payload.x(),payload.z(),payload.kind(),payload.id());
        });
        registrar.playToServer(ActionPayload.TYPE,ActionPayload.STREAM_CODEC,WwmcNetwork::action);
    }
    /** Only the screen the player actually has open, while it is still valid, receives the action. */
    private static void action(ActionPayload payload,IPayloadContext context) {
        if(context.player() instanceof ServerPlayer player && player.containerMenu.containerId==payload.containerId()
                && player.containerMenu instanceof ViewMenu menu && player.containerMenu.stillValid(player))
            menu.act(player,payload.action(),payload.index(),payload.value(),payload.key());
    }
}
