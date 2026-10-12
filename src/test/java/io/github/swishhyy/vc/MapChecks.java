package io.github.swishhyy.vc;

import io.github.swishhyy.vc.menu.MapData;
import io.github.swishhyy.vc.menu.MapMarkers;
import io.github.swishhyy.vc.menu.VcNetwork;
import io.netty.buffer.Unpooled;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.server.MinecraftServer;
import net.neoforged.neoforge.network.connection.ConnectionType;
import net.neoforged.testframework.junit.EphemeralTestServerProvider;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import static org.junit.jupiter.api.Assertions.*;

@ExtendWith(EphemeralTestServerProvider.class)
public final class MapChecks {
    private static MapData map() {
        return new MapData("minecraft:overworld",-512,1024,
                List.of(new MapData.Town("town-id","River Town",-500,87,1000,240,MapData.NEUTRAL)),
                List.of(new MapData.Route(-500,1000,-700,1200,false,List.of(new MapData.Point(-500,1000),new MapData.Point(-550,1150),new MapData.Point(-700,1200)))),
                List.of(new MapData.Site("site-id",-700,61,1200,"Ruins","Recover stolen supplies",MapData.UNCLEARED)),
                List.of(new MapData.Mark("ping-id",-800,94,1250,"bridge","Cross here","Dylan of River Town",true)));
    }
    @Test void markersKeepDimensionHeightAndIdentityAcrossNetwork(MinecraftServer server) {
        var original=new VcNetwork.MapPayload(map(),true);
        var buf=new RegistryFriendlyByteBuf(Unpooled.buffer(),server.registryAccess(),ConnectionType.NEOFORGE);
        try {
            VcNetwork.MapPayload.STREAM_CODEC.encode(buf,original);
            var decoded=VcNetwork.MapPayload.STREAM_CODEC.decode(buf);
            assertEquals(original,decoded); assertFalse(buf.isReadable());
            var markers=MapMarkers.from(decoded.map());
            assertEquals(3,markers.size());
            assertEquals("minecraft:overworld/town:town-id",markers.getFirst().id());
            assertEquals(new BlockPos(-500,87,1000),markers.getFirst().pos());
            assertEquals(MapMarkers.relationColor(MapData.NEUTRAL),markers.getFirst().color());
            assertTrue(markers.getLast().detail().contains("/vc ping remove ping-id"));
            var renamed=new MapData("minecraft:overworld",0,0,List.of(new MapData.Town("town-id","New Name",-500,87,1000,240,MapData.HOSTILE)),List.of(),List.of(),List.of());
            assertEquals(markers.getFirst().id(),MapMarkers.from(renamed).getFirst().id(),"Renaming or diplomacy must update an existing waypoint");
            assertEquals(MapMarkers.relationColor(MapData.HOSTILE),MapMarkers.from(renamed).getFirst().color());
            var nether=new MapData("minecraft:the_nether",0,0,renamed.towns(),List.of(),List.of(),List.of());
            assertNotEquals(markers.getFirst().id(),MapMarkers.from(nether).getFirst().id());
        } finally { buf.release(); }
    }
    @Test void mapDecoderRejectsCountsThatWouldLeaveAnUnreadPayload(MinecraftServer server) {
        var buf=new RegistryFriendlyByteBuf(Unpooled.buffer(),server.registryAccess(),ConnectionType.NEOFORGE);
        try {
            buf.writeUtf("minecraft:overworld",256); buf.writeInt(0); buf.writeInt(0); buf.writeVarInt(513);
            assertThrows(IllegalArgumentException.class,() -> MapData.STREAM_CODEC.decode(buf));
        } finally { buf.release(); }
    }
}
