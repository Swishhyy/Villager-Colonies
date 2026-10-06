package io.github.swishhyy.wwmc;

import com.mojang.serialization.JsonOps;
import io.github.swishhyy.wwmc.core.MiningLayout;
import io.github.swishhyy.wwmc.core.RoomBounds;
import io.github.swishhyy.wwmc.core.StructureRole;
import io.github.swishhyy.wwmc.settlement.*;
import java.util.*;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import net.neoforged.testframework.junit.EphemeralTestServerProvider;

public final class QuarryCraftingChecks {
    private static int checks;
    private static void check(boolean ok,String message) { checks++; if(!ok) throw new AssertionError(message); }
    @Test void quarryStaircase() {
        RoomBounds pit=MiningLayout.quarry(8,40,0,-1,70,-64);
        for(int i=0;i<MiningLayout.RING;i++) {
            MiningLayout.Cell cell=MiningLayout.ringCell(pit,i,0),next=MiningLayout.ringCell(pit,i+1,0);
            check(MiningLayout.ringIndex(pit,cell.x(),cell.z())==i,"Each edge cell has one place on the ring");
            check(Math.abs(cell.x()-next.x())+Math.abs(cell.z()-next.z())==1,"Neighboring ring cells share a side, so steps connect");
        }
        check(MiningLayout.ringIndex(pit,8,24)==-1 && MiningLayout.ringIndex(pit,8,32)==-1,"Interior and outside columns carry no steps");
        int start=MiningLayout.ringStart(pit,8,40,0,-1),top=64;
        check(MiningLayout.ringCell(pit,start,top).equals(new MiningLayout.Cell(8,top,31)),"The stairs start on the edge facing the station, in line with it");
        for(int y=pit.minY();y<=top;y++) {
            int steps=0;
            for(int x=pit.minX();x<=pit.maxX();x++) for(int z=pit.minZ();z<=pit.maxZ();z++) if(MiningLayout.stair(pit,start,top,x,y,z)) steps++;
            check(steps==1,"Each layer below the rim keeps exactly one step");
        }
        check(!MiningLayout.stair(pit,start,top,8,top+1,31),"Nothing above the rim is kept as a step");
        for(int step=0;step<top-pit.minY();step++) {
            MiningLayout.Cell here=MiningLayout.stairStand(pit,start,top,step),below=MiningLayout.stairStand(pit,start,top,step+1);
            check(MiningLayout.stair(pit,start,top,here.x(),here.y()-1,here.z()),"Workers stand on top of a kept step");
            check(below.y()==here.y()-1 && Math.abs(below.x()-here.x())+Math.abs(below.z()-here.z())==1,"Each next step is one block down and one block over");
            check(!MiningLayout.stair(pit,start,top,below.x(),below.y(),below.z()) && !MiningLayout.stair(pit,start,top,below.x(),below.y()+1,below.z()),"Every step has two blocks of headroom");
        }
        var corner=MiningLayout.quarry(100,-3,1,0,64,0);
        check(MiningLayout.ringIndex(corner,corner.minX(),Math.clamp(-3,corner.minZ(),corner.maxZ()))==MiningLayout.ringStart(corner,100,-3,1,0),"Eastward quarries start on their western edge");

        BlockPos station=new BlockPos(8,65,40);
        ExcavationJob job=new ExcavationJob(UUID.randomUUID(),station,StructureRole.QUARRY,Direction.NORTH,70,-64,24,4,0,List.of(),false,64);
        check(job.hasStairs() && job.rim().equals(new MiningLayout.Cell(8,65,32)),"Crews enter from the ground just outside the first step");
        check(job.floorY()==70 && job.stepAt(8,65,31)==-1,"Nothing below an untouched top layer is routed onto the stairs");
        ExcavationJob deep=new ExcavationJob(UUID.randomUUID(),station,StructureRole.QUARRY,Direction.NORTH,70,-64,24,4,31*256,List.of(),false,64);
        check(deep.floorY()==39 && deep.stepAt(8,65,31)==0 && deep.stepAt(3,40,20)==25 && deep.stepAt(8,65,33)==-1,"Positions in the dug pit map to the matching staircase level");
        check(deep.stepAt(3,30,20)==-1,"Mine tunnels beneath the working floor are never routed onto the quarry stairs");
        check(deep.stairsOpen(),"Stairs count as open until a scan finds them broken");
        deep.stairsOpen(false);
        check(!deep.stairsOpen(),"Broken stairs stop crews being routed down them");
        check(job.stair(8,64,31) && !job.stair(8,63,31),"The plan knows which blocks are steps");
        ExcavationJob saved=ExcavationJob.CODEC.parse(JsonOps.INSTANCE,ExcavationJob.CODEC.encodeStart(JsonOps.INSTANCE,job).getOrThrow()).getOrThrow();
        check(saved.stairTop()==64 && saved.stair(8,64,31),"The staircase survives a restart");
        var legacy=ExcavationJob.CODEC.encodeStart(JsonOps.INSTANCE,job).getOrThrow().getAsJsonObject(); legacy.remove("stair_top");
        ExcavationJob old=ExcavationJob.CODEC.parse(JsonOps.INSTANCE,legacy).getOrThrow();
        check(!old.hasStairs() && !old.stair(8,64,31) && old.stepAt(8,40,20)==-1,"Older quarry plans have no stairs until measured");
        old.measureStairs(80);
        check(old.stairTop()==70,"A rim above the pit starts the stairs at its top layer");
        System.out.println("Passed "+checks+" quarry staircase checks.");
    }
    @Test @ExtendWith(EphemeralTestServerProvider.class)
    void craftsmanOrders(MinecraftServer server) {
        check(Crafting.planks(new ItemStack(Items.OAK_LOG))==Items.OAK_PLANKS && Crafting.planks(new ItemStack(Items.STRIPPED_SPRUCE_LOG))==Items.SPRUCE_PLANKS
                && Crafting.planks(new ItemStack(Items.CRIMSON_STEM))==Items.CRIMSON_PLANKS && Crafting.planks(new ItemStack(Items.BIRCH_WOOD))==Items.BIRCH_PLANKS,"Every log saws into its own planks");
        check(Crafting.planks(new ItemStack(Items.OAK_PLANKS))==null && Crafting.planks(new ItemStack(Items.STONE))==null,"Only logs make planks");
        SimpleContainer warehouse=new SimpleContainer(9),bag=new SimpleContainer(36);
        warehouse.setItem(0,new ItemStack(Items.OAK_LOG,3));
        Crafting.Recipe order=Crafting.choose(List.of(warehouse),Set.of());
        check(order!=null && order.id().equals("planks"),"Logs in storage become planks when planks run short");
        check(Crafting.choose(List.of(warehouse),Set.of("planks"))==null,"A switched-off order is never made");
        check(Crafting.fetch(List.of(warehouse),bag,order)==3 && warehouse.getItem(0).isEmpty() && Crafting.ready(bag,order),"The craftsman carries the real logs to the bench");
        ItemStack made=Crafting.craft(bag,order);
        check(made.is(Items.OAK_PLANKS) && made.getCount()==4 && InventoryOps.count(List.of(bag),s -> s.is(Items.OAK_LOG))==2,"One log becomes four planks");
        SimpleContainer pantry=new SimpleContainer(9); pantry.setItem(0,new ItemStack(Items.WHEAT,7)); pantry.setItem(1,new ItemStack(Items.OAK_LOG,8));
        Crafting.Recipe bread=Crafting.choose(List.of(pantry),Set.of(),StructureRole.COOK);
        check(bread!=null && bread.id().equals("bread"),"Wheat is a cook order");
        check(Crafting.choose(List.of(pantry),Set.of()).id().equals("planks"),"Craftsmen leave bread to cooks");
        check(Crafting.choose(List.of(pantry),Set.of("bread"),StructureRole.COOK)==null,"The bread switch also controls cooks");
        SimpleContainer baker=new SimpleContainer(36);
        check(Crafting.fetch(List.of(pantry),baker,bread)==2 && pantry.getItem(0).getCount()==1,"Only whole batches of wheat are taken");
        check(Crafting.craft(baker,bread).is(Items.BREAD) && Crafting.craft(baker,bread).is(Items.BREAD) && Crafting.craft(baker,bread).isEmpty(),"Two batches bake two loaves and nothing more");
        SimpleContainer sticks=new SimpleContainer(9); sticks.setItem(0,new ItemStack(Items.STICK,14));
        Crafting.Recipe ladders=Crafting.choose(List.of(sticks),Set.of());
        check(ladders!=null && ladders.id().equals("ladders") && Crafting.batches(List.of(sticks),ladders)==2,"Sticks become ladders, three for every seven");
        SimpleContainer full=new SimpleContainer(9); full.setItem(0,new ItemStack(Items.OAK_PLANKS,64)); full.setItem(1,new ItemStack(Items.OAK_LOG,8));
        check(Crafting.choose(List.of(full),Set.of("sticks"))==null,"A stocked product is not overproduced");
        Settlement town=new Settlement(UUID.randomUUID(),UUID.randomUUID(),"Town",BlockPos.ZERO,240,List.of(),List.of(),"balanced");
        town.disabledRecipes.add("torches");
        Settlement reloaded=Settlement.CODEC.parse(JsonOps.INSTANCE,Settlement.CODEC.encodeStart(JsonOps.INSTANCE,town).getOrThrow()).getOrThrow();
        check(reloaded.disabledRecipes.contains("torches"),"Switched-off orders survive a restart");
        System.out.println("Passed "+checks+" craftsman checks.");
    }
}
