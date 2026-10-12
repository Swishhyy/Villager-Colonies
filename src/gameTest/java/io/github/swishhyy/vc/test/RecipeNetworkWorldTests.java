package io.github.swishhyy.vc.test;

import io.netty.buffer.Unpooled;
import java.util.ArrayList;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.protocol.game.ClientboundRecipeBookAddPacket;
import net.neoforged.neoforge.network.connection.ConnectionType;
import net.neoforged.testframework.DynamicTest;
import net.neoforged.testframework.annotation.TestHolder;
import net.neoforged.testframework.gametest.EmptyTemplate;
import net.neoforged.testframework.gametest.GameTest;

/** Exercise the real recipe-book packet decoder, which a headless server alone never runs. */
public final class RecipeNetworkWorldTests {
    @GameTest(timeoutTicks=40)
    @EmptyTemplate
    @TestHolder(description="Every loaded recipe display round-trips through the client recipe-book packet codec with no empty item templates or unread data.")
    static void recipeBookPacketsDecode(DynamicTest test) {
        test.onGameTest(helper -> {
            var level=helper.getLevel(); var manager=level.getServer().getRecipeManager(); int checked=0;
            var allEntries=new ArrayList<ClientboundRecipeBookAddPacket.Entry>();
            for(var recipe:manager.getRecipes()) {
                var entries=new ArrayList<ClientboundRecipeBookAddPacket.Entry>();
                manager.listDisplaysForRecipe(recipe.id(),display -> entries.add(new ClientboundRecipeBookAddPacket.Entry(display,true,true)));
                if(entries.isEmpty()) continue;
                allEntries.addAll(entries);
                var packet=new ClientboundRecipeBookAddPacket(entries,false);
                var buf=new RegistryFriendlyByteBuf(Unpooled.buffer(),level.registryAccess(),ConnectionType.NEOFORGE);
                try {
                    ClientboundRecipeBookAddPacket.STREAM_CODEC.encode(buf,packet);
                    ClientboundRecipeBookAddPacket.STREAM_CODEC.decode(buf);
                    helper.assertTrue(buf.readableBytes()==0,"Recipe packet left unread data: "+recipe.id());
                    checked+=entries.size();
                } catch(RuntimeException exception) {
                    throw new AssertionError("Recipe-book packet failed for "+recipe.id(),exception);
                } finally { buf.release(); }
            }
            helper.assertTrue(checked>0,"No loaded recipe displays were tested");
            for(boolean replace:new boolean[]{true,false}) {
                var buf=new RegistryFriendlyByteBuf(Unpooled.buffer(),level.registryAccess(),ConnectionType.NEOFORGE);
                try {
                    var packet=new ClientboundRecipeBookAddPacket(allEntries,replace);
                    ClientboundRecipeBookAddPacket.STREAM_CODEC.encode(buf,packet);
                    var decoded=ClientboundRecipeBookAddPacket.STREAM_CODEC.decode(buf);
                    helper.assertTrue(decoded.entries().size()==checked && buf.readableBytes()==0,"Complete recipe-book packet lost entries or left unread data");
                } finally { buf.release(); }
            }
            helper.succeed();
        });
    }
}
