package com.m4ytenn.barotraumaoceans.worldgen.core;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Every world-generation parameter of Barotrauma Oceans, declared exactly once.
 * <p>
 * The table below drives three things:
 * <ul>
 *     <li>the TOML config file and the in-game config screen (sections, names, tooltips, ranges),</li>
 *     <li>the per-world snapshot that is saved into level.dat when a world is created, so changing the
 *     config later never causes chunk seams in existing worlds,</li>
 *     <li>the values read by the terrain generator.</li>
 * </ul>
 * This class deliberately has no Minecraft dependencies.
 */
public final class OceanSettings {

    public enum Kind { INT, DOUBLE, BOOL, STRING, LIST }

    /** A config section (TOML table). */
    public record Section(String path, String name, String comment) {
    }

    /** A single parameter. {@code key} is "section.path.name". */
    public static final class Param {
        public final Section section;
        public final String name;
        public final String key;
        public final Kind kind;
        public final String displayName;
        public final String comment;
        public final String defaultValue;
        public final double min;
        public final double max;

        Param(Section section, String name, Kind kind, String displayName, String comment, String defaultValue, double min, double max) {
            this.section = section;
            this.name = name;
            this.key = section.path() + "." + name;
            this.kind = kind;
            this.displayName = displayName;
            this.comment = comment;
            this.defaultValue = defaultValue;
            this.min = min;
            this.max = max;
        }

        public List<String> defaultList() {
            return splitList(defaultValue);
        }

        public int defaultInt() {
            return Integer.parseInt(defaultValue);
        }

        public double defaultDouble() {
            return Double.parseDouble(defaultValue);
        }

        public boolean defaultBool() {
            return Boolean.parseBoolean(defaultValue);
        }
    }

    public static final List<Section> SECTIONS = new ArrayList<>();
    public static final List<Param> PARAMS = new ArrayList<>();

    private static Section section(String path, String name, String comment) {
        Section s = new Section(path, name, comment);
        SECTIONS.add(s);
        return s;
    }

    private static Param add(Param p) {
        PARAMS.add(p);
        return p;
    }

    private static Param i(Section s, String name, String display, int def, int min, int max, String comment) {
        return add(new Param(s, name, Kind.INT, display, comment, Integer.toString(def), min, max));
    }

    private static Param d(Section s, String name, String display, double def, double min, double max, String comment) {
        return add(new Param(s, name, Kind.DOUBLE, display, comment, Double.toString(def), min, max));
    }

    private static Param b(Section s, String name, String display, boolean def, String comment) {
        return add(new Param(s, name, Kind.BOOL, display, comment, Boolean.toString(def), 0, 1));
    }

    private static Param str(Section s, String name, String display, String def, String comment) {
        return add(new Param(s, name, Kind.STRING, display, comment, def, 0, 0));
    }

    private static Param list(Section s, String name, String display, String comment, String... def) {
        return add(new Param(s, name, Kind.LIST, display, comment, String.join(",", def), 0, 0));
    }

    // ------------------------------------------------------------------------------------------------
    // Ocean
    // ------------------------------------------------------------------------------------------------
    public static final Section OCEAN = section("ocean", "Ocean",
            "Global water and seabed levels.");
    public static final Param WATER_LEVEL = i(OCEAN, "water_level", "Water Level", 270, -480, 318,
            "Y level of the ocean surface. With the ice crust enabled this must lie inside the crust (between its bottom and top).");
    public static final Param SEABED_LEVEL = i(OCEAN, "seabed_level", "Average Seabed Level", -64, -440, 250,
            "Average Y level of the open seabed. Regions (plains, ridges, trenches...) are offset from this.");
    public static final Param SEABED_BIOME_HEIGHT = i(OCEAN, "seabed_biome_height", "Seabed Biome Height", 40, 4, 256,
            "How many blocks above the seabed the regional seabed biome reaches before the open-water 'Great Sea' biome takes over.");

