package io.github.swishhyy.wwmc.menu;

import java.util.ArrayList;
import java.util.List;
import java.util.function.BiConsumer;
import java.util.function.Function;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;

/**
 * What the shared settlement map shows one player: town claims by relation, trade routes, expedition sites and the
 * pings placed by their own and allied towns. Built on the server for optional map integrations.
 */
public record MapData(String dimension,int centerX,int centerZ,List<Town> towns,List<Route> routes,List<Site> sites,List<Mark> pings) {
    public static final int OWN=0,ALLY=1,NEUTRAL=2,HOSTILE=3,OTHER=4;
    public static final int UNCLEARED=0,CLEARED=1,CLAIMED=2,RAID=3;
    private static final int MAX=512;
    public record Town(String id,String name,int x,int y,int z,int radius,int relation) {}
    /** A trade route between two town centers; extra allied routes are drawn fainter. */
    public record Point(int x,int z) {}
    public record Route(int x1,int z1,int x2,int z2,boolean extra,List<Point> path) {
        public Route { path=List.copyOf(path); if(path.size()<2 || path.size()>64) throw new IllegalArgumentException("Invalid route path size"); }
        public Route(int x1,int z1,int x2,int z2,boolean extra) { this(x1,z1,x2,z2,extra,List.of(new Point(x1,z1),new Point(x2,z2))); }
    }
    public record Site(String id,int x,int y,int z,String title,String detail,int state) {}
    /** A ping: what to do there, who placed it, and whether this player may remove it. */
    public record Mark(String id,int x,int y,int z,String kind,String label,String author,boolean removable) {}
    public static final StreamCodec<RegistryFriendlyByteBuf,MapData> STREAM_CODEC=StreamCodec.of(MapData::write,MapData::read);
    private static <T> void list(RegistryFriendlyByteBuf buf,List<T> items,BiConsumer<RegistryFriendlyByteBuf,T> one) {
        List<T> sent=items.subList(0,Math.min(MAX,items.size()));
        buf.writeVarInt(sent.size());
        for(T item:sent) one.accept(buf,item);
    }
    private static <T> List<T> list(RegistryFriendlyByteBuf buf,Function<RegistryFriendlyByteBuf,T> one) {
        int count=buf.readVarInt();
        if(count<0 || count>MAX) throw new IllegalArgumentException("Invalid map entry count: "+count);
        List<T> items=new ArrayList<>();
        for(int n=0;n<count;n++) items.add(one.apply(buf));
        return items;
    }
    /** Long town and player names are clipped, so no name can exceed what the packet allows. */
    private static String cut(String text,int max) { return text.length()<=max ? text : text.substring(0,max-1)+"…"; }
    private static void write(RegistryFriendlyByteBuf buf,MapData map) {
        buf.writeUtf(map.dimension(),256); buf.writeInt(map.centerX()); buf.writeInt(map.centerZ());
        list(buf,map.towns(),(b,t) -> { b.writeUtf(t.id(),64); b.writeUtf(cut(t.name(),64),64); b.writeInt(t.x()); b.writeInt(t.y()); b.writeInt(t.z()); b.writeVarInt(t.radius()); b.writeVarInt(t.relation()); });
        list(buf,map.routes(),(b,r) -> { b.writeInt(r.x1()); b.writeInt(r.z1()); b.writeInt(r.x2()); b.writeInt(r.z2()); b.writeBoolean(r.extra());
            b.writeVarInt(r.path().size()); for(var p:r.path()) { b.writeInt(p.x()); b.writeInt(p.z()); } });
        list(buf,map.sites(),(b,s) -> { b.writeUtf(s.id(),64); b.writeInt(s.x()); b.writeInt(s.y()); b.writeInt(s.z()); b.writeUtf(cut(s.title(),64),64); b.writeUtf(cut(s.detail(),256),256); b.writeVarInt(s.state()); });
        list(buf,map.pings(),(b,m) -> { b.writeUtf(cut(m.id(),64),64); b.writeInt(m.x()); b.writeInt(m.y()); b.writeInt(m.z()); b.writeUtf(cut(m.kind(),16),16); b.writeUtf(cut(m.label(),128),128);
            b.writeUtf(cut(m.author(),64),64); b.writeBoolean(m.removable()); });
    }
    private static MapData read(RegistryFriendlyByteBuf buf) {
        String dimension=buf.readUtf(256);
        int x=buf.readInt(),z=buf.readInt();
        List<Town> towns=list(buf,b -> new Town(b.readUtf(64),b.readUtf(64),b.readInt(),b.readInt(),b.readInt(),b.readVarInt(),b.readVarInt()));
        List<Route> routes=list(buf,b -> {
            int x1=b.readInt(),z1=b.readInt(),x2=b.readInt(),z2=b.readInt(); boolean extra=b.readBoolean();
            int count=b.readVarInt(); if(count<2 || count>64) throw new IllegalArgumentException("Invalid route point count: "+count);
            List<Point> path=new ArrayList<>(); for(int n=0;n<count;n++) path.add(new Point(b.readInt(),b.readInt()));
            return new Route(x1,z1,x2,z2,extra,path);
        });
        List<Site> sites=list(buf,b -> new Site(b.readUtf(64),b.readInt(),b.readInt(),b.readInt(),b.readUtf(64),b.readUtf(256),b.readVarInt()));
        List<Mark> pings=list(buf,b -> new Mark(b.readUtf(64),b.readInt(),b.readInt(),b.readInt(),b.readUtf(16),b.readUtf(128),b.readUtf(64),b.readBoolean()));
        return new MapData(dimension,x,z,towns,routes,sites,pings);
    }
}
