package io.github.swishhyy.wwmc.core;

import io.github.swishhyy.wwmc.WWMC;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/** Damage is in health points. Each successful activation spends one of the trap's finite uses. */
public enum TrapKind {
    WOODEN_SPIKES("wooden_spikes","Wooden Spikes",0,12,2,40,60,0,false),
    TANGLE_NET("tangle_net","Tangle Net",0,4,0,0,80,3,true),
    BRONZE_SNARE("bronze_snare","Bronze Snare",1,6,3,0,40,6,true),
    BRONZE_CALTROPS("bronze_caltrops","Bronze Caltrops",1,12,1,60,100,1,false),
    IRON_SPRING_TRAP("iron_spring_trap","Iron Spring Trap",2,8,6,100,40,1,false);

    public final String id,title;
    public final int age,uses,cooldown,slowTicks,slowLevel;
    public final float damage;
    public final boolean manual;
    TrapKind(String id,String title,int age,int uses,float damage,int cooldown,int slowTicks,int slowLevel,boolean manual) {
        this.id=id; this.title=title; this.age=age; this.uses=uses; this.damage=damage;
        this.cooldown=cooldown; this.slowTicks=slowTicks; this.slowLevel=slowLevel; this.manual=manual;
    }
    public Item repairItem() {
        return switch(this) {
            case WOODEN_SPIKES -> Items.OAK_PLANKS;
            case TANGLE_NET -> Items.STRING;
            case BRONZE_SNARE,BRONZE_CALTROPS -> WWMC.BRONZE_INGOT.get();
            case IRON_SPRING_TRAP -> Items.IRON_INGOT;
        };
    }
    public int repairCount() { return this==TANGLE_NET || this==IRON_SPRING_TRAP ? 2 : 1; }
    public boolean repairMaterial(ItemStack stack) {
        return this==WOODEN_SPIKES ? stack.is(ItemTags.PLANKS) : stack.is(repairItem());
    }
    public String effect() {
        return switch(this) {
            case WOODEN_SPIKES -> "Light damage and a short slow";
            case TANGLE_NET -> "Strong slow for four seconds; rearm with string";
            case BRONZE_SNARE -> "Damage and a two-second hold; rearm with string";
            case BRONZE_CALTROPS -> "Light damage and a five-second slow";
            case IRON_SPRING_TRAP -> "Heavy damage; five-second cooldown";
        };
    }
}