    // ------------------------------------------------------------------------------------------------
    // Ice crust
    // ------------------------------------------------------------------------------------------------
    public static final Section ICE = section("ice_crust", "Ice Crust",
            "A Barotrauma/Europa style ice shell that seals the whole ocean.");
    public static final Param ICE_ENABLED = b(ICE, "enabled", "Enable Ice Crust", true,
            "If true the ocean is covered by a thick ice shell (no sky underwater). If false the ocean has an open surface.");
    public static final Param ICE_BOTTOM = i(ICE, "bottom_level", "Crust Underside Level", 236, -400, 316,
            "Average Y level of the underside of the ice crust.");
    public static final Param ICE_BOTTOM_VARIATION = i(ICE, "bottom_variation", "Underside Variation", 10, 0, 64,
            "How much the underside of the ice undulates up and down.");
    public static final Param ICE_TOP = i(ICE, "top_level", "Crust Surface Level", 300, -380, 318,
            "Average Y level of the frozen surface on top of the crust.");
    public static final Param ICE_TOP_VARIATION = i(ICE, "top_variation", "Surface Variation", 5, 0, 32,
            "How much the frozen surface undulates.");
    public static final Param ICE_KEEL_DEPTH = i(ICE, "keel_depth", "Ice Keel Depth", 36, 0, 200,
            "How far the ridges (keels) on the underside of the ice hang down into the water.");
    public static final Param ICE_KEEL_SCALE = i(ICE, "keel_scale", "Ice Keel Scale", 220, 32, 2048,
            "Horizontal size of the keel pattern in blocks.");
    public static final Param ICE_HOLE_CHANCE = d(ICE, "hole_chance", "Crust Hole Chance", 0.0, 0.0, 1.0,
            "Chance per 512x512 block area for an open hole through the ice up to the surface. 0 keeps the ocean fully sealed.");

    // ------------------------------------------------------------------------------------------------
    // Tunnel network
    // ------------------------------------------------------------------------------------------------
    public static final Section NETWORK = section("network", "Station Network",
            "The network of deep station caverns connected by long, wide, mostly junction-free channels.");
    public static final Param NETWORK_ENABLED = b(NETWORK, "enabled", "Enable Network", true,
            "Generate station caverns and the channels between them.");
    public static final Param STATION_SPACING = i(NETWORK, "station_spacing", "Station Spacing", 1024, 256, 8192,
            "Average distance in blocks between neighbouring station caverns (the length of most channels).");
    public static final Param STATION_JITTER = d(NETWORK, "station_jitter", "Station Position Jitter", 0.25, 0.0, 0.4,
            "How irregular the station grid is, as a fraction of the spacing. 0 = perfect grid.");
    public static final Param EXTRA_CONNECTION_CHANCE = d(NETWORK, "extra_connection_chance", "Extra Connection Chance", 0.45, 0.0, 1.0,
            "Chance for each optional channel between neighbouring stations. The network is always fully connected; higher values give more routes but more junctions (junctions only ever happen inside stations).");
    public static final Param STATION_MIN_Y = i(NETWORK, "station_min_y", "Station Min Y", -420, -496, 200,
            "Lowest Y level of station cavern centres.");
    public static final Param STATION_MAX_Y = i(NETWORK, "station_max_y", "Station Max Y", -200, -496, 200,
            "Highest Y level of station cavern centres (they are also kept below the seabed, see min_rock_cover).");
    public static final Param MIN_ROCK_COVER = i(NETWORK, "min_rock_cover", "Minimum Rock Cover", 28, 4, 200,
            "Minimum thickness of rock kept between channels/stations and the seabed above them.");
    public static final Param NETWORK_FLOOR = i(NETWORK, "floor_y", "Lowest Carving Level", -488, -508, 200,
            "Nothing of the network is carved below this Y level.");
    public static final Param MEANDER = d(NETWORK, "meander", "Channel Meander", 0.10, 0.0, 0.3,
            "How much channels wind left and right, as a fraction of their length. Low values keep them straightforward.");
    public static final Param VERTICAL_WANDER = i(NETWORK, "vertical_wander", "Vertical Wander", 28, 0, 160,
            "How much channels drift up and down along their length (blocks).");
    public static final Param CLEAR_RADIUS = i(NETWORK, "clear_radius", "Guaranteed Clearance Radius", 9, 3, 40,
            "Every channel keeps a clear corridor of at least this radius around its centre line: no pillars, rocks or spikes. A vessel up to twice this size always fits.");
    public static final Param WALL_ROUGHNESS = d(NETWORK, "wall_roughness", "Wall Roughness", 4.0, 0.0, 16.0,
            "Amplitude in blocks of the noise that roughens channel and cavern walls.");
    public static final Param FLOOR_FLATNESS = d(NETWORK, "floor_flatness", "Floor Cut Height", 0.72, 0.3, 1.0,
            "Where the flat sediment floor of a channel is cut, as a fraction of its vertical radius below the centre line. 1 = round tunnels without a flat floor.");

