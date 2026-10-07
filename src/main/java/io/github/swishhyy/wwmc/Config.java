package io.github.swishhyy.wwmc;
import io.github.swishhyy.wwmc.settlement.Settlement;
import net.neoforged.neoforge.common.ModConfigSpec;

public final class Config {
    private static final ModConfigSpec.Builder B = new ModConfigSpec.Builder();
    public static final ModConfigSpec.IntValue SETTLEMENT_RADIUS = B.comment("Horizontal claim radius of new settlements; never below 240.").defineInRange("settlementRadius", Settlement.MIN_RADIUS, Settlement.MIN_RADIUS, 512);
    public static final ModConfigSpec.IntValue MAX_CITIZENS = B.comment("Hard ceiling on citizens in one town, whatever population upgrades it buys; housing beds are also required.").defineInRange("maxCitizens", 64, 1, 256);
    public static final ModConfigSpec.IntValue BASE_POPULATION = B.comment("Citizen limit of a town before it buys population upgrades at its banner.").defineInRange("basePopulation", 10, 1, 256);
    public static final ModConfigSpec.IntValue POPULATION_STEP = B.comment("Extra citizens each population upgrade allows.").defineInRange("populationPerUpgrade", 5, 1, 64);
    public static final ModConfigSpec.IntValue POPULATION_COST = B.comment("Emeralds for the first population upgrade; each later upgrade costs this much more than the one before.").defineInRange("populationUpgradeCost", 8, 0, 4096);
    public static final ModConfigSpec.IntValue STATION_COST = B.comment("Emeralds for a station's first range or crew upgrade; each further level costs twice as much as the last.").defineInRange("stationUpgradeCost", 8, 0, 4096);
    public static final ModConfigSpec.IntValue STATION_WORKERS = B.comment("Workers sharing each lumber or mine station before crew upgrades. Farms, craftsmen and enchanters always have one worker.").defineInRange("stationWorkers", 4, 1, 32);
    public static final ModConfigSpec.IntValue COURIER_WORKERS = B.comment("Couriers per courier station before crew upgrades.").defineInRange("courierWorkers", 2, 1, 16);
    public static final ModConfigSpec.IntValue QUARRY_WORKERS = B.comment("Workers sharing each quarry station before crew upgrades.").defineInRange("quarryWorkers", 8, 1, 32);
    public static final ModConfigSpec.IntValue GUARD_WORKERS = B.comment("Guard crew slots per guard station before crew upgrades.").defineInRange("guardWorkers", 2, 1, 16);
    public static final ModConfigSpec.IntValue PROCESSING_WORKERS = B.comment("Workers sharing each smeltery or cook station before crew upgrades.").defineInRange("processingWorkers", 2, 1, 16);
    public static final ModConfigSpec.IntValue BLACKSMITH_WORKERS = B.comment("Blacksmiths sharing each repair station before crew upgrades.").defineInRange("blacksmithWorkers", 2, 1, 16);
    public static final ModConfigSpec.IntValue ENCHANT_MINUTES = B.comment("Minutes an enchanter works on a book or common item. Iron and gold gear take 1.3 times as long, diamond 1.6, netherite twice, and uncommon, rare and epic items longer still.").defineInRange("enchantMinutes", 5, 1, 60);
    public static final ModConfigSpec.IntValue ENCHANTER_MAX_LEVEL = B.comment("Highest enchanting level an enchanter reaches, whatever the bookshelves; players alone reach 30.").defineInRange("enchanterMaxLevel", 25, 1, 29);
    public static final ModConfigSpec.IntValue ORE_VEIN_SECONDS = B.comment("Seconds an ore vein beside a mine station needs between yields; gold takes twice as long, diamond and emerald six times, ancient debris eight.").defineInRange("oreVeinSeconds", 15, 1, 600);
    public static final ModConfigSpec.IntValue MINE_MIN_Y = B.comment("Lowest randomly chosen strip-mine floor Y; saved per mine.").defineInRange("mineMinY", -30, -64, 319);
    public static final ModConfigSpec.IntValue MINE_MAX_Y = B.comment("Highest randomly chosen strip-mine floor Y.").defineInRange("mineMaxY", 10, -64, 319);
    public static final ModConfigSpec.IntValue QUARRY_TARGET_Y = B.comment("Default quarry bottom Y; bedrock is preserved.").defineInRange("quarryTargetY", -64, -64, 319);
    public static final ModConfigSpec.IntValue MINE_BRANCH_LENGTH = B.comment("Length of each side tunnel.").defineInRange("mineBranchLength", 24, 1, 64);
    public static final ModConfigSpec.IntValue MINE_BRANCH_PAIRS = B.comment("Pairs of branch tunnels spaced three blocks apart.").defineInRange("mineBranchPairs", 4, 1, 16);
    public static final ModConfigSpec.IntValue WORK_TICKS = B.comment("Ticks to harvest a block after reaching it.").defineInRange("workTicks", 80, 20, 400);
    public static final ModConfigSpec.IntValue RATION_TICKS = B.comment("Loaded server ticks between meals.").defineInRange("rationTicks", 2400, 200, 24000);
    public static final ModConfigSpec.IntValue ALARM_THRESHOLD = B.comment("Hostiles citizens must sight at once before a guard runs to ring the town bell.").defineInRange("alarmThreshold", 10, 3, 128);
    public static final ModConfigSpec.BooleanValue WAVES = B.comment("Send hostile waves against settlements while their owner is home.").define("enemyWaves", true);
    public static final ModConfigSpec.IntValue WAVE_MIN_POPULATION = B.comment("Citizens a town needs before waves are scheduled.").defineInRange("waveMinPopulation", 3, 1, 128);
    public static final ModConfigSpec.IntValue WAVE_INTERVAL_DAYS = B.comment("Average in-game days between waves; each wave arrives at night.").defineInRange("waveIntervalDays", 2, 1, 30);
    public static final ModConfigSpec.IntValue WAVE_BASE_MOBS = B.comment("Hostiles in every wave before population scaling.").defineInRange("waveBaseMobs", 2, 0, 64);
    public static final ModConfigSpec.DoubleValue WAVE_MOBS_PER_CITIZEN = B.comment("Additional hostiles per citizen, rounded up.").defineInRange("waveMobsPerCitizen", 0.5, 0.0, 4.0);
    public static final ModConfigSpec.IntValue WAVE_MAX_MOBS = B.comment("Largest possible wave before population upgrades.").defineInRange("waveMaxMobs", 40, 1, 128);
    public static final ModConfigSpec.IntValue WAVE_MOBS_PER_UPGRADE = B.comment("Extra hostiles in every wave per population upgrade, also above the largest wave. Upgraded towns also face pillagers, and from the third upgrade vindicators.").defineInRange("waveMobsPerUpgrade", 2, 0, 16);
    public static final ModConfigSpec.IntValue MAX_TRADERS = B.comment("Maximum towns whose trader keeps a moving 3x3 chunk window active in one dimension.").defineInRange("maxActiveTraders", 8, 1, 32);
    public static final ModConfigSpec.IntValue TRADE_DISTANCE = B.comment("Longest permitted trader route in blocks, measured between town banners.").defineInRange("tradeRouteDistance", 8192, 512, 32768);
    public static final ModConfigSpec.BooleanValue RANDOM_TOWNS = B.comment("Discover small neutral NPC settlements in suitable loaded Overworld terrain.").define("randomSettlements", true);
    public static final ModConfigSpec.IntValue NPC_SPACING = B.comment("Size of deterministic NPC town regions; actual distance varies with terrain and the town's position.").defineInRange("npcTownSpacing", 1408, 1008, 4096);
    public static final ModConfigSpec.IntValue MAX_NPC_TOWNS = B.comment("Maximum automatically generated NPC towns in the Overworld.").defineInRange("maxNpcTowns", 48, 0, 256);
    public static final ModConfigSpec SPEC = B.build();
    private Config() {}
}
