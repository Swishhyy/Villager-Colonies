package io.github.swishhyy.wwmc.settlement;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.Collections;
import java.util.List;

/** Original terrain is saved before the first block changes, so construction resumes identically. */
public record NpcVillageLayout(String palette,List<Integer> ground,List<Integer> surface) {
    public static final int EXTENT=23,SIDE=2*EXTENT+1,CELLS=SIDE*SIDE;
    public static final Codec<NpcVillageLayout> CODEC=RecordCodecBuilder.create(i -> i.group(
            Codec.STRING.optionalFieldOf("palette","oak").forGetter(NpcVillageLayout::palette),
            Codec.INT.listOf().fieldOf("ground").forGetter(NpcVillageLayout::ground),
            Codec.INT.listOf().fieldOf("surface").forGetter(NpcVillageLayout::surface)
    ).apply(i,NpcVillageLayout::new));
    public NpcVillageLayout {
        if(ground.size()!=CELLS || surface.size()!=CELLS) throw new IllegalArgumentException("Invalid NPC village terrain snapshot");
        ground=List.copyOf(ground); surface=List.copyOf(surface);
    }
    public static NpcVillageLayout flat(String palette) {
        return new NpcVillageLayout(palette,Collections.nCopies(CELLS,0),Collections.nCopies(CELLS,0));
    }
    public static int index(int x,int z) { return (x+EXTENT)*SIDE+z+EXTENT; }
    public int ground(int x,int z) { return ground.get(index(x,z)); }
    public int surface(int x,int z) { return surface.get(index(x,z)); }
    public int highest(int x,int z,int radiusX,int radiusZ) {
        int high=Integer.MIN_VALUE;
        for(int dx=-radiusX;dx<=radiusX;dx++) for(int dz=-radiusZ;dz<=radiusZ;dz++)
            high=Math.max(high,ground(x+dx,z+dz));
        return high;
    }
}
