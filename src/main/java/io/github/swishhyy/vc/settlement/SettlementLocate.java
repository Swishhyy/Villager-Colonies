package io.github.swishhyy.vc.settlement;

import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.exceptions.SimpleCommandExceptionType;
import java.util.Comparator;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.RegisterCommandsEvent;

/** Locates saved NPC towns without generating terrain or loading their chunks. */
public final class SettlementLocate {
    private static final SimpleCommandExceptionType WRONG_DIMENSION=new SimpleCommandExceptionType(
            Component.literal("AI settlements are in the Overworld. Run /locate settlement there."));
    private static final SimpleCommandExceptionType NOT_FOUND=new SimpleCommandExceptionType(
            Component.literal("No completed AI settlement has generated yet. Explore suitable Overworld terrain, then try again."));

    @SubscribeEvent public void commands(RegisterCommandsEvent event) {
        // Brigadier merges this child into vanilla's root, preserving structure, biome and poi.
        event.getDispatcher().register(Commands.literal("locate")
                .requires(Commands.hasPermission(Commands.LEVEL_GAMEMASTERS))
                .then(Commands.literal("settlement").executes(c -> locate(c.getSource()))));
    }

    private static int locate(CommandSourceStack source) throws CommandSyntaxException {
        var level=source.getLevel();
        if(!level.dimension().equals(Level.OVERWORLD)) throw WRONG_DIMENSION.create();
        Vec3 origin=source.getPosition();
        Settlement town=SettlementData.get(level).settlements.stream()
                .filter(t -> t.trading.npc && t.trading.buildIndex<0)
                .min(Comparator.comparingDouble(t -> distanceSquared(origin,t)))
                .orElseThrow(NOT_FOUND::create);
        var pos=town.center;
        int distance=(int)Math.sqrt(distanceSquared(origin,town));
        Component coordinates=Component.translatable("chat.coordinates",pos.getX(),pos.getY(),pos.getZ())
                .withStyle(style -> style.withColor(ChatFormatting.GREEN)
                        .withClickEvent(new ClickEvent.SuggestCommand("/tp @s "+pos.getX()+" "+pos.getY()+" "+pos.getZ()))
                        .withHoverEvent(new HoverEvent.ShowText(Component.translatable("chat.coordinates.tooltip"))));
        source.sendSuccess(() -> Component.literal("The nearest AI settlement, "+town.name+", is at ")
                .append(coordinates).append(" ("+distance+" blocks away)."),false);
        return distance;
    }

    private static double distanceSquared(Vec3 origin,Settlement town) {
        double x=origin.x-town.center.getX(),z=origin.z-town.center.getZ();
        return x*x+z*z;
    }
}
