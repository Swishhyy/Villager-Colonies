package io.github.swishhyy.vc.settlement;

import io.github.swishhyy.vc.VC;
import io.github.swishhyy.vc.block.StationBlock;
import io.github.swishhyy.vc.core.StructureRole;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.CropBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BedPart;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import net.minecraft.world.level.block.state.properties.SlabType;

/**
 * Furnished cottages and workshops around a connected village square. Walled villages (layout design
 * {@link NpcVillageLayout#WALLED}) also grade every column of their land into one smooth slope, and stand inside a
 * crenellated wall with four gatehouses, a sentry post at each gate and a tower at each corner. Levelled villages
 * ({@link NpcVillageLayout#LEVELLED}) keep their square and the streets around it at one level, level each street
 * across its width and step it with stairs. The rare city ({@link NpcVillageLayout#CITY}) builds four quarters of
 * houses and workshops around a market square, inside a wall with twelve towers.
 */
public final class NpcVillagePlan {
    public record Column(int x,int z) {}
    public record Lot(int x,int z,int radiusX,int radiusZ) {}
    public static final List<Lot> LOTS=List.of(new Lot(-13,-13,4,4),new Lot(13,-13,4,4),
            new Lot(-13,13,4,4),new Lot(13,13,4,4),new Lot(-13,0,4,4),new Lot(13,0,4,4),
            new Lot(-21,-13,2,3),new Lot(21,-13,2,3),new Lot(-13,20,4,2),
            new Lot(0,-13,4,2),new Lot(13,20,4,3),new Lot(0,0,4,4));
    private static final Lot PLAZA=LOTS.getLast();
    /** The wall runs this far from the centre; it stands this many blocks high, and the corner towers higher. */
    public static final int WALL=27,WALL_HEIGHT=4,TOWER_HEIGHT=7;
    /** Most a walled village cuts into or builds up the land under any column. */
    public static final int MAX_CUT=5,MAX_FILL=5;
    /** A three-block opening in the wall; {@code out} points away from the village. */
    public record Gate(int x,int z,Direction out) {
        boolean opening(int px,int pz) { return out.getStepX()!=0 ? px==x && Math.abs(pz-z)<=1 : pz==z && Math.abs(px-x)<=1; }
        /** A column {@code along} blocks across the gate and {@code depth} blocks out from the village centre. */
        Column at(int along,int depth) { return out.getStepX()!=0 ? new Column(out.getStepX()*depth,z+along) : new Column(x+along,out.getStepZ()*depth); }
    }
    /** Each gate continues a street: north and south the ring and centre streets, east and west the main street. */
    public static final List<Gate> GATES=List.of(new Gate(6,-WALL,Direction.NORTH),new Gate(0,WALL,Direction.SOUTH),
            new Gate(WALL,-6,Direction.EAST),new Gate(-WALL,-6,Direction.WEST));
    public static final List<Lot> TOWERS=List.of(new Lot(-WALL,-WALL,2,2),new Lot(WALL,-WALL,2,2),new Lot(-WALL,WALL,2,2),new Lot(WALL,WALL,2,2));
    /** A guard's sentry post just inside each gate, in the order of {@link #GATES}. */
    public static final List<Lot> SENTRIES=List.of(new Lot(9,-25,1,1),new Lot(3,25,1,1),new Lot(25,-9,1,1),new Lot(-25,-9,1,1));
    /**
     * A walled design's defences: how far out its wall runs, how high it and its towers stand, where its gate streets
     * begin, and its gates, towers and sentry posts (two posts to a gate where there are twice as many).
     */
    public record Walls(int wall,int height,int towerHeight,int street,List<Gate> gates,List<Lot> towers,List<Lot> sentries) {}
    private static final Walls VILLAGE_WALLS=new Walls(WALL,WALL_HEIGHT,TOWER_HEIGHT,22,GATES,TOWERS,SENTRIES);
    private static final Walls LEVELLED_WALLS=new Walls(WALL,WALL_HEIGHT+1,TOWER_HEIGHT+1,22,GATES,TOWERS,SENTRIES);
    /** The square and the ring of streets around it, kept at one level in a levelled village. */
    private static final Lot APRON=new Lot(0,0,7,7);

    /** The city wall's distance from the market square; avenues leave through a gate in each side. */
    public static final int CITY_WALL=37;
    public static final List<Gate> CITY_GATES=List.of(new Gate(0,-CITY_WALL,Direction.NORTH),new Gate(0,CITY_WALL,Direction.SOUTH),
            new Gate(CITY_WALL,0,Direction.EAST),new Gate(-CITY_WALL,0,Direction.WEST));
    /** A tower at each corner and where each cross street meets the wall. */
    public static final List<Lot> CITY_TOWERS=List.of(new Lot(-37,-37,2,2),new Lot(37,-37,2,2),new Lot(-37,37,2,2),new Lot(37,37,2,2),
            new Lot(-22,-37,2,2),new Lot(22,-37,2,2),new Lot(-22,37,2,2),new Lot(22,37,2,2),
            new Lot(-37,-22,2,2),new Lot(37,-22,2,2),new Lot(-37,22,2,2),new Lot(37,22,2,2));
    /** Two sentry posts inside each gate, in the order of {@link #CITY_GATES}. */
    public static final List<Lot> CITY_SENTRIES=List.of(new Lot(-4,-35,1,1),new Lot(4,-35,1,1),new Lot(-4,35,1,1),new Lot(4,35,1,1),
            new Lot(35,-4,1,1),new Lot(35,4,1,1),new Lot(-35,-4,1,1),new Lot(-35,4,1,1));
    private static final Walls CITY_WALLS=new Walls(CITY_WALL,5,9,32,CITY_GATES,CITY_TOWERS,CITY_SENTRIES);
    private static final Lot CITY_PLAZA=new Lot(0,0,10,10),CITY_APRON=new Lot(0,0,11,11);
    private enum Kind {
        COTTAGE(StructureRole.HOUSING),BUNKHOUSE(StructureRole.HOUSING),BARRACKS(StructureRole.BARRACKS),WAREHOUSE(StructureRole.WAREHOUSE),
        BAKERY(StructureRole.COOK),INN(StructureRole.COOK),SMITHY(StructureRole.SMELTERY),FORGE(StructureRole.BLACKSMITH),
        WORKSHOP(StructureRole.CRAFTSMAN),FARM(StructureRole.FARM),LUMBER(StructureRole.LUMBER),MINE(StructureRole.MINE),WATCH(StructureRole.GUARD);
        final StructureRole role;
        Kind(StructureRole role) { this.role=role; }
    }
    /** A city building on a nine by nine lot, its door facing {@code front} onto a street. */
    private record Building(Kind kind,int x,int z,Direction front) {}
    private static final List<Building> CITY_BUILDINGS=List.of(
            // The smiths' quarter, north-east.
            new Building(Kind.WAREHOUSE,16,-6,Direction.SOUTH),new Building(Kind.SMITHY,6,-16,Direction.WEST),new Building(Kind.FORGE,16,-16,Direction.EAST),
            new Building(Kind.WATCH,28,-6,Direction.SOUTH),new Building(Kind.SMITHY,28,-16,Direction.WEST),new Building(Kind.WORKSHOP,6,-28,Direction.WEST),
            new Building(Kind.MINE,16,-28,Direction.SOUTH),new Building(Kind.LUMBER,28,-28,Direction.WEST),
            // The farm quarter, south-east.
            new Building(Kind.BAKERY,16,6,Direction.NORTH),new Building(Kind.INN,6,16,Direction.WEST),new Building(Kind.FARM,16,16,Direction.EAST),
            new Building(Kind.FARM,28,6,Direction.NORTH),new Building(Kind.FARM,28,16,Direction.WEST),new Building(Kind.WATCH,6,28,Direction.WEST),
            new Building(Kind.FARM,16,28,Direction.NORTH),new Building(Kind.LUMBER,28,28,Direction.WEST),
            // The lower town, south-west.
            new Building(Kind.COTTAGE,-16,6,Direction.NORTH),new Building(Kind.COTTAGE,-6,16,Direction.EAST),new Building(Kind.BUNKHOUSE,-16,16,Direction.WEST),
            new Building(Kind.WATCH,-28,6,Direction.NORTH),new Building(Kind.FARM,-28,16,Direction.EAST),new Building(Kind.BARRACKS,-6,28,Direction.EAST),
            new Building(Kind.INN,-16,28,Direction.NORTH),new Building(Kind.MINE,-28,28,Direction.NORTH),
            // The old town and garrison, north-west.
            new Building(Kind.COTTAGE,-16,-6,Direction.SOUTH),new Building(Kind.COTTAGE,-6,-16,Direction.EAST),new Building(Kind.BUNKHOUSE,-16,-16,Direction.WEST),
            new Building(Kind.BARRACKS,-28,-6,Direction.SOUTH),new Building(Kind.BUNKHOUSE,-28,-16,Direction.EAST),new Building(Kind.WATCH,-6,-28,Direction.EAST),
            new Building(Kind.WAREHOUSE,-16,-28,Direction.SOUTH),new Building(Kind.FARM,-28,-28,Direction.EAST));
    private static final List<Lot> WALLED_LOTS,CITY_LOTS;
    static {
        var lots=new ArrayList<>(LOTS); lots.addAll(TOWERS); lots.addAll(SENTRIES);
        WALLED_LOTS=List.copyOf(lots);
        lots=new ArrayList<>();
        for(var building:CITY_BUILDINGS) lots.add(new Lot(building.x(),building.z(),4,4));
        lots.add(CITY_PLAZA); lots.addAll(CITY_TOWERS); lots.addAll(CITY_SENTRIES);
        CITY_LOTS=List.copyOf(lots);
    }
    /** Building lots levelled for a design; the plaza comes before the walled additions. */
    public static List<Lot> lots(int design) { return design>=NpcVillageLayout.CITY ? CITY_LOTS : design>=NpcVillageLayout.WALLED ? WALLED_LOTS : LOTS; }
    /** The square at the centre of a design, where its banner stands. */
    public static Lot plaza(int design) { return design>=NpcVillageLayout.CITY ? CITY_PLAZA : PLAZA; }
    public static Walls walls(int design) {
        return design>=NpcVillageLayout.CITY ? CITY_WALLS : design>=NpcVillageLayout.LEVELLED ? LEVELLED_WALLS : VILLAGE_WALLS;
    }
    /** The street just outside the gate on one side of a walled village, where roads from other towns arrive. */
    public static Column gateOffset(Direction side) { return gateOffset(NpcVillageLayout.WALLED,side); }
    public static Column gateOffset(int design,Direction side) {
        return walls(design).gates().stream().filter(g -> g.out()==side).findFirst().orElseThrow().at(0,NpcVillageLayout.extent(design)+1);
    }
    /** The side of a village facing something this far east and south of its centre. */
    public static Direction side(int dx,int dz) {
        return Math.abs(dx)>=Math.abs(dz) ? dx>=0 ? Direction.EAST : Direction.WEST : dz>=0 ? Direction.SOUTH : Direction.NORTH;
    }
    public static boolean walled(Settlement town) { return town.trading.layout!=null && town.trading.layout.design()>=NpcVillageLayout.WALLED; }
    public static boolean city(Settlement town) { return town.trading.layout!=null && town.trading.layout.design()>=NpcVillageLayout.CITY; }
    /** Outside the gate of a walled village that faces something this far east and south of its centre. */
    public static BlockPos entrance(Settlement town,int dx,int dz) {
        int design=town.trading.layout==null ? NpcVillageLayout.WALLED : town.trading.layout.design();
        var column=gateOffset(design,side(dx,dz)); return town.center.offset(column.x(),0,column.z());
    }
    private static final Map<Integer,Set<Column>> FOOTPRINTS=new java.util.concurrent.ConcurrentHashMap<>();
    private NpcVillagePlan() {}

