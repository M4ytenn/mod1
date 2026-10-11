# Barotrauma Oceans

A NeoForge **1.21.1** world-generation mod inspired by *Barotrauma*: the whole world is a dark, very deep
ocean sealed under a thick ice crust. Under the seabed runs a network of wide channels that connect deep
**station caverns**, made for submarines and big Create Aeronautics vessels.

## Features

### World type `Barotrauma Oceans`
In *Create New World → More → World Type*, pick **Barotrauma Oceans**. On a dedicated server, set
`level-type=barotrauma_oceans:barotrauma_oceans` in `server.properties`.

* World height from **Y −512 to 320**.
* **Ice crust** (can be turned off): packed ice and blue-ice veins from about Y 236 up to about Y 300, with ice
  keels hanging down from the underside and a frozen, snowy surface on top. Below it, everything is water.
* **Seabed** at about Y −64, split into large regions that each have their own terrain:

| Region | What it looks like |
|---|---|
| Abyssal Plains | spacious, very flat planes of sand, gravel and clay |
| Spire Fields | rocky spiky plains: dense rock spikes, tall spires and small rock needles |
| Pillar Forest | big rock columns from the seabed up to the ice |
| Monolith Reach | big rock formations: boulders, arches, mesas, leaning slabs |
| Kelp Forest | giant kelp up to 60 blocks tall and seagrass meadows |
| Coral Gardens | raised plateaus covered in coral, seagrass and sea pickles |
| Europan Ridge | sharp underwater mountain ridges |
| Hydrothermal Wastes | terraced basalt, magma, and vent chimneys pushing bubble-column currents upwards |
| Hadal Trench | basins about 150 blocks deeper, with cliff walls |

* **Ravines**: long, deep, V-shaped cracks in the seabed that sometimes break into the channel network.

### The station network
* A **station cavern** sits on a jittered grid, about every 1024 blocks by default. Each cavern is a big dome or
  elongated hall, about 100–170 blocks across, with a **flat floor** to build on. Some have support columns, and
  some have a wide **shaft up to the seabed** that serves as an entrance from the open ocean.
* **Channels** connect neighbouring stations. They follow a general direction with gentle meanders and **never
  branch**: junctions only happen inside station caverns. The network is always fully connected.
  * Three size classes: spacious (64–100 blocks wide), standard (36–60) and narrow (20–30).
  * Spacious stretches can widen into big caverns. Some channels also have tall canyon stretches, narrower
    constrictions, and floor-to-ceiling columns near the walls.
  * **Guaranteed clearance:** every channel keeps a clear corridor around its centre line (radius 9 by default).
    Pillars, rocks and spikes never go inside it, so a vessel up to 18 blocks across always fits.
    `tools/preview/NavCheck.java` tests this.
* **Crew tunnels**: tiny 2–4 block tunnels branching off channels and stations, only wide enough for divers.
  They often end in a small grotto with sea pickles, and some grottoes hold a supply chest.

### Biomes
19 biomes: Ice Shelf, The Great Sea (open water), the 9 seabed regions, Abyssal Ravine, Great Channels,
Sunken Channels, Narrow Passages, Luminous Caverns, Cold Caverns, Station Hollow and Deep Crust. Biomes are
fully 3D: the open water, the seabed, the channels and the caverns at the same X/Z can all have different
biomes. Each biome has its own sediment, colours, fog, decoration and mobs. Glow squid, cod and salmon live at
every depth, guardians in Monolith Reach and drowned in some areas.

### Compatibility
* **Alex's Caves** (optional; the original and the *Alex's Caves Continued* NeoForge port both work)
  * The Abyssal Chasm biome appears in Hadal Trench regions.
  * Magnetic Caves, Primordial Caves, Toxic Caves, Forlorn Hollows and Candy Cavity appear as rare **rock
    pockets**: sealed, dry caverns (about 150 blocks across) deep in the crust, at least 28 blocks away from any
    channel or station. Alex's Caves only builds its own cave structures near Y −48..20, so this mod carves
    the caverns itself. Its mobs, decoration and surface blocks still appear there. Without Alex's Caves
    (or with an empty `rock_pockets` biome list) there are no rock pockets.
  * Lanternfish, tripodfish, sea pigs, gossamer worms, Deep Ones and the occasional Hullbreaker spawn in the
    abyssal biomes of this mod. Black vents, whale falls, tube worms and anemones decorate them.
* **Hybrid Aquatic** (optional)
  * Its trench, coral reef, seagrass bed, red meadow and sulfuric cave biomes are mixed into the matching
    regions.
  * Our biomes are added to its biome tags, so its fish, sharks, squids, crabs, whale falls, thermal vents and
    sponges appear here too.
* Vanilla and modded sea creatures normally only spawn near sea level. Inside this mod's biomes they may also
  spawn at **any depth**; the `compat` config controls this.
* Shipwrecks and ocean ruins generate on the seabed, not on top of the ice.
* **Any** biome from any mod can be added to any part of the world through the `biomes` config section.

### Spawning
By default a new world gets an **air pocket carved into the underside of the ice**. It is lit, has a moon pool
leading down into the ocean, and a starter chest. New players and players without a bed spawn there. With the
ice crust turned off you get a raft on the surface instead.

### Commands
| Command | |
|---|---|
| `/barotrauma stations [count]` | nearest station caverns, with clickable coordinates, the direction and size of each channel, and seabed entrance shafts |
| `/barotrauma route <x> <z>` | station-by-station route along the channels towards a position |
| `/barotrauma info` | region, biome and levels at your position, and the nearest station |
| `/barotrauma tp [n]` | teleport to the n-th nearest station floor (needs op) |

The F3 screen also shows the region and the nearest station.

## Configuration
*Mods → Barotrauma Oceans → Config* opens an editor where every option is grouped into sections with names
and tooltips. You can also edit `config/barotrauma_oceans-common.toml` and `barotrauma_oceans-client.toml`.

* **World Generation** (common config): ocean and seabed levels, ice crust, station network (spacing, how
  many extra connections, depth, meander, clearance, wall roughness), channel size classes, station caverns,
  crew tunnels and grottoes, ravines, region weights and size, columns, spikes and rock formations,
  vegetation and ore densities, materials, and the biome list of every part of the world.
  * These values are **copied into each new world** and saved in it, so changing the config later never
    causes chunk borders in existing worlds. If you really want to apply new values to an existing world, use
    `gameplay.world.apply_to_existing_worlds`.
* **Gameplay** (common config): spawn mode, spawn pocket, starter items, depth spawning of sea creatures, and
  command permissions.
* **Depth Fog** (client config): water gets darker and visibility shorter the deeper you are.

## Building
```
./gradlew build          # jar in build/libs
./gradlew runClient      # dev client
```
GitHub Actions builds every push. It also starts a headless server with the world type, generates chunks,
checks a few things (no lava, the spawn pocket, water in stations and channels) and prints ASCII
cross-sections of the generated terrain into the log.

`tools/datagen.py` regenerates the biome, feature, tag and language files. `tools/preview/` holds small
stand-alone Java tools that render the terrain to PNG images and check the channels for navigability
without starting Minecraft. Compile them together with `src/main/java/.../worldgen/core`.
