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
    public static final ModConfigSpec.IntValue YIELD_COST = B.comment("Emeralds for a farm or mine's first yield upgrade. Prices double each level; three levels add 10%, 20% and 30% average produce or mineral drops.").defineInRange("yieldUpgradeCost", 16, 0, 4096);
    public static final ModConfigSpec.IntValue QUARRY_WORKERS = B.comment("Workers sharing each quarry station before crew upgrades. Every other job block has exactly one worker.").defineInRange("quarryWorkers", 8, 1, 32);
    public static final ModConfigSpec.IntValue ENCHANT_MINUTES = B.comment("Minutes an enchanter works on a book or common item. Iron and gold gear take 1.3 times as long, diamond 1.6, netherite twice, and uncommon, rare and epic items longer still.").defineInRange("enchantMinutes", 5, 1, 60);
    public static final ModConfigSpec.IntValue ENCHANTER_MAX_LEVEL = B.comment("Highest enchanting level an enchanter reaches, whatever the bookshelves; players alone reach 30.").defineInRange("enchanterMaxLevel", 25, 1, 29);
    public static final ModConfigSpec.IntValue ORE_VEIN_SECONDS = B.comment("Seconds an ore vein beside a mine station needs to replenish with a stone pickaxe. Faster pickaxes reduce this time, capped at 1.5 times stone speed; gold takes twice as long, diamond and emerald six times, ancient debris eight.").defineInRange("oreVeinSeconds", 15, 1, 600);
    public static final ModConfigSpec.IntValue MINE_MIN_Y = B.comment("Lowest randomly chosen strip-mine floor Y; saved per mine.").defineInRange("mineMinY", -30, -64, 319);
    public static final ModConfigSpec.IntValue MINE_MAX_Y = B.comment("Highest randomly chosen strip-mine floor Y.").defineInRange("mineMaxY", 10, -64, 319);
    public static final ModConfigSpec.IntValue QUARRY_TARGET_Y = B.comment("Default quarry bottom Y; bedrock is preserved.").defineInRange("quarryTargetY", -64, -64, 319);
    public static final ModConfigSpec.IntValue MINE_BRANCH_LENGTH = B.comment("Length of each side tunnel.").defineInRange("mineBranchLength", 24, 1, 64);
    public static final ModConfigSpec.IntValue MINE_BRANCH_PAIRS = B.comment("Pairs of branch tunnels spaced three blocks apart.").defineInRange("mineBranchPairs", 4, 1, 16);
    public static final ModConfigSpec.IntValue WORK_TICKS = B.comment("Ticks to harvest a block after reaching it.").defineInRange("workTicks", 80, 20, 400);
    public static final ModConfigSpec.IntValue RATION_TICKS = B.comment("Base loaded server ticks between meals; multiply by mealIntervalMultiplier for the actual interval.").defineInRange("rationTicks", 2400, 200, 24000);
    public static final ModConfigSpec.IntValue MEAL_MULTIPLIER = B.comment("Multiplies the saved rationTicks value. Default 3 makes regular meals three times less frequent, including in existing worlds.").defineInRange("mealIntervalMultiplier", 3, 1, 12);
    public static final ModConfigSpec.IntValue FISHING_SECONDS = B.comment("Seconds spent fishing at a valid bank for each whole fish catch.").defineInRange("fishingSeconds", 30, 5, 300);
    public static final ModConfigSpec.IntValue ANIMAL_BREEDERS = B.comment("Adult animals kept for breeding per species in each keeper pen. Only surplus adults are harvested; babies, named animals and mating animals are preserved.").defineInRange("animalBreeders", 4, 2, 16);
    public static int mealIntervalTicks() { return RATION_TICKS.get()*MEAL_MULTIPLIER.get(); }
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
    public static final ModConfigSpec.BooleanValue EXPEDITIONS = B.comment("Discover finite bandit camps, occupied mines and ruined forts in loaded, unclaimed natural terrain.").define("expeditionSites",true);
    public static final ModConfigSpec.IntValue MAX_EXPEDITIONS = B.comment("Maximum expedition sites per dimension; cleared sites are retained for journals and outposts.").defineInRange("maxExpeditionSites",64,0,256);
    public static final ModConfigSpec.IntValue MAX_BANDITS = B.comment("Maximum living expedition defenders and convoy raiders, including unloaded defenders.").defineInRange("maxExpeditionBandits",48,0,256);
    public static final ModConfigSpec.BooleanValue CONVOY_RAIDS = B.comment("Occupied sites can warn of and launch small convoy raids only while a player is nearby.").define("convoyRaids",true);
    public static final ModConfigSpec.BooleanValue SERVER_DIAGNOSTICS = B.comment("Log sustained worker problems, citizen recovery and missing-citizen searches to the server console and latest.log. Recipe exceptions always log their stack trace.").define("serverDiagnostics",true);
    public static final ModConfigSpec.IntValue DIAGNOSTIC_DELAY = B.comment("Loaded seconds a worker must report a problem before its first warning; ordinary idle work and off-duty citizens are excluded.").defineInRange("diagnosticStallSeconds",120,10,3600);
    public static final ModConfigSpec.IntValue DIAGNOSTIC_REPEAT = B.comment("Minimum seconds between repeated warnings for one citizen, including changing problems and repeated rescue attempts.").defineInRange("diagnosticRepeatSeconds",300,30,3600);
    public static final ModConfigSpec SPEC = B.build();
    private Config() {}
}
