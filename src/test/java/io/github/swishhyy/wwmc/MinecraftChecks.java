package io.github.swishhyy.wwmc;
import com.mojang.serialization.JsonOps;
import io.github.swishhyy.wwmc.core.*;
import io.github.swishhyy.wwmc.settlement.*;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import net.minecraft.SharedConstants;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.server.Bootstrap;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

public final class MinecraftChecks {
    private static int checks;
    private static void check(boolean result,String message) { checks++; if(!result) throw new AssertionError(message); }
    public static void main(String[] args) {
        SharedConstants.tryDetectVersion(); Bootstrap.bootStrap();
        SimpleContainer chest=new SimpleContainer(1);
        chest.setItem(0,new ItemStack(Items.BREAD,60));
        ItemStack offered=new ItemStack(Items.BREAD,10);
        ItemStack remainder=InventoryOps.insert(chest,offered);
        check(chest.getItem(0).getCount()==64 && remainder.getCount()==6,"Full storage preserves overflow");
        check(offered.getCount()==10,"Offers are copied; the caller commits its inventory once");
        ItemStack meal=InventoryOps.takeOne(List.of(chest),s -> s.is(Items.BREAD));
        check(meal.getCount()==1 && chest.getItem(0).getCount()==63,"Food consumption removes exactly one item");
        ItemStack named=new ItemStack(Items.BREAD,1); named.set(DataComponents.CUSTOM_NAME,Component.literal("Special bread"));
        check(InventoryOps.insert(chest,named).getCount()==1,"Different components never merge into an existing stack");
        SimpleContainer tools=new SimpleContainer(1); tools.setItem(0,new ItemStack(Items.STONE_PICKAXE));
        check(!InventoryOps.takeOne(List.of(tools),s -> s.is(Items.STONE_PICKAXE)).isEmpty(),"A worker takes an actual tool");
        check(InventoryOps.takeOne(List.of(tools),s -> s.is(Items.STONE_PICKAXE)).isEmpty(),"Another worker cannot receive the same tool");
        Settlement town=new Settlement(UUID.randomUUID(),UUID.randomUUID(),"Test town",new BlockPos(0,64,0),64,List.of(UUID.randomUUID()),
                List.of(new Station(new BlockPos(1,64,1),StructureRole.HOSPITAL,Optional.of(new RoomBounds(1,64,1,5,67,5)))),"food");
        var json=Settlement.CODEC.encodeStart(JsonOps.INSTANCE,town).getOrThrow();
        Settlement restored=Settlement.CODEC.parse(JsonOps.INSTANCE,json).getOrThrow();
        check(restored.id.equals(town.id) && restored.owner.equals(town.owner) && restored.citizens.equals(town.citizens),"Settlement ownership and population survive saves");
        check(restored.stations.getFirst().role()==StructureRole.HOSPITAL && restored.stations.getFirst().room().equals(town.stations.getFirst().room()),"Room role and bounds survive saves");
        check(restored.priority.equals("food"),"Player direction survives a restart");
        check(town.contains(new BlockPos(64,90,64)) && !town.contains(new BlockPos(65,64,0)),"Claims have explicit inclusive boundaries");
        check(town.overlaps(new BlockPos(128,64,0),64) && !town.overlaps(new BlockPos(129,64,0),64),"New claims cannot share a block");
        System.out.println("Passed "+checks+" Minecraft adapter checks.");
    }
}
