package io.github.swishhyy.wwmc.client.screen;

import com.electronwill.nightconfig.core.UnmodifiableConfig;
import io.github.swishhyy.wwmc.ConfigSections;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Set;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.config.ModConfig;
import net.neoforged.neoforge.client.gui.ConfigurationScreen;
import net.neoforged.neoforge.client.gui.ConfigurationScreen.ConfigurationSectionScreen;

/** Native config editing, undo/reset and save behavior, with a short category menu. */
public final class WwmcConfigScreen extends ConfigurationSectionScreen {
    public static Screen create(ModContainer mod,Screen parent) {
        return new ConfigurationScreen(mod,parent,WwmcConfigScreen::new);
    }
    private WwmcConfigScreen(ConfigurationScreen parent,ModConfig.Type type,ModConfig config,Component title) {
        // Only the top menu hides the flat values. Its child screens keep the native editors.
        super(parent,type,config,title,(c,key,element) -> c.parent() instanceof ConfigurationScreen ? null : element);
    }
    @Override protected Collection<? extends Element> createSyntheticValues() {
        List<Element> groups=new ArrayList<>();
        for(ConfigSections.Section section:ConfigSections.SECTIONS)
            addSection(groups,section.id(),section.entries(context.entries()));
        addSection(groups,"other",ConfigSections.other(context.entries()));
        return groups;
    }
    private void addSection(List<Element> groups,String id,Set<? extends UnmodifiableConfig.Entry> entries) {
        if(entries.isEmpty()) return;
        String key="wwmc.configuration.category."+id;
        Component name=Component.translatable(key),description=Component.translatable(key+".tooltip");
        Button button=Button.builder(Component.translatable("wwmc.configuration.category.open",entries.size()),b -> {
            ConfigurationSectionScreen section=sectionCache.computeIfAbsent(id,k ->
                    new ConfigurationSectionScreen(context,this,context.valueSpecs(),id,entries,name));
            minecraft.gui.setScreen(section);
        }).tooltip(Tooltip.create(description)).width(Button.DEFAULT_WIDTH).build();
        groups.add(new Element(name,description,button,false));
    }
}
