package io.github.swishhyy.vc;
import io.github.swishhyy.vc.client.CitizenRenderer;
import io.github.swishhyy.vc.client.StationRangePreview;
import io.github.swishhyy.vc.client.screen.CitizenScreen;
import io.github.swishhyy.vc.client.screen.CraftsmanScreen;
import io.github.swishhyy.vc.client.screen.PanelScreen;
import io.github.swishhyy.vc.client.screen.VcConfigScreen;
import io.github.swishhyy.vc.client.screen.TraderScreen;
import io.github.swishhyy.vc.menu.ViewMenu;
import io.github.swishhyy.vc.menu.VcMenus;
import io.github.swishhyy.vc.menu.VcNetwork;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;
import net.neoforged.neoforge.client.event.RegisterMenuScreensEvent;
import net.neoforged.neoforge.client.network.event.RegisterClientPayloadHandlersEvent;
import net.neoforged.neoforge.client.gui.IConfigScreenFactory;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.player.ItemTooltipEvent;
import io.github.swishhyy.vc.block.StationBlock;
import io.github.swishhyy.vc.core.Upgrades;
import net.minecraft.ChatFormatting;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.BlockItem;

@Mod(value=VC.MODID,dist=Dist.CLIENT)
public final class VCClient {
    public VCClient(IEventBus bus, ModContainer container) {
        container.registerExtensionPoint(IConfigScreenFactory.class,VcConfigScreen::create);
        bus.addListener(VCClient::renderers);
        bus.addListener(VCClient::layers);
        bus.addListener(VCClient::screens);
        bus.addListener(VCClient::payloads);
        NeoForge.EVENT_BUS.register(new StationRangePreview());
        NeoForge.EVENT_BUS.addListener(VCClient::tooltip);
        NeoForge.EVENT_BUS.register(new io.github.swishhyy.vc.client.map.ExternalMaps());
    }
    /** A station item from a broken, upgraded station names the upgrades it will bring back. */
    private static void tooltip(ItemTooltipEvent event) {
        if(io.github.swishhyy.vc.settlement.AgeProgression.required(event.getItemStack())>0)
            event.getToolTip().add(Component.literal("Settlement research: "+io.github.swishhyy.vc.settlement.AgeProgression.requirement(event.getItemStack())).withStyle(ChatFormatting.GOLD));
        if(event.getItemStack().getItem() instanceof BlockItem trapItem && trapItem.getBlock() instanceof io.github.swishhyy.vc.block.TrapBlock trap) {
            var saved=event.getItemStack().get(DataComponents.BLOCK_STATE);
            int wear=0;
            if(saved!=null) try { wear=Integer.parseInt(saved.properties().getOrDefault("wear","0")); } catch(NumberFormatException ignored) {}
            event.getToolTip().add(Component.literal(trap.kind().effect()).withStyle(ChatFormatting.GRAY));
            event.getToolTip().add(Component.literal(Math.max(0,trap.kind().uses-wear)+" uses remaining; hostile mobs only")
                    .withStyle(wear>=trap.kind().uses ? ChatFormatting.RED : ChatFormatting.GREEN));
        }
        if(!(event.getItemStack().getItem() instanceof BlockItem item) || !(item.getBlock() instanceof StationBlock block)) return;
        var state=event.getItemStack().get(DataComponents.BLOCK_STATE);
        if(state==null) return;
        String range=state.properties().getOrDefault(StationBlock.RANGE.getName(),"0"),crew=state.properties().getOrDefault(StationBlock.CREW.getName(),"0");
        if(!range.equals("0")) event.getToolTip().add(Component.literal("Range upgrade "+range).withStyle(ChatFormatting.GREEN));
        if(Upgrades.hires(block.role()) && !crew.equals("0")) event.getToolTip().add(Component.literal("Crew upgrade "+crew).withStyle(ChatFormatting.GREEN));
        String yieldLevel=state.properties().getOrDefault(StationBlock.YIELD.getName(),"0");
        if(Upgrades.yields(block.role()) && !yieldLevel.equals("0")) event.getToolTip().add(Component.literal("Yield upgrade "+yieldLevel+" (+"+yieldLevel+"0%)").withStyle(ChatFormatting.GREEN));
    }
    private static void renderers(EntityRenderersEvent.RegisterRenderers event) {
        event.registerEntityRenderer(VC.CITIZEN.get(),CitizenRenderer::new);
        event.registerEntityRenderer(VC.BARBARIAN.get(),io.github.swishhyy.vc.client.BarbarianRenderer::new);
    }
    private static void layers(EntityRenderersEvent.RegisterLayerDefinitions event) {
        event.registerLayerDefinition(CitizenRenderer.LAYER,CitizenRenderer::createBodyLayer);
        event.registerLayerDefinition(io.github.swishhyy.vc.client.CitizenOutfitLayer.PROFESSION,CitizenRenderer::createBodyLayer);
        event.registerLayerDefinition(io.github.swishhyy.vc.client.CitizenOutfitLayer.LAYER,io.github.swishhyy.vc.client.CitizenOutfitLayer::createBodyLayer);
    }
    private static void screens(RegisterMenuScreensEvent event) {
        event.register(VcMenus.ALLOY_FURNACE.get(),io.github.swishhyy.vc.client.screen.AlloyFurnaceScreen::new);
        event.register(VcMenus.PANEL.get(),PanelScreen::new);
        event.register(VcMenus.CRAFTSMAN.get(),CraftsmanScreen::new);
        event.register(VcMenus.CITIZEN.get(),CitizenScreen::new);
        event.register(VcMenus.TRADER.get(),TraderScreen::new);
    }
    /** A refresh only applies to the screen it was built for. */
    private static void payloads(RegisterClientPayloadHandlersEvent event) {
        event.register(VcNetwork.GuidePayload.TYPE,(payload,context) ->
                io.github.swishhyy.vc.client.screen.GuideScreen.open(null,payload.topic()));
        event.register(VcNetwork.ViewPayload.TYPE,(payload,context) -> {
            if(context.player().containerMenu.containerId==payload.containerId() && context.player().containerMenu instanceof ViewMenu menu) menu.view(payload.view());
        });
        event.register(VcNetwork.HighlightPayload.TYPE,(payload,context) -> StationRangePreview.highlight(payload.pos(),payload.seconds()));
        event.register(VcNetwork.MapPayload.TYPE,(payload,context) ->
                io.github.swishhyy.vc.client.map.ExternalMaps.receive(payload.map(),payload.open()));
    }
}