    public static final Section TUNNELS = section("network.tunnel_types", "Channel Types",
            "Every channel picks one of three size classes. Radius = half of the width.");
    public static final Param SPACIOUS_WEIGHT = d(TUNNELS, "spacious_weight", "Spacious Weight", 3.0, 0.0, 100.0,
            "Relative chance for a spacious channel.");
    public static final Param SPACIOUS_MIN = i(TUNNELS, "spacious_min_radius", "Spacious Min Radius", 32, 4, 120, "Minimum radius of spacious channels.");
    public static final Param SPACIOUS_MAX = i(TUNNELS, "spacious_max_radius", "Spacious Max Radius", 50, 4, 120, "Maximum radius of spacious channels.");
    public static final Param STANDARD_WEIGHT = d(TUNNELS, "standard_weight", "Standard Weight", 5.0, 0.0, 100.0,
            "Relative chance for a standard channel.");
    public static final Param STANDARD_MIN = i(TUNNELS, "standard_min_radius", "Standard Min Radius", 18, 4, 120, "Minimum radius of standard channels.");
    public static final Param STANDARD_MAX = i(TUNNELS, "standard_max_radius", "Standard Max Radius", 30, 4, 120, "Maximum radius of standard channels.");
    public static final Param NARROW_WEIGHT = d(TUNNELS, "narrow_weight", "Narrow Weight", 2.0, 0.0, 100.0,
            "Relative chance for a narrow channel.");
    public static final Param NARROW_MIN = i(TUNNELS, "narrow_min_radius", "Narrow Min Radius", 10, 4, 120, "Minimum radius of narrow channels.");
    public static final Param NARROW_MAX = i(TUNNELS, "narrow_max_radius", "Narrow Max Radius", 15, 4, 120, "Maximum radius of narrow channels.");
    public static final Param CHAMBER_CHANCE = d(TUNNELS, "chamber_chance", "Chamber Chance", 0.6, 0.0, 1.0,
            "Chance for a channel to widen into one or two big caverns along its way.");
    public static final Param CHAMBER_SCALE = d(TUNNELS, "chamber_scale", "Chamber Size", 1.8, 1.0, 3.0,
            "How much wider than the channel those caverns get.");
    public static final Param NARROWS_CHANCE = d(TUNNELS, "narrows_chance", "Constriction Chance", 0.3, 0.0, 1.0,
            "Chance for a channel to have a narrower stretch (never below the guaranteed clearance).");
    public static final Param CANYON_CHANCE = d(TUNNELS, "canyon_section_chance", "Canyon Section Chance", 0.3, 0.0, 1.0,
            "Chance for a channel to have a tall, canyon-like stretch.");
    public static final Param TUNNEL_PILLAR_CHANCE = d(TUNNELS, "pillar_chance", "Channel Pillar Chance", 0.35, 0.0, 1.0,
            "Chance per 100 blocks of a wide channel for a floor-to-ceiling column near the walls (outside the clear corridor).");

    public static final Section STATIONS = section("network.stations", "Station Caverns",
            "Big caverns at every network node, meant for building research stations. Use /barotrauma stations to find them.");
    public static final Param STATION_MIN_RADIUS = i(STATIONS, "min_radius", "Min Radius", 52, 16, 160, "Minimum horizontal radius of a station cavern.");
    public static final Param STATION_MAX_RADIUS = i(STATIONS, "max_radius", "Max Radius", 84, 16, 160, "Maximum horizontal radius of a station cavern.");
    public static final Param STATION_HEIGHT_RATIO = d(STATIONS, "height_ratio", "Height Ratio", 0.55, 0.25, 1.2,
            "Vertical radius divided by horizontal radius.");
    public static final Param STATION_HALL_CHANCE = d(STATIONS, "hall_chance", "Elongated Hall Chance", 0.35, 0.0, 1.0,
            "Chance for a station cavern to be an elongated hall instead of a round dome.");
    public static final Param STATION_COLUMN_CHANCE = d(STATIONS, "column_chance", "Support Column Chance", 0.5, 0.0, 1.0,
            "Chance for a ring of big support columns near the cavern walls.");
    public static final Param STATION_SHAFT_CHANCE = d(STATIONS, "shaft_chance", "Seabed Shaft Chance", 0.55, 0.0, 1.0,
            "Chance for a wide shaft from the cavern up to the open seabed (an entrance into the network).");
    public static final Param SHAFT_MIN_RADIUS = i(STATIONS, "shaft_min_radius", "Shaft Min Radius", 12, 4, 60, "Minimum radius of seabed shafts.");
    public static final Param SHAFT_MAX_RADIUS = i(STATIONS, "shaft_max_radius", "Shaft Max Radius", 20, 4, 60, "Maximum radius of seabed shafts.");

