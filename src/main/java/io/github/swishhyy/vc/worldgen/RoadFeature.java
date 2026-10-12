package io.github.swishhyy.vc.worldgen;

import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.levelgen.feature.Feature;
import net.minecraft.world.level.levelgen.feature.FeaturePlaceContext;
import net.minecraft.world.level.levelgen.feature.configurations.NoneFeatureConfiguration;

/** Paves each new chunk's part of the {@link RoadNetwork}, before towns, trees and plants are placed on the land. */
public final class RoadFeature extends Feature<NoneFeatureConfiguration> {
    public RoadFeature() { super(NoneFeatureConfiguration.CODEC); }
    @Override public boolean place(FeaturePlaceContext<NoneFeatureConfiguration> context) {
        var network=RoadNetwork.of(context.level().getLevel());
        return network!=null && network.pave(context.level(),ChunkPos.containing(context.origin()));
    }
}
