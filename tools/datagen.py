#!/usr/bin/env python3
"""
Generates the data-pack and language files of Barotrauma Oceans.

Run from the repository root:  python3 tools/datagen.py
The generated JSON files are committed, so this is only needed after changing this script
(or OceanSettings.java, whose option names feed the config screen translations).

Feature order matters: Minecraft requires every placed feature to appear in the same relative order
in all biomes. All biomes therefore pick their features from the ordered STEP lists below.
"""
import json
import os
import re
import shutil

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
RES = os.path.join(ROOT, "src", "main", "resources")
DATA = os.path.join(RES, "data")
MOD = "barotrauma_oceans"


def write(path, obj):
    full = os.path.join(DATA, path)
    os.makedirs(os.path.dirname(full), exist_ok=True)
    with open(full, "w", encoding="utf-8") as f:
        json.dump(obj, f, indent=2)
        f.write("\n")


def m(name):
    return f"{MOD}:{name}"


def state(name, **props):
    s = {"Name": name if ":" in name else "minecraft:" + name}
    if props:
        s["Properties"] = {k: str(v).lower() if isinstance(v, bool) else str(v) for k, v in props.items()}
    return s


def simple(name, **props):
    return {"type": "minecraft:simple_state_provider", "state": state(name, **props)}


def uniform(lo, hi):
    return {"type": "minecraft:uniform", "min_inclusive": lo, "max_inclusive": hi}


def absolute_range(lo, hi):
    return {"type": "minecraft:height_range",
            "height": {"type": "minecraft:uniform", "min_inclusive": {"absolute": lo}, "max_inclusive": {"absolute": hi}}}


def trapezoid_range(lo, hi):
    return {"type": "minecraft:height_range",
            "height": {"type": "minecraft:trapezoid", "min_inclusive": {"absolute": lo}, "max_inclusive": {"absolute": hi}}}


def scaled(count, setting):
    return {"type": m("scaled_count"), "count": count, "setting": setting}


IN_SQUARE = {"type": "minecraft:in_square"}
BIOME = {"type": "minecraft:biome"}
SEAFLOOR = {"type": "minecraft:heightmap", "heightmap": "OCEAN_FLOOR_WG"}


def cavity(surface, **kw):
    d = {"type": m("cavity"), "surface": surface}
    d.update(kw)
    return d


# ---------------------------------------------------------------------------------------------------
# Clean previously generated folders
# ---------------------------------------------------------------------------------------------------
for sub in ["worldgen/biome", "worldgen/configured_feature", "worldgen/placed_feature", "tags", "neoforge"]:
    p = os.path.join(DATA, MOD, sub)
    if os.path.isdir(p):
        shutil.rmtree(p)
for ns in ["minecraft", "c", "hybrid_aquatic"]:
    p = os.path.join(DATA, ns)
    if os.path.isdir(p):
        shutil.rmtree(p)

# ---------------------------------------------------------------------------------------------------
# Dimension type, noise, noise settings, world preset
# ---------------------------------------------------------------------------------------------------
MIN_Y = -512
HEIGHT = 832
WATER = 270

write(f"{MOD}/dimension_type/abyssal_overworld.json", {
    "ultrawarm": False,
    "natural": True,
    "coordinate_scale": 1.0,
    "has_skylight": True,
    "has_ceiling": False,
    "ambient_light": 0.0,
    "monster_spawn_light_level": {"type": "minecraft:uniform", "min_inclusive": 0, "max_inclusive": 7},
    "monster_spawn_block_light_limit": 0,
    "piglin_safe": False,
    "bed_works": True,
    "respawn_anchor_works": False,
    "has_raids": True,
    "logical_height": HEIGHT,
    "min_y": MIN_Y,
    "height": HEIGHT,
    "infiniburn": "#minecraft:infiniburn_overworld",
    "effects": "minecraft:overworld"
})

write(f"{MOD}/worldgen/noise/seed_probe.json", {"firstOctave": -3, "amplitudes": [1.0, 1.0]})

write(f"{MOD}/worldgen/noise_settings/ocean.json", {
    "sea_level": WATER,
    "disable_mob_generation": False,
    "aquifers_enabled": False,
    "ore_veins_enabled": False,
    "legacy_random_source": False,
    "default_block": state("stone"),
    "default_fluid": state("water", level=0),
    "noise": {"min_y": MIN_Y, "height": HEIGHT, "size_horizontal": 1, "size_vertical": 1},
    "noise_router": {
        "barrier": 0.0,
        "fluid_level_floodedness": 0.0,
        "fluid_level_spread": 0.0,
        "lava": 0.0,
        "temperature": {"type": m("seed_carrier"), "noise": m("seed_probe")},
        "vegetation": 0.0,
        "continents": 0.0,
        "erosion": 0.0,
        "depth": 0.0,
        "ridges": 0.0,
        "initial_density_without_jaggedness": 0.0,
        # Unused (the generator fills terrain itself), but given a wide value range so that mods which wrap
        # the overworld final density (e.g. Hybrid Aquatic trenches) do not log warnings for every chunk.
        "final_density": {"type": "minecraft:y_clamped_gradient", "from_y": MIN_Y, "to_y": MIN_Y + HEIGHT,
                          "from_value": 1.0, "to_value": -1.0},
        "vein_toggle": 0.0,
        "vein_ridged": 0.0,
        "vein_gap": 0.0
    },
    "spawn_target": [{
        "temperature": [-1.0, 1.0], "humidity": [-1.0, 1.0], "continentalness": [-1.0, 1.0],
        "erosion": [-1.0, 1.0], "weirdness": [-1.0, 1.0], "depth": 0.0, "offset": 0.0
    }],
    # Bedrock, deepslate, strata and ice are chosen by the chunk generator while filling the terrain
    # (worldgen/Materials.java); sediment is placed by SurfacePainter.
    "surface_rule": {"type": "minecraft:sequence", "sequence": []}
})