    public static final Section CREW = section("network.crew_tunnels", "Crew Tunnels",
            "Very small, short tunnels branching off channels and stations - only divers fit through. They often end in a small grotto.");
    public static final Param CREW_ENABLED = b(CREW, "enabled", "Enable Crew Tunnels", true, "Generate crew tunnels.");
    public static final Param CREW_PER_TUNNEL = d(CREW, "per_1000_blocks", "Per 1000 Blocks of Channel", 5.0, 0.0, 40.0,
            "Average number of crew tunnels per 1000 blocks of channel.");
    public static final Param CREW_PER_STATION = i(CREW, "per_station", "Per Station", 2, 0, 12, "Crew tunnels branching off every station cavern.");
    public static final Param CREW_MIN_LENGTH = i(CREW, "min_length", "Min Length", 12, 4, 120, "Minimum length in blocks.");
    public static final Param CREW_MAX_LENGTH = i(CREW, "max_length", "Max Length", 40, 4, 120, "Maximum length in blocks.");
    public static final Param CREW_RADIUS = d(CREW, "radius", "Radius", 1.6, 1.0, 3.5, "Radius of crew tunnels (1.6 = about 3 blocks wide).");
    public static final Param GROTTO_CHANCE = d(CREW, "grotto_chance", "Grotto Chance", 0.7, 0.0, 1.0, "Chance that a crew tunnel ends in a small grotto.");
    public static final Param GROTTO_MIN_RADIUS = i(CREW, "grotto_min_radius", "Grotto Min Radius", 3, 2, 16, "Minimum grotto radius.");
    public static final Param GROTTO_MAX_RADIUS = i(CREW, "grotto_max_radius", "Grotto Max Radius", 6, 2, 16, "Maximum grotto radius.");
    public static final Param GROTTO_LOOT_CHANCE = d(CREW, "loot_chance", "Grotto Loot Chance", 0.35, 0.0, 1.0, "Chance for a supply chest inside a grotto.");
    public static final Param GROTTO_LOOT_TABLE = str(CREW, "loot_table", "Grotto Loot Table", "minecraft:chests/shipwreck_supply",
            "Loot table used for grotto chests.");

    // ------------------------------------------------------------------------------------------------
    // Ravines
    // ------------------------------------------------------------------------------------------------
    public static final Section RAVINES = section("ravines", "Ravines",
            "Long, deep cracks in the seabed. They sometimes cut into channels and give extra entrances to the network.");
    public static final Param RAVINES_ENABLED = b(RAVINES, "enabled", "Enable Ravines", true, "Generate ravines.");
    public static final Param RAVINE_SPACING = i(RAVINES, "spacing", "Spacing", 640, 128, 4096, "Size of the grid used to place ravines.");
    public static final Param RAVINE_CHANCE = d(RAVINES, "chance", "Chance", 0.45, 0.0, 1.0, "Chance per grid cell for a ravine.");
    public static final Param RAVINE_MIN_LENGTH = i(RAVINES, "min_length", "Min Length", 260, 32, 2048, "Minimum ravine length.");
    public static final Param RAVINE_MAX_LENGTH = i(RAVINES, "max_length", "Max Length", 760, 32, 2048, "Maximum ravine length.");
    public static final Param RAVINE_MIN_WIDTH = i(RAVINES, "min_width", "Min Width", 22, 6, 200, "Minimum width at the top.");
    public static final Param RAVINE_MAX_WIDTH = i(RAVINES, "max_width", "Max Width", 56, 6, 200, "Maximum width at the top.");
    public static final Param RAVINE_MIN_DEPTH = i(RAVINES, "min_depth", "Min Depth", 70, 8, 480, "Minimum depth below the seabed.");
    public static final Param RAVINE_MAX_DEPTH = i(RAVINES, "max_depth", "Max Depth", 200, 8, 480, "Maximum depth below the seabed.");

    // ------------------------------------------------------------------------------------------------
    // Regions
    // ------------------------------------------------------------------------------------------------
    public static final Section PROVINCES = section("regions", "Seabed Regions",
            "The open seabed is split into regions, each with its own terrain shape. Weights control how common each region is (0 disables it).");
    public static final Param PROVINCE_SIZE = i(PROVINCES, "size", "Region Size", 576, 128, 4096, "Average size of a region in blocks.");
    public static final Param PROVINCE_BLEND = i(PROVINCES, "blend", "Blend Width", 56, 8, 256, "Width of the smooth terrain transition between regions.");
    public static final Param PROVINCE_WARP = i(PROVINCES, "warp", "Border Warp", 110, 0, 512, "How irregular the region borders are.");
    public static final Param W_PLAINS = d(PROVINCES, "weight_abyssal_plains", "Abyssal Plains (flat seabed)", 5.0, 0, 100, "Spacious, very flat planes of sediment.");
    public static final Param W_SPIRES = d(PROVINCES, "weight_spire_fields", "Spire Fields (rocky spikes)", 3.0, 0, 100, "Rocky planes covered in spikes and tall spires.");
    public static final Param W_PILLARS = d(PROVINCES, "weight_pillar_forest", "Pillar Forest (big columns)", 2.0, 0, 100, "Huge rock columns rising from the seabed to the ice.");
    public static final Param W_MONOLITHS = d(PROVINCES, "weight_monolith_reach", "Monolith Reach (rock formations)", 3.0, 0, 100, "Boulders, arches, mesas and leaning slabs.");
    public static final Param W_KELP = d(PROVINCES, "weight_kelp_forest", "Kelp Forest (vegetation)", 3.0, 0, 100, "Dense forests of giant kelp and seagrass.");
    public static final Param W_GARDENS = d(PROVINCES, "weight_coral_gardens", "Coral Gardens (vegetation)", 2.0, 0, 100, "Raised plateaus overgrown with coral, seagrass and sea pickles.");
    public static final Param W_RIDGE = d(PROVINCES, "weight_europan_ridge", "Europan Ridge (mountains)", 3.0, 0, 100, "Sharp underwater mountain ridges.");
    public static final Param W_VENTS = d(PROVINCES, "weight_hydrothermal_wastes", "Hydrothermal Wastes (vents)", 2.0, 0, 100, "Basalt fields with magma and hot vents pushing bubble columns.");
    public static final Param W_TRENCH = d(PROVINCES, "weight_hadal_trench", "Hadal Trench (deep basins)", 2.0, 0, 100, "Very deep basins with cliffs.");

