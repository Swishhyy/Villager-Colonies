package io.github.swishhyy.wwmc;
import net.neoforged.neoforge.common.ModConfigSpec;

public final class Config {
    private static final ModConfigSpec.Builder B = new ModConfigSpec.Builder();
    public static final ModConfigSpec.IntValue SETTLEMENT_RADIUS = B.comment("Horizontal claim radius; new settlements only.").defineInRange("settlementRadius", 150, 16, 512);
    public static final ModConfigSpec.IntValue MAX_CITIZENS = B.comment("Citizen limit; housing beds are also required.").defineInRange("maxCitizens", 32, 1, 128);
    public static final ModConfigSpec.IntValue STATION_WORKERS = B.comment("Workers sharing each farm, lumber or mine station.").defineInRange("stationWorkers", 4, 1, 32);
    public static final ModConfigSpec.IntValue QUARRY_WORKERS = B.comment("Workers sharing each quarry station.").defineInRange("quarryWorkers", 8, 1, 32);
    public static final ModConfigSpec.IntValue GUARD_WORKERS = B.comment("Guard crew slots per guard station.").defineInRange("guardWorkers", 2, 1, 16);
    public static final ModConfigSpec.IntValue MINE_MIN_Y = B.comment("Lowest randomly chosen strip-mine floor Y; saved per mine.").defineInRange("mineMinY", -30, -64, 319);
    public static final ModConfigSpec.IntValue MINE_MAX_Y = B.comment("Highest randomly chosen strip-mine floor Y.").defineInRange("mineMaxY", 10, -64, 319);
    public static final ModConfigSpec.IntValue QUARRY_TARGET_Y = B.comment("Default quarry bottom Y; bedrock is preserved.").defineInRange("quarryTargetY", -64, -64, 319);
    public static final ModConfigSpec.IntValue MINE_BRANCH_LENGTH = B.comment("Length of each side tunnel.").defineInRange("mineBranchLength", 24, 1, 64);
    public static final ModConfigSpec.IntValue MINE_BRANCH_PAIRS = B.comment("Pairs of branch tunnels spaced three blocks apart.").defineInRange("mineBranchPairs", 4, 1, 16);
    public static final ModConfigSpec.IntValue WORK_TICKS = B.comment("Ticks to harvest a block after reaching it.").defineInRange("workTicks", 80, 20, 400);
    public static final ModConfigSpec.IntValue RATION_TICKS = B.comment("Loaded server ticks between meals.").defineInRange("rationTicks", 2400, 200, 24000);
    public static final ModConfigSpec SPEC = B.build();
    private Config() {}
}
