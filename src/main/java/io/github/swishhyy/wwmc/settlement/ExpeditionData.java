package io.github.swishhyy.wwmc.settlement;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import io.github.swishhyy.wwmc.WWMC;
import java.util.*;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.level.saveddata.SavedDataType;

/** Encounter sites, their surviving defenders and ownership survive chunk unload and server restart. */
public final class ExpeditionData extends SavedData {
    public static final class Site {
        public static final Codec<Site> CODEC=RecordCodecBuilder.create(i -> i.group(
                Settlement.UUID_CODEC.fieldOf("id").forGetter(s -> s.id),BlockPos.CODEC.fieldOf("pos").forGetter(s -> s.pos),
                Codec.STRING.fieldOf("kind").forGetter(s -> s.kind),Codec.BOOL.optionalFieldOf("spawned",false).forGetter(s -> s.spawned),
                Settlement.UUID_CODEC.listOf().optionalFieldOf("guards",List.of()).forGetter(s -> s.guards),Codec.BOOL.optionalFieldOf("cleared",false).forGetter(s -> s.cleared),
                Settlement.UUID_CODEC.optionalFieldOf("claimed").forGetter(s -> Optional.ofNullable(s.claimed)),Codec.LONG.optionalFieldOf("next_raid",0L).forGetter(s -> s.nextRaid),
                BlockPos.CODEC.optionalFieldOf("ambush").forGetter(s -> Optional.ofNullable(s.ambush)),Settlement.UUID_CODEC.optionalFieldOf("victim").forGetter(s -> Optional.ofNullable(s.victim)),
                Codec.STRING.optionalFieldOf("region","").forGetter(s -> s.region)
        ).apply(i,Site::new));
        public final UUID id;
        public final BlockPos pos;
        public final String kind,region;
        public boolean spawned,cleared;
        public final List<UUID> guards;
        public UUID claimed,victim;
        public long nextRaid;
        public BlockPos ambush;
        public Site(UUID id,BlockPos pos,String kind,String region) { this(id,pos,kind,false,List.of(),false,Optional.empty(),0,Optional.empty(),Optional.empty(),region); }
        private Site(UUID id,BlockPos pos,String kind,boolean spawned,List<UUID> guards,boolean cleared,Optional<UUID> claimed,long nextRaid,
                Optional<BlockPos> ambush,Optional<UUID> victim,String region) {
            this.id=id; this.pos=pos.immutable(); this.kind=kind; this.region=region; this.spawned=spawned; this.guards=new ArrayList<>(guards);
            this.cleared=cleared; this.claimed=claimed.orElse(null); this.nextRaid=nextRaid; this.ambush=ambush.orElse(null); this.victim=victim.orElse(null);
        }
        public String title() { return switch(kind) { case "mine" -> "Occupied Mine"; case "fort" -> "Ruined Fort"; default -> "Bandit Camp"; }; }
    }
    private static final Codec<ExpeditionData> CODEC=Site.CODEC.listOf().xmap(ExpeditionData::new,d -> d.sites);
    private static final SavedDataType<ExpeditionData> TYPE=new SavedDataType<>(Identifier.fromNamespaceAndPath(WWMC.MODID,"expeditions"),ExpeditionData::new,CODEC);
    public final List<Site> sites;
    public ExpeditionData() { this(List.of()); }
    private ExpeditionData(List<Site> sites) { this.sites=new ArrayList<>(sites); }
    public static ExpeditionData get(ServerLevel level) { return level.getDataStorage().computeIfAbsent(TYPE); }
    public Site byId(UUID id) { return sites.stream().filter(s -> s.id.equals(id)).findFirst().orElse(null); }
}
