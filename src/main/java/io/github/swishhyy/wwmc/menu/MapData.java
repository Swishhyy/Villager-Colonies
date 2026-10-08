package io.github.swishhyy.wwmc.menu;

import java.util.ArrayList;
import java.util.List;
import java.util.function.BiConsumer;
import java.util.function.Function;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;

/**
 * What the shared settlement map shows one player: town claims by relation, trade routes, expedition sites and the
 * pings placed by their own and allied towns. Built on the server; the client only draws it.
 */
public record MapData(int centerX,int centerZ,List<Town> towns,List<Route> routes,List<Site> sites,List<Mark> pings) {
    public static final int OWN=0,ALLY=1,NEUTRAL=2,HOSTILE=3,OTHER=4;
    public static final int UNCLEARED=0,CLEARED=1,CLAIMED=2,RAID=3;
    private static final int MAX=512;
    public record Town(String name,int x,int z,int radius,int relation) {}
    /** A trade route between two town centers; extra allied routes are drawn fainter. */
    public record Route(int x1,int z1,int x2,int z2,boolean extra) {}
    public record Site(int x,int z,String title,String detail,int state) {}
    /** A ping: what to do there, who placed it, and whether this player may remove it. */
    public record Mark(String id,int x,int z,String kind,String label,String author,boolean removable) {}
    public static final StreamCodec<RegistryFriendlyByteBuf,MapData> STREAM_CODEC=StreamCodec.of(MapData::write,MapData::read);
    private static <T> void list(RegistryFriendlyByteBuf buf,List<T> items,BiConsumer<RegistryFriendlyByteBuf,T> one) {
        List<T> sent=items.subList(0,Math.min(MAX,items.size()));
        buf.writeVarInt(sent.size());
        for(T item:sent) one.accept(buf,item);
    }
    private static <T> List<T> list(RegistryFriendlyByteBuf buf,Function<RegistryFriendlyByteBuf,T> one) {
        int count=Math.min(MAX,buf.readVarInt());
        List<T> items=new ArrayList<>();
        for(int n=0;n<count;n++) items.add(one.apply(buf));
        return items;
    }
    /** Long town and player names are clipped, so no name can exceed what the packet allows. */
    private static String cut(String text,int max) { return text.length()<=max ? text : text.substring(0,max-1)+"…"; }
    private static void write(RegistryFriendlyByteBuf buf,MapData map) {
        buf.writeInt(map.centerX()); buf.writeInt(map.centerZ());
        list(buf,map.towns(),(b,t) -> { b.writeUtf(cut(t.name(),64),64); b.writeInt(t.x()); b.writeInt(t.z()); b.writeVarInt(t.radius()); b.writeVarInt(t.relation()); });
        list(buf,map.routes(),(b,r) -> { b.writeInt(r.x1()); b.writeInt(r.z1()); b.writeInt(r.x2()); b.writeInt(r.z2()); b.writeBoolean(r.extra()); });
        list(buf,map.sites(),(b,s) -> { b.writeInt(s.x()); b.writeInt(s.z()); b.writeUtf(cut(s.title(),64),64); b.writeUtf(cut(s.detail(),256),256); b.writeVarInt(s.state()); });
        list(buf,map.pings(),(b,m) -> { b.writeUtf(cut(m.id(),64),64); b.writeInt(m.x()); b.writeInt(m.z()); b.writeUtf(cut(m.kind(),16),16); b.writeUtf(cut(m.label(),128),128);
            b.writeUtf(cut(m.author(),64),64); b.writeBoolean(m.removable()); });
    }
    private static MapData read(RegistryFriendlyByteBuf buf) {
        int x=buf.readInt(),z=buf.readInt();
        List<Town> towns=list(buf,b -> new Town(b.readUtf(64),b.readInt(),b.readInt(),b.readVarInt(),b.readVarInt()));
        List<Route> routes=list(buf,b -> new Route(b.readInt(),b.readInt(),b.readInt(),b.readInt(),b.readBoolean()));
        List<Site> sites=list(buf,b -> new Site(b.readInt(),b.readInt(),b.readUtf(64),b.readUtf(256),b.readVarInt()));
        List<Mark> pings=list(buf,b -> new Mark(b.readUtf(64),b.readInt(),b.readInt(),b.readUtf(16),b.readUtf(128),b.readUtf(64),b.readBoolean()));
        return new MapData(x,z,towns,routes,sites,pings);
    }
}