    /** Roof overhangs, gardens and road columns are included; untouched corners need no clearing. */
    public static Set<Column> footprint() { return footprint(NpcVillageLayout.LEVELLED); }
    public static Set<Column> footprint(int design) {
        return FOOTPRINTS.computeIfAbsent(design,d -> {
            var town=new Settlement(new UUID(0,0),new UUID(0,0),"Village",BlockPos.ZERO,240,List.of(),List.of(),"balanced");
            town.trading.layout=NpcVillageLayout.flat("oak",d);
            Set<Column> columns=new LinkedHashSet<>();
            for(var p:blueprint(town)) columns.add(new Column(p.pos().getX(),p.pos().getZ()));
            return Set.copyOf(columns);
        });
    }

    public static List<Station> stations(Settlement town) {
        var b=new Builder(town);
        if(b.layout.design()>=NpcVillageLayout.CITY) return b.cityStations();
        var stations=new ArrayList<>(List.of(b.station(-13,-13,StructureRole.HOUSING,Direction.SOUTH),
                b.station(13,-13,StructureRole.HOUSING,Direction.SOUTH),
                b.station(-13,13,StructureRole.HOUSING,Direction.NORTH),
                b.station(13,13,StructureRole.WAREHOUSE,Direction.NORTH),
                b.station(0,-13,StructureRole.TRADER,Direction.SOUTH),
                b.station(-21,-13,StructureRole.GUARD,Direction.EAST),
                b.station(-13,0,StructureRole.COOK,Direction.EAST),
                b.station(13,20,StructureRole.FARM,Direction.WEST),
                b.station(-14,19,StructureRole.LUMBER,Direction.EAST).withRange(3),
                b.station(21,-13,StructureRole.MINE,Direction.WEST),
                b.station(13,0,StructureRole.SMELTERY,Direction.WEST),
                b.station(3,2,StructureRole.COURIER,Direction.NORTH)));
        if(b.layout.design()>=NpcVillageLayout.WALLED) b.sentryStations(stations);
        return List.copyOf(stations);
    }

    public static List<NpcSettlements.Placement> blueprint(Settlement town) {
        return plan(town).finish();
    }
    /** The graded ground height of every column of a walled village's land, indexed like its layout; null for older designs. */
    public static int[] grade(Settlement town) {
        var b=new Builder(town); return b.gradedPaths==null ? null : b.gradedPaths.clone();
    }
    /** Judge the actual graded floors, not just the variation inside a building lot. */
    public static String earthworkIssue(Settlement town) {
        var walledDesign=town.trading.layout.design()>=NpcVillageLayout.WALLED;
        var b=walledDesign ? new Builder(town) : plan(town);
        if(walledDesign) {
            if(b.conflict) return "slopes too steep to grade between the buildings and the land around the wall";
            int extent=b.layout.extent();
            for(int x=-extent;x<=extent;x++) for(int z=-extent;z<=extent;z++) {
                int i=b.layout.cell(x,z),change=b.gradedPaths[i]-b.smoothed[i];
                if(change<-MAX_CUT) return "excavation exceeds "+MAX_CUT+" blocks at "+new Column(x,z);
                if(change>MAX_FILL) return "foundation fill exceeds "+MAX_FILL+" blocks at "+new Column(x,z);
            }
            return null;
        }
        for(var entry:b.floors.entrySet()) {
            int ground=b.layout.ground(entry.getKey().x(),entry.getKey().z());
            int cut=ground-entry.getValue().height();
            if(cut>6) return "excavation exceeds six blocks at "+entry.getKey();
            if(cut< -8) return "foundation fill exceeds eight blocks at "+entry.getKey();
        }
        return null;
    }
    private static Builder plan(Settlement town) {
        var b=new Builder(town);
        if(b.layout.design()>=NpcVillageLayout.CITY) { b.city(); return b; }
        boolean levelled=b.layout.design()>=NpcVillageLayout.LEVELLED;
        // A loop connects every doorway; short spurs lead to the outer work yards.
        for(int n=-22;n<=22;n++) for(int w=-1;w<=1;w++) {
            b.path(n,-6+w); b.path(n,6+w); b.path(-6+w,n); b.path(6+w,n);
        }
        for(int n=-8;n<=8;n++) for(int w=-1;w<=1;w++) b.path(n,w);
        for(int n=-10;n<=23;n++) for(int w=-1;w<=1;w++) b.path(w,n);
        for(int x:new int[]{-13,13}) for(int z:new int[]{-8,-7,7,8}) for(int w=-1;w<=1;w++) b.path(x+w,z);
        for(int z=-13;z<=-6;z++) { b.path(-18,z); b.path(18,z); }
        for(int x=-18;x<=-6;x++) b.path(x,20);
        for(int x=6;x<=9;x++) b.path(x,20);
        for(int x=-4;x<=4;x++) for(int z=-4;z<=4;z++) b.floor(x,z,0,
                (Math.abs(x)==4 || Math.abs(z)==4) ? Blocks.STONE_BRICKS : Blocks.COBBLESTONE);

        b.cottage(-13,-13,Direction.SOUTH,nativeBlock("yellow_bed"));
        b.cottage(13,-13,Direction.SOUTH,nativeBlock("red_bed"));
        b.cottage(-13,13,Direction.NORTH,nativeBlock("light_blue_bed"));
        b.warehouse(); b.bakery(); b.smithy(); b.watchPost(); b.lumberYard(); b.mine(); b.market(); b.farm();

        // A covered well, noticeboard and seats make the square a place rather than a crossroads.
        int well=b.walk(-4,4);
        for(int x=-5;x<=-3;x++) for(int z=3;z<=5;z++) b.floor(x,z,well,Blocks.STONE_BRICKS);
        for(int[] p:new int[][]{{-5,3},{-3,3},{-5,5},{-3,5}}) {
            b.block(p[0],well,p[1],Blocks.COBBLESTONE_WALL);
            b.block(p[0],well+1,p[1],b.palette.log()); b.block(p[0],well+2,p[1],b.palette.log());
        }
        b.block(-4,well-1,4,Blocks.WATER,false);
        if(b.layout.design()>=2) {
            for(int x=-6;x<=-2;x++) for(int z=2;z<=6;z++) b.slab(x,well+3,z,b.palette.slab(),SlabType.BOTTOM);
            for(int x=-5;x<=-3;x++) b.block(x,well+2,3,b.palette.log());
            for(int x=-5;x<=-3;x++) b.block(x,well+2,5,b.palette.log());
            for(int x=-1;x<=1;x++) b.block(x,2,3,b.palette.log());
            b.block(-1,1,3,b.palette.log()); b.block(1,1,3,b.palette.log());
            // The bell frame's posts stand on the square rather than hanging a block above it.
            if(levelled) { b.block(-1,0,3,b.palette.log()); b.block(1,0,3,b.palette.log()); }
        } else for(int x=-5;x<=-3;x++) for(int z=3;z<=5;z++) b.slab(x,well+3,z,b.palette.slab(),SlabType.BOTTOM);
        b.block(0,0,0,VC.BANNER.get());
        b.block(3,1,2,b.palette.log()); b.block(3,2,2,Blocks.OAK_PLANKS);
        b.block(2,1,2,Blocks.OAK_PLANKS); b.block(4,1,2,Blocks.OAK_PLANKS);
        if(levelled) {
            // A bench facing the banner, a row in from the rim so it does not read as a gap in the kerb.
            BlockState seat=Blocks.STONE_BRICK_STAIRS.defaultBlockState().setValue(BlockStateProperties.HORIZONTAL_FACING,Direction.NORTH);
            b.put(2,0,-2,seat,true); b.put(3,0,-2,seat,true);
        } else { b.block(2,0,-3,Blocks.STONE_BRICK_STAIRS); b.block(3,0,-3,Blocks.STONE_BRICK_STAIRS); }
        b.put(0,1,3,Blocks.BELL.defaultBlockState().setValue(net.minecraft.world.level.block.BellBlock.ATTACHMENT,
                net.minecraft.world.level.block.state.properties.BellAttachType.CEILING),true);
        b.block(0,2,3,b.palette.log());
        for(int[] p:new int[][]{{-5,-5},{5,-5},{-5,5},{5,5},{-6,-20},{6,20},{-20,6},{20,6}}) b.lamp(p[0],p[1]);
        for(int[] p:new int[][]{{-8,-8},{8,-8},{-8,8},{8,8},{-3,-10},{3,-10}}) b.planter(p[0],p[1]);
        if(b.layout.design()>=NpcVillageLayout.WALLED) b.fortify();
        b.stationBlocks();
        if(b.layout.design()>=NpcVillageLayout.WALLED) b.terrain();
        if(levelled) b.steps();
        return b;
    }

