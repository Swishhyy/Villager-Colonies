package io.github.swishhyy.wwmc.settlement;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.*;
import net.minecraft.core.BlockPos;

/**
 * What a town has learned and marked: finished research, schematics recovered on expeditions, and the pings its people
 * placed on the shared map. Towns saved before this start with none.
 */
public final class TownProgress {
    public static final int MAX_PINGS=16;
    /** A marker on the shared map, such as "meet here" or "build a bridge here", seen by the town's people and its allies. */
    public record Ping(UUID id,UUID author,String name,BlockPos pos,String kind,String note,long placed) {
        public static final List<String> KINDS=List.of("meet","bridge","build","danger","resource");
        public static final Codec<Ping> CODEC=RecordCodecBuilder.create(i -> i.group(
            Settlement.UUID_CODEC.fieldOf("id").forGetter(Ping::id),Settlement.UUID_CODEC.fieldOf("author").forGetter(Ping::author),
            Codec.STRING.optionalFieldOf("name","").forGetter(Ping::name),BlockPos.CODEC.fieldOf("pos").forGetter(Ping::pos),
            Codec.STRING.optionalFieldOf("kind","meet").forGetter(Ping::kind),Codec.STRING.optionalFieldOf("note","").forGetter(Ping::note),
            Codec.LONG.optionalFieldOf("placed",0L).forGetter(Ping::placed)
        ).apply(i,Ping::new));
        public Ping {
            pos=pos.immutable();
            if(!KINDS.contains(kind)) kind="meet";
            if(note.length()>64) note=note.substring(0,64);
            if(name.length()>32) name=name.substring(0,32);
        }
        public String label() {
            String title=switch(kind) { case "bridge" -> "Build a bridge here"; case "build" -> "Build here"; case "danger" -> "Danger"; case "resource" -> "Resources here"; default -> "Meet here"; };
            return note.isEmpty() ? title : title+": "+note;
        }
    }
    public static final Codec<TownProgress> CODEC=RecordCodecBuilder.create(i -> i.group(
        Codec.STRING.listOf().optionalFieldOf("research",List.of()).forGetter(p -> List.copyOf(p.research)),
        Codec.STRING.listOf().optionalFieldOf("schematics",List.of()).forGetter(p -> List.copyOf(p.schematics)),
        Ping.CODEC.listOf().optionalFieldOf("pings",List.of()).forGetter(p -> p.pings)
    ).apply(i,TownProgress::new));
    public final Set<String> research=new LinkedHashSet<>(),schematics=new LinkedHashSet<>();
    public final List<Ping> pings=new ArrayList<>();
    public TownProgress() {}
    private TownProgress(List<String> research,List<String> schematics,List<Ping> pings) {
        this.research.addAll(research); this.schematics.addAll(schematics);
        this.pings.addAll(pings.subList(Math.max(0,pings.size()-MAX_PINGS),pings.size()));
    }
    /** Adds a ping, dropping the oldest beyond the limit. */
    public void ping(Ping ping) {
        pings.add(ping);
        while(pings.size()>MAX_PINGS) pings.removeFirst();
    }
}
