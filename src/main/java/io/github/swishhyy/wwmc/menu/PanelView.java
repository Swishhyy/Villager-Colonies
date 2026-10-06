package io.github.swishhyy.wwmc.menu;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.ComponentSerialization;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.world.item.ItemStack;

/**
 * What a settlement screen shows, built on the server each second: tabs of rows, plus the buttons the owner may use.
 * Text arrives as components so item and block names are translated on the client.
 */
public record PanelView(Component title,Component subtitle,List<Tab> tabs,List<Action> actions) {
    public static final int NO_BAR=-1,NO_VALUE=-1,MAX_ROWS=256,MAX_TABS=16;
    /** One line: an item icon, a heading, a detail line, an optional 0..1 bar and an optional number (a craft target). */
    public record Row(ItemStack icon,Component text,Component detail,int color,float bar,int value) {
        public Row(ItemStack icon,String text,String detail) { this(icon,Component.literal(text),Component.literal(detail),0,NO_BAR,NO_VALUE); }
        public Row bar(float fraction,int color) { return new Row(icon,text,detail,color,Math.clamp(fraction,0F,1F),value); }
        public Row value(int amount) { return new Row(icon,text,detail,color,bar,amount); }
    }
    public record Tab(Component name,List<Row> rows) {
        public Tab(String name,List<Row> rows) { this(Component.literal(name),rows); }
    }
    public record Action(int id,Component label,boolean enabled) {
        public Action(int id,String label,boolean enabled) { this(id,Component.literal(label),enabled); }
    }
    public static final StreamCodec<RegistryFriendlyByteBuf,PanelView> STREAM_CODEC=StreamCodec.of(PanelView::write,PanelView::read);
    private static void write(RegistryFriendlyByteBuf buf,PanelView view) {
        ComponentSerialization.STREAM_CODEC.encode(buf,view.title());
        ComponentSerialization.STREAM_CODEC.encode(buf,view.subtitle());
        List<Tab> tabs=view.tabs().subList(0,Math.min(MAX_TABS,view.tabs().size()));
        buf.writeVarInt(tabs.size());
        for(Tab tab:tabs) {
            ComponentSerialization.STREAM_CODEC.encode(buf,tab.name());
            List<Row> rows=tab.rows().subList(0,Math.min(MAX_ROWS,tab.rows().size()));
            buf.writeVarInt(rows.size());
            for(Row row:rows) {
                ItemStack.OPTIONAL_STREAM_CODEC.encode(buf,row.icon());
                ComponentSerialization.STREAM_CODEC.encode(buf,row.text());
                ComponentSerialization.STREAM_CODEC.encode(buf,row.detail());
                buf.writeInt(row.color()); buf.writeFloat(row.bar()); buf.writeVarInt(row.value());
            }
        }
        List<Action> actions=view.actions().subList(0,Math.min(MAX_TABS,view.actions().size()));
        buf.writeVarInt(actions.size());
        for(Action action:actions) {
            buf.writeVarInt(action.id()); ComponentSerialization.STREAM_CODEC.encode(buf,action.label()); buf.writeBoolean(action.enabled());
        }
    }
    private static PanelView read(RegistryFriendlyByteBuf buf) {
        Component title=ComponentSerialization.STREAM_CODEC.decode(buf),subtitle=ComponentSerialization.STREAM_CODEC.decode(buf);
        int tabCount=Math.min(MAX_TABS,buf.readVarInt());
        List<Tab> tabs=new ArrayList<>();
        for(int t=0;t<tabCount;t++) {
            Component name=ComponentSerialization.STREAM_CODEC.decode(buf);
            int rowCount=Math.min(MAX_ROWS,buf.readVarInt());
            List<Row> rows=new ArrayList<>();
            for(int r=0;r<rowCount;r++) {
                ItemStack icon=ItemStack.OPTIONAL_STREAM_CODEC.decode(buf);
                Component text=ComponentSerialization.STREAM_CODEC.decode(buf),detail=ComponentSerialization.STREAM_CODEC.decode(buf);
                rows.add(new Row(icon,text,detail,buf.readInt(),buf.readFloat(),buf.readVarInt()));
            }
            tabs.add(new Tab(name,rows));
        }
        int actionCount=Math.min(MAX_TABS,buf.readVarInt());
        List<Action> actions=new ArrayList<>();
        for(int a=0;a<actionCount;a++) actions.add(new Action(buf.readVarInt(),ComponentSerialization.STREAM_CODEC.decode(buf),buf.readBoolean()));
        return new PanelView(title,subtitle,tabs,actions);
    }
}