    /**
     * Building materials. Walled villages build in timber with bark-covered frames instead of pale plaster, and their
     * ground, fill and fortifications follow the biome too.
     */
    private record Palette(Block log,Block floor,Block wall,Block roof,Block stairs,Block slab,
            Block ground,Block fill,Block fortBase,Block fort,Block fortAccent) {
        static Palette of(String name,int design) {
            boolean walled=design>=NpcVillageLayout.WALLED;
            return switch(name) {
                case "sandstone" -> new Palette(Blocks.CUT_SANDSTONE,Blocks.SMOOTH_SANDSTONE,Blocks.SANDSTONE,
                        Blocks.CUT_RED_SANDSTONE,Blocks.RED_SANDSTONE_STAIRS,Blocks.RED_SANDSTONE_SLAB,
                        Blocks.SAND,Blocks.SANDSTONE,Blocks.SANDSTONE,Blocks.CUT_SANDSTONE,Blocks.SMOOTH_SANDSTONE);
                case "acacia" -> new Palette(walled ? Blocks.ACACIA_LOG : Blocks.STRIPPED_ACACIA_LOG,Blocks.ACACIA_PLANKS,nativeBlock("white_terracotta"),
                        Blocks.ACACIA_PLANKS,Blocks.ACACIA_STAIRS,Blocks.ACACIA_SLAB,
                        Blocks.GRASS_BLOCK,Blocks.DIRT,Blocks.PACKED_MUD,Blocks.MUD_BRICKS,Blocks.PACKED_MUD);
                case "spruce" -> new Palette(walled ? Blocks.SPRUCE_LOG : Blocks.STRIPPED_SPRUCE_LOG,walled ? Blocks.DARK_OAK_PLANKS : Blocks.SPRUCE_PLANKS,
                        walled ? Blocks.SPRUCE_PLANKS : nativeBlock("white_terracotta"),
                        Blocks.DARK_OAK_PLANKS,Blocks.DARK_OAK_STAIRS,Blocks.DARK_OAK_SLAB,
                        Blocks.GRASS_BLOCK,Blocks.DIRT,Blocks.COBBLESTONE,Blocks.STONE_BRICKS,Blocks.MOSSY_STONE_BRICKS);
                default -> new Palette(walled ? Blocks.OAK_LOG : Blocks.STRIPPED_OAK_LOG,walled ? Blocks.SPRUCE_PLANKS : Blocks.OAK_PLANKS,
                        walled ? Blocks.OAK_PLANKS : nativeBlock("white_terracotta"),
                        Blocks.SPRUCE_PLANKS,Blocks.SPRUCE_STAIRS,Blocks.SPRUCE_SLAB,
                        Blocks.GRASS_BLOCK,Blocks.DIRT,Blocks.COBBLESTONE,Blocks.STONE_BRICKS,Blocks.MOSSY_STONE_BRICKS);
            };
        }
    }
    private static Block nativeBlock(String key) {
        return net.minecraft.core.registries.BuiltInRegistries.BLOCK.getValue(net.minecraft.resources.Identifier.withDefaultNamespace(key));
    }
    /** A graded column: its top block and what fills beneath it; terrain columns keep plants and stay unprotected. */
    private record Floor(int height,Block block,Block fill,boolean clear,boolean protect) {
        Floor(int height,Block block) { this(height,block,Blocks.COBBLESTONE,true,true); }
    }
    /** A finished grading, shared by the several plans made of one site. */
    private record Grading(int[] lotHeights,int[] gradedPaths,int[] smoothed,boolean conflict) {}
    private static final Map<NpcVillageLayout,Grading> GRADINGS=java.util.Collections.synchronizedMap(new LinkedHashMap<>(16,0.75F,true) {
        @Override protected boolean removeEldestEntry(Map.Entry<NpcVillageLayout,Grading> eldest) { return size()>32; }
    });
    private static final Map<Integer,StreetPlan> STREETS=new java.util.concurrent.ConcurrentHashMap<>();
    /**
     * The three-block streets of a levelled design as cross-sections, those that meet merged into one junction, nearest
     * the centre first; and which of the design's columns are street.
     */
    private record StreetPlan(List<List<Column>> sections,List<int[]> groups,boolean[] street) {}
    private static StreetPlan streets(int design) {
        return STREETS.computeIfAbsent(design,d -> {
            List<List<Column>> sections=new ArrayList<>();
            java.util.function.Consumer<java.util.function.IntFunction<Column>> section=lane -> sections.add(List.of(lane.apply(-1),lane.apply(0),lane.apply(1)));
            int extent=NpcVillageLayout.extent(d);
            if(d>=NpcVillageLayout.CITY) {
                for(int n=-extent;n<=extent;n++) { int m=n; section.accept(w -> new Column(w,m)); section.accept(w -> new Column(m,w)); }
                for(int n=-34;n<=34;n++) for(int at:new int[]{-22,22}) {
                    int m=n; section.accept(w -> new Column(at+w,m)); section.accept(w -> new Column(m,at+w));
                }
            } else {
                for(int n=-22;n<=22;n++) {
                    int m=n;
                    section.accept(w -> new Column(m,-6+w)); section.accept(w -> new Column(m,6+w));
                    section.accept(w -> new Column(-6+w,m)); section.accept(w -> new Column(6+w,m));
                }
                for(int n=-8;n<=8;n++) { int m=n; section.accept(w -> new Column(m,w)); }
                for(int n=-10;n<=23;n++) { int m=n; section.accept(w -> new Column(w,m)); }
                for(int x:new int[]{-13,13}) for(int z:new int[]{-8,-7,7,8}) section.accept(w -> new Column(x+w,z));
            }
            var walls=walls(d);
            for(var gate:walls.gates()) for(int depth=walls.street();depth<=extent;depth++) { int m=depth; section.accept(w -> gate.at(w,m)); }
            int side=NpcVillageLayout.side(d);
            int[] parent=new int[side*side];
            for(int i=0;i<parent.length;i++) parent[i]=-1;
            java.util.function.IntUnaryOperator find=new java.util.function.IntUnaryOperator() {
                @Override public int applyAsInt(int a) { while(parent[a]!=a) { parent[a]=parent[parent[a]]; a=parent[a]; } return a; }
            };
            boolean[] street=new boolean[side*side];
            for(var cross:sections) {
                int first=-1;
                for(Column c:cross) {
                    int i=NpcVillageLayout.index(d,c.x(),c.z());
                    if(parent[i]<0) parent[i]=i;
                    street[i]=true;
                    if(first<0) first=i; else parent[find.applyAsInt(i)]=find.applyAsInt(first);
                }
            }
            Map<Integer,List<Integer>> members=new LinkedHashMap<>();
            for(int i=0;i<parent.length;i++) if(parent[i]>=0) members.computeIfAbsent(find.applyAsInt(i),k -> new ArrayList<>()).add(i);
            List<int[]> groups=new ArrayList<>();
            for(var cells:members.values()) groups.add(cells.stream().mapToInt(Integer::intValue).toArray());
            groups.sort(java.util.Comparator.comparingInt((int[] g) -> java.util.Arrays.stream(g)
                    .map(i -> Math.max(Math.abs(i/side-extent),Math.abs(i%side-extent))).min().orElse(0)).thenComparingInt(g -> g[0]));
            return new StreetPlan(List.copyOf(sections),List.copyOf(groups),street);
        });
    }
    private static final class Builder {
        final Settlement town; final NpcVillageLayout layout; final Palette palette;
        final List<Lot> lots;
        final Lot plaza;
        final Walls walls;
        final int[] lotHeights;
        final int minimumGround;
        final int[] gradedPaths;
        /** Walled villages: the natural ground with lone holes and spikes smoothed away, and whether grading failed. */
        int[] smoothed;
        boolean conflict;
        final Map<Column,Floor> floors=new LinkedHashMap<>();
        final Map<BlockPos,NpcSettlements.Placement> detail=new LinkedHashMap<>();
        Builder(Settlement town) {
            this.town=town; layout=town.trading.layout; palette=Palette.of(layout.palette(),layout.design());
            lots=lots(layout.design()); plaza=plaza(layout.design()); walls=walls(layout.design()); lotHeights=new int[lots.size()];
            minimumGround=layout.ground().stream().mapToInt(Integer::intValue).min().orElse(0);
            if(layout.design()>=NpcVillageLayout.LEVELLED) {
                var known=GRADINGS.get(layout);
                if(known==null) {
                    var graded=gradeLevelled();
                    known=new Grading(lotHeights.clone(),graded,smoothed,conflict);
                    GRADINGS.put(layout,known);
                }
                System.arraycopy(known.lotHeights(),0,lotHeights,0,lotHeights.length);
                gradedPaths=known.gradedPaths(); smoothed=known.smoothed(); conflict=known.conflict();
                return;
            }
            levelLots(plaza);
            gradedPaths=layout.design()>=NpcVillageLayout.WALLED ? gradeAll() : layout.design()>=4 ? gradePaths() : null;
        }
        /** Each lot at the median of its ground, near the given square and a block per road step from its neighbours. */
        void levelLots(Lot anchor) {
            for(int n=0;n<lots.size();n++) {
                Lot lot=lots.get(n);
                lotHeights[n]=lot.x()==0 && lot.z()==0 ? 0 : layout.design()>=3
                        ? layout.median(lot.x(),lot.z(),lot.radiusX(),lot.radiusZ()) : layout.highest(lot.x(),lot.z(),lot.radiusX(),lot.radiusZ());
            }
            if(layout.design()>=3) {
                for(int n=0;n<lots.size();n++) {
                    int reach=separation(lots.get(n),anchor); lotHeights[n]=Math.clamp(lotHeights[n],-reach,reach);
                }
                // One block per road step between terraces, including on steeper natural hills. A levelled village's square
                // keeps its own level, which its banner and fittings are built at; land that cannot meet it is refused.
                boolean square=layout.design()>=NpcVillageLayout.LEVELLED;
                for(int pass=0;pass<lots.size();pass++) for(int a=0;a<lots.size();a++) {
                    if(square && lots.get(a)==plaza) continue;
                    for(int b=0;b<lots.size();b++) lotHeights[a]=Math.min(lotHeights[a],lotHeights[b]+separation(lots.get(a),lots.get(b)));
                }
            }
        }
        /**
         * A levelled village holds its lots near the square and the ring of streets around it; where that would cut or
         * fill too much, near the square alone; where even that fails, it grades as a walled village does.
         */
        int[] gradeLevelled() {
            Lot apron=layout.design()>=NpcVillageLayout.CITY ? CITY_APRON : APRON;
            for(Lot anchor:new Lot[]{apron,plaza}) {
                levelLots(anchor); conflict=false;
                int[] grade=levelled(apron);
                if(!conflict && !excessive(grade)) return grade;
            }
            levelLots(plaza); conflict=false;
            return gradeAll();
        }
        boolean excessive(int[] grade) {
            for(int i=0;i<grade.length;i++) { int change=grade[i]-smoothed[i]; if(change<-MAX_CUT || change>MAX_FILL) return true; }
            return false;
        }
        /** The natural ground with lone holes and spikes, such as a cave mouth or a boulder, smoothed away. */
        void smooth() {
            int extent=layout.extent();
            smoothed=new int[layout.side()*layout.side()];
            int[] window=new int[9];
            for(int x=-extent;x<=extent;x++) for(int z=-extent;z<=extent;z++) {
                int n=0;
                for(int dx=-1;dx<=1;dx++) for(int dz=-1;dz<=1;dz++)
                    window[n++]=layout.ground(Math.clamp(x+dx,-extent,extent),Math.clamp(z+dz,-extent,extent));
                Arrays.sort(window); smoothed[layout.cell(x,z)]=window[4];
            }
        }
        /**
         * The walled grading, with the square's apron pinned to the square's level and each street then pinned level
         * across its width, nearest the centre first, wherever the slope allows. Turf beside a street a block higher
         * rises to meet it inside the wall, so street edges show grass rather than earth.
         */
        int[] levelled(Lot apron) {
            int extent=layout.extent(),cells=layout.side()*layout.side();
            smooth();
            int[] lower=new int[cells],upper=new int[cells];
            boolean[] pinned=new boolean[cells];
            for(int x=-extent;x<=extent;x++) for(int z=-extent;z<=extent;z++) {
                int i=layout.cell(x,z),allowance=extent-Math.max(Math.abs(x),Math.abs(z))+1;
                lower[i]=smoothed[i]-allowance; upper[i]=smoothed[i]+allowance;
            }
            for(int n=0;n<lots.size();n++) {
                Lot lot=lots.get(n);
                for(int x=lot.x()-lot.radiusX();x<=lot.x()+lot.radiusX();x++) for(int z=lot.z()-lot.radiusZ();z<=lot.z()+lot.radiusZ();z++) {
                    int i=layout.cell(x,z); lower[i]=upper[i]=lotHeights[n]; pinned[i]=true;
                }
            }
            spread(upper,true); spread(lower,false);
            List<Integer> ring=new ArrayList<>(); boolean fits=true;
            for(int x=apron.x()-apron.radiusX();x<=apron.x()+apron.radiusX();x++) for(int z=apron.z()-apron.radiusZ();z<=apron.z()+apron.radiusZ();z++) {
                int i=layout.cell(x,z);
                if(pinned[i]) continue;
                ring.add(i); fits&=lower[i]<=0 && upper[i]>=0 && -smoothed[i]<=MAX_FILL && smoothed[i]<=MAX_CUT;
            }
            if(fits) pin(ring,0,lower,upper,pinned);
            var plan=streets(layout.design());
            for(int[] group:plan.groups()) {
                List<Integer> open=new ArrayList<>(); int lo=Integer.MIN_VALUE,hi=Integer.MAX_VALUE;
                for(int i:group) if(!pinned[i]) { open.add(i); lo=Math.max(lo,lower[i]); hi=Math.min(hi,upper[i]); }
                if(open.size()<2 || lo>hi) continue;
                int[] levels=open.stream().mapToInt(i -> smoothed[i]).sorted().toArray();
                pin(open,Math.clamp(levels[levels.length/2],lo,hi),lower,upper,pinned);
            }
            int[] grade=new int[cells];
            for(int i=0;i<cells;i++) {
                if(lower[i]>upper[i]) { conflict=true; grade[i]=lower[i]; }
                else grade[i]=Math.clamp(smoothed[i],lower[i],upper[i]);
            }
            int[] cut=grade.clone(),fill=grade.clone();
            spread(cut,true); spread(fill,false);
            for(int i=0;i<cells;i++) {
                int even=Math.floorDiv(cut[i]+fill[i],2);
                grade[i]=lower[i]<=upper[i] ? Math.clamp(even,lower[i],upper[i]) : even;
            }
            boolean[] street=plan.street();
            for(int pass=0;pass<4;pass++) for(int x=-walls.wall()+1;x<walls.wall();x++) for(int z=-walls.wall()+1;z<walls.wall();z++) {
                int i=layout.cell(x,z),raised=grade[i]+1;
                if(pinned[i] || street[i] || raised>upper[i] || raised-smoothed[i]>MAX_FILL) continue;
                boolean beside=false,even=true;
                for(Direction d:Direction.Plane.HORIZONTAL) {
                    int j=layout.cell(x+d.getStepX(),z+d.getStepZ());
                    beside|=street[j] && grade[j]==raised; even&=Math.abs(grade[j]-raised)<=1;
                }
                if(beside && even) grade[i]=raised;
            }
            return grade;
        }
        /** Holds these columns at one level; both bounds stay a block per block walked, so this never conflicts. */
        void pin(List<Integer> cells,int level,int[] lower,int[] upper,boolean[] pinned) {
            for(int i:cells) { lower[i]=upper[i]=level; pinned[i]=true; }
            spread(upper,true); spread(lower,false);
        }
        /** Closest terrain-following grade constrained by every doorway and one-block steps. */
        int[] gradePaths() {
            int side=layout.side(),extent=layout.extent();
            int[] heights=new int[side*side],lower=new int[heights.length];
            for(int x=-extent;x<=extent;x++) for(int z=-extent;z<=extent;z++) {
                int lo=Integer.MIN_VALUE,hi=Integer.MAX_VALUE;
                for(int n=0;n<lots.size();n++) {
                    Lot lot=lots.get(n);
                    int distance=Math.max(0,Math.abs(x-lot.x())-lot.radiusX())+Math.max(0,Math.abs(z-lot.z())-lot.radiusZ());
                    lo=Math.max(lo,lotHeights[n]-distance); hi=Math.min(hi,lotHeights[n]+distance);
                }
                int i=layout.cell(x,z); lower[i]=lo;
                heights[i]=Math.clamp(layout.ground(x,z),lo,hi);
            }
            // Two Manhattan-distance passes prevent a cliff or hole from creating an unwalkable step.
            for(int i=0;i<heights.length;i++) {
                if(i>=side) heights[i]=Math.min(heights[i],heights[i-side]+1);
                if(i%side>0) heights[i]=Math.min(heights[i],heights[i-1]+1);
            }
            for(int i=heights.length-1;i>=0;i--) {
                if(i+side<heights.length) heights[i]=Math.min(heights[i],heights[i+side]+1);
                if(i%side<side-1) heights[i]=Math.min(heights[i],heights[i+1]+1);
            }
            for(int i=0;i<heights.length;i++) heights[i]=Math.max(heights[i],lower[i]);
            return heights;
        }
        /**
         * Walled villages grade every column into one slope of at most a block per block walked: level under each lot,
         * within a block of the untouched land at the edge, and otherwise as close to the natural ground as that allows,
         * so there are no trenches, terraces or retaining walls between the buildings.
         */
        int[] gradeAll() {
            int extent=layout.extent(),cells=layout.side()*layout.side();
            // A lone hole or spike, such as a cave mouth or a boulder, does not drag the slope around it.
            smooth();
            int[] lower=new int[cells],upper=new int[cells];
            for(int x=-extent;x<=extent;x++) for(int z=-extent;z<=extent;z++) {
                int i=layout.cell(x,z),allowance=extent-Math.max(Math.abs(x),Math.abs(z))+1;
                lower[i]=smoothed[i]-allowance; upper[i]=smoothed[i]+allowance;
            }
            for(int n=0;n<lots.size();n++) {
                Lot lot=lots.get(n);
                for(int x=lot.x()-lot.radiusX();x<=lot.x()+lot.radiusX();x++) for(int z=lot.z()-lot.radiusZ();z<=lot.z()+lot.radiusZ();z++) {
                    int i=layout.cell(x,z); lower[i]=upper[i]=lotHeights[n];
                }
            }
            spread(upper,true); spread(lower,false);
            int[] grade=new int[cells];
            for(int i=0;i<cells;i++) {
                if(lower[i]>upper[i]) { conflict=true; grade[i]=lower[i]; }
                else grade[i]=Math.clamp(smoothed[i],lower[i],upper[i]);
            }
            int[] cut=grade.clone(),fill=grade.clone();
            spread(cut,true); spread(fill,false);
            for(int i=0;i<cells;i++) {
                int even=Math.floorDiv(cut[i]+fill[i],2);
                grade[i]=lower[i]<=upper[i] ? Math.clamp(even,lower[i],upper[i]) : even;
            }
            return grade;
        }
        /** Limits heights to one block of change per block walked, by lowering peaks or by raising hollows. */
        void spread(int[] heights,boolean lowerPeaks) {
            int side=layout.side();
            for(int i=0;i<heights.length;i++) {
                if(i>=side) heights[i]=step(heights[i],heights[i-side],lowerPeaks);
                if(i%side>0) heights[i]=step(heights[i],heights[i-1],lowerPeaks);
            }
            for(int i=heights.length-1;i>=0;i--) {
                if(i+side<heights.length) heights[i]=step(heights[i],heights[i+side],lowerPeaks);
                if(i%side<side-1) heights[i]=step(heights[i],heights[i+1],lowerPeaks);
            }
        }
        static int step(int height,int neighbour,boolean lowerPeaks) { return lowerPeaks ? Math.min(height,neighbour+1) : Math.max(height,neighbour-1); }
        int separation(Lot a,Lot b) {
            return Math.max(0,Math.abs(a.x()-b.x())-a.radiusX()-b.radiusX())+Math.max(0,Math.abs(a.z()-b.z())-a.radiusZ()-b.radiusZ());
        }
        int lot(int x,int z) {
            for(int n=0;n<lots.size();n++) if(lots.get(n).x()==x && lots.get(n).z()==z) return lotHeights[n];
            return walk(x,z);
        }
        int walk(int x,int z) {
            if(gradedPaths!=null) return gradedPaths[layout.cell(x,z)];
            int high=layout.design()>=3 ? minimumGround : layout.ground(x,z);
            for(int n=0;n<lots.size();n++) {
                Lot lot=lots.get(n);
                if(Math.abs(x-lot.x())<=lot.radiusX() && Math.abs(z-lot.z())<=lot.radiusZ()) return lotHeights[n];
            }
            for(int n=0;n<lots.size();n++) {
                Lot lot=lots.get(n);
                int distance=Math.max(0,Math.abs(x-lot.x())-lot.radiusX())+Math.max(0,Math.abs(z-lot.z())-lot.radiusZ());
                high=Math.max(high,lotHeights[n]-distance);
            }
            return high;
        }
        boolean walled() { return layout.design()>=NpcVillageLayout.WALLED; }
        void path(int x,int z) {
            if(walled()) floors.put(new Column(x,z),new Floor(walk(x,z),Blocks.DIRT_PATH,palette.fill(),true,true));
            else floor(x,z,walk(x,z),Blocks.DIRT_PATH);
        }
        void floor(int x,int z,int y,Block block) { floors.put(new Column(x,z),new Floor(y,block)); }
        void put(int x,int y,int z,BlockState state,boolean protect) {
            floors.putIfAbsent(new Column(x,z),walled() ? new Floor(walk(x,z),palette.ground(),palette.fill(),true,true) : new Floor(walk(x,z),Blocks.GRASS_BLOCK));
            BlockPos p=town.center.offset(x,y,z); detail.put(p,new NpcSettlements.Placement(p,state,protect));
        }
        void block(int x,int y,int z,Block block) { block(x,y,z,block,true); }
        void block(int x,int y,int z,Block block,boolean protect) { put(x,y,z,block.defaultBlockState(),protect); }
        void slab(int x,int y,int z,Block slab,SlabType type) { put(x,y,z,slab.defaultBlockState().setValue(BlockStateProperties.SLAB_TYPE,type),true); }
        Station station(int x,int z,StructureRole role,Direction facing) {
            return new Station(town.center.offset(x,lot(x,z),z),role,facing);
        }
        Room room(int x,int z,Direction front) { return new Room(x,z,lot(x,z),front); }
        void cottage(int x,int z,Direction front,Block bedBlock) {
            Room r=room(x,z,front); r.shell();
            for(int dx:new int[]{-2,0}) for(int dz:new int[]{-2,1}) {
                BlockState bed=bedBlock.defaultBlockState().setValue(BlockStateProperties.HORIZONTAL_FACING,Direction.EAST);
                r.put(dx,0,dz,bed.setValue(BlockStateProperties.BED_PART,BedPart.FOOT));
                r.put(dx+1,0,dz,bed.setValue(BlockStateProperties.BED_PART,BedPart.HEAD));
            }
            r.block(2,0,-2,Blocks.BARREL); r.slab(-2,0,0,Blocks.OAK_SLAB,SlabType.TOP);
            r.block(2,0,1,Blocks.POTTED_POPPY);
        }
        void warehouse() { warehouse(13,13,Direction.NORTH); }
        void warehouse(int cx,int cz,Direction front) {
            Room r=room(cx,cz,front); r.shell();
            for(int x:new int[]{-2,2}) for(int z=-2;z<=2;z++) r.block(x,0,z,Blocks.BARREL);
            for(int x:new int[]{-2,2}) for(int z:new int[]{-2,0,2}) r.block(x,1,z,Blocks.BARREL);
            r.block(0,0,-2,Blocks.CRAFTING_TABLE); r.block(1,0,-2,Blocks.HAY_BLOCK);
            r.block(-1,0,-2,Blocks.HAY_BLOCK);
        }
        void bakery() { bakery(-13,0,Direction.EAST); }
        void bakery(int x,int z,Direction front) {
            Room r=room(x,z,front); r.shell();
            r.block(-2,0,-2,Blocks.BARREL); r.block(-2,0,-1,Blocks.SMOKER); r.block(-2,0,0,Blocks.FURNACE);
            r.block(-2,1,-1,Blocks.BRICKS); r.block(-2,2,-1,Blocks.BRICKS);
            for(int y=3;y<=7;y++) r.block(-2,y,-1,Blocks.BRICKS);
            r.block(2,0,-2,Blocks.CRAFTING_TABLE); r.block(2,0,-1,Blocks.BARREL);
            r.slab(2,0,1,Blocks.OAK_SLAB,SlabType.TOP); r.block(2,1,1,Blocks.CAKE);
        }
        void smithy() { smithy(13,0,Direction.WEST); }
        void smithy(int x,int z,Direction front) {
            Room r=room(x,z,front); r.shell();
            r.block(-2,0,-2,Blocks.BARREL); r.block(-2,0,-1,Blocks.FURNACE); r.block(-2,0,0,Blocks.BLAST_FURNACE);
            r.block(2,0,-2,Blocks.ANVIL); r.block(2,0,-1,Blocks.GRINDSTONE); r.block(2,0,1,Blocks.SMITHING_TABLE);
            for(int y=1;y<=7;y++) r.block(-2,y,0,Blocks.STONE_BRICKS);
        }
        void watchPost() {
            int y=lot(-21,-13);
            for(int x=-23;x<=-19;x++) for(int z=-15;z<=-11;z++) {
                floor(x,z,y,Blocks.STONE_BRICKS);
                if(x==-23 || x==-19 || z==-15 || z==-11) for(int h=0;h<=3;h++) block(x,y+h,z,
                        (x==-23 || x==-19) && (z==-15 || z==-11) ? Blocks.STONE_BRICKS : palette.wall());
                slab(x,y+4,z,Blocks.STONE_BRICK_SLAB,layout.design()>=2 ? x==-21 && z==-13 ? SlabType.DOUBLE : SlabType.TOP : SlabType.BOTTOM);
                if((x==-23 || x==-19 || z==-15 || z==-11) && ((x+z)&1)==0) block(x,y+5,z,Blocks.STONE_BRICK_WALL);
            }
            put(-19,y,-13,Blocks.OAK_DOOR.defaultBlockState().setValue(BlockStateProperties.HORIZONTAL_FACING,Direction.EAST)
                    .setValue(BlockStateProperties.DOUBLE_BLOCK_HALF,DoubleBlockHalf.LOWER),true);
            put(-19,y+1,-13,Blocks.OAK_DOOR.defaultBlockState().setValue(BlockStateProperties.HORIZONTAL_FACING,Direction.EAST)
                    .setValue(BlockStateProperties.DOUBLE_BLOCK_HALF,DoubleBlockHalf.UPPER),true);
            block(-21,y+2,-11,Blocks.GLASS); block(-23,y+2,-13,Blocks.GLASS);
            put(-21,y+3,-13,Blocks.LANTERN.defaultBlockState().setValue(BlockStateProperties.HANGING,true),true);
            block(-22,y,-14,Blocks.BARREL);
        }
        void lumberYard() {
            int y=lot(-13,20); shed(-13,20,4,2,y);
            for(int z=19;z<=21;z++) {
                put(-16,y,z,Blocks.OAK_LOG.defaultBlockState().setValue(BlockStateProperties.AXIS,Direction.Axis.Z),false);
                block(-16,y+1,z,Blocks.OAK_LOG,false);
            }
            block(-12,y,19,Blocks.BARREL); block(-15,y,19,Blocks.CRAFTING_TABLE);
            for(int x:new int[]{-20,-19}) { int h=walk(x,20); floor(x,20,h,Blocks.GRASS_BLOCK); block(x,h,20,Blocks.OAK_SAPLING,false); }
            // A real tree starts the forester's work even in a superflat village; the stored log pile is scenery.
            int root=walk(-20,13); floor(-20,13,root,Blocks.GRASS_BLOCK);
            for(int h=0;h<5;h++) block(-20,root+h,13,Blocks.OAK_LOG,false);
            for(int h=3;h<=5;h++) for(int dx=-2;dx<=2;dx++) for(int dz=-2;dz<=2;dz++) {
                if(dx==0 && dz==0 && h<5 || Math.abs(dx)==2 && Math.abs(dz)==2) continue;
                put(-20+dx,root+h,13+dz,Blocks.OAK_LEAVES.defaultBlockState()
                        .setValue(net.minecraft.world.level.block.LeavesBlock.DISTANCE,Math.abs(dx)+Math.abs(dz)+(h==5 ? 1 : 0)),false);
            }
        }
        void mine() {
            int y=lot(21,-13); shed(21,-13,2,3,y);
            block(22,y,-14,Blocks.BARREL); block(22,y,-12,Blocks.IRON_ORE,false);
            block(22,y+1,-12,Blocks.IRON_ORE,false); block(22,y,-11,Blocks.COAL_ORE,false);
            block(20,y,-15,Blocks.STONECUTTER);
        }
        void shed(int x,int z,int rx,int rz,int y) {
            for(int dx=-rx;dx<=rx;dx++) for(int dz=-rz;dz<=rz;dz++) {
                floor(x+dx,z+dz,y,Blocks.COBBLESTONE);
                if(Math.abs(dx)==rx && Math.abs(dz)==rz) for(int h=0;h<=2;h++) block(x+dx,y+h,z+dz,palette.log());
                slab(x+dx,y+3,z+dz,palette.slab(),SlabType.BOTTOM);
            }
            block(x,y+2,z,Blocks.LANTERN);
        }
        void market() {
            int y=lot(0,-13); shed(0,-13,4,2,y);
            for(int x=-4;x<=4;x++) for(int z=-15;z<=-11;z++) block(x,y+3,z,
                    (Math.floorMod(x,3)==0) ? nativeBlock("red_wool") : nativeBlock("white_wool"));
            for(int x:new int[]{-3,-2,2,3}) slab(x,y,-12,Blocks.OAK_SLAB,SlabType.TOP);
            block(-3,y,-14,Blocks.BARREL); block(3,y,-14,Blocks.BARREL);
            block(-2,y+1,-12,Blocks.POTTED_DANDELION); block(2,y+1,-12,Blocks.POTTED_POPPY);
        }
        void farm() {
            int y=lot(13,20);
            for(int x=10;x<=16;x++) for(int z=18;z<=22;z++) {
                floor(x,z,y,Blocks.FARMLAND);
                put(x,y-1,z,Blocks.FARMLAND.defaultBlockState().setValue(BlockStateProperties.MOISTURE,7),true);
                if(x!=13 || z!=20) put(x,y,z,((CropBlock)Blocks.CARROTS).getStateForAge(7),false);
            }
            block(13,y-1,20,Blocks.WATER,false); block(10,y-1,18,Blocks.DIRT); block(10,y,18,Blocks.BARREL);
            for(int x=9;x<=17;x++) for(int z=17;z<=23;z++) if(x==9 || x==17 || z==17 || z==23) {
                floor(x,z,y,Blocks.DIRT); put(x,y,z,Blocks.OAK_FENCE.defaultBlockState()
                        .setValue(BlockStateProperties.NORTH,(x==9 || x==17) && z>17)
                        .setValue(BlockStateProperties.SOUTH,(x==9 || x==17) && z<23)
                        .setValue(BlockStateProperties.WEST,(z==17 || z==23) && x>9)
                        .setValue(BlockStateProperties.EAST,(z==17 || z==23) && x<17),true);
            }
            put(9,y,20,Blocks.OAK_FENCE_GATE.defaultBlockState().setValue(BlockStateProperties.HORIZONTAL_FACING,Direction.EAST)
                    .setValue(BlockStateProperties.OPEN,true),true);
        }
        void lamp(int x,int z) {
            int y=walk(x,z); floor(x,z,y,Blocks.COBBLESTONE);
            for(int h=0;h<=2;h++) block(x,y+h,z,palette.log());
            block(x,y+3,z,Blocks.LANTERN);
        }
        void planter(int x,int z) {
            int y=walk(x,z); floor(x,z,y,Blocks.GRASS_BLOCK);
            block(x,y,z,Blocks.FLOWERING_AZALEA);
        }

