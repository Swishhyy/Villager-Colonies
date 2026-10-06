package io.github.swishhyy.wwmc;
import com.mojang.serialization.JsonOps;
import com.google.gson.JsonParser;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
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
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.level.storage.loot.LootTable;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import net.neoforged.testframework.junit.EphemeralTestServerProvider;

public final class MinecraftChecks {
    @Test @ExtendWith(EphemeralTestServerProvider.class)
    void inventoryAndPersistence(MinecraftServer server) throws IOException {
        main(new String[0]);
        var ops=server.registryAccess().createSerializationContext(JsonOps.INSTANCE);
        List<String> markers=new java.util.ArrayList<>(List.of("settlement_banner"));
        for(StructureRole role:StructureRole.values()) markers.add(role.id()+"_station");
        List<String> recipes=new java.util.ArrayList<>(markers); recipes.add("surveyor");
        for(String id:recipes) {
            try(var input=MinecraftChecks.class.getResourceAsStream("/data/wwmc/recipe/"+id+".json")) {
                if(input==null) throw new AssertionError("Missing recipe: "+id);
                Recipe.CODEC.parse(ops,JsonParser.parseReader(new InputStreamReader(input,StandardCharsets.UTF_8))).getOrThrow();
            }
        }
        for(String id:markers) {
            try(var input=MinecraftChecks.class.getResourceAsStream("/data/wwmc/loot_table/blocks/"+id+".json")) {
                if(input==null) throw new AssertionError("Missing block drops: "+id);
                LootTable.DIRECT_CODEC.parse(ops,JsonParser.parseReader(new InputStreamReader(input,StandardCharsets.UTF_8))).getOrThrow();
            }
        }
    }
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