write(f"{MOD}/worldgen/world_preset/barotrauma_oceans.json", {
    "dimensions": {
        "minecraft:overworld": {
            "type": m("abyssal_overworld"),
            "generator": {"type": m("ocean"), "biome_source": {"type": m("ocean")}, "settings": m("ocean")}
        },
        "minecraft:the_nether": {
            "type": "minecraft:the_nether",
            "generator": {"type": "minecraft:noise",
                          "biome_source": {"type": "minecraft:multi_noise", "preset": "minecraft:nether"},
                          "settings": "minecraft:nether"}
        },
        "minecraft:the_end": {
            "type": "minecraft:the_end",
            "generator": {"type": "minecraft:noise", "biome_source": {"type": "minecraft:the_end"},
                          "settings": "minecraft:end"}
        }
    }
})
write("minecraft/tags/worldgen/world_preset/normal.json", {"replace": False, "values": [m("barotrauma_oceans")]})

# ---------------------------------------------------------------------------------------------------
# Configured features
# ---------------------------------------------------------------------------------------------------
LICHEN_SURFACES = ["minecraft:stone", "minecraft:deepslate", "minecraft:tuff", "minecraft:andesite", "minecraft:diorite",
                   "minecraft:granite", "minecraft:calcite", "minecraft:dripstone_block", "minecraft:smooth_basalt",
                   "minecraft:blackstone", "minecraft:basalt", "minecraft:packed_ice", "minecraft:clay", "minecraft:mud"]


def configured(name, type_, config):
    write(f"{MOD}/worldgen/configured_feature/{name}.json", {"type": type_, "config": config})


configured("giant_kelp", m("giant_kelp"), {"stalks": uniform(6, 14), "spread": 7, "height": uniform(14, 64)})
configured("kelp_small", m("giant_kelp"), {"stalks": uniform(1, 3), "spread": 3, "height": uniform(4, 16)})
configured("seagrass_meadow", m("seabed_meadow"), {"style": "seagrass", "radius": uniform(5, 11), "density": 0.75})
configured("seagrass_patch", m("seabed_meadow"), {"style": "seagrass", "radius": uniform(2, 4), "density": 0.55})
configured("coral_garden", m("seabed_meadow"), {"style": "coral", "radius": uniform(4, 9), "density": 0.7})
configured("glow_meadow", m("seabed_meadow"), {"style": "glow", "radius": uniform(3, 7), "density": 0.45})
configured("cave_floor_meadow", m("seabed_meadow"), {"style": "seagrass", "radius": uniform(3, 6), "density": 0.6})
configured("cave_floor_glow", m("seabed_meadow"), {"style": "glow", "radius": uniform(3, 6), "density": 0.6})
configured("hydrothermal_vent", m("hydrothermal_vent"), {"height": uniform(4, 14), "upwelling_chance": 0.6, "max_bubble_column": 220})
configured("rock_needle", m("rock_spike"), {
    "material": {"type": "minecraft:weighted_state_provider", "entries": [
        {"data": state("stone"), "weight": 3}, {"data": state("tuff"), "weight": 2},
        {"data": state("andesite"), "weight": 2}, {"data": state("cobblestone"), "weight": 1}]},
    "height": uniform(3, 14), "max_radius": 2.5, "hanging": False})
configured("ice_stalactite", m("rock_spike"), {
    "material": {"type": "minecraft:weighted_state_provider", "entries": [
        {"data": state("packed_ice"), "weight": 4}, {"data": state("blue_ice"), "weight": 1}]},
    "height": uniform(3, 20), "max_radius": 3.0, "hanging": True})
configured("ice_needle", m("rock_spike"), {
    "material": {"type": "minecraft:weighted_state_provider", "entries": [
        {"data": state("packed_ice"), "weight": 3}, {"data": state("blue_ice"), "weight": 1}]},
    "height": uniform(2, 9), "max_radius": 1.8, "hanging": False})
configured("icicle", m("rock_spike"), {
    "material": {"type": "minecraft:weighted_state_provider", "entries": [
        {"data": state("packed_ice"), "weight": 3}, {"data": state("blue_ice"), "weight": 1}]},
    "height": uniform(2, 10), "max_radius": 1.8, "hanging": True})