        /** A workshop's or an inn's furniture inside its shell. */
        void shop(Room room,String kind) {
            room.block(-2,0,-2,Blocks.BARREL); room.block(2,0,-2,Blocks.BARREL);
            if(kind.equals("workshop")) {
                room.block(-2,0,0,Blocks.CRAFTING_TABLE); room.block(2,0,0,Blocks.SMITHING_TABLE);
                room.block(-2,0,2,Blocks.STONECUTTER); room.block(2,0,2,Blocks.GRINDSTONE);
            } else {
                room.block(-2,0,0,Blocks.SMOKER); room.block(2,0,0,Blocks.CRAFTING_TABLE);
                room.slab(-2,0,2,Blocks.OAK_SLAB,SlabType.TOP); room.slab(2,0,2,Blocks.OAK_SLAB,SlabType.TOP);
                room.block(-2,1,2,Blocks.CAKE); room.block(2,1,2,Blocks.POTTED_POPPY);
            }
        }

        // ---------- The city ----------
        /** Streets, the market square, the four quarters' buildings, the wall and every station, then the graded land. */
        void city() {
            for(var section:streets(layout.design()).sections()) for(Column c:section) path(c.x(),c.z());
            for(int x=-10;x<=10;x++) for(int z=-10;z<=10;z++) floor(x,z,0,Math.abs(x)==10 || Math.abs(z)==10 ? Blocks.STONE_BRICKS : Blocks.COBBLESTONE);
            Block[] beds={nativeBlock("yellow_bed"),nativeBlock("red_bed"),nativeBlock("light_blue_bed"),nativeBlock("lime_bed")};
            Block[] crops={Blocks.CARROTS,Blocks.WHEAT,Blocks.POTATOES,Blocks.WHEAT,Blocks.BEETROOTS,Blocks.CARROTS};
            int cottages=0,farms=0;
            for(var building:CITY_BUILDINGS) {
                int x=building.x(),z=building.z(); Direction front=building.front();
                switch(building.kind()) {
                    case COTTAGE -> cottage(x,z,front,beds[cottages++%beds.length]);
                    case BUNKHOUSE -> bunkhouse(x,z,front,nativeBlock("white_bed"),false);
                    case BARRACKS -> bunkhouse(x,z,front,nativeBlock("gray_bed"),true);
                    case WAREHOUSE -> warehouse(x,z,front);
                    case BAKERY -> bakery(x,z,front);
                    case INN -> { Room r=room(x,z,front); r.shell(); shop(r,"inn"); }
                    case SMITHY -> smithy(x,z,front);
                    case FORGE -> forge(x,z,front);
                    case WORKSHOP -> { Room r=room(x,z,front); r.shell(); shop(r,"workshop"); }
                    case FARM -> farm(x,z,front,crops[farms++%crops.length]);
                    case LUMBER -> lumberYard(x,z,front);
                    case MINE -> mine(x,z,front);
                    case WATCH -> watchPost(x,z,front);
                }
            }
            citySquare();
            fortify();
            stationBlocks();
            terrain();
            steps();
        }
        List<Station> cityStations() {
            List<Station> stations=new ArrayList<>();
            for(var building:CITY_BUILDINGS) {
                int x=building.x(),z=building.z(); var kind=building.kind();
                if(kind==Kind.BUNKHOUSE || kind==Kind.BARRACKS) {
                    // The bunks fill the middle of the room; the station keeps to the aisle at the back.
                    Room r=room(x,z,building.front()); stations.add(station(r.px(-1,-2),r.pz(-1,-2),kind.role,building.front()));
                } else if(kind==Kind.LUMBER) stations.add(station(x,z,kind.role,building.front()).withRange(3));
                else stations.add(station(x,z,kind.role,building.front()));
            }
            stations.add(station(6,-6,StructureRole.TRADER,Direction.SOUTH));
            stations.add(station(-6,4,StructureRole.COURIER,Direction.NORTH));
            stations.add(station(6,4,StructureRole.COURIER,Direction.NORTH));
            sentryStations(stations);
            return List.copyOf(stations);
        }
        /** A market hall, a covered well, the alarm bell, noticeboards over the couriers, the banner, lamps and planters. */
        void citySquare() {
            shed(6,-6,3,2,0);
            for(int x=3;x<=9;x++) for(int z=-8;z<=-4;z++) block(x,3,z,Math.floorMod(x,3)==0 ? nativeBlock("red_wool") : nativeBlock("white_wool"));
            for(int x:new int[]{3,4,8,9}) slab(x,0,-5,Blocks.OAK_SLAB,SlabType.TOP);
            block(3,0,-7,Blocks.BARREL); block(9,0,-7,Blocks.BARREL);
            block(4,1,-5,Blocks.POTTED_DANDELION); block(8,1,-5,Blocks.POTTED_POPPY);
            for(int x=-7;x<=-5;x++) for(int z=-7;z<=-5;z++) floor(x,z,0,Blocks.STONE_BRICKS);
            for(int[] p:new int[][]{{-7,-7},{-5,-7},{-7,-5},{-5,-5}}) {
                block(p[0],0,p[1],Blocks.COBBLESTONE_WALL); block(p[0],1,p[1],palette.log()); block(p[0],2,p[1],palette.log());
            }
            block(-6,-1,-6,Blocks.WATER,false);
            for(int x=-8;x<=-4;x++) for(int z=-8;z<=-4;z++) slab(x,3,z,palette.slab(),SlabType.BOTTOM);
            for(int x=-7;x<=-5;x++) { block(x,2,-7,palette.log()); block(x,2,-5,palette.log()); }
            for(int x:new int[]{5,7}) for(int h=0;h<=2;h++) block(x,h,8,palette.log());
            for(int x=5;x<=7;x++) block(x,3,8,palette.log());
            put(6,2,8,Blocks.BELL.defaultBlockState().setValue(net.minecraft.world.level.block.BellBlock.ATTACHMENT,
                    net.minecraft.world.level.block.state.properties.BellAttachType.CEILING),true);
            for(int x:new int[]{-6,6}) {
                block(x,1,4,palette.log()); block(x,2,4,Blocks.OAK_PLANKS); block(x-1,1,4,Blocks.OAK_PLANKS); block(x+1,1,4,Blocks.OAK_PLANKS);
            }
            block(0,0,0,VC.BANNER.get());
            for(int[] p:new int[][]{{9,9},{-9,9},{9,-9},{-9,-9},{11,11},{-11,11},{11,-11},{-11,-11},
                    {2,33},{-2,33},{2,-33},{-2,-33},{33,2},{33,-2},{-33,2},{-33,-2}}) lamp(p[0],p[1]);
            for(int[] p:new int[][]{{3,9},{-3,9},{3,-9},{-3,-9},{11,6},{-11,6},{11,-6},{-11,-6},{6,11},{-6,11},{6,-11},{-6,-11}}) planter(p[0],p[1]);
        }
        /** Six bunks in three rows, with an aisle either side of the middle row and a barracks' kit chest at the back. */
        void bunkhouse(int x,int z,Direction front,Block bedBlock,boolean barracks) {
            Room r=room(x,z,front); r.shell();
            BlockState bed=bedBlock.defaultBlockState().setValue(BlockStateProperties.HORIZONTAL_FACING,Direction.NORTH);
            for(int dx:new int[]{-2,0,2}) for(int dz:new int[]{-2,0}) {
                r.put(dx,0,dz,bed.setValue(BlockStateProperties.BED_PART,BedPart.HEAD));
                r.put(dx,0,dz+1,bed.setValue(BlockStateProperties.BED_PART,BedPart.FOOT));
            }
            if(barracks) r.block(1,0,-2,Blocks.BARREL);
        }
        void forge(int x,int z,Direction front) {
            Room r=room(x,z,front); r.shell();
            r.block(-2,0,-2,Blocks.BARREL); r.block(-2,0,-1,Blocks.FURNACE); r.block(-2,0,0,Blocks.FURNACE);
            for(int y=1;y<=7;y++) r.block(-2,y,-1,Blocks.BRICKS);
            r.block(0,0,-2,Blocks.SMITHING_TABLE); r.block(2,0,-2,Blocks.ANVIL); r.block(2,0,-1,Blocks.GRINDSTONE); r.block(2,0,1,Blocks.BARREL);
        }
        /** A fenced field of ripe crops around a water hole, its gate on the street side. */
        void farm(int x,int z,Direction front,Block crop) {
            int y=lot(x,z),rx=front.getAxis()==Direction.Axis.X ? 4 : 3,rz=7-rx;
            var ripe=((CropBlock)crop).getStateForAge(((CropBlock)crop).getMaxAge());
            for(int dx=1-rx;dx<=rx-1;dx++) for(int dz=1-rz;dz<=rz-1;dz++) {
                floor(x+dx,z+dz,y,Blocks.FARMLAND);
                put(x+dx,y-1,z+dz,Blocks.FARMLAND.defaultBlockState().setValue(BlockStateProperties.MOISTURE,7),true);
                if(dx!=0 || dz!=0) put(x+dx,y,z+dz,ripe,false);
            }
            block(x,y-1,z,Blocks.WATER,false); block(x-rx+1,y-1,z-rz+1,Blocks.DIRT); block(x-rx+1,y,z-rz+1,Blocks.BARREL);
            for(int dx=-rx;dx<=rx;dx++) for(int dz=-rz;dz<=rz;dz++) if(Math.abs(dx)==rx || Math.abs(dz)==rz) {
                floor(x+dx,z+dz,y,Blocks.DIRT); put(x+dx,y,z+dz,Blocks.OAK_FENCE.defaultBlockState()
                        .setValue(BlockStateProperties.NORTH,Math.abs(dx)==rx && dz>-rz)
                        .setValue(BlockStateProperties.SOUTH,Math.abs(dx)==rx && dz<rz)
                        .setValue(BlockStateProperties.WEST,Math.abs(dz)==rz && dx>-rx)
                        .setValue(BlockStateProperties.EAST,Math.abs(dz)==rz && dx<rx),true);
            }
            put(x+front.getStepX()*rx,y,z+front.getStepZ()*rz,Blocks.OAK_FENCE_GATE.defaultBlockState()
                    .setValue(BlockStateProperties.HORIZONTAL_FACING,front.getOpposite()).setValue(BlockStateProperties.OPEN,true),true);
        }
        /** An open-sided mine shed over ore the miner can work, whichever way it faces. */
        void mine(int x,int z,Direction front) {
            int y=lot(x,z); boolean across=front.getAxis()==Direction.Axis.Z;
            shed(x,z,across ? 3 : 2,across ? 2 : 3,y);
            Room r=room(x,z,front);
            r.block(-1,0,-1,Blocks.BARREL); r.block(-2,0,1,Blocks.STONECUTTER);
            block(r.px(1,-1),y,r.pz(1,-1),Blocks.IRON_ORE,false); block(r.px(1,-1),y+1,r.pz(1,-1),Blocks.IRON_ORE,false);
            block(r.px(2,-1),y,r.pz(2,-1),Blocks.COAL_ORE,false);
        }
        /** A log shed along one side, saplings and a grown oak for the forester, whichever way it faces. */
        void lumberYard(int x,int z,Direction front) {
            int y=lot(x,z); Room r=room(x,z,front); boolean across=front.getAxis()==Direction.Axis.Z;
            shed(r.px(3,0),r.pz(3,0),across ? 1 : 3,across ? 3 : 1,y);
            for(int dz=-2;dz<=0;dz++) {
                put(r.px(3,dz),y,r.pz(3,dz),Blocks.OAK_LOG.defaultBlockState().setValue(BlockStateProperties.AXIS,front.getAxis()),false);
                block(r.px(3,dz),y+1,r.pz(3,dz),Blocks.OAK_LOG,false);
            }
            r.block(3,0,2,Blocks.BARREL); r.block(3,0,1,Blocks.CRAFTING_TABLE);
            for(int dx:new int[]{-3,-1}) { int px=r.px(dx,2),pz=r.pz(dx,2),h=walk(px,pz); floor(px,pz,h,Blocks.GRASS_BLOCK); block(px,h,pz,Blocks.OAK_SAPLING,false); }
            int tx=r.px(-2,-2),tz=r.pz(-2,-2),root=walk(tx,tz); floor(tx,tz,root,Blocks.GRASS_BLOCK);
            for(int h=0;h<5;h++) block(tx,root+h,tz,Blocks.OAK_LOG,false);
            for(int h=3;h<=5;h++) for(int dx=-2;dx<=2;dx++) for(int dz=-2;dz<=2;dz++) {
                if(dx==0 && dz==0 && h<5 || Math.abs(dx)==2 && Math.abs(dz)==2) continue;
                put(tx+dx,root+h,tz+dz,Blocks.OAK_LEAVES.defaultBlockState()
                        .setValue(net.minecraft.world.level.block.LeavesBlock.DISTANCE,Math.abs(dx)+Math.abs(dz)+(h==5 ? 1 : 0)),false);
            }
        }
        /** A stone lookout tower in its lot, its door toward the street. */
        void watchPost(int x,int z,Direction front) {
            int y=lot(x,z); Room r=room(x,z,front);
            for(int dx=-2;dx<=2;dx++) for(int dz=-2;dz<=2;dz++) {
                int px=r.px(dx,dz),pz=r.pz(dx,dz); boolean edge=Math.abs(dx)==2 || Math.abs(dz)==2;
                floor(px,pz,y,Blocks.STONE_BRICKS);
                if(edge) for(int h=0;h<=3;h++) block(px,y+h,pz,Math.abs(dx)==2 && Math.abs(dz)==2 ? Blocks.STONE_BRICKS : palette.wall());
                slab(px,y+4,pz,Blocks.STONE_BRICK_SLAB,dx==0 && dz==0 ? SlabType.DOUBLE : SlabType.TOP);
                if(edge && ((dx+dz)&1)==0) block(px,y+5,pz,Blocks.STONE_BRICK_WALL);
            }
            BlockState door=Blocks.OAK_DOOR.defaultBlockState().setValue(BlockStateProperties.HORIZONTAL_FACING,Direction.SOUTH);
            r.put(0,0,2,door.setValue(BlockStateProperties.DOUBLE_BLOCK_HALF,DoubleBlockHalf.LOWER));
            r.put(0,1,2,door.setValue(BlockStateProperties.DOUBLE_BLOCK_HALF,DoubleBlockHalf.UPPER));
            r.block(0,2,-2,Blocks.GLASS); r.block(-2,2,0,Blocks.GLASS);
            r.put(0,3,0,Blocks.LANTERN.defaultBlockState().setValue(BlockStateProperties.HANGING,true));
            r.block(1,0,-1,Blocks.BARREL);
            lamp(r.px(-3,3),r.pz(-3,3)); planter(r.px(3,3),r.pz(3,3));
        }

