package io.github.swishhyy.wwmc;

import com.mojang.serialization.JsonOps;
import io.github.swishhyy.wwmc.core.StructureRole;
import io.github.swishhyy.wwmc.settlement.*;
import java.util.*;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import net.neoforged.testframework.junit.EphemeralTestServerProvider;

public final class CitizenInventoryGuardChecks {
    private static int checks;
    private static void check(boolean ok,String message) { checks++; if(!ok) throw new AssertionError(message); }
    private static final class Gear implements GuardEquipment.Equipment {
        final Map<EquipmentSlot,ItemStack> slots=new EnumMap<>(EquipmentSlot.class);
        public ItemStack get(EquipmentSlot slot) { return slots.getOrDefault(slot,ItemStack.EMPTY); }
        public void set(EquipmentSlot slot,ItemStack stack) { slots.put(slot,stack); }
    }
    @Test @ExtendWith(EphemeralTestServerProvider.class)
    void inventoriesEquipmentAndSavedPosts(MinecraftServer server) {
        CitizenInventory bag=new CitizenInventory(p -> false);
        for(int i=0;i<CitizenInventory.SIZE;i++) bag.setItem(i,new ItemStack(Items.COBBLESTONE,64));
        bag.offer(new ItemStack(Items.DIAMOND,5));
        check(bag.needsDelivery() && bag.hasPending() && bag.count(Items.DIAMOND)==5,"A full bag keeps a large harvest's overflow locally");
        var stacks=ItemStack.OPTIONAL_CODEC.listOf().encodeStart(JsonOps.INSTANCE,bag.contents()).getOrThrow();
        var pending=ItemStack.OPTIONAL_CODEC.listOf().encodeStart(JsonOps.INSTANCE,bag.pendingItems()).getOrThrow();
        CitizenInventory restored=new CitizenInventory(p -> false);
        restored.restore(ItemStack.OPTIONAL_CODEC.listOf().parse(JsonOps.INSTANCE,stacks).getOrThrow(),ItemStack.OPTIONAL_CODEC.listOf().parse(JsonOps.INSTANCE,pending).getOrThrow());
        check(restored.count(Items.COBBLESTONE)==36*64 && restored.count(Items.DIAMOND)==5 && restored.hasPending(),"A restart preserves both the visible bag and unplaced remainder");
        restored.removeItemNoUpdate(0); restored.flush();
        check(!restored.hasPending() && restored.getItem(0).is(Items.DIAMOND) && restored.getItem(0).getCount()==5,"Remainders enter the inventory as soon as space is freed");
        check(!restored.stillValid(null),"Menu validity delegates to the citizen's ownership and distance check");
        CitizenInventory legacy=new CitizenInventory(p -> true);
        legacy.restore(List.of(new ItemStack(Items.IRON_PICKAXE),new ItemStack(Items.OAK_SAPLING,4)),List.of());
        check(legacy.getContainerSize()==36 && legacy.count(Items.OAK_SAPLING)==4 && legacy.getItem(0).is(Items.IRON_PICKAXE),"The former nine-slot cargo format migrates into the bigger personal inventory");
        SimpleContainer warehouse=new SimpleContainer(1); warehouse.setItem(0,new ItemStack(Items.COBBLESTONE,60));
        CitizenInventory partial=new CitizenInventory(p -> true); partial.offer(new ItemStack(Items.COBBLESTONE,64));
        partial.deposit(List.of(warehouse),s -> 0);
        check(warehouse.getItem(0).getCount()==64 && partial.count(Items.COBBLESTONE)==60,"Full storage leaves the undelivered items in the bag");
        CitizenInventory food=new CitizenInventory(p -> true); food.offer(new ItemStack(Items.BREAD,64));
        SimpleContainer pantry=new SimpleContainer(1); int[] reserve={8};
        food.deposit(List.of(pantry),s -> { int keep=Math.min(reserve[0],s.getCount()); reserve[0]-=keep; return keep; });
        check(food.count(Items.BREAD)==8 && pantry.getItem(0).getCount()==56,"Workers keep local rations while the town receives the surplus");
        ItemStack ration=InventoryOps.takeOne(List.of(food),s -> s.is(Items.BREAD));
        check(ration.getCount()==1 && food.count(Items.BREAD)==7,"A local meal consumes an actual stored item");

        Gear stand=new Gear(),guard=new Gear(),otherGuard=new Gear();
        ItemStack helmet=new ItemStack(Items.IRON_HELMET); helmet.setDamageValue(7);
        stand.set(EquipmentSlot.HEAD,helmet);
        check(GuardEquipment.transfer(stand,guard,EquipmentSlot.HEAD),"A missing helmet is taken from the stand");
        check(stand.get(EquipmentSlot.HEAD).isEmpty() && guard.get(EquipmentSlot.HEAD).getDamageValue()==7,"The transfer preserves actual item durability and empties its source");
        check(!GuardEquipment.transfer(stand,otherGuard,EquipmentSlot.HEAD) && otherGuard.get(EquipmentSlot.HEAD).isEmpty(),"Two guards cannot receive the same stand item");
        stand.set(EquipmentSlot.HEAD,new ItemStack(Items.DIAMOND_HELMET));
        check(!GuardEquipment.transfer(stand,guard,EquipmentSlot.HEAD) && stand.get(EquipmentSlot.HEAD).is(Items.DIAMOND_HELMET),"Equipped guards leave spare pieces available for others");
        stand.set(EquipmentSlot.FEET,new ItemStack(Items.IRON_HELMET));
        check(!GuardEquipment.transfer(stand,otherGuard,EquipmentSlot.FEET),"An item cannot be equipped in the wrong armor slot");
        stand.set(EquipmentSlot.CHEST,new ItemStack(Items.BREAD));
        check(!GuardEquipment.transfer(stand,otherGuard,EquipmentSlot.CHEST),"Non-armor does not become defensive equipment");

        BlockPos station=new BlockPos(0,64,0),day=new BlockPos(12,64,4),night=new BlockPos(-12,64,4);
        GuardPosts posts=new GuardPosts(station,day,night);
        WorldWorkData data=new WorldWorkData(List.of(),List.of(),List.of(),List.of(posts));
        WorldWorkData saved=WorldWorkData.CODEC.parse(JsonOps.INSTANCE,WorldWorkData.CODEC.encodeStart(JsonOps.INSTANCE,data).getOrThrow()).getOrThrow();
        check(saved.guardPosts.get(station).active(false).equals(day) && saved.guardPosts.get(station).active(true).equals(night),"Both day/night posts persist and alternate without modifying world blocks");
        var old=WorldWorkData.CODEC.encodeStart(JsonOps.INSTANCE,data).getOrThrow().getAsJsonObject(); old.remove("guard_posts");
        check(WorldWorkData.CODEC.parse(JsonOps.INSTANCE,old).getOrThrow().guardPosts.isEmpty(),"Older work-data saves remain readable");
        ExcavationJob mine=new ExcavationJob(UUID.randomUUID(),station,StructureRole.MINE,Direction.NORTH,64,-12,24,4,0,List.of(),true);
        ExcavationJob resumed=ExcavationJob.CODEC.parse(JsonOps.INSTANCE,ExcavationJob.CODEC.encodeStart(JsonOps.INSTANCE,mine).getOrThrow()).getOrThrow();
        check(resumed.automaticDepth && resumed.targetY==-12 && resumed.id.equals(mine.id),"A randomly chosen mine depth is saved rather than rerolled on reload");
        var oldMine=ExcavationJob.CODEC.encodeStart(JsonOps.INSTANCE,mine).getOrThrow().getAsJsonObject(); oldMine.remove("automatic_depth");
        check(!ExcavationJob.CODEC.parse(JsonOps.INSTANCE,oldMine).getOrThrow().automaticDepth,"Manual legacy plans are recognizable for one-time migration");
        UUID citizen=UUID.randomUUID();
        Settlement town=new Settlement(UUID.randomUUID(),UUID.randomUUID(),"Town",station,150,List.of(citizen),List.of(new Station(station,StructureRole.GUARD)),"balanced",List.of(),Map.of(citizen,"Ada Stone"));
        Settlement resumedTown=Settlement.CODEC.parse(JsonOps.INSTANCE,Settlement.CODEC.encodeStart(JsonOps.INSTANCE,town).getOrThrow()).getOrThrow();
        check(resumedTown.citizenNames.get(citizen).equals("Ada Stone") && resumedTown.stations.getFirst().role()==StructureRole.GUARD,"Names remain reserved even when their citizens are unloaded");
        check(CaveMining.ore(Blocks.IRON_ORE.defaultBlockState()) && !CaveMining.ore(Blocks.STONE.defaultBlockState()) && !CaveMining.ore(Blocks.OAK_LOG.defaultBlockState()),"Cave searches target real ore rather than random cave walls or structures");
        System.out.println("Passed "+checks+" inventory, guard and mining persistence checks.");
    }
}
