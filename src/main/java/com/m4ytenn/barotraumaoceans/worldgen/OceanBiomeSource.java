package com.m4ytenn.barotraumaoceans.worldgen;

import com.m4ytenn.barotraumaoceans.BarotraumaOceans;
import com.m4ytenn.barotraumaoceans.config.BOConfig;
import com.m4ytenn.barotraumaoceans.worldgen.core.BiomeSlot;
import com.m4ytenn.barotraumaoceans.worldgen.core.Hashing;
import com.m4ytenn.barotraumaoceans.worldgen.core.OceanSettings;
import com.m4ytenn.barotraumaoceans.worldgen.core.OceanTerrain;
import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderGetter;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.RegistryOps;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.BiomeSource;
import net.minecraft.world.level.biome.Climate;
import net.neoforged.fml.ModList;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Stream;

/**
 * 3D biome source for Barotrauma Oceans. The terrain decides which {@link BiomeSlot} a position belongs to
 * (ice shelf, open water, a seabed region, a channel, a station...), and each slot holds a weighted list of
 * biomes from the config - including biomes from other mods, which are silently skipped when absent.
 */
public final class OceanBiomeSource extends BiomeSource {

    public static final MapCodec<OceanBiomeSource> CODEC = RecordCodecBuilder.mapCodec(instance -> instance.group(
            RegistryOps.retrieveGetter(Registries.BIOME),
            Codec.unboundedMap(Codec.STRING, Codec.STRING).optionalFieldOf("settings")
                    .forGetter(src -> Optional.of(src.settings.toMap()))
    ).apply(instance, OceanBiomeSource::new));

    private record Weighted(Holder<Biome>[] biomes, double[] cumulative, double total) {
        Holder<Biome> pick(long variant) {
            if (biomes.length == 1) {
                return biomes[0];
            }
            double r = Hashing.unit(Hashing.mix(variant ^ 0x5DEECE66DL)) * total;
            for (int i = 0; i < cumulative.length; i++) {
                if (r < cumulative[i]) {
                    return biomes[i];
                }
            }
            return biomes[biomes.length - 1];
        }
    }

    private final OceanSettings settings;
    private final Map<BiomeSlot, Weighted> slots = new EnumMap<>(BiomeSlot.class);
    private final Set<Holder<Biome>> all = new LinkedHashSet<>();
    private final Holder<Biome> fallback;
    private final ThreadLocal<OceanTerrain.SlotResult> scratch = ThreadLocal.withInitial(OceanTerrain.SlotResult::new);
    private volatile OceanTerrain lastTerrain;

    public OceanBiomeSource(HolderGetter<Biome> biomes, Optional<Map<String, String>> savedSettings) {
        if (savedSettings.isPresent() && !(BOConfig.SPEC.isLoaded() && BOConfig.APPLY_TO_EXISTING_WORLDS.get())) {
            this.settings = OceanSettings.fromMap(savedSettings.get());
        } else {
            this.settings = BOConfig.worldgenSnapshot();
        }
        this.fallback = biomes.getOrThrow(BOBiomes.THE_GREAT_SEA);
        for (BiomeSlot slot : BiomeSlot.VALUES) {
            Weighted w = resolve(biomes, settings.getList(slot.listParam));
            if (w == null && slot.fallbackBiome != null) {
                w = resolve(biomes, List.of(slot.fallbackBiome + "=1"));
            }
            if (w != null) {
                slots.put(slot, w);
                for (Holder<Biome> h : w.biomes()) {
                    all.add(h);
                }
            }
        }
        all.add(fallback);
        if (!slots.containsKey(BiomeSlot.DEEP_CRUST)) {
            BarotraumaOceans.LOGGER.warn("Barotrauma Oceans: deep crust biome slot is empty, using the open-water biome instead");
        }
    }

