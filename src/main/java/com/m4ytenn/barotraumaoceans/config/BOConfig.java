package com.m4ytenn.barotraumaoceans.config;

import com.m4ytenn.barotraumaoceans.BarotraumaOceans;
import com.m4ytenn.barotraumaoceans.worldgen.core.OceanSettings;
import net.neoforged.neoforge.common.ModConfigSpec;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Common config (config/barotrauma_oceans-common.toml).
 * <p>
 * The "World Generation" part is generated from {@link OceanSettings#PARAMS}; its values are copied into
 * every new world when the world is created, so later edits never break existing worlds.
 * The "Gameplay" part is read live.
 */
public final class BOConfig {
    public static final String LANG_PREFIX = BarotraumaOceans.MODID + ".configuration.";

    public enum SpawnMode {
        /** Air pocket under the ice when the crust is enabled, otherwise a raft on the surface. */
        AUTO,
        /** Always carve an air pocket into the ice crust (falls back to a raft without ice). */
        ICE_POCKET,
        /** Always build a small raft on the water surface (falls back to the pocket with ice). */
        RAFT,
        /** Let vanilla pick the spawn point. */
        VANILLA
    }

    private static final Map<String, ModConfigSpec.ConfigValue<?>> WORLDGEN = new LinkedHashMap<>();

    public static final ModConfigSpec SPEC;

    // Gameplay values
    public static final ModConfigSpec.BooleanValue APPLY_TO_EXISTING_WORLDS;
    public static final ModConfigSpec.EnumValue<SpawnMode> SPAWN_MODE;
    public static final ModConfigSpec.IntValue SPAWN_POCKET_RADIUS;
    public static final ModConfigSpec.BooleanValue TELEPORT_NEW_PLAYERS;
    public static final ModConfigSpec.BooleanValue RESPAWN_AT_SPAWN_STRUCTURE;
    public static final ModConfigSpec.BooleanValue SET_SPAWN_RADIUS_ZERO;
    public static final ModConfigSpec.ConfigValue<List<? extends String>> STARTER_ITEMS;
    public static final ModConfigSpec.BooleanValue RELAX_WATER_MOB_DEPTH;
    public static final ModConfigSpec.ConfigValue<List<? extends String>> RELAXED_EXTRA_MOBS;
    public static final ModConfigSpec.IntValue LOCATOR_SEARCH_RADIUS;
    public static final ModConfigSpec.IntValue LOCATOR_PERMISSION_LEVEL;

    static {
        ModConfigSpec.Builder b = new ModConfigSpec.Builder();

        b.comment("World generation. These values are copied into each NEW world when it is created and stored inside it,",
                        "so changing them never causes chunk borders in existing worlds (see gameplay.apply_to_existing_worlds).")
                .translation(LANG_PREFIX + "worldgen").push("worldgen");
        List<String> openPath = new ArrayList<>();
        for (OceanSettings.Section section : OceanSettings.SECTIONS) {
            // close previous section
            if (!openPath.isEmpty()) {
                b.pop(openPath.size());
                openPath.clear();
            }
            String[] parts = section.path().split("\\.");
            // push parent levels (already commented when first created) then the leaf with its comment
            for (int i = 0; i < parts.length; i++) {
                String levelKey = String.join(".", java.util.Arrays.copyOfRange(parts, 0, i + 1));
                if (i == parts.length - 1) {
                    b.comment(section.comment()).translation(LANG_PREFIX + "worldgen." + levelKey);
                } else {
                    b.translation(LANG_PREFIX + "worldgen." + levelKey);
                }
                b.push(parts[i]);
                openPath.add(parts[i]);
            }
            for (OceanSettings.Param p : OceanSettings.PARAMS) {
                if (p.section != section) {
                    continue;
                }
                b.comment(p.comment).translation(LANG_PREFIX + "worldgen." + p.key);
                ModConfigSpec.ConfigValue<?> value = switch (p.kind) {
                    case INT -> b.defineInRange(p.name, p.defaultInt(), (int) p.min, (int) p.max);
                    case DOUBLE -> b.defineInRange(p.name, p.defaultDouble(), p.min, p.max);
                    case BOOL -> b.define(p.name, p.defaultBool());
                    case STRING -> b.define(p.name, p.defaultValue);
                    case LIST -> b.defineListAllowEmpty(p.name, p.defaultList(), () -> "namespace:biome=1", BOConfig::isBiomeEntry);
                };
                WORLDGEN.put(p.key, value);
            }
        }
        if (!openPath.isEmpty()) {
            b.pop(openPath.size());
        }
        b.pop();

        b.comment("Gameplay options. These are read live and are not stored per world.")
                .translation(LANG_PREFIX + "gameplay").push("gameplay");

        b.comment("World creation behaviour.").translation(LANG_PREFIX + "gameplay.world").push("world");
        APPLY_TO_EXISTING_WORLDS = b.comment("If true, existing Barotrauma Oceans worlds use the CURRENT world generation values instead of the ones",
                        "saved when they were created. New chunks will then not match old ones - only useful while experimenting.")
                .translation(LANG_PREFIX + "gameplay.world.apply_to_existing_worlds")
                .define("apply_to_existing_worlds", false);
        b.pop();

        b.comment("Where and how players spawn.").translation(LANG_PREFIX + "gameplay.spawn").push("spawn");
        SPAWN_MODE = b.comment("AUTO: an air pocket carved into the underside of the ice crust (or a raft when the ice is disabled).",
                        "ICE_POCKET / RAFT force one of them. VANILLA lets Minecraft choose (you will probably spawn on top of the ice).")
                .translation(LANG_PREFIX + "gameplay.spawn.mode")
                .defineEnum("mode", SpawnMode.AUTO);
        SPAWN_POCKET_RADIUS = b.comment("Radius of the spawn air pocket / raft.")
                .translation(LANG_PREFIX + "gameplay.spawn.pocket_radius")
                .defineInRange("pocket_radius", 5, 3, 16);
        TELEPORT_NEW_PLAYERS = b.comment("Move players to the spawn pocket/raft the first time they join a Barotrauma Oceans world.")
                .translation(LANG_PREFIX + "gameplay.spawn.teleport_new_players")
                .define("teleport_new_players", true);
        RESPAWN_AT_SPAWN_STRUCTURE = b.comment("Players without a bed respawn inside the spawn pocket/raft instead of on top of the ice.")
                .translation(LANG_PREFIX + "gameplay.spawn.respawn_at_spawn_structure")
                .define("respawn_at_spawn_structure", true);
        SET_SPAWN_RADIUS_ZERO = b.comment("Set the spawnRadius game rule to 0 when the world is created, so players appear exactly in the pocket.")
                .translation(LANG_PREFIX + "gameplay.spawn.set_spawn_radius_zero")
                .define("set_spawn_radius_zero", true);
        STARTER_ITEMS = b.comment("Items placed in a chest inside the spawn pocket, format \"namespace:item*count\". Empty = no chest.")
                .translation(LANG_PREFIX + "gameplay.spawn.starter_items")
                .defineListAllowEmpty("starter_items", List.of("minecraft:sea_lantern*8", "minecraft:bread*8"), () -> "minecraft:bread*1", BOConfig::isItemEntry);
        b.pop();

        b.comment("Compatibility with other mods.").translation(LANG_PREFIX + "gameplay.compat").push("compat");
        RELAX_WATER_MOB_DEPTH = b.comment("Most fish and sea creatures (vanilla and modded, e.g. Hybrid Aquatic) only spawn close to sea level.",
                        "If true, every water creature may also spawn at any depth inside Barotrauma Oceans biomes, as long as it is in water.",
                        "Requires a game restart.")
                .translation(LANG_PREFIX + "gameplay.compat.relax_water_mob_depth")
                .gameRestart()
                .define("relax_water_mob_depth", true);
        RELAXED_EXTRA_MOBS = b.comment("Additional entity ids (for example water monsters) that get the same relaxed depth rule. Requires a game restart.")
                .translation(LANG_PREFIX + "gameplay.compat.relaxed_extra_mobs")
                .gameRestart()
                .defineListAllowEmpty("relaxed_extra_mobs", List.of(), () -> "minecraft:drowned", o -> o instanceof String);
        b.pop();

        b.comment("The /barotrauma command.").translation(LANG_PREFIX + "gameplay.commands").push("commands");
        LOCATOR_SEARCH_RADIUS = b.comment("How many station grid cells around the player /barotrauma stations searches.")
                .translation(LANG_PREFIX + "gameplay.commands.search_radius")
                .defineInRange("search_radius", 3, 1, 16);
        LOCATOR_PERMISSION_LEVEL = b.comment("Permission level needed for /barotrauma stations, route and info. 0 = everyone. Teleport always needs level 2.")
                .translation(LANG_PREFIX + "gameplay.commands.permission_level")
                .defineInRange("permission_level", 0, 0, 4);
        b.pop();

        b.pop();
        SPEC = b.build();
    }

    private BOConfig() {
    }

    private static boolean isBiomeEntry(Object o) {
        return o instanceof String s && s.contains(":");
    }

    private static boolean isItemEntry(Object o) {
        return o instanceof String s && s.contains(":");
    }

    /** Snapshot of the current world generation values. */
    public static OceanSettings worldgenSnapshot() {
        Map<String, String> map = new LinkedHashMap<>();
        if (!SPEC.isLoaded()) {
            return OceanSettings.defaults();
        }
        for (Map.Entry<String, ModConfigSpec.ConfigValue<?>> e : WORLDGEN.entrySet()) {
            Object v = e.getValue().get();
            if (v instanceof List<?> list) {
                List<String> parts = new ArrayList<>();
                for (Object o : list) {
                    parts.add(String.valueOf(o));
                }
                map.put(e.getKey(), String.join(",", parts));
            } else {
                map.put(e.getKey(), String.valueOf(v));
            }
        }
        return OceanSettings.fromMap(map);
    }
}
