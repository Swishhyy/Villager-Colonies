package io.github.swishhyy.wwmc.settlement;

import java.util.*;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.Container;
import net.minecraft.world.item.ItemStack;

/**
 * Hearty meals keep a citizen full for longer, and a varied diet lifts its morale. Bread is the yardstick: it keeps the
 * configured meal interval, a steak lasts about three quarters longer and a carrot a little over half as long. A citizen
 * that ate three different foods in its last six meals works 5% faster, four or more 8%. Meals never heal injuries;
 * hospital beds do that.
 */
public final class MealVariety {
    /** Meals remembered for variety. */
    public static final int REMEMBERED=6;
    /** Nutrition plus half the saturation of a loaf of bread. */
    private static final float BREAD=8.0F;
    private static final float SHORTEST=0.5F,LONGEST=1.75F;
    private MealVariety() {}
    /** How long a meal keeps a citizen full, relative to bread. */
    public static float fullness(int nutrition,float saturation) {
        return Math.clamp((nutrition+saturation/2)/BREAD,SHORTEST,LONGEST);
    }
    public static int fullTicks(ItemStack meal,int interval) {
        var food=meal.get(DataComponents.FOOD);
        if(food==null) return interval;
        return Math.max(20,Math.round(interval*fullness(food.nutrition(),food.saturation())));
    }
    public static String id(ItemStack meal) { return BuiltInRegistries.ITEM.getKey(meal.getItem()).toString(); }
    /** The meals remembered after eating this one, oldest first. */
    public static List<String> remember(List<String> recent,String meal) {
        List<String> next=new ArrayList<>(recent);
        next.add(meal);
        while(next.size()>REMEMBERED) next.removeFirst();
        return next;
    }
    public static int distinct(List<String> recent) { return new HashSet<>(recent).size(); }
    /** Extra work speed, in percent, from a varied diet. */
    public static int bonus(List<String> recent) {
        int kinds=distinct(recent);
        return kinds>=4 ? 8 : kinds==3 ? 5 : 0;
    }
    public static String mood(List<String> recent) {
        int kinds=distinct(recent);
        if(recent.isEmpty()) return "Settling in";
        return kinds>=4 ? "Delighted" : kinds==3 ? "Content" : recent.size()>=4 && kinds==1 ? "Bored of the same meal" : "Fed";
    }
    /** Different foods in these containers. */
    public static int kinds(List<Container> containers) {
        Set<String> seen=new HashSet<>();
        for(Container container:containers) for(int slot=0;slot<container.getContainerSize();slot++) {
            ItemStack stack=container.getItem(slot);
            if(FoodHealing.food(stack)) seen.add(id(stack));
        }
        return seen.size();
    }
}
