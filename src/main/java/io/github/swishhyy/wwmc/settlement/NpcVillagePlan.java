package io.github.swishhyy.wwmc.settlement;

import io.github.swishhyy.wwmc.VC;
import io.github.swishhyy.wwmc.block.StationBlock;
import io.github.swishhyy.wwmc.core.StructureRole;
import java.util.ArrayList;
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

/** Furnished cottages and workshops around a connected, terraced village square. */
public final class NpcVillagePlan {
    public record Column(int x,int z) {}
    public record Lot(int x,int z,int radiusX,int radiusZ) {}
    public static final List<Lot> LOTS=List.of(new Lot(-13,-13,4,4),new Lot(13,-13,4,4),
            new Lot(-13,13,4,4),new Lot(13,13,4,4),new Lot(-13,0,4,4),new Lot(13,0,4,4),
            new Lot(-21,-13,2,3),new Lot(21,-13,2,3),new Lot(-13,20,4,2),
            new Lot(0,-13,4,2),new Lot(13,20,4,3),new Lot(0,0,4,4));
    private static Set<Column> footprint;
    private NpcVillagePlan() {}

    /** Roof overhangs, gardens and road columns are included; untouched corners need no clearing. */
    public static Set<Column> footprint() {
        if(footprint==null) {
            var town=new Settlement(new UUID(0,0),new UUID(0,0),"Village",BlockPos.ZERO,240,List.of(),List.of(),"balanced");
            town.trading.layout=NpcVillageLayout.flat("oak");
            Set<Column> columns=new LinkedHashSet<>();
            for(var p:blueprint(town)) columns.add(new Column(p.pos().getX(),p.pos().getZ()));
            footprint=Set.copyOf(columns);
        }
        return footprint;
    }

    public static List<Station> stations(Settlement town) {
        var b=new Builder(town);
        return List.of(b.station(-13,-13,StructureRole.HOUSING,Direction.SOUTH),
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
                b.station(3,2,StructureRole.COURIER,Direction.NORTH));
    }

