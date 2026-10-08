package io.github.swishhyy.wwmc.settlement;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import com.mojang.datafixers.util.Pair;
import java.util.*;
import net.minecraft.core.BlockPos;

/** Optional, versioned town policy. Old saves start with no members, projects, orders or claims. */
public final class CampaignState {
    public record Entry(long time,String text) {
        public static final Codec<Entry> CODEC=RecordCodecBuilder.create(i -> i.group(
                Codec.LONG.fieldOf("time").forGetter(Entry::time),Codec.STRING.fieldOf("text").forGetter(Entry::text)).apply(i,Entry::new));
    }
    public record Squad(UUID leader,List<UUID> guards,String order,BlockPos rally,Optional<UUID> escort) {
        public static final Codec<Squad> CODEC=RecordCodecBuilder.create(i -> i.group(
                Settlement.UUID_CODEC.fieldOf("leader").forGetter(Squad::leader),Settlement.UUID_CODEC.listOf().fieldOf("guards").forGetter(Squad::guards),
                Codec.STRING.fieldOf("order").forGetter(Squad::order),BlockPos.CODEC.fieldOf("rally").forGetter(Squad::rally),
                Settlement.UUID_CODEC.optionalFieldOf("escort").forGetter(Squad::escort)).apply(i,Squad::new));
        public Squad {
            guards=List.copyOf(guards.stream().distinct().limit(6).toList()); rally=rally.immutable();
            if(!List.of("follow","hold","defend","retreat","escort","return").contains(order)) order="return";
        }
        public Squad command(String command,BlockPos point) { return new Squad(leader,guards,command,point,Optional.empty()); }
    }
    private static final MapCodec<CampaignState> CORE=RecordCodecBuilder.mapCodec(i -> i.group(
            Codec.unboundedMap(Settlement.UUID_CODEC,Codec.STRING).optionalFieldOf("members",Map.of()).forGetter(s -> s.members),
            Codec.unboundedMap(Settlement.UUID_CODEC,Codec.STRING).optionalFieldOf("invitations",Map.of()).forGetter(s -> s.invitations),
            Settlement.UUID_CODEC.listOf().optionalFieldOf("allies",List.of()).forGetter(s -> new ArrayList<>(s.allies)),
            Settlement.UUID_CODEC.listOf().optionalFieldOf("alliance_offers",List.of()).forGetter(s -> new ArrayList<>(s.allianceOffers)),
            Codec.unboundedMap(Codec.STRING,Codec.INT).optionalFieldOf("requests",Map.of()).forGetter(s -> s.requests),
            Codec.STRING.listOf().optionalFieldOf("projects",List.of()).forGetter(s -> new ArrayList<>(s.projects)),
            Squad.CODEC.listOf().optionalFieldOf("squads",List.of()).forGetter(s -> s.squads),
            Codec.STRING.optionalFieldOf("specialty","balanced").forGetter(s -> s.specialty),
            Entry.CODEC.listOf().optionalFieldOf("journal",List.of()).forGetter(s -> s.journal),
            Settlement.UUID_CODEC.optionalFieldOf("parent").forGetter(s -> Optional.ofNullable(s.parent)),
            Codec.LONG.optionalFieldOf("next_event",0L).forGetter(s -> s.nextEvent),
            SupplyContract.CODEC.listOf().optionalFieldOf("contracts",List.of()).forGetter(s -> s.contracts),
            Codec.unboundedMap(Codec.STRING,Codec.INT).optionalFieldOf("stock",Map.of()).forGetter(s -> s.stock),
            Settlement.UUID_CODEC.listOf().optionalFieldOf("extra_routes",List.of()).forGetter(s -> new ArrayList<>(s.extraRoutes)),
            Codec.INT.optionalFieldOf("route_cursor",0).forGetter(s -> s.routeCursor),
            Codec.unboundedMap(Settlement.UUID_CODEC,Codec.unboundedMap(Codec.STRING,Codec.INT)).optionalFieldOf("incoming",Map.of()).forGetter(s -> s.incoming)
    ).apply(i,CampaignState::new));
    public static final Codec<CampaignState> CODEC=Codec.mapPair(CORE,
            Codec.unboundedMap(Settlement.UUID_CODEC,Codec.STRING).optionalFieldOf("player_names",Map.of())).xmap(pair -> {
                CampaignState state=pair.getFirst();
                pair.getSecond().entrySet().stream().limit(65).forEach(e -> state.playerNames.put(e.getKey(),e.getValue()));
                return state;
            },state -> Pair.of(state,state.playerNames)).codec();
    public final Map<UUID,String> members=new LinkedHashMap<>(),invitations=new LinkedHashMap<>();
    /** Names remain readable on the Relationships screen when a member disconnects. */
    public final Map<UUID,String> playerNames=new LinkedHashMap<>();
    public final Set<UUID> allies=new LinkedHashSet<>(),allianceOffers=new LinkedHashSet<>(),extraRoutes=new LinkedHashSet<>();
    public final Map<String,Integer> requests=new LinkedHashMap<>(),stock=new HashMap<>();
    public final Map<UUID,Map<String,Integer>> incoming=new HashMap<>();
    public final Set<String> projects=new LinkedHashSet<>();
    public final List<Squad> squads=new ArrayList<>();
    public final List<Entry> journal=new ArrayList<>();
    public final List<SupplyContract> contracts=new ArrayList<>();
    public String specialty="balanced";
    public UUID parent;
    public long nextEvent;
    public int routeCursor;
    public CampaignState() {}
    private CampaignState(Map<UUID,String> members,Map<UUID,String> invitations,List<UUID> allies,List<UUID> offers,Map<String,Integer> requests,
            List<String> projects,List<Squad> squads,String specialty,List<Entry> journal,Optional<UUID> parent,long nextEvent,
            List<SupplyContract> contracts,Map<String,Integer> stock,List<UUID> routes,int cursor,Map<UUID,Map<String,Integer>> incoming) {
        members.entrySet().stream().filter(e -> TownAccess.ROLES.contains(e.getValue())).limit(32).forEach(e -> this.members.put(e.getKey(),e.getValue()));
        invitations.entrySet().stream().filter(e -> TownAccess.ROLES.contains(e.getValue())).limit(32).forEach(e -> this.invitations.put(e.getKey(),e.getValue()));
        this.allies.addAll(allies.stream().limit(32).toList()); this.allianceOffers.addAll(offers.stream().limit(32).toList());
        requests.entrySet().stream().filter(e -> e.getValue()>0).limit(24).forEach(e -> this.requests.put(e.getKey(),Math.min(4096,e.getValue())));
        this.projects.addAll(projects); this.squads.addAll(squads.stream().limit(8).toList());
        this.specialty=Specialization.NAMES.contains(specialty) ? specialty : "balanced";
        this.journal.addAll(journal.subList(Math.max(0,journal.size()-64),journal.size()));
        this.parent=parent.orElse(null); this.nextEvent=nextEvent; this.contracts.addAll(contracts.stream().limit(8).toList());
        stock.forEach((key,value) -> this.stock.put(key,Math.max(0,value)));
        this.extraRoutes.addAll(routes.stream().limit(4).toList()); this.routeCursor=Math.max(0,cursor);
        incoming.entrySet().stream().limit(32).forEach(e -> this.incoming.put(e.getKey(),new HashMap<>(e.getValue())));
    }
    public void log(long time,String text) {
        journal.add(new Entry(time,text.length()>240 ? text.substring(0,240) : text));
        while(journal.size()>64) journal.removeFirst();
    }
    public Squad squad(UUID guard) { return squads.stream().filter(s -> s.guards().contains(guard)).findFirst().orElse(null); }
}
