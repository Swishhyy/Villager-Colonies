package io.github.swishhyy.wwmc.settlement;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.List;
import net.minecraft.core.BlockPos;

/**
 * A Guard Station's plan: its day and night posts, the guard's role, and an optional patrol route walked from the
 * active post. Stations saved before roles and routes are swordsmen with no route.
 */
public record GuardPosts(BlockPos station,BlockPos day,BlockPos night,String role,List<BlockPos> patrol) {
    public static final String SWORD="sword",SHIELD="shield",ARCHER="archer";
    public static final List<String> ROLES=List.of(SWORD,SHIELD,ARCHER);
    public static final int MAX_PATROL=8;
    public static final Codec<GuardPosts> CODEC=RecordCodecBuilder.create(i -> i.group(
        BlockPos.CODEC.fieldOf("station").forGetter(GuardPosts::station),
        BlockPos.CODEC.fieldOf("day").forGetter(GuardPosts::day),
        BlockPos.CODEC.fieldOf("night").forGetter(GuardPosts::night),
        Codec.STRING.optionalFieldOf("role",SWORD).forGetter(GuardPosts::role),
        BlockPos.CODEC.listOf().optionalFieldOf("patrol",List.of()).forGetter(GuardPosts::patrol)
    ).apply(i,GuardPosts::new));
    public GuardPosts {
        station=station.immutable(); day=day.immutable(); night=night.immutable();
        if(!ROLES.contains(role)) role=SWORD;
        patrol=patrol.stream().limit(MAX_PATROL).map(BlockPos::immutable).toList();
    }
    public GuardPosts(BlockPos station,BlockPos day,BlockPos night) { this(station,day,night,SWORD,List.of()); }
    public BlockPos active(boolean isNight) { return isNight ? night : day; }
    /** Whether the owner chose posts, rather than the guard standing at its station. */
    public boolean chosen() { return !day.equals(station) || !night.equals(station); }
    public GuardPosts withPosts(BlockPos day,BlockPos night) { return new GuardPosts(station,day,night,role,patrol); }
    public GuardPosts withRole(String role) { return new GuardPosts(station,day,night,role,patrol); }
    public GuardPosts withPatrol(List<BlockPos> patrol) { return new GuardPosts(station,day,night,role,patrol); }
    public static String title(String role) {
        return switch(role) { case SHIELD -> "Shield guard"; case ARCHER -> "Archer"; default -> "Swordsman"; };
    }
    public static String describe(String role) {
        return switch(role) {
            case SHIELD -> "Holds its gate within 10 blocks in calm periods, joins town defense during alarms, and takes 15% less damage, 25% with a shield in hand";
            case ARCHER -> "Covers approaches: spots hostiles from 28 blocks, 40 on alert, and pauses longer at each patrol point; needs a bow and arrows";
            default -> "Patrols and chases hostiles within 16 blocks, 32 on alert, and answers calls from citizens";
        };
    }
}