    public static List<NpcSettlements.Placement> blueprint(Settlement town) {
        var b=new Builder(town);
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
        } else for(int x=-5;x<=-3;x++) for(int z=3;z<=5;z++) b.slab(x,well+3,z,b.palette.slab(),SlabType.BOTTOM);
        b.block(0,0,0,VC.BANNER.get());
        b.block(3,1,2,b.palette.log()); b.block(3,2,2,Blocks.OAK_PLANKS);
        b.block(2,1,2,Blocks.OAK_PLANKS); b.block(4,1,2,Blocks.OAK_PLANKS);
        b.block(2,0,-3,Blocks.STONE_BRICK_STAIRS); b.block(3,0,-3,Blocks.STONE_BRICK_STAIRS);
        b.put(0,1,3,Blocks.BELL.defaultBlockState().setValue(net.minecraft.world.level.block.BellBlock.ATTACHMENT,
                net.minecraft.world.level.block.state.properties.BellAttachType.CEILING),true);
        b.block(0,2,3,b.palette.log());
        for(int[] p:new int[][]{{-5,-5},{5,-5},{-5,5},{5,5},{-6,-20},{6,20},{-20,6},{20,6}}) b.lamp(p[0],p[1]);
        for(int[] p:new int[][]{{-8,-8},{8,-8},{-8,8},{8,8},{-3,-10},{3,-10}}) b.planter(p[0],p[1]);
        for(Station station:stations(town)) b.put(station.position().getX()-town.center.getX(),
                station.position().getY()-town.center.getY(),station.position().getZ()-town.center.getZ(),
                VC.STATIONS.get(station.role()).get().defaultBlockState().setValue(StationBlock.FACING,station.facing())
                        .setValue(StationBlock.RANGE,station.range()),true);
        return b.finish();
    }

    private record Palette(Block log,Block floor,Block wall,Block roof,Block stairs,Block slab) {
        static Palette of(String name) {
            return switch(name) {
                case "sandstone" -> new Palette(Blocks.CUT_SANDSTONE,Blocks.SMOOTH_SANDSTONE,Blocks.SANDSTONE,
                        Blocks.CUT_RED_SANDSTONE,Blocks.RED_SANDSTONE_STAIRS,Blocks.RED_SANDSTONE_SLAB);
                case "acacia" -> new Palette(Blocks.STRIPPED_ACACIA_LOG,Blocks.ACACIA_PLANKS,nativeBlock("white_terracotta"),
                        Blocks.ACACIA_PLANKS,Blocks.ACACIA_STAIRS,Blocks.ACACIA_SLAB);
                case "spruce" -> new Palette(Blocks.STRIPPED_SPRUCE_LOG,Blocks.SPRUCE_PLANKS,nativeBlock("white_terracotta"),
                        Blocks.DARK_OAK_PLANKS,Blocks.DARK_OAK_STAIRS,Blocks.DARK_OAK_SLAB);
                default -> new Palette(Blocks.STRIPPED_OAK_LOG,Blocks.OAK_PLANKS,nativeBlock("white_terracotta"),
                        Blocks.SPRUCE_PLANKS,Blocks.SPRUCE_STAIRS,Blocks.SPRUCE_SLAB);
            };
        }
    }
    private static Block nativeBlock(String key) {
        return net.minecraft.core.registries.BuiltInRegistries.BLOCK.getValue(net.minecraft.resources.Identifier.withDefaultNamespace(key));
    }
    private record Floor(int height,Block block) {}
    private static final class Builder {
        final Settlement town; final NpcVillageLayout layout; final Palette palette;
        final int[] lotHeights=new int[LOTS.size()];
        final Map<Column,Floor> floors=new LinkedHashMap<>();
        final Map<BlockPos,NpcSettlements.Placement> detail=new LinkedHashMap<>();
        Builder(Settlement town) {
            this.town=town; layout=town.trading.layout; palette=Palette.of(layout.palette());
            for(int n=0;n<LOTS.size();n++) {
                Lot lot=LOTS.get(n);
                lotHeights[n]=lot.x()==0 && lot.z()==0 ? 0 : layout.highest(lot.x(),lot.z(),lot.radiusX(),lot.radiusZ());
            }
        }
        int lot(int x,int z) {
            for(int n=0;n<LOTS.size();n++) if(LOTS.get(n).x()==x && LOTS.get(n).z()==z) return lotHeights[n];
            return walk(x,z);
        }
        int walk(int x,int z) {
            int high=layout.ground(x,z);
            for(int n=0;n<LOTS.size();n++) {
                Lot lot=LOTS.get(n);
                if(Math.abs(x-lot.x())<=lot.radiusX() && Math.abs(z-lot.z())<=lot.radiusZ()) return lotHeights[n];
            }
            for(int n=0;n<LOTS.size();n++) {
                Lot lot=LOTS.get(n);
                int distance=Math.max(0,Math.abs(x-lot.x())-lot.radiusX())+Math.max(0,Math.abs(z-lot.z())-lot.radiusZ());
                high=Math.max(high,lotHeights[n]-distance);
            }
            return high;
        }
        void path(int x,int z) { floor(x,z,walk(x,z),Blocks.DIRT_PATH); }
        void floor(int x,int z,int y,Block block) { floors.put(new Column(x,z),new Floor(y,block)); }
        void put(int x,int y,int z,BlockState state,boolean protect) {
            floors.putIfAbsent(new Column(x,z),new Floor(walk(x,z),Blocks.GRASS_BLOCK));
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
        void warehouse() {
            Room r=room(13,13,Direction.NORTH); r.shell();
            for(int x:new int[]{-2,2}) for(int z=-2;z<=2;z++) r.block(x,0,z,Blocks.BARREL);
            for(int x:new int[]{-2,2}) for(int z:new int[]{-2,0,2}) r.block(x,1,z,Blocks.BARREL);
            r.block(0,0,-2,Blocks.CRAFTING_TABLE); r.block(1,0,-2,Blocks.HAY_BLOCK);
            r.block(-1,0,-2,Blocks.HAY_BLOCK);
        }
        void bakery() {
            Room r=room(-13,0,Direction.EAST); r.shell();
            r.block(-2,0,-2,Blocks.BARREL); r.block(-2,0,-1,Blocks.SMOKER); r.block(-2,0,0,Blocks.FURNACE);
            r.block(-2,1,-1,Blocks.BRICKS); r.block(-2,2,-1,Blocks.BRICKS);
            for(int y=3;y<=7;y++) r.block(-2,y,-1,Blocks.BRICKS);
            r.block(2,0,-2,Blocks.CRAFTING_TABLE); r.block(2,0,-1,Blocks.BARREL);
            r.slab(2,0,1,Blocks.OAK_SLAB,SlabType.TOP); r.block(2,1,1,Blocks.CAKE);
        }
        void smithy() {
            Room r=room(13,0,Direction.WEST); r.shell();
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

        List<NpcSettlements.Placement> finish() {
            Map<BlockPos,NpcSettlements.Placement> plan=new LinkedHashMap<>();
            for(var entry:floors.entrySet()) {
                int x=entry.getKey().x(),z=entry.getKey().z(),floor=entry.getValue().height();
                int bottom=Math.min(layout.ground(x,z)-1,floor-2);
                for(int y=bottom;y<floor-1;y++) add(plan,x,y,z,Blocks.COBBLESTONE.defaultBlockState(),true);
                add(plan,x,floor-1,z,entry.getValue().block().defaultBlockState(),true);
                int top=Math.max(layout.surface(x,z),floor+8);
                for(int y=floor;y<=top;y++) add(plan,x,y,z,Blocks.AIR.defaultBlockState(),false);
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
        else {
            var room=b.room(0,0,front); room.shell();
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
        var orientation=b.room(0,0,front);
        for(int x=-1;x<=1;x++) for(int z=4;z<=6;z++) b.path(orientation.px(x,z),orientation.pz(x,z));
        b.lamp(orientation.px(-4,6),orientation.pz(-4,6)); b.planter(orientation.px(4,6),orientation.pz(4,6));
        StructureRole role=kind.equals("cottage") ? StructureRole.HOUSING : kind.equals("workshop") ? StructureRole.CRAFTSMAN : StructureRole.COOK;
        b.put(0,0,0,VC.STATIONS.get(role).get().defaultBlockState().setValue(StationBlock.FACING,front),true);
        return b.finish();
    }
}