    @SuppressWarnings("unchecked")
    private static Weighted resolve(HolderGetter<Biome> biomes, List<String> entries) {
        List<Holder<Biome>> found = new ArrayList<>();
        List<Double> weights = new ArrayList<>();
        for (String entry : entries) {
            String id = entry;
            double weight = 1.0;
            int eq = entry.lastIndexOf('=');
            if (eq > 0) {
                id = entry.substring(0, eq).trim();
                try {
                    weight = Double.parseDouble(entry.substring(eq + 1).trim());
                } catch (NumberFormatException e) {
                    BarotraumaOceans.LOGGER.warn("Barotrauma Oceans: bad biome weight in '{}'", entry);
                }
            }
            if (weight <= 0) {
                continue;
            }
            ResourceLocation rl = ResourceLocation.tryParse(id);
            if (rl == null) {
                BarotraumaOceans.LOGGER.warn("Barotrauma Oceans: invalid biome id '{}'", id);
                continue;
            }
            // While data packs load, asking for an unknown biome creates a reference that must be filled
            // later, so biomes of mods that are not installed must be skipped before looking them up.
            String ns = rl.getNamespace();
            if (!ns.equals("minecraft") && !ns.equals(BarotraumaOceans.MODID) && !ModList.get().isLoaded(ns)) {
                continue;
            }
            Optional<? extends Holder<Biome>> holder = biomes.get(ResourceKey.create(Registries.BIOME, rl));
            if (holder.isPresent()) {
                found.add(holder.get());
                weights.add(weight);
            } else {
                BarotraumaOceans.LOGGER.debug("Barotrauma Oceans: biome {} is not present, skipping it", rl);
            }
        }
        if (found.isEmpty()) {
            return null;
        }
        Holder<Biome>[] arr = found.toArray(new Holder[0]);
        double[] cum = new double[arr.length];
        double t = 0;
        for (int i = 0; i < arr.length; i++) {
            t += weights.get(i);
            cum[i] = t;
        }
        return new Weighted(arr, cum, t);
    }

    public OceanSettings oceanSettings() {
        return settings;
    }

    /** Whether a biome slot resolved to at least one biome (modded entries of missing mods are dropped). */
    public boolean hasSlot(BiomeSlot slot) {
        return slots.containsKey(slot);
    }

    @Override
    protected MapCodec<? extends BiomeSource> codec() {
        return CODEC;
    }

    @Override
    protected Stream<Holder<Biome>> collectPossibleBiomes() {
        return all.stream();
    }

    /** Terrain for the seed carried by the sampler, or null when the sampler is not wired by our noise settings. */
    public OceanTerrain terrain(Climate.Sampler sampler) {
        if (sampler.temperature() instanceof SeedCarrier carrier && carrier.isSeeded()) {
            long seed = carrier.terrainSeed();
            OceanTerrain t = lastTerrain;
            if (t == null || t.seed != seed) {
                t = OceanTerrain.get(seed, settings, hasSlot(BiomeSlot.ROCK_POCKET));
                lastTerrain = t;
            }
            return t;
        }
        return null;
    }

    public Holder<Biome> biomeFor(OceanTerrain.SlotResult r) {
        Weighted w = slots.get(r.slot);
        if (w == null) {
            w = slots.get(BiomeSlot.DEEP_CRUST);
            if (w == null) {
                return fallback;
            }
        }
        return w.pick(r.variant);
    }

    @Override
    public Holder<Biome> getNoiseBiome(int qx, int qy, int qz, Climate.Sampler sampler) {
        OceanTerrain t = terrain(sampler);
        if (t == null) {
            return fallback;
        }
        OceanTerrain.Column c = t.column(qx << 2, qz << 2);
        OceanTerrain.SlotResult r = scratch.get();
        t.slot(c, (qy << 2) + 2, r);
        return biomeFor(r);
    }

    @Override
    public void addDebugInfo(List<String> info, BlockPos pos, Climate.Sampler sampler) {
        OceanTerrain t = terrain(sampler);
        if (t == null) {
            return;
        }
        OceanTerrain.Column c = t.column(pos.getX() & ~3, pos.getZ() & ~3);
        OceanTerrain.SlotResult r = new OceanTerrain.SlotResult();
        t.slot(c, pos.getY(), r);
        info.add("Barotrauma: region " + c.ps.dominant.name().toLowerCase() + ", slot " + r.slot.name().toLowerCase()
                + String.format(", seabed %.0f", c.seabed));
    }
}
