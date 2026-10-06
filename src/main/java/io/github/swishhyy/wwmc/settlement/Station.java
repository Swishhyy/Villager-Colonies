package io.github.swishhyy.wwmc.settlement;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import io.github.swishhyy.wwmc.core.RoomBounds;
import io.github.swishhyy.wwmc.core.StructureRole;
import java.util.Optional;
import net.minecraft.core.BlockPos;

public record Station(BlockPos position, StructureRole role, Optional<RoomBounds> room) {
    private static final Codec<RoomBounds> BOUNDS = RecordCodecBuilder.create(i -> i.group(
        Codec.INT.fieldOf("min_x").forGetter(RoomBounds::minX), Codec.INT.fieldOf("min_y").forGetter(RoomBounds::minY),
        Codec.INT.fieldOf("min_z").forGetter(RoomBounds::minZ), Codec.INT.fieldOf("max_x").forGetter(RoomBounds::maxX),
        Codec.INT.fieldOf("max_y").forGetter(RoomBounds::maxY), Codec.INT.fieldOf("max_z").forGetter(RoomBounds::maxZ)
    ).apply(i, RoomBounds::new));
    public static final Codec<Station> CODEC = RecordCodecBuilder.create(i -> i.group(
        BlockPos.CODEC.fieldOf("position").forGetter(Station::position),
        Codec.STRING.xmap(StructureRole::valueOf, StructureRole::name).fieldOf("role").forGetter(Station::role),
        BOUNDS.optionalFieldOf("room").forGetter(Station::room)
    ).apply(i, Station::new));
    public Station(BlockPos position, StructureRole role) { this(position.immutable(), role, Optional.empty()); }
}