configured("glow_lichen", "minecraft:multiface_growth", {
    "block": "minecraft:glow_lichen", "search_range": 20, "chance_of_spreading": 0.5,
    "can_place_on_floor": False, "can_place_on_ceiling": True, "can_place_on_wall": True,
    "can_be_placed_on": LICHEN_SURFACES})
configured("flooded_geode", "minecraft:geode", {
    "blocks": {
        "filling_provider": simple("water", level=0),
        "inner_layer_provider": simple("amethyst_block"),
        "alternate_inner_layer_provider": simple("budding_amethyst"),
        "middle_layer_provider": simple("calcite"),
        "outer_layer_provider": simple("smooth_basalt"),
        "inner_placements": [state("small_amethyst_bud", facing="up", waterlogged=False),
                             state("medium_amethyst_bud", facing="up", waterlogged=False),
                             state("large_amethyst_bud", facing="up", waterlogged=False),
                             state("amethyst_cluster", facing="up", waterlogged=False)],
        "cannot_replace": "#minecraft:features_cannot_replace",
        "invalid_blocks": "#minecraft:geode_invalid_blocks"
    },
    "layers": {"filling": 1.7, "inner_layer": 2.2, "middle_layer": 3.2, "outer_layer": 4.2},
    "crack": {"generate_crack_chance": 0.95, "base_crack_size": 2.0, "crack_point_offset": 2},
    "use_potential_placements_chance": 0.35,
    "use_alternate_layer0_chance": 0.083,
    "placements_require_layer0_alternate": True,
    "outer_wall_distance": uniform(4, 6),
    "distribution_points": uniform(3, 4),
    "point_offset": uniform(1, 2),
    "min_gen_offset": -16,
    "max_gen_offset": 16,
    "noise_multiplier": 0.05,
    "invalid_blocks_threshold": 1
})


def ore(name, stone_ore, deep_ore, size, discard=0.0):
    targets = []
    if stone_ore:
        targets.append({"target": {"predicate_type": "minecraft:tag_match", "tag": "minecraft:stone_ore_replaceables"},
                        "state": state(stone_ore)})
    if deep_ore:
        targets.append({"target": {"predicate_type": "minecraft:tag_match", "tag": "minecraft:deepslate_ore_replaceables"},
                        "state": state(deep_ore)})
    configured(name, "minecraft:ore", {"targets": targets, "size": size, "discard_chance_on_air_exposure": discard})


def blob(name, block, size):
    configured(name, "minecraft:ore", {
        "targets": [{"target": {"predicate_type": "minecraft:tag_match", "tag": "minecraft:base_stone_overworld"},
                     "state": state(block)}],
        "size": size, "discard_chance_on_air_exposure": 0.0})


blob("ore_gravel", "gravel", 33)
blob("ore_clay", "clay", 33)
blob("ore_tuff", "tuff", 48)
ore("ore_coal", "coal_ore", "deepslate_coal_ore", 17)
ore("ore_iron", "iron_ore", "deepslate_iron_ore", 9)
ore("ore_copper", "copper_ore", "deepslate_copper_ore", 14)
ore("ore_gold", "gold_ore", "deepslate_gold_ore", 9)
ore("ore_redstone", "redstone_ore", "deepslate_redstone_ore", 8)
ore("ore_lapis", "lapis_ore", "deepslate_lapis_ore", 7)
ore("ore_diamond", "diamond_ore", "deepslate_diamond_ore", 8, 0.3)
ore("ore_emerald", "emerald_ore", "deepslate_emerald_ore", 3)
blob("ore_magma", "magma_block", 24)

# ---------------------------------------------------------------------------------------------------
# Placed features, in global order per decoration step
# ---------------------------------------------------------------------------------------------------
STEPS = ["raw_generation", "lakes", "local_modifications", "underground_structures", "surface_structures",
         "strongholds", "underground_ores", "underground_decoration", "fluid_springs", "vegetal_decoration",
         "top_layer_modification"]

ORE_DENSITY = "vegetation.ore_density"
PLACED = {}  # name -> step index (ordered dict keeps global order)


def placed(name, step, feature, placement):
    write(f"{MOD}/worldgen/placed_feature/{name}.json", {"feature": feature, "placement": placement})
    PLACED[name] = STEPS.index(step)


placed("flooded_geode", "local_modifications", m("flooded_geode"),
       [{"type": "minecraft:rarity_filter", "chance": 6}, IN_SQUARE, absolute_range(-490, -60), BIOME])
placed("hydrothermal_vent", "surface_structures", m("hydrothermal_vent"),
       [scaled(3, "vegetation.vent_density"), IN_SQUARE, SEAFLOOR, BIOME])
placed("rock_needles", "surface_structures", m("rock_needle"),
       [scaled(10, "vegetation.small_spike_density"), IN_SQUARE, SEAFLOOR, BIOME])
