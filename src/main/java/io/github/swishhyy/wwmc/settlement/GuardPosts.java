package io.github.swishhyy.wwmc.settlement;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.BlockPos;

public record GuardPosts(BlockPos station,BlockPos day,BlockPos night) {
    public static final Codec<GuardPosts> CODEC=RecordCodecBuilder.create(i -> i.group(
        BlockPos.CODEC.fieldOf("station").forGetter(GuardPosts::station),
        BlockPos.CODEC.fieldOf("day").forGetter(GuardPosts::day),
        BlockPos.CODEC.fieldOf("night").forGetter(GuardPosts::night)
    ).apply(i,GuardPosts::new));
    public GuardPosts { station=station.immutable(); day=day.immutable(); night=night.immutable(); }
    public BlockPos active(boolean isNight) { return isNight ? night : day; }
}
