package io.github.swishhyy.wwmc.settlement;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import io.github.swishhyy.wwmc.core.RoomBounds;
import io.github.swishhyy.wwmc.core.StationRange;
import io.github.swishhyy.wwmc.core.StructureRole;
import io.github.swishhyy.wwmc.core.Upgrades;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;

/** A registered station, with the range, crew and yield levels bought for it; a level its role cannot use is always zero. */
public record Station(BlockPos position, StructureRole role, Direction facing, int range, int crew, int yieldLevel) {
    // Older saves may contain a "room" field. It is ignored: all stations now use the automatic range.
    public static final Codec<Station> CODEC = RecordCodecBuilder.create(i -> i.group(
        BlockPos.CODEC.fieldOf("position").forGetter(Station::position),
        Codec.STRING.xmap(StructureRole::valueOf, StructureRole::name).fieldOf("role").forGetter(Station::role),
        Codec.STRING.xmap(Direction::valueOf,Direction::name).optionalFieldOf("facing",Direction.NORTH).forGetter(Station::facing),
        Codec.INT.optionalFieldOf("range",0).forGetter(Station::range),
        Codec.INT.optionalFieldOf("crew",0).forGetter(Station::crew),
        Codec.INT.optionalFieldOf("yield",0).forGetter(Station::yieldLevel)
    ).apply(i, Station::new));
    public Station {
        position=position.immutable();
        if(facing==Direction.UP || facing==Direction.DOWN) throw new IllegalArgumentException("Station facing must be horizontal");
        range=Upgrades.widens(role) ? Math.clamp(range,0,Upgrades.MAX_STATION_LEVEL) : 0;
        crew=Upgrades.hires(role) ? Math.clamp(crew,0,Upgrades.MAX_STATION_LEVEL) : 0;
        yieldLevel=Upgrades.yields(role) ? Math.clamp(yieldLevel,0,Upgrades.MAX_STATION_LEVEL) : 0;
    }
    public Station(BlockPos position,StructureRole role,Direction facing,int range,int crew) { this(position,role,facing,range,crew,0); }
    public Station(BlockPos position,StructureRole role,Direction facing) { this(position,role,facing,0,0); }
    public Station(BlockPos position,StructureRole role) { this(position,role,Direction.NORTH); }
    /** Blocks the station reaches in each direction. */
    public int radius() { return Upgrades.radius(role,range); }
    public RoomBounds area() { return StationRange.around(position.getX(),position.getY(),position.getZ(),radius()); }
    public boolean contains(BlockPos pos) {
        int radius=radius();
        return Math.abs((long)pos.getX()-position.getX())<=radius
                && Math.abs((long)pos.getY()-position.getY())<=radius
                && Math.abs((long)pos.getZ()-position.getZ())<=radius;
    }
    public Station withRange(int level) { return new Station(position,role,facing,level,crew,yieldLevel); }
    public Station withCrew(int level) { return new Station(position,role,facing,range,level,yieldLevel); }
    public Station withYield(int level) { return new Station(position,role,facing,range,crew,level); }
    public int upgradeLevel(Upgrades.Kind kind) { return switch(kind) { case RANGE -> range; case CREW -> crew; case YIELD -> yieldLevel; }; }
    public Station upgraded(Upgrades.Kind kind,int level) { return switch(kind) { case RANGE -> withRange(level); case CREW -> withCrew(level); case YIELD -> withYield(level); }; }
    /** "9x9x9" */
    public String size() { int side=radius()*2+1; return side+"x"+side+"x"+side; }
}
