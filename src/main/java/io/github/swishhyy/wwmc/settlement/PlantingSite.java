package io.github.swishhyy.wwmc.settlement;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.BlockPos;
import net.minecraft.world.item.ItemStack;
import java.util.ArrayList;
import java.util.function.Predicate;
import java.util.function.Consumer;

public record PlantingSite(BlockPos station,BlockPos root,TreeSpecies species,int width) {
    public static final Codec<PlantingSite> CODEC=RecordCodecBuilder.create(i -> i.group(
        BlockPos.CODEC.fieldOf("station").forGetter(PlantingSite::station),
        BlockPos.CODEC.fieldOf("root").forGetter(PlantingSite::root),
        Codec.STRING.xmap(TreeSpecies::valueOf,TreeSpecies::name).fieldOf("species").forGetter(PlantingSite::species),
        Codec.intRange(1,2).fieldOf("width").forGetter(PlantingSite::width)
    ).apply(i,PlantingSite::new));
    public PlantingSite {
        station=station.immutable(); root=root.immutable();
        if(width<1 || width>2 || species.width==2 && width!=2)
            throw new IllegalArgumentException("Invalid sapling plot width");
    }
    public int cost() { return width*width; }
    /** Commit seed consumption only if every block was placed. */
    public boolean apply(ItemStack seeds,Predicate<BlockPos> place,Consumer<BlockPos> rollback) {
        if(!seeds.is(species.seed) || seeds.getCount()<cost()) return false;
        var placed=new ArrayList<BlockPos>();
        for(int x=0;x<width;x++) for(int z=0;z<width;z++) {
            BlockPos pos=root.offset(x,0,z);
            if(!place.test(pos)) { placed.forEach(rollback); return false; }
            placed.add(pos);
        }
        seeds.shrink(cost()); return true;
    }
}
