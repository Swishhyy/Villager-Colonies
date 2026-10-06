package io.github.swishhyy.wwmc;
import net.neoforged.neoforge.common.ModConfigSpec;

public final class Config {
    private static final ModConfigSpec.Builder B = new ModConfigSpec.Builder();
    public static final ModConfigSpec.IntValue SETTLEMENT_RADIUS = B.comment("Claim radius in blocks.").defineInRange("settlementRadius", 64, 16, 128);
    public static final ModConfigSpec.IntValue MAX_CITIZENS = B.comment("Citizen limit; housing beds are also required.").defineInRange("maxCitizens", 32, 1, 128);
    public static final ModConfigSpec.IntValue WORK_RADIUS = B.comment("Search radius around job stations.").defineInRange("workRadius", 8, 2, 16);
    public static final ModConfigSpec.IntValue WORK_TICKS = B.comment("Ticks to harvest a block after reaching it.").defineInRange("workTicks", 80, 20, 400);
    public static final ModConfigSpec.IntValue RATION_TICKS = B.comment("Loaded server ticks between meals.").defineInRange("rationTicks", 2400, 200, 24000);
    public static final ModConfigSpec SPEC = B.build();
    private Config() {}
}