        /** Walls, gatehouses, corner towers, sentry posts and the streets out through each gate. */
        void fortify() {
            for(var gate:walls.gates()) for(int depth=walls.street();depth<=layout.extent();depth++) for(int along=-1;along<=1;along++) {
                var column=gate.at(along,depth); path(column.x(),column.z());
            }
            int w=walls.wall();
            for(int n=-w;n<=w;n++) for(int[] c:new int[][]{{n,-w},{n,w},{-w,n},{w,n}}) wall(c[0],c[1]);
            for(var gate:walls.gates()) gatehouse(gate);
            for(var tower:walls.towers()) tower(tower);
            for(var sentry:walls.sentries()) sentry(sentry);
        }
        /** Stone with the odd mossy or weathered block, the same for every rebuild of the plan. */
        Block masonry(int x,int y,int z) { return Math.floorMod(x*31+y*7+z*17,9)==0 ? palette.fortAccent() : palette.fort(); }
        void wall(int x,int z) {
            if(walls.towers().stream().anyMatch(t -> Math.abs(x-t.x())<=t.radiusX() && Math.abs(z-t.z())<=t.radiusZ())
                    || walls.gates().stream().anyMatch(g -> g.opening(x,z))) return;
            int y=walk(x,z),height=walls.height(); floor(x,z,y,palette.fortBase());
            block(x,y,z,palette.fortBase());
            for(int h=1;h<height;h++) block(x,y+h,z,masonry(x,y+h,z));
            if(((x+z)&1)==0) block(x,y+height,z,masonry(x,y+height,z));
            // A lantern in every fifth gap of the battlements lights the wall walk, so nothing spawns on it.
            else if(layout.design()>=NpcVillageLayout.LEVELLED && Math.floorMod(x+z,10)==5) block(x,y+height,z,Blocks.LANTERN);
        }
        void gatehouse(Gate gate) {
            int top=Integer.MIN_VALUE,height=walls.height(),w=walls.wall();
            for(int along=-2;along<=2;along++) { var c=gate.at(along,w); top=Math.max(top,walk(c.x(),c.z())); }
            for(int along:new int[]{-2,2}) {
                var c=gate.at(along,w); int y=walk(c.x(),c.z());
                floor(c.x(),c.z(),y,palette.fortBase());
                for(int h=y;h<=top+height+1;h++) block(c.x(),h,c.z(),h==y ? palette.fortBase() : palette.fort());
                block(c.x(),top+height+2,c.z(),Blocks.LANTERN);
            }
            for(int along=-1;along<=1;along++) { var c=gate.at(along,w); block(c.x(),top+height,c.z(),palette.fort()); }
            var middle=gate.at(0,w);
            block(middle.x(),top+height+1,middle.z(),palette.fort());
            put(middle.x(),top+height-1,middle.z(),Blocks.LANTERN.defaultBlockState().setValue(BlockStateProperties.HANGING,true),true);
        }
        void tower(Lot lot) {
            int cx=lot.x(),cz=lot.z(),y=lot(cx,cz),sx=Integer.signum(cx),sz=Integer.signum(cz),height=walls.towerHeight();
            for(int dx=-2;dx<=2;dx++) for(int dz=-2;dz<=2;dz++) {
                int x=cx+dx,z=cz+dz; boolean edge=Math.abs(dx)==2 || Math.abs(dz)==2;
                floor(x,z,y,edge ? palette.fortBase() : Blocks.COBBLESTONE);
                if(edge) {
                    block(x,y,z,palette.fortBase());
                    for(int h=1;h<height;h++) block(x,y+h,z,masonry(x,y+h,z));
                    block(x,y+height,z,palette.fort());
                    if(((dx+dz)&1)==0) block(x,y+height+1,z,masonry(x,y+height+1,z));
                } else block(x,y+height,z,palette.fort());
            }
            // A tower part way along a wall (a city's) looks out of its outer face alone: its sides meet the wall, and on
            // the north and south walls the ladder moves to the inner face to leave the outer one open.
            boolean along=Math.abs(cx)!=Math.abs(cz),northSouth=Math.abs(cz)>Math.abs(cx);
            // Arrow slits in the faces that do not hold the ladder or the door.
            for(int[] face:new int[][]{{2,0},{-2,0},{0,2},{0,-2}}) {
                if(along) { if(northSouth ? face[0]!=0 || face[1]!=2*sz : face[1]!=0 || face[0]!=2*sx) continue; }
                else if(face[0]==0 && face[1]==2*sz || face[1]==0 && face[0]==-2*sx) continue;
                put(cx+face[0],y+3,cz+face[1],Blocks.AIR.defaultBlockState(),false);
            }
            // A door faces into the village; a ladder climbs the outer wall to the lookout.
            BlockState door=Blocks.OAK_DOOR.defaultBlockState().setValue(BlockStateProperties.HORIZONTAL_FACING,sx<0 ? Direction.EAST : Direction.WEST);
            put(cx-2*sx,y,cz-sz,door.setValue(BlockStateProperties.DOUBLE_BLOCK_HALF,DoubleBlockHalf.LOWER),true);
            put(cx-2*sx,y+1,cz-sz,door.setValue(BlockStateProperties.DOUBLE_BLOCK_HALF,DoubleBlockHalf.UPPER),true);
            int ladderZ=along && northSouth ? -sz : sz;
            BlockState ladder=Blocks.LADDER.defaultBlockState().setValue(BlockStateProperties.HORIZONTAL_FACING,ladderZ<0 ? Direction.SOUTH : Direction.NORTH);
            for(int h=0;h<=height;h++) put(cx,y+h,cz+ladderZ,ladder,true);
            block(cx,y+height+1,cz,Blocks.LANTERN);
        }
        void sentry(Lot lot) {
            int x=lot.x(),z=lot.z(),y=lot(x,z);
            for(int dx=-1;dx<=1;dx++) for(int dz=-1;dz<=1;dz++) {
                floor(x+dx,z+dz,y,Blocks.COBBLESTONE);
                if(dx!=0 && dz!=0) for(int h=0;h<3;h++) block(x+dx,y+h,z+dz,palette.log());
                slab(x+dx,y+3,z+dz,palette.slab(),SlabType.BOTTOM);
            }
            put(x,y+2,z,Blocks.LANTERN.defaultBlockState().setValue(BlockStateProperties.HANGING,true),true);
        }
        /** A guard's post just inside each gate, facing out through it. */
        void sentryStations(List<Station> stations) {
            var sentries=walls.sentries(); var gates=walls.gates();
            for(int n=0;n<sentries.size();n++)
                stations.add(station(sentries.get(n).x(),sentries.get(n).z(),StructureRole.GUARD,gates.get(n*gates.size()/sentries.size()).out()));
        }
        void stationBlocks() {
            for(Station station:stations(town)) put(station.position().getX()-town.center.getX(),
                    station.position().getY()-town.center.getY(),station.position().getZ()-town.center.getZ(),
                    VC.STATIONS.get(station.role()).get().defaultBlockState().setValue(StationBlock.FACING,station.facing())
                            .setValue(StationBlock.RANGE,station.range()),true);
        }
        /**
         * Where a street steps down a block, a stair sunk into the upper column climbs it; on a crest or a corner, a slab.
         * The square's rim steps down the same way where it stands above a street.
         */
        void steps() {
            for(var entry:List.copyOf(floors.entrySet())) {
                Column c=entry.getKey(); Floor f=entry.getValue();
                boolean rim=square(c) && f.block()==Blocks.STONE_BRICKS;
                if(f.block()!=Blocks.DIRT_PATH && !rim) continue;
                Direction down=null; int lower=0; boolean higher=false;
                for(Direction d:Direction.Plane.HORIZONTAL) {
                    Column next=new Column(c.x()+d.getStepX(),c.z()+d.getStepZ()); Floor n=floors.get(next);
                    if(n==null) continue;
                    higher|=n.height()>f.height();
                    if(n.height()==f.height()-1 && walkable(next,n)) { lower++; down=d; }
                }
                BlockPos at=town.center.offset(c.x(),f.height()-1,c.z());
                // A slab beside higher ground would leave a step and a half up to it.
                if(lower==0 || lower>1 && higher || detail.containsKey(at)) continue;
                Block stair=rim ? Blocks.STONE_BRICK_STAIRS : palette.stairs(),slab=rim ? Blocks.STONE_BRICK_SLAB : palette.slab();
                put(c.x(),f.height()-1,c.z(),lower==1 ? stair.defaultBlockState().setValue(BlockStateProperties.HORIZONTAL_FACING,down.getOpposite())
                        : slab.defaultBlockState(),true);
            }
        }
        boolean square(Column c) { return Math.abs(c.x()-plaza.x())<=plaza.radiusX() && Math.abs(c.z()-plaza.z())<=plaza.radiusZ(); }
        /** Ground a walker steps down onto: a street, the square or a building's lot, such as its doorstep. */
        boolean walkable(Column c,Floor f) {
            if(f.block()==Blocks.DIRT_PATH || square(c)) return true;
            for(Lot lot:lots) if(Math.abs(c.x()-lot.x())<=lot.radiusX() && Math.abs(c.z()-lot.z())<=lot.radiusZ())
                return !walls.towers().contains(lot) && !walls.sentries().contains(lot);
            return false;
        }
        /** Every remaining column of a walled village takes its graded height; plants stay where the ground is unchanged. */
        void terrain() {
            int extent=layout.extent();
            for(int x=-extent;x<=extent;x++) for(int z=-extent;z<=extent;z++) {
                int grade=walk(x,z);
                floors.putIfAbsent(new Column(x,z),new Floor(grade,palette.ground(),palette.fill(),grade!=layout.ground(x,z),false));
            }
        }

