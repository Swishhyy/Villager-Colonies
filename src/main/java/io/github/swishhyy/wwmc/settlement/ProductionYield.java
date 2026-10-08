package io.github.swishhyy.wwmc.settlement;

import io.github.swishhyy.wwmc.core.StructureRole;
import io.github.swishhyy.wwmc.core.Upgrades;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.component.DataComponents;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.CropBlock;
import net.minecraft.world.level.block.state.BlockState;

/** Bonus units from real harvests, after reserving a crop's planting item. Seeds and Silk Touch blocks never multiply. */
public final class ProductionYield {
    private ProductionYield() {}
    public static boolean eligible(StructureRole role,BlockState harvested,ItemStack drop) {
        if(drop.isEmpty()) return false;
        return role==StructureRole.FARM && harvested.getBlock() instanceof CropBlock
                && (drop.is(Items.WHEAT) || drop.has(DataComponents.FOOD) && !drop.is(Items.POISONOUS_POTATO))
                || role==StructureRole.MINE && CaveMining.ore(harvested) && !(drop.getItem() instanceof BlockItem);
    }
    public static List<ItemStack> apply(Station station,BlockState harvested,List<ItemStack> drops,RandomSource random) {
        int level=station.yieldLevel();
        if(level==0) return drops;
        List<ItemStack> result=new ArrayList<>(drops);
        for(ItemStack drop:drops) {
            if(!eligible(station.role(),harvested,drop)) continue;
            int extra=0;
            for(int i=0;i<drop.getCount();i++) if(random.nextInt(10)<Math.clamp(level,0,Upgrades.MAX_STATION_LEVEL)) extra++;
            // Original loot and its components stay intact; bag overflow uses the normal pending-delivery path.
            while(extra>0) { int count=Math.min(extra,drop.getMaxStackSize()); result.add(drop.copyWithCount(count)); extra-=count; }
        }
        return result;
    }
}
