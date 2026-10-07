package io.github.swishhyy.wwmc;

import com.electronwill.nightconfig.core.CommentedConfig;
import com.google.gson.JsonParser;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import net.minecraft.server.MinecraftServer;
import net.neoforged.neoforge.common.ModConfigSpec;
import net.neoforged.testframework.junit.EphemeralTestServerProvider;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import static org.junit.jupiter.api.Assertions.*;

public final class ConfigScreenChecks {
    @Test @ExtendWith(EphemeralTestServerProvider.class)
    void everySettingHasOneReadableSectionWithoutMovingItsSavedPath(MinecraftServer server) throws Exception {
        var source=Config.SPEC.getValues().entrySet();
        Set<String> seen=new HashSet<>();
        try(var input=ConfigScreenChecks.class.getResourceAsStream("/assets/wwmc/lang/en_us.json")) {
            assertNotNull(input);
            var language=JsonParser.parseReader(new InputStreamReader(input,StandardCharsets.UTF_8)).getAsJsonObject();
            for(ConfigSections.Section section:ConfigSections.SECTIONS) {
                assertTrue(language.has(section.translationKey()));
                assertTrue(language.has(section.translationKey()+".tooltip"));
                var entries=section.entries(source);
                assertFalse(entries.isEmpty());
                for(var entry:entries) {
                    assertTrue(seen.add(entry.getKey()),"Setting appears twice: "+entry.getKey());
                    var original=source.stream().filter(e -> e.getKey().equals(entry.getKey())).findFirst().orElseThrow();
                    assertSame(original.getRawValue(),entry.getRawValue(),"Sections must edit the original ConfigValue");
                    var value=(ModConfigSpec.ConfigValue<?>)entry.getRawValue();
                    assertEquals(List.of(entry.getKey()),value.getPath(),"Grouping must preserve existing flat TOML paths");
                    String key="wwmc.configuration."+entry.getKey();
                    assertTrue(language.has(key),"Missing config label: "+key);
                    assertTrue(language.has(key+".tooltip"),"Missing config explanation: "+key);
                    String label=language.get(key).getAsString();
                    assertFalse(label.isBlank()); assertFalse(label.startsWith("wwmc."));
                    assertTrue(label.length()<=25,"Label should fit the native config column: "+label);
                }
            }
        }
        assertEquals(source.size(),seen.size(),"Every current setting must remain reachable");
        assertTrue(ConfigSections.other(source).isEmpty());
    }
    @Test @ExtendWith(EphemeralTestServerProvider.class)
    void previousServerOverridesStillPassTheUnchangedConfigSpec(MinecraftServer server) {
        CommentedConfig existing=CommentedConfig.inMemory();
        for(var entry:Config.SPEC.getSpec().entrySet())
            existing.set(entry.getKey(),((ModConfigSpec.ValueSpec)entry.getRawValue()).getDefault());
        existing.set("guardWorkers",4);
        existing.set("basePopulation",25);
        existing.set("mineMinY",-44);
        existing.set("rationTicks",1800);
        existing.set("enemyWaves",false);
        Config.SPEC.correct(existing);
        assertEquals(Integer.valueOf(4),existing.get("guardWorkers"));
        assertEquals(Integer.valueOf(25),existing.get("basePopulation"));
        assertEquals(Integer.valueOf(-44),existing.get("mineMinY"));
        assertEquals(Integer.valueOf(1800),existing.get("rationTicks"));
        assertEquals(Boolean.FALSE,existing.get("enemyWaves"));
        assertEquals(Config.SPEC.getSpec().size(),existing.size());
        for(var entry:existing.entrySet()) assertFalse(entry.getRawValue() instanceof CommentedConfig,"No new nested sections in the saved file");
    }
}
