package io.github.swishhyy.wwmc;
import io.github.swishhyy.wwmc.client.CitizenRenderer;
import io.github.swishhyy.wwmc.client.StationRangePreview;
import io.github.swishhyy.wwmc.client.screen.CitizenScreen;
import io.github.swishhyy.wwmc.client.screen.CraftsmanScreen;
import io.github.swishhyy.wwmc.client.screen.PanelScreen;
import io.github.swishhyy.wwmc.menu.ViewMenu;
import io.github.swishhyy.wwmc.menu.WwmcMenus;
import io.github.swishhyy.wwmc.menu.WwmcNetwork;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;
import net.neoforged.neoforge.client.event.RegisterMenuScreensEvent;
import net.neoforged.neoforge.client.network.event.RegisterClientPayloadHandlersEvent;
import net.neoforged.neoforge.client.gui.ConfigurationScreen;
import net.neoforged.neoforge.client.gui.IConfigScreenFactory;
import net.neoforged.neoforge.common.NeoForge;

@Mod(value=WWMC.MODID,dist=Dist.CLIENT)
public final class WWMCClient {
    public WWMCClient(IEventBus bus, ModContainer container) {
        container.registerExtensionPoint(IConfigScreenFactory.class,ConfigurationScreen::new);
        bus.addListener(WWMCClient::renderers);
        bus.addListener(WWMCClient::layers);
        bus.addListener(WWMCClient::screens);
        bus.addListener(WWMCClient::payloads);
        NeoForge.EVENT_BUS.register(new StationRangePreview());
    }
    private static void renderers(EntityRenderersEvent.RegisterRenderers event) { event.registerEntityRenderer(WWMC.CITIZEN.get(),CitizenRenderer::new); }
    private static void layers(EntityRenderersEvent.RegisterLayerDefinitions event) { event.registerLayerDefinition(CitizenRenderer.LAYER,CitizenRenderer::createBodyLayer); }
    private static void screens(RegisterMenuScreensEvent event) {
        event.register(WwmcMenus.PANEL.get(),PanelScreen::new);
        event.register(WwmcMenus.CRAFTSMAN.get(),CraftsmanScreen::new);
        event.register(WwmcMenus.CITIZEN.get(),CitizenScreen::new);
    }
    /** A refresh only applies to the screen it was built for. */
    private static void payloads(RegisterClientPayloadHandlersEvent event) {
        event.register(WwmcNetwork.ViewPayload.TYPE,(payload,context) -> {
            if(context.player().containerMenu.containerId==payload.containerId() && context.player().containerMenu instanceof ViewMenu menu) menu.view(payload.view());
        });
    }
}
