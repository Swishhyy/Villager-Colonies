package io.github.swishhyy.wwmc.settlement;

import com.mojang.serialization.Codec;
import io.github.swishhyy.wwmc.WWMC;
import java.util.*;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.level.saveddata.SavedDataType;

/** A destroyed generated town does not respawn just because its region is visited again. */
public final class NpcWorldData extends SavedData {
    private static final Codec<NpcWorldData> CODEC=Codec.STRING.listOf().xmap(NpcWorldData::new,d -> new ArrayList<>(d.generated));
    private static final SavedDataType<NpcWorldData> TYPE=new SavedDataType<>(Identifier.fromNamespaceAndPath(WWMC.MODID,"npc_regions"),NpcWorldData::new,CODEC);
    public final Set<String> generated;
    public NpcWorldData() { this(List.of()); }
    private NpcWorldData(List<String> regions) { generated=new HashSet<>(regions); }
    public static NpcWorldData get(ServerLevel level) { return level.getDataStorage().computeIfAbsent(TYPE); }
    public void generated(String key) { if(generated.add(key)) setDirty(); }
}
