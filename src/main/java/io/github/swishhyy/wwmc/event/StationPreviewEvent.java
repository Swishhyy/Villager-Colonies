package io.github.swishhyy.wwmc.event;

import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.neoforged.bus.api.Event;
import net.neoforged.neoforge.common.NeoForge;

/** Client placement/inspection notification; no rendering types are loaded on a server. */
public final class StationPreviewEvent extends Event {
    private final Level level;
    private final BlockPos position;
    private final UUID player;
    private StationPreviewEvent(Level level, BlockPos position, UUID player) {
        this.level=level; this.position=position.immutable(); this.player=player;
    }
    public Level level() { return level; }
    public BlockPos position() { return position; }
    public UUID player() { return player; }
    public static void show(Level level, BlockPos position, Player player) {
        if(level.isClientSide()) NeoForge.EVENT_BUS.post(new StationPreviewEvent(level,position,player.getUUID()));
    }
}