    // ------------------------------------------------------------------------------------------------
    // Formations
    // ------------------------------------------------------------------------------------------------
    public static final Section FORMATIONS = section("formations", "Formations",
            "Big columns, spikes and rock formations on the open seabed.");
    public static final Param PILLAR_SPACING = i(FORMATIONS, "pillar_spacing", "Column Spacing", 64, 24, 512, "Grid size used to place big columns.");
    public static final Param PILLAR_CHANCE_FOREST = d(FORMATIONS, "pillar_chance_forest", "Column Chance (Pillar Forest)", 0.7, 0.0, 1.0, "Chance per grid cell inside Pillar Forests.");
    public static final Param PILLAR_CHANCE_ELSEWHERE = d(FORMATIONS, "pillar_chance_elsewhere", "Column Chance (elsewhere)", 0.02, 0.0, 1.0, "Chance per grid cell in all other regions.");
    public static final Param PILLAR_MIN_RADIUS = i(FORMATIONS, "pillar_min_radius", "Column Min Radius", 5, 2, 64, "Minimum column radius.");
    public static final Param PILLAR_MAX_RADIUS = i(FORMATIONS, "pillar_max_radius", "Column Max Radius", 16, 2, 64, "Maximum column radius.");
    public static final Param PILLAR_MAX_HEIGHT = i(FORMATIONS, "pillar_max_height", "Column Max Height (no ice)", 200, 16, 800, "Maximum column height when there is no ice crust to reach.");
    public static final Param SPIKE_DENSITY = d(FORMATIONS, "spike_density", "Spike Density", 0.7, 0.0, 1.0, "How densely Spire Fields are covered in spikes.");
    public static final Param SPIKE_MAX_HEIGHT = i(FORMATIONS, "spike_max_height", "Spike Max Height", 52, 6, 200, "Maximum height of normal spikes.");
    public static final Param SPIRE_CHANCE = d(FORMATIONS, "spire_chance", "Tall Spire Chance", 0.5, 0.0, 1.0, "Chance per large grid cell for a tall spire in Spire Fields.");
    public static final Param SPIRE_MAX_HEIGHT = i(FORMATIONS, "spire_max_height", "Tall Spire Max Height", 110, 20, 400, "Maximum height of tall spires.");
    public static final Param ROCK_SPACING = i(FORMATIONS, "rock_spacing", "Rock Formation Spacing", 88, 24, 512, "Grid size used to place rock formations.");
    public static final Param ROCK_CHANCE_REACH = d(FORMATIONS, "rock_chance_reach", "Formation Chance (Monolith Reach)", 0.7, 0.0, 1.0, "Chance per grid cell inside Monolith Reach.");
    public static final Param ROCK_CHANCE_ELSEWHERE = d(FORMATIONS, "rock_chance_elsewhere", "Formation Chance (elsewhere)", 0.05, 0.0, 1.0, "Chance per grid cell in all other regions.");
    public static final Param ROCK_SCALE = d(FORMATIONS, "rock_scale", "Formation Size", 1.0, 0.3, 3.0, "Size multiplier of boulders, arches, mesas and slabs.");
    public static final Param OVERHANGS = d(FORMATIONS, "overhangs", "Overhang Strength", 1.0, 0.0, 4.0, "Strength of the 3D noise that creates overhangs and ledges on rough seabed.");

