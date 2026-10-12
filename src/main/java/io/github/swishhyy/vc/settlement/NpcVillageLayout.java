package io.github.swishhyy.vc.settlement;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.Collections;
import java.util.List;

/**
 * Original terrain is saved before the first block changes, so construction resumes identically. Walled villages
 * (design {@link #WALLED}) read a wider square than older designs, for their wall, corner towers and graded verge.
 */
public record NpcVillageLayout(String palette,List<Integer> ground,List<Integer> surface,int design) {
    /** The walled design and the furthest reach of the newest village from its centre. */
    public static final int WALLED=5,EXTENT=29;
    /** How far villages of the earlier, unwalled designs reach. */
    public static final int LEGACY_EXTENT=23;
    public static final int SIDE=2*EXTENT+1,CELLS=SIDE*SIDE;
    public static final Codec<NpcVillageLayout> CODEC=RecordCodecBuilder.create(i -> i.group(
            Codec.STRING.optionalFieldOf("palette","oak").forGetter(NpcVillageLayout::palette),
            Codec.INT.listOf().fieldOf("ground").forGetter(NpcVillageLayout::ground),
            Codec.INT.listOf().fieldOf("surface").forGetter(NpcVillageLayout::surface),
            Codec.INT.optionalFieldOf("design",1).forGetter(NpcVillageLayout::design)
    ).apply(i,NpcVillageLayout::new));
    public NpcVillageLayout {
        int cells=cells(design);
        if(ground.size()!=cells || surface.size()!=cells) throw new IllegalArgumentException("Invalid NPC village terrain snapshot");
        ground=List.copyOf(ground); surface=List.copyOf(surface);
    }
    public NpcVillageLayout(String palette,List<Integer> ground,List<Integer> surface) { this(palette,ground,surface,WALLED); }
    public static int extent(int design) { return design>=WALLED ? EXTENT : LEGACY_EXTENT; }
    public static int side(int design) { return 2*extent(design)+1; }
    public static int cells(int design) { return side(design)*side(design); }
    public int extent() { return extent(design); }
    public int side() { return side(design); }
    public static NpcVillageLayout flat(String palette) { return flat(palette,WALLED); }
    public static NpcVillageLayout flat(String palette,int design) {
        return new NpcVillageLayout(palette,Collections.nCopies(cells(design),0),Collections.nCopies(cells(design),0),design);
    }
    /** A column's index in a walled layout's terrain lists. */
    public static int index(int x,int z) { return (x+EXTENT)*SIDE+z+EXTENT; }
    /** A column's index in this layout's own terrain lists. */
    public int cell(int x,int z) { int extent=extent(); return (x+extent)*side()+z+extent; }
    public boolean contains(int x,int z) { int extent=extent(); return Math.abs(x)<=extent && Math.abs(z)<=extent; }
    public int ground(int x,int z) { return ground.get(cell(x,z)); }
    public int surface(int x,int z) { return surface.get(cell(x,z)); }
    public int highest(int x,int z,int radiusX,int radiusZ) {
        int high=Integer.MIN_VALUE;
        for(int dx=-radiusX;dx<=radiusX;dx++) for(int dz=-radiusZ;dz<=radiusZ;dz++)
            high=Math.max(high,ground(x+dx,z+dz));
        return high;
    }
    public int median(int x,int z,int radiusX,int radiusZ) {
        var levels=new java.util.ArrayList<Integer>();
        for(int dx=-radiusX;dx<=radiusX;dx++) for(int dz=-radiusZ;dz<=radiusZ;dz++) levels.add(ground(x+dx,z+dz));
        levels.sort(Integer::compareTo); return levels.get(levels.size()/2);
    }
}
