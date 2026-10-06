package io.github.swishhyy.wwmc.settlement;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import io.github.swishhyy.wwmc.core.RoomBounds;
import io.github.swishhyy.wwmc.core.StationRange;
import io.github.swishhyy.wwmc.core.StructureRole;
import net.minecraft.core.BlockPos;

public record Station(BlockPos position, StructureRole role) {
    // Older saves may contain a "room" field. It is ignored: all stations now use the fixed range.
    public static final Codec<Station> CODEC = RecordCodecBuilder.create(i -> i.group(
        BlockPos.CODEC.fieldOf("position").forGetter(Station::position),
        Codec.STRING.xmap(StructureRole::valueOf, StructureRole::name).fieldOf("role").forGetter(Station::role)
    ).apply(i, Station::new));
    public Station { position=position.immutable(); }
    public RoomBounds area() { return StationRange.around(position.getX(),position.getY(),position.getZ()); }
    public boolean contains(BlockPos pos) {
        return Math.abs((long)pos.getX()-position.getX())<=StationRange.RADIUS
                && Math.abs((long)pos.getY()-position.getY())<=StationRange.RADIUS
                && Math.abs((long)pos.getZ()-position.getZ())<=StationRange.RADIUS;
    }
}
