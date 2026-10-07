package io.github.swishhyy.wwmc.settlement;

import io.github.swishhyy.wwmc.WWMC;
import io.github.swishhyy.wwmc.core.StructureRole;
import io.github.swishhyy.wwmc.entity.CitizenEntity;
import java.util.List;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.entity.living.LivingDropsEvent;

/** One actual animal or fishing catch produces one whole carcass; only a butcher turns it into raw portions. */
public final class Carcasses {
    public enum Kind {
        COW("cow",Items.BEEF,4), PIG("pig",Items.PORKCHOP,4),
        SHEEP("sheep",Items.MUTTON,3), CHICKEN("chicken",Items.CHICKEN,2),
        RABBIT("rabbit",Items.RABBIT,2), COD("cod",Items.COD,2),
        SALMON("salmon",Items.SALMON,2);
        public final String id;
        public final Item meat;
        public final int portions;
        Kind(String id,Item meat,int portions) { this.id=id; this.meat=meat; this.portions=portions; }
        public ItemStack stack() { return new ItemStack(WWMC.CARCASSES.get(this).get()); }
    }
    public static Kind kind(LivingEntity entity) {
        for(Kind kind:Kind.values()) if(BuiltInRegistries.ENTITY_TYPE.getKey(entity.getType()).equals(Identifier.withDefaultNamespace(kind.id))) return kind;
        return null;
    }
    public static Kind kind(ItemStack stack) {
        if(stack.isEmpty()) return null;
        for(Kind kind:Kind.values()) if(stack.is(WWMC.CARCASSES.get(kind).get())) return kind;
        return null;
    }
    public static boolean carcass(ItemStack stack) { return kind(stack)!=null; }
    public static boolean meatDrop(ItemStack stack) {
        return List.of(Items.BEEF,Items.COOKED_BEEF,Items.PORKCHOP,Items.COOKED_PORKCHOP,Items.MUTTON,Items.COOKED_MUTTON,
                Items.CHICKEN,Items.COOKED_CHICKEN,Items.RABBIT,Items.COOKED_RABBIT,Items.COD,Items.COOKED_COD,
                Items.SALMON,Items.COOKED_SALMON).stream().anyMatch(stack::is);
    }
    /** A completed butcher action consumes precisely one carcass. Full bags keep their normal saved overflow. */
    public static int prepare(CitizenInventory bag) {
        ItemStack carcass=InventoryOps.takeOne(List.of(bag),Carcasses::carcass);
        Kind kind=kind(carcass);
        if(kind==null) return 0;
        bag.offer(new ItemStack(kind.meat,kind.portions));
        return kind.portions;
    }
    @SubscribeEvent public void drops(LivingDropsEvent event) {
        if(!(event.getSource().getEntity() instanceof CitizenEntity citizen)) return;
        StructureRole role=citizen.jobRole();
        if(role!=StructureRole.HUNTER && role!=StructureRole.ANIMAL_KEEPER) return;
        Kind kind=kind(event.getEntity());
        if(kind==null || event.getEntity().isBaby()) return;
        // Replace meat, preserve real leather/wool/feathers, and prevent a second pile of vanilla meat on the ground.
        for(var drop:event.getDrops()) if(!meatDrop(drop.getItem())) citizen.bag().offer(drop.getItem());
        citizen.bag().offer(kind.stack());
        event.getDrops().clear();
    }
}
