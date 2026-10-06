package io.github.swishhyy.wwmc.settlement;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.function.Predicate;
import java.util.List;
import java.util.UUID;
import java.util.Map;
import java.util.HashMap;
import net.minecraft.core.BlockPos;

public final class Settlement {
    public static final Codec<UUID> UUID_CODEC = Codec.STRING.xmap(UUID::fromString, UUID::toString);
    public static final Codec<Settlement> CODEC = RecordCodecBuilder.create(i -> i.group(
        UUID_CODEC.fieldOf("id").forGetter(s -> s.id), UUID_CODEC.fieldOf("owner").forGetter(s -> s.owner),
        Codec.STRING.fieldOf("name").forGetter(s -> s.name), BlockPos.CODEC.fieldOf("center").forGetter(s -> s.center),
        Codec.intRange(16,512).fieldOf("radius").forGetter(s -> s.radius),
        UUID_CODEC.listOf().fieldOf("citizens").forGetter(s -> s.citizens),
        Station.CODEC.listOf().fieldOf("stations").forGetter(s -> s.stations),
        Codec.STRING.optionalFieldOf("priority", "balanced").forGetter(s -> s.priority),
        BlockPos.CODEC.listOf().optionalFieldOf("border_banners",List.of()).forGetter(s -> s.borderBanners),
        Codec.unboundedMap(UUID_CODEC,Codec.STRING).optionalFieldOf("citizen_names",Map.of()).forGetter(s -> s.citizenNames)
    ).apply(i, Settlement::new));
    public final UUID id, owner;
    public String name, priority;
    public final BlockPos center;
    public final int radius;
    public final List<UUID> citizens;
    public final List<Station> stations;
    public final List<BlockPos> borderBanners;
    public final Map<UUID,String> citizenNames;
    public Settlement(UUID id, UUID owner, String name, BlockPos center, int radius, List<UUID> citizens, List<Station> stations, String priority) {
        this(id,owner,name,center,radius,citizens,stations,priority,List.of());
    }
    public Settlement(UUID id, UUID owner, String name, BlockPos center, int radius, List<UUID> citizens, List<Station> stations, String priority,List<BlockPos> borderBanners) {
        this(id,owner,name,center,radius,citizens,stations,priority,borderBanners,Map.of());
    }
    public Settlement(UUID id, UUID owner, String name, BlockPos center, int radius, List<UUID> citizens, List<Station> stations, String priority,List<BlockPos> borderBanners,Map<UUID,String> citizenNames) {
        this.id=id; this.owner=owner; this.name=name; this.center=center.immutable(); this.radius=radius;
        this.citizens=new ArrayList<>(citizens); this.stations=new ArrayList<>(stations); this.priority=priority;
        this.borderBanners=new ArrayList<>();
        borderBanners.forEach(p -> this.borderBanners.add(p.immutable()));
        this.citizenNames=new HashMap<>(citizenNames);
    }
    public boolean contains(BlockPos pos) {
        return Math.abs((long)pos.getX()-center.getX()) <= radius && Math.abs((long)pos.getZ()-center.getZ()) <= radius;
    }
    public boolean overlaps(BlockPos pos, int r) {
        return Math.abs((long)pos.getX()-center.getX()) <= (long)radius+r && Math.abs((long)pos.getZ()-center.getZ()) <= (long)radius+r;
    }
    /** Nearest eligible station owns a block; coordinate ties are independent of placement/save order. */
    public Station nearestStation(BlockPos pos, Predicate<Station> eligible) {
        return stations.stream().filter(s -> s.contains(pos) && eligible.test(s))
                .min(Comparator.comparingDouble((Station s) -> s.position().distSqr(pos))
                    .thenComparingInt(s -> s.position().getX())
                    .thenComparingInt(s -> s.position().getY())
                    .thenComparingInt(s -> s.position().getZ())).orElse(null);
    }
    public Station station(BlockPos pos) { return stations.stream().filter(s -> s.position().equals(pos)).findFirst().orElse(null); }
}