placed("ore_gravel", "underground_ores", m("ore_gravel"), [scaled(10, ORE_DENSITY), IN_SQUARE, absolute_range(-500, 240), BIOME])
placed("ore_clay", "underground_ores", m("ore_clay"), [scaled(4, ORE_DENSITY), IN_SQUARE, absolute_range(-500, 240), BIOME])
placed("ore_tuff", "underground_ores", m("ore_tuff"), [scaled(4, ORE_DENSITY), IN_SQUARE, absolute_range(-500, 0), BIOME])
placed("ore_coal", "underground_ores", m("ore_coal"), [scaled(24, ORE_DENSITY), IN_SQUARE, absolute_range(-300, 240), BIOME])
placed("ore_iron", "underground_ores", m("ore_iron"), [scaled(22, ORE_DENSITY), IN_SQUARE, trapezoid_range(-500, 120), BIOME])
placed("ore_copper", "underground_ores", m("ore_copper"), [scaled(14, ORE_DENSITY), IN_SQUARE, trapezoid_range(-260, 160), BIOME])
placed("ore_gold", "underground_ores", m("ore_gold"), [scaled(8, ORE_DENSITY), IN_SQUARE, absolute_range(-500, -80), BIOME])
placed("ore_redstone", "underground_ores", m("ore_redstone"), [scaled(10, ORE_DENSITY), IN_SQUARE, absolute_range(-500, -150), BIOME])
placed("ore_lapis", "underground_ores", m("ore_lapis"), [scaled(5, ORE_DENSITY), IN_SQUARE, trapezoid_range(-500, -40), BIOME])
placed("ore_diamond", "underground_ores", m("ore_diamond"), [scaled(8, ORE_DENSITY), IN_SQUARE, absolute_range(-500, -280), BIOME])
placed("ore_emerald", "underground_ores", m("ore_emerald"), [scaled(4, ORE_DENSITY), IN_SQUARE, absolute_range(-200, 160), BIOME])
placed("ore_magma", "underground_ores", m("ore_magma"), [scaled(8, ORE_DENSITY), IN_SQUARE, absolute_range(-300, 120), BIOME])
placed("cave_glow_lichen", "underground_decoration", m("glow_lichen"),
       [scaled(40, "vegetation.glow_lichen_density"), IN_SQUARE, cavity("inside"), BIOME])
placed("cave_glow_lichen_dense", "underground_decoration", m("glow_lichen"),
       [scaled(140, "vegetation.glow_lichen_density"), IN_SQUARE, cavity("inside"), BIOME])
placed("cave_floor_meadow", "underground_decoration", m("cave_floor_meadow"),
       [scaled(3, "vegetation.channel_vegetation"), IN_SQUARE, cavity("floor"), BIOME])
placed("cave_floor_glow", "underground_decoration", m("cave_floor_glow"),
       [scaled(4, "vegetation.channel_vegetation"), IN_SQUARE, cavity("floor"), BIOME])
placed("cave_ice_needles", "underground_decoration", m("ice_needle"),
       [scaled(8, "vegetation.ice_stalactite_density"), IN_SQUARE, cavity("floor"), BIOME])
placed("cave_icicles", "underground_decoration", m("icicle"),
       [scaled(10, "vegetation.ice_stalactite_density"), IN_SQUARE, cavity("ceiling"), BIOME])
placed("giant_kelp_forest", "vegetal_decoration", m("giant_kelp"),
       [scaled(3, "vegetation.kelp_density"), IN_SQUARE, SEAFLOOR, BIOME])
placed("kelp_sparse", "vegetal_decoration", m("kelp_small"),
       [scaled(2, "vegetation.kelp_density"), IN_SQUARE, SEAFLOOR, BIOME])
placed("seagrass_meadow", "vegetal_decoration", m("seagrass_meadow"),
       [scaled(2, "vegetation.meadow_density"), IN_SQUARE, SEAFLOOR, BIOME])
placed("seagrass_sparse", "vegetal_decoration", m("seagrass_patch"),
       [scaled(2, "vegetation.meadow_density"), IN_SQUARE, SEAFLOOR, BIOME])
placed("coral_garden", "vegetal_decoration", m("coral_garden"),
       [scaled(2, "vegetation.coral_density"), IN_SQUARE, SEAFLOOR, BIOME])
placed("coral_patch", "vegetal_decoration", "minecraft:warm_ocean_vegetation",
       [scaled(5, "vegetation.coral_density"), IN_SQUARE, SEAFLOOR, BIOME])
placed("glow_meadow", "vegetal_decoration", m("glow_meadow"),
       [scaled(1, "vegetation.meadow_density"), IN_SQUARE, SEAFLOOR, BIOME])
placed("pillar_lichen", "vegetal_decoration", m("glow_lichen"),
       [scaled(48, "vegetation.glow_lichen_density"), IN_SQUARE, absolute_range(-240, 236), BIOME])
placed("ice_stalactites", "top_layer_modification", m("ice_stalactite"),
       [scaled(10, "vegetation.ice_stalactite_density"), IN_SQUARE, {"type": m("ice_underside")}, BIOME])
# vanilla feature, only ever used last in its step (as in vanilla biomes)
VANILLA_FREEZE = "minecraft:freeze_top_layer"

ORES = ["ore_gravel", "ore_clay", "ore_tuff", "ore_coal", "ore_iron", "ore_copper", "ore_gold", "ore_redstone",
        "ore_lapis", "ore_diamond", "ore_emerald"]