    // ------------------------------------------------------------------------------------------------
    // Vegetation & decoration
    // ------------------------------------------------------------------------------------------------
    public static final Section VEGETATION = section("vegetation", "Vegetation & Decoration",
            "Density multipliers for decoration features (1 = default, 0 = off).");
    public static final Param KELP_DENSITY = d(VEGETATION, "kelp_density", "Giant Kelp Density", 1.0, 0.0, 5.0, "Giant kelp clusters in kelp forests.");
    public static final Param KELP_MAX_HEIGHT = i(VEGETATION, "kelp_max_height", "Giant Kelp Max Height", 48, 4, 200, "Maximum height of giant kelp.");
    public static final Param MEADOW_DENSITY = d(VEGETATION, "meadow_density", "Seagrass Meadow Density", 1.0, 0.0, 5.0, "Big patches of seagrass and sea pickles.");
    public static final Param CORAL_DENSITY = d(VEGETATION, "coral_density", "Coral Density", 1.0, 0.0, 5.0, "Coral in the coral gardens.");
    public static final Param LICHEN_DENSITY = d(VEGETATION, "glow_lichen_density", "Glow Lichen Density", 1.0, 0.0, 5.0, "Glowing lichen on channel and cavern walls.");
    public static final Param TUNNEL_VEGETATION = d(VEGETATION, "channel_vegetation", "Channel Floor Vegetation", 1.0, 0.0, 5.0, "Seagrass and sea pickle patches on channel floors.");
    public static final Param VENT_DENSITY = d(VEGETATION, "vent_density", "Hydrothermal Vent Density", 1.0, 0.0, 5.0, "Vent chimneys with bubble columns.");
    public static final Param STALACTITE_DENSITY = d(VEGETATION, "ice_stalactite_density", "Ice Stalactite Density", 1.0, 0.0, 5.0, "Icicles hanging from the underside of the ice.");
    public static final Param SMALL_SPIKE_DENSITY = d(VEGETATION, "small_spike_density", "Small Spike Density", 1.0, 0.0, 5.0, "Small rock needles in Spire Fields.");
    public static final Param ORE_DENSITY = d(VEGETATION, "ore_density", "Ore Density", 1.0, 0.0, 5.0, "Multiplier for ores in the deep crust.");

    // ------------------------------------------------------------------------------------------------
    // Materials
    // ------------------------------------------------------------------------------------------------
    public static final Section MATERIALS = section("materials", "Materials",
            "Rock layering, sediment and ice materials.");
    public static final Param ROCK_STRATA = b(MATERIALS, "rock_strata", "Rock Strata", true, "Layer tuff, andesite, calcite and other rocks in wavy bands through the crust.");
    public static final Param STRATA_THICKNESS = i(MATERIALS, "strata_thickness", "Strata Thickness", 9, 2, 64, "Average thickness of a rock band.");
    public static final Param SEDIMENT_DEPTH = i(MATERIALS, "sediment_depth", "Sediment Depth", 3, 0, 8, "Thickness of the sand/gravel/mud layer on the seabed and channel floors.");
    public static final Param BLUE_ICE_VEINS = b(MATERIALS, "blue_ice_veins", "Blue Ice Veins", true, "Veins of blue ice inside the crust.");

    public static final Section MISC = section("misc", "Miscellaneous", "Other world generation switches.");
    public static final Param VANILLA_CARVERS = b(MISC, "vanilla_carvers", "Vanilla Cave Carvers", false,
            "Allow vanilla (and modded) cave/canyon carvers listed in biomes. They make small, maze-like caves, so they are off by default.");