        List<NpcSettlements.Placement> finish() {
            Map<BlockPos,NpcSettlements.Placement> plan=new LinkedHashMap<>();
            for(var entry:floors.entrySet()) {
                int x=entry.getKey().x(),z=entry.getKey().z(); Floor floor=entry.getValue();
                int bottom=Math.min(layout.ground(x,z)-1,floor.height()-2);
                for(int y=bottom;y<floor.height()-1;y++) add(plan,x,y,z,floor.fill().defaultBlockState(),floor.protect());
                add(plan,x,floor.height()-1,z,floor.block().defaultBlockState(),floor.protect());
                if(!floor.clear()) continue;
                int top=Math.max(layout.surface(x,z),floor.height()+8);
                for(int y=floor.height();y<=top;y++) add(plan,x,y,z,Blocks.AIR.defaultBlockState(),false);
            }
            plan.putAll(detail); return List.copyOf(plan.values());
        }
        void add(Map<BlockPos,NpcSettlements.Placement> plan,int x,int y,int z,BlockState state,boolean protect) {
            BlockPos p=town.center.offset(x,y,z); plan.put(p,new NpcSettlements.Placement(p,state,protect));
        }
        final class Room {
            final int x,z,y; final Direction front;
            Room(int x,int z,int y,Direction front) { this.x=x; this.z=z; this.y=y; this.front=front; }
            int px(int dx,int dz) { return x+switch(front) { case NORTH -> -dx; case EAST -> dz; case WEST -> -dz; default -> dx; }; }
            int pz(int dx,int dz) { return z+switch(front) { case NORTH -> -dz; case EAST -> -dx; case WEST -> dx; default -> dz; }; }
            Direction turn(Direction direction) { return switch(front) { case NORTH -> direction.getOpposite(); case EAST -> direction.getCounterClockWise(); case WEST -> direction.getClockWise(); default -> direction; }; }
            void put(int dx,int dy,int dz,BlockState state) {
                if(state.hasProperty(BlockStateProperties.HORIZONTAL_FACING)) state=state.setValue(BlockStateProperties.HORIZONTAL_FACING,
                        turn(state.getValue(BlockStateProperties.HORIZONTAL_FACING)));
                Builder.this.put(px(dx,dz),y+dy,pz(dx,dz),state,true);
            }
            void block(int dx,int dy,int dz,Block block) { put(dx,dy,dz,block.defaultBlockState()); }
            void slab(int dx,int dy,int dz,Block slab,SlabType type) { put(dx,dy,dz,slab.defaultBlockState().setValue(BlockStateProperties.SLAB_TYPE,type)); }
            void shell() {
                for(int dx=-4;dx<=4;dx++) for(int dz=-4;dz<=4;dz++) {
                    floor(px(dx,dz),pz(dx,dz),y,Math.abs(dx)<=3 && Math.abs(dz)<=3 ? palette.floor() : Blocks.COBBLESTONE);
                    if(Math.abs(dx)<=3 && Math.abs(dz)<=3 && (Math.abs(dx)==3 || Math.abs(dz)==3)) {
                        for(int h=0;h<=2;h++) block(dx,h,dz,h==0 ? Blocks.COBBLESTONE : palette.wall());
                        if(Math.abs(dx)==3 && Math.abs(dz)==3) for(int h=0;h<=3;h++) block(dx,h,dz,palette.log());
                    }
                    int rise=4-Math.abs(dx);
                    if(dx==0) slab(dx,3+rise,dz,palette.slab(),SlabType.BOTTOM);
                    else put(dx,3+rise,dz,palette.stairs().defaultBlockState()
                            .setValue(BlockStateProperties.HORIZONTAL_FACING,dx<0 ? Direction.EAST : Direction.WEST));
                }
                for(int dx=-3;dx<=3;dx++) {
                    for(int dz:new int[]{-3,3}) for(int h=3;h<7-Math.abs(dx);h++) block(dx,h,dz,dx==0 ? palette.log() : palette.wall());
                    block(dx,3,0,palette.log());
                }
                for(int dx:new int[]{-3,3}) { block(dx,1,-1,Blocks.GLASS); block(dx,1,0,Blocks.GLASS); block(dx,2,0,Blocks.GLASS); }
                block(-1,1,-3,Blocks.GLASS); block(0,1,-3,Blocks.GLASS); block(1,1,-3,Blocks.GLASS);
                BlockState door=Blocks.OAK_DOOR.defaultBlockState().setValue(BlockStateProperties.HORIZONTAL_FACING,Direction.SOUTH);
                put(0,0,3,door.setValue(BlockStateProperties.DOUBLE_BLOCK_HALF,DoubleBlockHalf.LOWER));
                put(0,1,3,door.setValue(BlockStateProperties.DOUBLE_BLOCK_HALF,DoubleBlockHalf.UPPER));
                put(0,2,0,Blocks.LANTERN.defaultBlockState().setValue(BlockStateProperties.HANGING,true));
                block(-2,0,4,Blocks.POTTED_DANDELION); block(2,0,4,Blocks.POTTED_POPPY);
            }
        }
    }