def feature_lists(names, extra_last=None):
    steps = [[] for _ in STEPS]
    for name in PLACED:  # keeps the global order
        if name in names:
            steps[PLACED[name]].append(m(name))
    if extra_last:
        for step, feat in extra_last:
            steps[STEPS.index(step)].append(feat)
    return steps


# ---------------------------------------------------------------------------------------------------
# Biomes
# ---------------------------------------------------------------------------------------------------
def spawner(entity, weight, lo, hi):
    return {"type": entity, "weight": weight, "minCount": lo, "maxCount": hi}


FISH = [spawner("minecraft:cod", 15, 3, 6), spawner("minecraft:salmon", 8, 1, 5)]
TROPICAL = [spawner("minecraft:tropical_fish", 25, 8, 8), spawner("minecraft:pufferfish", 15, 1, 3)]
GLOW_SQUID = [spawner("minecraft:glow_squid", 10, 2, 4)]
SQUID = [spawner("minecraft:squid", 4, 1, 4)]
DROWNED = [spawner("minecraft:drowned", 5, 1, 1)]
GUARDIAN = [spawner("minecraft:guardian", 2, 1, 2)]

BIOMES = {}


def biome(name, display, water, water_fog, fog, temperature=0.5, features=(), extra_last=None, water_ambient=(),
          water_creature=(), underground=(), monster=(), axolotls=(), particle=None, precipitation=False, sky=0x0B1D3A):
    effects = {
        "fog_color": fog,
        "sky_color": sky,
        "water_color": water,
        "water_fog_color": water_fog,
        "mood_sound": {"sound": "minecraft:ambient.cave", "tick_delay": 6000, "block_search_extent": 8, "offset": 2.0},
        "music": {"sound": "minecraft:music.under_water", "min_delay": 12000, "max_delay": 24000, "replace_current_music": False}
    }
    if particle:
        effects["particle"] = particle
    obj = {
        "has_precipitation": precipitation,
        "temperature": temperature,
        "downfall": 0.4 if precipitation else 0.0,
        "effects": effects,
        "spawners": {
            "monster": list(monster), "creature": [], "ambient": [], "axolotls": list(axolotls),
            "underground_water_creature": list(underground), "water_creature": list(water_creature),
            "water_ambient": list(water_ambient), "misc": []
        },
        "spawn_costs": {},
        "carvers": {},
        "features": feature_lists(set(features), extra_last)
    }
    write(f"{MOD}/worldgen/biome/{name}.json", obj)
    BIOMES[name] = display


SNOW = {"options": {"type": "minecraft:underwater"}, "probability": 0.006}
ASH = {"options": {"type": "minecraft:white_ash"}, "probability": 0.004}
GLOW = {"options": {"type": "minecraft:glow"}, "probability": 0.0015}

biome("ice_shelf", "Ice Shelf", 0x3D8FD0, 0x0A2A4A, 0xC0D8FF, temperature=-0.5, precipitation=True, sky=0x7FA1FF,
      features=["ice_stalactites"], extra_last=[("top_layer_modification", VANILLA_FREEZE)],
      water_ambient=FISH, water_creature=SQUID)
biome("the_great_sea", "The Great Sea", 0x1B3A6B, 0x020814, 0x0B1530, particle=SNOW,
      water_ambient=FISH, water_creature=SQUID, underground=GLOW_SQUID, monster=DROWNED)
biome("abyssal_plains", "Abyssal Plains", 0x2B4F7E, 0x05101F, 0x0B1530,
      features=ORES + ["seagrass_sparse", "glow_meadow"], water_ambient=FISH, underground=GLOW_SQUID)
biome("spire_fields", "Spire Fields", 0x3A4A5A, 0x080C10, 0x10141A,
      features=ORES + ["rock_needles", "glow_meadow"], water_ambient=FISH, underground=GLOW_SQUID)
biome("pillar_forest", "Pillar Forest", 0x2E3F55, 0x060A12, 0x0C1220,
      features=ORES + ["kelp_sparse", "pillar_lichen"], water_ambient=FISH, underground=GLOW_SQUID)
biome("monolith_reach", "Monolith Reach", 0x3B4B60, 0x0A0E14, 0x10141C,
      features=ORES + ["rock_needles", "seagrass_sparse", "kelp_sparse"], water_ambient=FISH, underground=GLOW_SQUID,
      monster=GUARDIAN)
biome("kelp_forest", "Kelp Forest", 0x1F6A5A, 0x041A14, 0x0A2A20,
      features=ORES + ["giant_kelp_forest", "kelp_sparse", "seagrass_meadow"], water_ambient=FISH, water_creature=SQUID,
      underground=GLOW_SQUID)
biome("coral_gardens", "Coral Gardens", 0x2A80A8, 0x08263A, 0x103040, temperature=0.8,
      features=ORES + ["coral_garden", "coral_patch", "seagrass_meadow", "glow_meadow"], water_ambient=FISH + TROPICAL,
      water_creature=SQUID, underground=GLOW_SQUID)
biome("europan_ridge", "Europan Ridge", 0x4A5E8A, 0x0B1224, 0x141C30,
      features=ORES + ["rock_needles", "kelp_sparse"], water_ambient=FISH, underground=GLOW_SQUID)