    // ------------------------------------------------------------------------------------------------
    // Biomes
    // ------------------------------------------------------------------------------------------------
    public static final Section BIOMES = section("biomes", "Biome Slots",
            "Which biomes fill each part of the world. Format: \"namespace:biome=weight\". Biomes from mods that are not installed are skipped automatically, so modded entries are safe to keep. You can add any biome from any mod here.");
    public static final Param B_ICE = list(BIOMES, "ice_shelf", "Ice Shelf", "The ice crust and the water right beneath it.",
            "barotrauma_oceans:ice_shelf=10");
    public static final Param B_GREAT_SEA = list(BIOMES, "great_sea", "Open Water", "Open water between the seabed and the ice.",
            "barotrauma_oceans:the_great_sea=10");
    public static final Param B_PLAINS = list(BIOMES, "abyssal_plains", "Abyssal Plains", "Biomes of the Abyssal Plains region.",
            "barotrauma_oceans:abyssal_plains=10");
    public static final Param B_SPIRES = list(BIOMES, "spire_fields", "Spire Fields", "Biomes of the Spire Fields region.",
            "barotrauma_oceans:spire_fields=10");
    public static final Param B_PILLARS = list(BIOMES, "pillar_forest", "Pillar Forest", "Biomes of the Pillar Forest region.",
            "barotrauma_oceans:pillar_forest=10");
    public static final Param B_MONOLITHS = list(BIOMES, "monolith_reach", "Monolith Reach", "Biomes of the Monolith Reach region.",
            "barotrauma_oceans:monolith_reach=10");
    public static final Param B_KELP = list(BIOMES, "kelp_forest", "Kelp Forest", "Biomes of the Kelp Forest region.",
            "barotrauma_oceans:kelp_forest=10", "hybrid_aquatic:seagrass_bed=3");
    public static final Param B_GARDENS = list(BIOMES, "coral_gardens", "Coral Gardens", "Biomes of the Coral Gardens region.",
            "barotrauma_oceans:coral_gardens=10", "hybrid_aquatic:coral_reef=4", "hybrid_aquatic:deep_coral_reef=3", "hybrid_aquatic:red_meadow=3");
    public static final Param B_RIDGE = list(BIOMES, "europan_ridge", "Europan Ridge", "Biomes of the Europan Ridge region.",
            "barotrauma_oceans:europan_ridge=10");
    public static final Param B_VENTS = list(BIOMES, "hydrothermal_wastes", "Hydrothermal Wastes", "Biomes of the Hydrothermal Wastes region.",
            "barotrauma_oceans:hydrothermal_wastes=10", "hybrid_aquatic:sulfuric_caves=2");
    public static final Param B_TRENCH = list(BIOMES, "hadal_trench", "Hadal Trench", "Biomes of the Hadal Trench region.",
            "barotrauma_oceans:hadal_trench=8", "alexscaves:abyssal_chasm=6", "hybrid_aquatic:trench=3", "hybrid_aquatic:cold_trench=2");
    public static final Param B_RAVINE = list(BIOMES, "ravine", "Ravines", "Biomes inside ravines.",
            "barotrauma_oceans:abyssal_ravine=10");
    public static final Param B_SPACIOUS = list(BIOMES, "spacious_channels", "Spacious Channels", "Biomes inside spacious channels.",
            "barotrauma_oceans:great_channels=8", "barotrauma_oceans:luminous_caverns=3");
    public static final Param B_STANDARD = list(BIOMES, "standard_channels", "Standard Channels", "Biomes inside standard channels.",
            "barotrauma_oceans:sunken_channels=8", "barotrauma_oceans:luminous_caverns=2", "barotrauma_oceans:cold_caverns=3");
    public static final Param B_NARROW = list(BIOMES, "narrow_channels", "Narrow Channels", "Biomes inside narrow channels.",
            "barotrauma_oceans:narrow_passages=8", "barotrauma_oceans:cold_caverns=2");
    public static final Param B_STATION = list(BIOMES, "station_hollow", "Station Caverns", "Biomes inside station caverns and their shafts.",
            "barotrauma_oceans:station_hollow=10");
    public static final Param B_CRUST = list(BIOMES, "deep_crust", "Deep Crust", "Biomes of solid rock between the caves.",
            "barotrauma_oceans:deep_crust=10");
    public static final Param B_POCKETS = list(BIOMES, "rock_pockets", "Rock Pockets", "Rare biome pockets deep inside the crust, away from the channels. Ideal for modded cave biomes (Alex's Caves generates its caves wherever its biomes appear). Empty = disabled.",
            "alexscaves:magnetic_caves=3", "alexscaves:primordial_caves=3", "alexscaves:toxic_caves=2", "alexscaves:forlorn_hollows=2", "alexscaves:candy_cavity=1");
    public static final Param POCKET_SIZE = i(BIOMES, "rock_pocket_size", "Rock Pocket Size", 320, 64, 2048, "Grid size used to place rock pockets.");
    public static final Param POCKET_CHANCE = d(BIOMES, "rock_pocket_chance", "Rock Pocket Chance", 0.2, 0.0, 1.0, "Chance per grid cell for a rock pocket.");

    // ------------------------------------------------------------------------------------------------

    private final Map<String, String> values;

    private OceanSettings(Map<String, String> values) {
        this.values = values;
    }

    public static OceanSettings defaults() {
        return fromMap(Collections.emptyMap());
    }

    /** Builds settings from a (possibly partial) map, filling gaps with defaults and clamping to ranges. */
    public static OceanSettings fromMap(Map<String, String> raw) {
        Map<String, String> out = new LinkedHashMap<>();
        for (Param p : PARAMS) {
            String v = raw.get(p.key);
            out.put(p.key, normalise(p, v));
        }
        OceanSettings s = new OceanSettings(out);
        s.repair();
        return s;
    }

