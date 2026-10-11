package com.m4ytenn.barotraumaoceans.command;

import com.m4ytenn.barotraumaoceans.config.BOConfig;
import com.m4ytenn.barotraumaoceans.worldgen.TerrainAccess;
import com.m4ytenn.barotraumaoceans.worldgen.core.OceanTerrain;
import com.m4ytenn.barotraumaoceans.worldgen.core.TunnelNetwork;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.Style;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.event.RegisterCommandsEvent;

import java.util.List;

/**
 * /barotrauma stations [count]  - nearest station caverns, their channels and seabed shafts
 * /barotrauma route &lt;x&gt; &lt;z&gt;   - station-to-station route along the channels towards a position
 * /barotrauma info              - what is around you (region, biome slot, nearest station)
 * /barotrauma tp [index]        - teleport to a nearby station floor (ops)
 */
public final class BarotraumaCommand {
    private BarotraumaCommand() {
    }

    public static void onRegisterCommands(RegisterCommandsEvent event) {
        register(event.getDispatcher());
    }

    private static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        int perm = BOConfig.SPEC.isLoaded() ? BOConfig.LOCATOR_PERMISSION_LEVEL.get() : 0;
        dispatcher.register(Commands.literal("barotrauma")
                .requires(src -> src.hasPermission(perm))
                .then(Commands.literal("stations")
                        .executes(ctx -> stations(ctx, 5))
                        .then(Commands.argument("count", IntegerArgumentType.integer(1, 25))
                                .executes(ctx -> stations(ctx, IntegerArgumentType.getInteger(ctx, "count")))))
                .then(Commands.literal("route")
                        .then(Commands.argument("x", IntegerArgumentType.integer())
                                .then(Commands.argument("z", IntegerArgumentType.integer())
                                        .executes(ctx -> route(ctx, IntegerArgumentType.getInteger(ctx, "x"), IntegerArgumentType.getInteger(ctx, "z"))))))
                .then(Commands.literal("info").executes(BarotraumaCommand::info))
                .then(Commands.literal("tp")
                        .requires(src -> src.hasPermission(2))
                        .executes(ctx -> tp(ctx, 1))
                        .then(Commands.argument("index", IntegerArgumentType.integer(1, 25))
                                .executes(ctx -> tp(ctx, IntegerArgumentType.getInteger(ctx, "index"))))));
    }

    private static OceanTerrain terrainOrFail(CommandSourceStack src) {
        ServerLevel level = src.getLevel();
        OceanTerrain t = TerrainAccess.terrain(level);
        if (t == null) {
            src.sendFailure(Component.translatable("commands.barotrauma_oceans.not_ocean_world"));
            return null;
        }
        if (!t.network.enabled()) {
            src.sendFailure(Component.translatable("commands.barotrauma_oceans.no_network"));
            return null;
        }
        return t;
    }

    private static int radius() {
        return BOConfig.SPEC.isLoaded() ? BOConfig.LOCATOR_SEARCH_RADIUS.get() : 3;
    }

    static String compass(double dx, double dz) {
        double angle = Math.toDegrees(Math.atan2(dx, -dz));
        if (angle < 0) {
            angle += 360;
        }
        String[] names = {"N", "NE", "E", "SE", "S", "SW", "W", "NW"};
        return names[(int) Math.round(angle / 45.0) % 8];
    }

    private static MutableComponent coords(int x, int y, int z) {
        String text = x + " " + y + " " + z;
        return Component.literal("[" + text + "]").withStyle(Style.EMPTY
                .withColor(ChatFormatting.AQUA)
                .withClickEvent(new ClickEvent(ClickEvent.Action.SUGGEST_COMMAND, "/tp @s " + text))
                .withHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT, Component.translatable("commands.barotrauma_oceans.click_to_tp"))));
    }

    private static String className(TunnelNetwork.TunnelClass cls) {
        return switch (cls) {
            case SPACIOUS -> "spacious";
            case STANDARD -> "standard";
            case NARROW -> "narrow";
        };
    }

    private static int stations(CommandContext<CommandSourceStack> ctx, int count) {
        CommandSourceStack src = ctx.getSource();
        OceanTerrain t = terrainOrFail(src);
        if (t == null) {
            return 0;
        }
        Vec3 p = src.getPosition();
        List<TunnelNetwork.Station> list = t.network.nearestStations(p.x, p.z, count, radius());
        src.sendSuccess(() -> Component.translatable("commands.barotrauma_oceans.stations.header", list.size()).withStyle(ChatFormatting.GOLD), false);
        int i = 1;
        for (TunnelNetwork.Station st : list) {
            final int index = i++;
            double dx = st.x - p.x;
            double dz = st.z - p.z;
            int dist = (int) Math.round(Math.sqrt(dx * dx + dz * dz));
            MutableComponent line = Component.literal(" #" + index + " ").withStyle(ChatFormatting.YELLOW)
                    .append(coords((int) st.x, st.floorBlockY() + 1, (int) st.z))
                    .append(Component.literal(" " + dist + "m " + compass(dx, dz) + ", ").withStyle(ChatFormatting.GRAY))
                    .append(Component.translatable("commands.barotrauma_oceans.stations.size", (int) (st.maxR * 2), (int) (st.ry * 2)).withStyle(ChatFormatting.GRAY));
            src.sendSuccess(() -> line, false);
            StringBuilder links = new StringBuilder();
            for (TunnelNetwork.Tunnel tn : t.network.tunnelsOf(st)) {
                TunnelNetwork.Station other = t.network.other(tn, st);
                if (links.length() > 0) {
                    links.append(", ");
                }
                links.append(compass(other.x - st.x, other.z - st.z)).append(" (").append(className(tn.cls)).append(", ")
                        .append((int) tn.length).append("m)");
            }
            MutableComponent channels = Component.literal("    ").append(Component.translatable("commands.barotrauma_oceans.stations.channels", links.toString())
                    .withStyle(ChatFormatting.DARK_AQUA));
            src.sendSuccess(() -> channels, false);
            if (st.shaft != null) {
                MutableComponent shaft = Component.literal("    ").append(Component.translatable("commands.barotrauma_oceans.stations.shaft").withStyle(ChatFormatting.DARK_GREEN))
                        .append(" ").append(coords((int) st.shaft.x1, (int) st.shaft.y1, (int) st.shaft.z1));
                src.sendSuccess(() -> shaft, false);
            }
        }
        return list.size();
    }

    private static int route(CommandContext<CommandSourceStack> ctx, int tx, int tz) {
        CommandSourceStack src = ctx.getSource();
        OceanTerrain t = terrainOrFail(src);
        if (t == null) {
            return 0;
        }
        Vec3 p = src.getPosition();
        List<TunnelNetwork.Station> fromList = t.network.nearestStations(p.x, p.z, 1, 1);
        List<TunnelNetwork.Station> toList = t.network.nearestStations(tx, tz, 1, 1);
        if (fromList.isEmpty() || toList.isEmpty()) {
            src.sendFailure(Component.translatable("commands.barotrauma_oceans.route.none"));
            return 0;
        }
        TunnelNetwork.Station from = fromList.get(0);
        TunnelNetwork.Station to = toList.get(0);
        long cells = Math.max(Math.abs(from.gx - to.gx), Math.abs(from.gz - to.gz));
        if (cells > 64) {
            src.sendFailure(Component.translatable("commands.barotrauma_oceans.route.too_far"));
            return 0;
        }
        List<TunnelNetwork.Station> path = t.network.route(from, to, 6);
        if (path.isEmpty()) {
            src.sendFailure(Component.translatable("commands.barotrauma_oceans.route.none"));
            return 0;
        }
        src.sendSuccess(() -> Component.translatable("commands.barotrauma_oceans.route.header", path.size() - 1).withStyle(ChatFormatting.GOLD), false);
        for (int i = 0; i < path.size(); i++) {
            TunnelNetwork.Station st = path.get(i);
            MutableComponent line = Component.literal(" " + (i + 1) + ". ").withStyle(ChatFormatting.YELLOW)
                    .append(coords((int) st.x, st.floorBlockY() + 1, (int) st.z));
            if (i + 1 < path.size()) {
                TunnelNetwork.Station next = path.get(i + 1);
                line.append(Component.literal(" -> " + compass(next.x - st.x, next.z - st.z)).withStyle(ChatFormatting.GRAY));
            }
            src.sendSuccess(() -> line, false);
        }
        return path.size();
    }

    private static int info(CommandContext<CommandSourceStack> ctx) {
        CommandSourceStack src = ctx.getSource();
        ServerLevel level = src.getLevel();
        OceanTerrain t = TerrainAccess.terrain(level);
        if (t == null) {
            src.sendFailure(Component.translatable("commands.barotrauma_oceans.not_ocean_world"));
            return 0;
        }
        Vec3 p = src.getPosition();
        OceanTerrain.Column c = t.column((int) Math.floor(p.x) & ~3, (int) Math.floor(p.z) & ~3);
        OceanTerrain.SlotResult r = new OceanTerrain.SlotResult();
        t.slot(c, p.y, r);
        String biome = level.getBiome(net.minecraft.core.BlockPos.containing(p)).unwrapKey().map(k -> k.location().toString()).orElse("?");
        src.sendSuccess(() -> Component.translatable("commands.barotrauma_oceans.info.region",
                c.ps.dominant.name().toLowerCase(), r.slot.name().toLowerCase(), biome).withStyle(ChatFormatting.GOLD), false);
        src.sendSuccess(() -> Component.translatable("commands.barotrauma_oceans.info.levels",
                (int) c.seabed, t.ice ? String.valueOf((int) c.iceBottom) : "-", t.waterLevel).withStyle(ChatFormatting.GRAY), false);
        if (t.network.enabled()) {
            List<TunnelNetwork.Station> near = t.network.nearestStations(p.x, p.z, 1, 1);
            if (!near.isEmpty()) {
                TunnelNetwork.Station st = near.get(0);
                double dx = st.x - p.x;
                double dz = st.z - p.z;
                int dist = (int) Math.round(Math.sqrt(dx * dx + dz * dz));
                MutableComponent line = Component.translatable("commands.barotrauma_oceans.info.nearest", dist, compass(dx, dz)).withStyle(ChatFormatting.GRAY)
                        .append(" ").append(coords((int) st.x, st.floorBlockY() + 1, (int) st.z));
                src.sendSuccess(() -> line, false);
            }
        }
        return 1;
    }

    private static int tp(CommandContext<CommandSourceStack> ctx, int index) throws CommandSyntaxException {
        CommandSourceStack src = ctx.getSource();
        ServerPlayer player = src.getPlayerOrException();
        OceanTerrain t = terrainOrFail(src);
        if (t == null) {
            return 0;
        }
        Vec3 p = src.getPosition();
        List<TunnelNetwork.Station> list = t.network.nearestStations(p.x, p.z, index, radius());
        if (list.size() < index) {
            src.sendFailure(Component.translatable("commands.barotrauma_oceans.route.none"));
            return 0;
        }
        TunnelNetwork.Station st = list.get(index - 1);
        player.teleportTo(src.getLevel(), st.x + 0.5, st.floorBlockY() + 2, st.z + 0.5, player.getYRot(), player.getXRot());
        src.sendSuccess(() -> Component.translatable("commands.barotrauma_oceans.tp", (int) st.x, st.floorBlockY() + 2, (int) st.z), true);
        return 1;
    }
}