    /** Small additions use the same furnished architecture as the founding village. */
    public static List<NpcSettlements.Placement> addition(Settlement town,BlockPos center,String kind) {
        return addition(town,center,kind,Direction.SOUTH);
    }
    public static List<NpcSettlements.Placement> addition(Settlement town,BlockPos center,String kind,Direction front) {
        var preview=new Settlement(town.id,town.owner,town.name,center,town.radius,List.of(),List.of(),"balanced");
        preview.trading.layout=NpcVillageLayout.flat(town.trading.layout==null ? "oak" : town.trading.layout.palette());
        var b=new Builder(preview);
        if(kind.equals("cottage")) b.cottage(0,0,front,nativeBlock("green_bed"));
        else { var room=b.room(0,0,front); room.shell(); b.shop(room,kind); }
        var orientation=b.room(0,0,front);
        for(int x=-1;x<=1;x++) for(int z=4;z<=6;z++) b.path(orientation.px(x,z),orientation.pz(x,z));
        b.lamp(orientation.px(-4,6),orientation.pz(-4,6)); b.planter(orientation.px(4,6),orientation.pz(4,6));
        StructureRole role=kind.equals("cottage") ? StructureRole.HOUSING : kind.equals("workshop") ? StructureRole.CRAFTSMAN : StructureRole.COOK;
        b.put(0,0,0,VC.STATIONS.get(role).get().defaultBlockState().setValue(StationBlock.FACING,front),true);
        return b.finish();
    }
}
