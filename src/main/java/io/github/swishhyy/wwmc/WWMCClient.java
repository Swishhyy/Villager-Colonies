package io.github.swishhyy.wwmc;
import io.github.swishhyy.wwmc.client.StationRangePreview;
import net.minecraft.client.renderer.entity.VillagerRenderer;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;
import net.neoforged.neoforge.client.gui.ConfigurationScreen;
import net.neoforged.neoforge.client.gui.IConfigScreenFactory;
import net.neoforged.neoforge.common.NeoForge;

@Mod(value=WWMC.MODID,dist=Dist.CLIENT)
public final class WWMCClient {
    public WWMCClient(IEventBus bus, ModContainer container) {
        container.registerExtensionPoint(IConfigScreenFactory.class,ConfigurationScreen::new);
        bus.addListener(WWMCClient::renderers);
        NeoForge.EVENT_BUS.register(new StationRangePreview());
    }
    private static void renderers(EntityRenderersEvent.RegisterRenderers event) { event.registerEntityRenderer(WWMC.CITIZEN.get(),VillagerRenderer::new); }
}
