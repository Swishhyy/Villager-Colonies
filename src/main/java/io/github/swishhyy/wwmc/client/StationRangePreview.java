package io.github.swishhyy.wwmc.client;

import io.github.swishhyy.wwmc.block.StationBlock;
import io.github.swishhyy.wwmc.core.RoomBounds;
import io.github.swishhyy.wwmc.core.StationRange;
import io.github.swishhyy.wwmc.event.StationPreviewEvent;
import io.github.swishhyy.wwmc.item.SurveyorItem;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.gizmos.Gizmo;
import net.minecraft.gizmos.GizmoPrimitives;
import net.minecraft.gizmos.Gizmos;
import net.minecraft.util.ARGB;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.client.event.ExtractLevelRenderStateEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent;

/** World-space outlines use Minecraft's gizmo submission during level extraction. */
public final class StationRangePreview {
    private static final long PREVIEW_NANOS=3_000_000_000L;
    private static final int MAX_RECENT=16;
    private final Map<BlockPos,Long> recent=new LinkedHashMap<>();
    private ClientLevel previewLevel;

    private void syncLevel(ClientLevel level) {
        if(previewLevel!=level) { recent.clear(); previewLevel=level; }
    }
    @SubscribeEvent public void tick(ClientTickEvent.Post event) { syncLevel(Minecraft.getInstance().level); }
    @SubscribeEvent public void show(StationPreviewEvent event) {
        Minecraft mc=Minecraft.getInstance();
        if(mc.player==null || event.level()!=mc.level || !event.player().equals(mc.player.getUUID())) return;
        syncLevel(mc.level);
        recent.put(event.position(),System.nanoTime()+PREVIEW_NANOS);
        if(recent.size()>MAX_RECENT) recent.remove(recent.keySet().iterator().next());
    }
    @SubscribeEvent public void extract(ExtractLevelRenderStateEvent event) {
        Minecraft mc=Minecraft.getInstance();
        syncLevel(event.getLevel());
        if(mc.player==null || mc.level!=event.getLevel()) return;
        long now=System.nanoTime();
        Iterator<Map.Entry<BlockPos,Long>> iterator=recent.entrySet().iterator();
        while(iterator.hasNext()) {
            var entry=iterator.next();
            if(entry.getValue()<=now || !(mc.level.getBlockState(entry.getKey()).getBlock() instanceof StationBlock)) {
                iterator.remove(); continue;
            }
            float alpha=(float)Math.min(1.0,(entry.getValue()-now)/750_000_000.0);
            outline(entry.getKey(),ARGB.multiplyAlpha(0xff80ed99,alpha));
        }
        if(!(mc.hitResult instanceof BlockHitResult hit) || hit.getType()!=HitResult.Type.BLOCK) return;
        for(InteractionHand hand:InteractionHand.values()) {
            ItemStack held=mc.player.getItemInHand(hand);
            if(held.getItem() instanceof BlockItem item && item.getBlock() instanceof StationBlock) {
                // The placement context handles replaceable blocks such as grass and snow.
                BlockPlaceContext placement=new BlockPlaceContext(new UseOnContext(mc.player,hand,hit));
                outline(placement.getClickedPos(),placement.canPlace() ? 0xff66d9ff : 0xffff6666);
                return;
            }
            if(held.getItem() instanceof SurveyorItem && mc.level.getBlockState(hit.getBlockPos()).getBlock() instanceof StationBlock) {
                outline(hit.getBlockPos(),0xff66d9ff);
                return;
            }
        }
    }
    private static void outline(BlockPos pos,int color) {
        Gizmos.addGizmo(new RangeGizmo(StationRange.around(pos.getX(),pos.getY(),pos.getZ()),color)).setAlwaysOnTop();
    }
    private record RangeGizmo(RoomBounds bounds,int color) implements Gizmo {
        @Override public void emit(GizmoPrimitives primitives,float alphaMultiplier) {
            int tint=ARGB.multiplyAlpha(color,alphaMultiplier);
            double x0=bounds.minX(),y0=bounds.minY(),z0=bounds.minZ();
            // Inclusive block coordinates become outer faces, making every side exactly seven blocks long.
            double x1=bounds.maxX()+1.0,y1=bounds.maxY()+1.0,z1=bounds.maxZ()+1.0;
            for(int a=0;a<2;a++) for(int b=0;b<2;b++) {
                double x=a==0 ? x0 : x1,y=a==0 ? y0 : y1,z=b==0 ? z0 : z1;
                primitives.addLine(new Vec3(x0,y,z),new Vec3(x1,y,z),tint,2.0F);
                primitives.addLine(new Vec3(x,y0,z),new Vec3(x,y1,z),tint,2.0F);
                primitives.addLine(new Vec3(x,b==0 ? y0 : y1,z0),new Vec3(x,b==0 ? y0 : y1,z1),tint,2.0F);
            }
        }
    }
}