biome("hydrothermal_wastes", "Hydrothermal Wastes", 0x5A3A2A, 0x1A0A05, 0x201008, temperature=1.2, particle=ASH,
      features=ORES + ["ore_magma", "hydrothermal_vent"], underground=GLOW_SQUID, monster=DROWNED)
biome("hadal_trench", "Hadal Trench", 0x101830, 0x010206, 0x020408, particle=SNOW,
      features=ORES + ["glow_meadow"], underground=GLOW_SQUID, monster=DROWNED)
biome("abyssal_ravine", "Abyssal Ravine", 0x142040, 0x02040A, 0x04060C,
      features=["cave_glow_lichen", "cave_floor_glow"], underground=GLOW_SQUID)
biome("great_channels", "Great Channels", 0x24406A, 0x030812, 0x060A14,
      features=["cave_glow_lichen", "cave_floor_meadow"], water_ambient=FISH, underground=GLOW_SQUID, monster=DROWNED)
biome("sunken_channels", "Sunken Channels", 0x2A3F5F, 0x040A14, 0x060A14,
      features=["cave_glow_lichen", "cave_floor_meadow"], water_ambient=FISH, underground=GLOW_SQUID, monster=DROWNED)
biome("narrow_passages", "Narrow Passages", 0x1E2E44, 0x02060C, 0x04060A,
      features=["cave_glow_lichen"], underground=GLOW_SQUID)
biome("luminous_caverns", "Luminous Caverns", 0x2A7FA0, 0x06303A, 0x0A3040, particle=GLOW,
      features=["cave_glow_lichen_dense", "cave_floor_glow", "cave_floor_meadow"], water_ambient=FISH,
      underground=GLOW_SQUID, axolotls=[spawner("minecraft:axolotl", 10, 4, 6)])
biome("cold_caverns", "Cold Caverns", 0x6FA8D8, 0x0E2A40, 0x20405A, temperature=-0.7,
      features=["cave_glow_lichen", "cave_ice_needles", "cave_icicles"], water_ambient=FISH, underground=GLOW_SQUID)
biome("station_hollow", "Station Hollow", 0x355A7A, 0x0A1A28, 0x10202C,
      features=["cave_glow_lichen"], water_ambient=FISH, underground=GLOW_SQUID)
biome("deep_crust", "Deep Crust", 0x1A2A40, 0x02050A, 0x04060A,
      features=["flooded_geode"] + ORES + ["ore_magma"], underground=GLOW_SQUID)

ALL = list(BIOMES.keys())
SEABED = ["abyssal_plains", "spire_fields", "pillar_forest", "monolith_reach", "kelp_forest", "coral_gardens",
          "europan_ridge", "hydrothermal_wastes", "hadal_trench"]
CAVES = ["abyssal_ravine", "great_channels", "sunken_channels", "narrow_passages", "luminous_caverns", "cold_caverns",
         "station_hollow", "deep_crust"]

# ---------------------------------------------------------------------------------------------------
# Tags
# ---------------------------------------------------------------------------------------------------
def tag(path, values, optional=()):
    vals = [m(v) if ":" not in v and not v.startswith("#") else v for v in values]
    vals += [{"id": o, "required": False} for o in optional]
    write(path, {"replace": False, "values": vals})


MODDED_DEEP = ["alexscaves:abyssal_chasm", "hybrid_aquatic:trench", "hybrid_aquatic:cold_trench",
               "hybrid_aquatic:frozen_trench", "hybrid_aquatic:lukewarm_trench", "hybrid_aquatic:warm_trench",
               "hybrid_aquatic:deep_coral_reef", "hybrid_aquatic:coral_reef", "hybrid_aquatic:seagrass_bed",
               "hybrid_aquatic:red_meadow", "hybrid_aquatic:sulfuric_caves", "hybrid_aquatic:deep_warm_ocean"]

tag(f"{MOD}/tags/worldgen/biome/all.json", ALL)
tag(f"{MOD}/tags/worldgen/biome/relaxed_water_spawning.json", ALL, MODDED_DEEP)
tag(f"{MOD}/tags/worldgen/biome/depth_fog.json", ALL, MODDED_DEEP)
tag(f"{MOD}/tags/worldgen/biome/alexscaves_abyss.json", ["hadal_trench", "abyssal_ravine", "the_great_sea", "abyssal_plains", "pillar_forest"])
tag(f"{MOD}/tags/worldgen/biome/alexscaves_abyss_floor.json", ["hadal_trench", "abyssal_plains"])

tag("minecraft/tags/worldgen/biome/is_ocean.json", SEABED + ["the_great_sea", "ice_shelf", "abyssal_ravine"])
tag("minecraft/tags/worldgen/biome/plays_underwater_music.json", ALL)
tag("minecraft/tags/worldgen/biome/without_wandering_trader_spawns.json", ALL)
tag("minecraft/tags/worldgen/biome/without_patrol_spawns.json", ALL)
tag("minecraft/tags/worldgen/biome/without_zombie_sieges.json", ALL)
tag("minecraft/tags/worldgen/biome/has_structure/shipwreck.json", SEABED)
tag("minecraft/tags/worldgen/biome/has_structure/ocean_ruin_cold.json",
    ["abyssal_plains", "spire_fields", "pillar_forest", "monolith_reach", "kelp_forest", "europan_ridge", "hadal_trench"])
