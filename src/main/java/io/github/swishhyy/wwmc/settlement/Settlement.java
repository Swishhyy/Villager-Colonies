package io.github.swishhyy.wwmc.settlement;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import net.minecraft.core.BlockPos;

public final class Settlement {
    public static final Codec<UUID> UUID_CODEC = Codec.STRING.xmap(UUID::fromString, UUID::toString);
    public static final Codec<Settlement> CODEC = RecordCodecBuilder.create(i -> i.group(
        UUID_CODEC.fieldOf("id").forGetter(s -> s.id), UUID_CODEC.fieldOf("owner").forGetter(s -> s.owner),
        Codec.STRING.fieldOf("name").forGetter(s -> s.name), BlockPos.CODEC.fieldOf("center").forGetter(s -> s.center),
        Codec.intRange(16,128).fieldOf("radius").forGetter(s -> s.radius),
        UUID_CODEC.listOf().fieldOf("citizens").forGetter(s -> s.citizens),
        Station.CODEC.listOf().fieldOf("stations").forGetter(s -> s.stations),
        Codec.STRING.optionalFieldOf("priority", "balanced").forGetter(s -> s.priority)
    ).apply(i, Settlement::new));
    public final UUID id, owner;
    public String name, priority;
    public final BlockPos center;
    public final int radius;
    public final List<UUID> citizens;
    public final List<Station> stations;
    public Settlement(UUID id, UUID owner, String name, BlockPos center, int radius, List<UUID> citizens, List<Station> stations, String priority) {
        this.id=id; this.owner=owner; this.name=name; this.center=center.immutable(); this.radius=radius;
        this.citizens=new ArrayList<>(citizens); this.stations=new ArrayList<>(stations); this.priority=priority;
    }
    public boolean contains(BlockPos pos) {
        return Math.abs((long)pos.getX()-center.getX()) <= radius && Math.abs((long)pos.getZ()-center.getZ()) <= radius;
    }
    public boolean overlaps(BlockPos pos, int r) {
        return Math.abs((long)pos.getX()-center.getX()) <= (long)radius+r && Math.abs((long)pos.getZ()-center.getZ()) <= (long)radius+r;
    }
    public Station station(BlockPos pos) { return stations.stream().filter(s -> s.position().equals(pos)).findFirst().orElse(null); }
}
