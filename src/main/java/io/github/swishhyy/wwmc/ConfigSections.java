package io.github.swishhyy.wwmc;

import com.electronwill.nightconfig.core.UnmodifiableConfig;
import java.util.LinkedHashSet;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/** Display groups over the existing config entries. Saved TOML paths stay flat and unchanged. */
public final class ConfigSections {
    public record Section(String id,List<String> keys) {
        public Section { keys=List.copyOf(keys); }
        public String translationKey() { return "wwmc.configuration.category."+id; }
        public Set<UnmodifiableConfig.Entry> entries(Set<? extends UnmodifiableConfig.Entry> source) {
            Set<UnmodifiableConfig.Entry> result=new LinkedHashSet<>();
            for(String key:keys) for(var entry:source) if(entry.getKey().equals(key)) result.add(entry);
            return result;
        }
    }
    public static final List<Section> SECTIONS=List.of(
        new Section("settlements",List.of("settlementRadius","maxCitizens","basePopulation","populationPerUpgrade","populationUpgradeCost","stationUpgradeCost")),
        new Section("workers",List.of("stationWorkers","courierWorkers","quarryWorkers","guardWorkers","processingWorkers","animalWorkers","blacksmithWorkers")),
        new Section("mining",List.of("oreVeinSeconds","mineMinY","mineMaxY","quarryTargetY","mineBranchLength","mineBranchPairs")),
        new Section("food",List.of("workTicks","rationTicks","mealIntervalMultiplier","fishingSeconds","animalBreeders")),
        new Section("enchanting",List.of("enchantMinutes","enchanterMaxLevel")),
        new Section("defense",List.of("alarmThreshold","enemyWaves","waveMinPopulation","waveIntervalDays","waveBaseMobs","waveMobsPerCitizen","waveMaxMobs","waveMobsPerUpgrade")),
        new Section("world",List.of("maxActiveTraders","tradeRouteDistance","randomSettlements","npcTownSpacing","maxNpcTowns","expeditionSites","maxExpeditionSites","maxExpeditionBandits","convoyRaids"))
    );
    private ConfigSections() {}
    /** Future settings remain reachable until they receive a dedicated group. */
    public static Set<UnmodifiableConfig.Entry> other(Set<? extends UnmodifiableConfig.Entry> source) {
        Set<String> known=new HashSet<>();
        for(Section section:SECTIONS) known.addAll(section.keys());
        Set<UnmodifiableConfig.Entry> result=new LinkedHashSet<>();
        for(var entry:source) if(!known.contains(entry.getKey())) result.add(entry);
        return result;
    }
}
