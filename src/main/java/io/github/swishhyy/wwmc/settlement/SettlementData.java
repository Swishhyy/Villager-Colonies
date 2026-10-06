package io.github.swishhyy.wwmc.settlement;
import com.mojang.serialization.Codec;
import io.github.swishhyy.wwmc.WWMC;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.level.saveddata.SavedDataType;

public final class SettlementData extends SavedData {
    private static final Codec<SettlementData> CODEC = Settlement.CODEC.listOf().xmap(SettlementData::new, d -> d.settlements);
    private static final SavedDataType<SettlementData> TYPE = new SavedDataType<>(Identifier.fromNamespaceAndPath(WWMC.MODID,"settlements"), SettlementData::new, CODEC);
    public final List<Settlement> settlements;
    public SettlementData() { this(List.of()); }
    public SettlementData(List<Settlement> settlements) { this.settlements = new ArrayList<>(settlements); }
    public static SettlementData get(ServerLevel level) { return level.getDataStorage().computeIfAbsent(TYPE); }
    public Settlement at(BlockPos pos) { return settlements.stream().filter(s -> s.contains(pos)).findFirst().orElse(null); }
    public Settlement byId(UUID id) { return settlements.stream().filter(s -> s.id.equals(id)).findFirst().orElse(null); }
}