    private static String normalise(Param p, String v) {
        if (v == null) {
            return p.defaultValue;
        }
        try {
            switch (p.kind) {
                case INT -> {
                    long x = Math.round(Double.parseDouble(v.trim()));
                    x = Math.max((long) p.min, Math.min((long) p.max, x));
                    return Long.toString(x);
                }
                case DOUBLE -> {
                    double x = Double.parseDouble(v.trim());
                    if (Double.isNaN(x)) {
                        return p.defaultValue;
                    }
                    x = Math.max(p.min, Math.min(p.max, x));
                    return Double.toString(x);
                }
                case BOOL -> {
                    return Boolean.toString(Boolean.parseBoolean(v.trim()));
                }
                case LIST -> {
                    return String.join(",", splitList(v));
                }
                default -> {
                    return v.trim();
                }
            }
        } catch (NumberFormatException e) {
            return p.defaultValue;
        }
    }

    /** Fixes combinations of values that would otherwise break generation (e.g. min > max). */
    private void repair() {
        orderPair(SPACIOUS_MIN, SPACIOUS_MAX);
        orderPair(STANDARD_MIN, STANDARD_MAX);
        orderPair(NARROW_MIN, NARROW_MAX);
        orderPair(STATION_MIN_RADIUS, STATION_MAX_RADIUS);
        orderPair(SHAFT_MIN_RADIUS, SHAFT_MAX_RADIUS);
        orderPair(CREW_MIN_LENGTH, CREW_MAX_LENGTH);
        orderPair(GROTTO_MIN_RADIUS, GROTTO_MAX_RADIUS);
        orderPair(RAVINE_MIN_LENGTH, RAVINE_MAX_LENGTH);
        orderPair(RAVINE_MIN_WIDTH, RAVINE_MAX_WIDTH);
        orderPair(RAVINE_MIN_DEPTH, RAVINE_MAX_DEPTH);
        orderPair(PILLAR_MIN_RADIUS, PILLAR_MAX_RADIUS);
        orderPair(STATION_MIN_Y, STATION_MAX_Y);
        if (getBool(ICE_ENABLED)) {
            int bottom = getInt(ICE_BOTTOM);
            int bottomVar = getInt(ICE_BOTTOM_VARIATION);
            int top = getInt(ICE_TOP);
            int topVar = getInt(ICE_TOP_VARIATION);
            if (top - topVar < bottom + bottomVar + 12) {
                top = bottom + bottomVar + topVar + 12;
                top = Math.min(top, 316 - topVar);
                values.put(ICE_TOP.key, Integer.toString(top));
            }
            int water = getInt(WATER_LEVEL);
            int lo = bottom + bottomVar + 2;
            int hi = top - topVar - 2;
            if (water < lo || water > hi) {
                values.put(WATER_LEVEL.key, Integer.toString((lo + hi) / 2));
            }
        }
    }

    private void orderPair(Param lo, Param hi) {
        if (lo.kind == Kind.INT) {
            int a = getInt(lo);
            int b = getInt(hi);
            if (a > b) {
                values.put(lo.key, Integer.toString(b));
                values.put(hi.key, Integer.toString(a));
            }
        }
    }

    public Map<String, String> toMap() {
        return Collections.unmodifiableMap(values);
    }

    public String getRaw(Param p) {
        return values.get(p.key);
    }

    public int getInt(Param p) {
        return Integer.parseInt(values.get(p.key));
    }

    public double getDouble(Param p) {
        return Double.parseDouble(values.get(p.key));
    }

    public boolean getBool(Param p) {
        return Boolean.parseBoolean(values.get(p.key));
    }

    public String getString(Param p) {
        return values.get(p.key);
    }

    public List<String> getList(Param p) {
        return splitList(values.get(p.key));
    }

    /** Looks up a numeric value by key (used by data-driven placement modifiers). Returns fallback if unknown. */
    public double getNumber(String key, double fallback) {
        String v = values.get(key);
        if (v == null) {
            return fallback;
        }
        try {
            return Double.parseDouble(v);
        } catch (NumberFormatException e) {
            return fallback;
        }
    }

    public static Param byKey(String key) {
        for (Param p : PARAMS) {
            if (p.key.equals(key)) {
                return p;
            }
        }
        return null;
    }

    public static List<String> splitList(String s) {
        if (s == null || s.isBlank()) {
            return List.of();
        }
        List<String> out = new ArrayList<>();
        for (String part : s.split(",")) {
            String t = part.trim();
            if (!t.isEmpty()) {
                out.add(t);
            }
        }
        return out;
    }

    @Override
    public boolean equals(Object o) {
        return o instanceof OceanSettings other && other.values.equals(values);
    }

    @Override
    public int hashCode() {
        return values.hashCode();
    }

    @Override
    public String toString() {
        return "OceanSettings" + Arrays.toString(values.entrySet().toArray());
    }
}