tag("minecraft/tags/worldgen/biome/has_structure/ocean_ruin_warm.json", ["coral_gardens", "hydrothermal_wastes"])

tag("c/tags/worldgen/biome/is_ocean.json", SEABED + ["the_great_sea", "ice_shelf"])
tag("c/tags/worldgen/biome/is_deep_ocean.json", SEABED + ["the_great_sea"])
tag("c/tags/worldgen/biome/is_aquatic.json", ALL)
tag("c/tags/worldgen/biome/is_underground.json", CAVES)
tag("c/tags/worldgen/biome/is_cave.json", CAVES)
tag("c/tags/worldgen/biome/is_cold.json", ["ice_shelf", "cold_caverns"])
tag("c/tags/worldgen/biome/is_icy.json", ["ice_shelf", "cold_caverns"])
tag("c/tags/worldgen/biome/is_snowy.json", ["ice_shelf"])
tag("c/tags/worldgen/biome/is_hot.json", ["hydrothermal_wastes"])

# Hybrid Aquatic: its creatures and decorations are added to biomes through these tags.
HA = "hybrid_aquatic/tags/worldgen/biome/"
tag(HA + "deep_temperate_oceans.json", ["the_great_sea", "abyssal_plains", "pillar_forest", "monolith_reach", "europan_ridge", "spire_fields"])
tag(HA + "ha_deep_oceans.json", ["abyssal_plains", "monolith_reach", "spire_fields"])
tag(HA + "deep_cold_oceans.json", ["ice_shelf"])
tag(HA + "temperate_trench.json", ["hadal_trench", "abyssal_ravine"])
tag(HA + "cold_trench.json", ["cold_caverns"])
tag(HA + "all_trenches.json", ["hadal_trench", "abyssal_ravine"])
tag(HA + "seagrass_bed.json", ["kelp_forest"])
tag(HA + "coral_reef.json", ["coral_gardens"])
tag(HA + "deep_reef.json", ["coral_gardens"])
tag(HA + "has_thermal_vents.json", ["hydrothermal_wastes"])
tag(HA + "sulfuric_cave.json", ["hydrothermal_wastes"])
tag(HA + "has_whale_fall.json", ["abyssal_plains", "hadal_trench", "pillar_forest"])
tag(HA + "caves.json", ["great_channels", "sunken_channels", "narrow_passages", "luminous_caverns"])

# Alex's Caves: deep-sea creatures and abyssal decorations in our deep biomes (only when the mod is present).
AC_COND = [{"type": "neoforge:mod_loaded", "modid": "alexscaves"}]
write(f"{MOD}/neoforge/biome_modifier/alexscaves_abyssal_creatures.json", {
    "neoforge:conditions": AC_COND,
    "type": "neoforge:add_spawns",
    "biomes": f"#{MOD}:alexscaves_abyss",
    "spawners": [spawner("alexscaves:lanternfish", 30, 6, 12), spawner("alexscaves:tripodfish", 10, 1, 2),
                 spawner("alexscaves:sea_pig", 12, 2, 4), spawner("alexscaves:gossamer_worm", 5, 1, 1)]
})
write(f"{MOD}/neoforge/biome_modifier/alexscaves_deep_ones.json", {
    "neoforge:conditions": AC_COND,
    "type": "neoforge:add_spawns",
    "biomes": f"#{MOD}:alexscaves_abyss",
    "spawners": [spawner("alexscaves:deep_one", 4, 1, 1), spawner("alexscaves:deep_one_knight", 2, 1, 1),
                 spawner("alexscaves:deep_one_mage", 1, 1, 1)]
})
write(f"{MOD}/neoforge/biome_modifier/alexscaves_hullbreaker.json", {
    "neoforge:conditions": AC_COND,
    "type": "neoforge:add_spawns",
    "biomes": m("hadal_trench"),
    "spawners": [spawner("alexscaves:hullbreaker", 1, 1, 1)]
})
write(f"{MOD}/neoforge/biome_modifier/alexscaves_abyssal_floor.json", {
    "neoforge:conditions": AC_COND,
    "type": "neoforge:add_features",
    "biomes": f"#{MOD}:alexscaves_abyss_floor",
    "features": ["alexscaves:black_vent", "alexscaves:whalefall"],
    "step": "underground_structures"
})
write(f"{MOD}/neoforge/biome_modifier/alexscaves_abyssal_life.json", {
    "neoforge:conditions": AC_COND,
    "type": "neoforge:add_features",
    "biomes": f"#{MOD}:alexscaves_abyss_floor",
    "features": ["alexscaves:tube_worm", "alexscaves:mussel", "alexscaves:dusk_anemone",
                 "alexscaves:twilight_anemone", "alexscaves:midnight_anemone"],
    "step": "vegetal_decoration"
})

# ---------------------------------------------------------------------------------------------------
# Language file (biomes, world preset, commands, config screen)
# ---------------------------------------------------------------------------------------------------
lang = {
    "generator.barotrauma_oceans.barotrauma_oceans": "Barotrauma Oceans",
    "barotrauma_oceans.configuration.title": "Barotrauma Oceans Configuration",
    "barotrauma_oceans.configuration.worldgen": "World Generation (new worlds)",
    "barotrauma_oceans.configuration.gameplay": "Gameplay",
    "barotrauma_oceans.configuration.gameplay.world": "World",
    "barotrauma_oceans.configuration.gameplay.world.apply_to_existing_worlds": "Apply To Existing Worlds",
    "barotrauma_oceans.configuration.gameplay.spawn": "Spawn",
    "barotrauma_oceans.configuration.gameplay.spawn.mode": "Spawn Mode",
    "barotrauma_oceans.configuration.gameplay.spawn.pocket_radius": "Spawn Pocket Radius",
    "barotrauma_oceans.configuration.gameplay.spawn.teleport_new_players": "Move New Players To Spawn Pocket",
    "barotrauma_oceans.configuration.gameplay.spawn.respawn_at_spawn_structure": "Respawn In Spawn Pocket",
    "barotrauma_oceans.configuration.gameplay.spawn.set_spawn_radius_zero": "Set Spawn Radius To 0",
    "barotrauma_oceans.configuration.gameplay.spawn.starter_items": "Starter Chest Items",
    "barotrauma_oceans.configuration.gameplay.compat": "Mod Compatibility",
    "barotrauma_oceans.configuration.gameplay.compat.relax_water_mob_depth": "Sea Creatures Spawn At Any Depth",
    "barotrauma_oceans.configuration.gameplay.compat.relaxed_extra_mobs": "Extra Mobs With Relaxed Depth",
    "barotrauma_oceans.configuration.gameplay.commands": "Commands",
    "barotrauma_oceans.configuration.gameplay.commands.search_radius": "Station Search Radius",
    "barotrauma_oceans.configuration.gameplay.commands.permission_level": "Command Permission Level",
    "barotrauma_oceans.configuration.fog": "Depth Fog",
    "barotrauma_oceans.configuration.fog.depth_fog": "Enable Depth Fog",
    "barotrauma_oceans.configuration.fog.start_y": "Fog Start Y",
    "barotrauma_oceans.configuration.fog.fade_range": "Fog Fade Range",
    "barotrauma_oceans.configuration.fog.min_distance": "Deepest Visibility",
    "barotrauma_oceans.configuration.fog.depth_darkening": "Depth Darkening",
    "barotrauma_oceans.configuration.fog.only_mod_biomes": "Only In Mod Biomes",
    "commands.barotrauma_oceans.not_ocean_world": "This dimension is not a Barotrauma Oceans world.",
    "commands.barotrauma_oceans.no_network": "The station network is disabled in this world.",
    "commands.barotrauma_oceans.click_to_tp": "Click to put a /tp command into the chat",
    "commands.barotrauma_oceans.stations.header": "Nearest %s station caverns:",
    "commands.barotrauma_oceans.stations.size": "cavern %sx%s",
    "commands.barotrauma_oceans.stations.channels": "channels: %s",
    "commands.barotrauma_oceans.stations.shaft": "seabed entrance shaft at",
    "commands.barotrauma_oceans.route.header": "Route through %s channel(s):",
    "commands.barotrauma_oceans.route.none": "No route found nearby.",
    "commands.barotrauma_oceans.route.too_far": "That target is too far away to plan a route.",
    "commands.barotrauma_oceans.info.region": "Region: %s, place: %s, biome: %s",
    "commands.barotrauma_oceans.info.levels": "Seabed Y %s, ice underside Y %s, water level Y %s",
    "commands.barotrauma_oceans.info.nearest": "Nearest station: %s blocks %s",
    "commands.barotrauma_oceans.tp": "Teleported to the station at %s %s %s",
}
for name, display in BIOMES.items():
    lang[f"biome.{MOD}.{name}"] = display

# Config names straight from OceanSettings.java
src = open(os.path.join(ROOT, "src/main/java/com/m4ytenn/barotraumaoceans/worldgen/core/OceanSettings.java"), encoding="utf-8").read()
sections = {}
for var, path, display in re.findall(r'Section (\w+) = section\("([\w.]+)", "([^"]+)"', src):
    sections[var] = path
    lang[f"barotrauma_oceans.configuration.worldgen.{path}"] = display
    # parent levels of nested sections
    parts = path.split(".")
for var, name, display in re.findall(r'= (?:i|d|b|str|list)\((\w+), "(\w+)", "([^"]+)"', src):
    lang[f"barotrauma_oceans.configuration.worldgen.{sections[var]}.{name}"] = display

out = os.path.join(RES, "assets", MOD, "lang")
os.makedirs(out, exist_ok=True)
with open(os.path.join(out, "en_us.json"), "w", encoding="utf-8") as f:
    json.dump(lang, f, indent=2, ensure_ascii=False)
    f.write("\n")

print(f"biomes: {len(BIOMES)}, placed features: {len(PLACED)}, lang entries: {len(lang)}")
