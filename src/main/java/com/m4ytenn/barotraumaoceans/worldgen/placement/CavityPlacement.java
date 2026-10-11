package com.m4ytenn.barotraumaoceans.worldgen.placement;

import com.m4ytenn.barotraumaoceans.registry.BORegistries;
import com.m4ytenn.barotraumaoceans.worldgen.TerrainAccess;
import com.m4ytenn.barotraumaoceans.worldgen.core.OceanTerrain;
import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.BlockPos;
import net.minecraft.tags.FluidTags;
import net.minecraft.util.RandomSource;
import net.minecraft.util.StringRepresentable;
import net.minecraft.world.level.levelgen.placement.PlacementContext;
import net.minecraft.world.level.levelgen.placement.PlacementModifier;
import net.minecraft.world.level.levelgen.placement.PlacementModifierType;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

/**
 * Moves a position into one of the network cavities (channels, station caverns, ravines) crossing its column,
 * then onto the floor / ceiling of that cavity. Far cheaper than scanning random heights of the whole world.
 */
public class CavityPlacement extends PlacementModifier {

    public enum Surface implements StringRepresentable {
        FLOOR("floor"), CEILING("ceiling"), INSIDE("inside");
        public static final Codec<Surface> CODEC = StringRepresentable.fromEnum(Surface::values);
        private final String name;

        Surface(String name) {
            this.name = name;
        }

        @Override
        public String getSerializedName() {
            return name;
        }
    }

    public static final MapCodec<CavityPlacement> CODEC = RecordCodecBuilder.mapCodec(i -> i.group(
            Surface.CODEC.fieldOf("surface").forGetter(p -> p.surface),
            Codec.BOOL.optionalFieldOf("channels", true).forGetter(p -> p.channels),
            Codec.BOOL.optionalFieldOf("stations", true).forGetter(p -> p.stations),
            Codec.BOOL.optionalFieldOf("ravines", true).forGetter(p -> p.ravines)
    ).apply(i, CavityPlacement::new));

    private final Surface surface;
    private final boolean channels;
    private final boolean stations;
    private final boolean ravines;

    public CavityPlacement(Surface surface, boolean channels, boolean stations, boolean ravines) {
        this.surface = surface;
        this.channels = channels;
        this.stations = stations;
        this.ravines = ravines;
    }

    @Override
    public Stream<BlockPos> getPositions(PlacementContext context, RandomSource random, BlockPos pos) {
        OceanTerrain t = TerrainAccess.terrain(context.getLevel());
        if (t == null) {
            return Stream.empty();
        }
        OceanTerrain.Column c = t.column(pos.getX(), pos.getZ());
        List<double[]> spans = new ArrayList<>();
        t.cavitySpans(c, spans);
        spans.removeIf(s -> (s[2] == 0 && !channels) || (s[2] == 1 && !stations) || (s[2] == 2 && !ravines));
        if (spans.isEmpty()) {
            return Stream.empty();
        }
        double[] span = spans.get(random.nextInt(spans.size()));
        int lo = (int) Math.floor(span[0]);
        int hi = (int) Math.ceil(span[1]);
        if (hi <= lo) {
            return Stream.empty();
        }
        int y = lo + random.nextInt(hi - lo + 1);
        int minY = context.getMinBuildHeight();
        BlockPos.MutableBlockPos m = new BlockPos.MutableBlockPos(pos.getX(), y, pos.getZ());
        if (!isWater(context, m)) {
            return Stream.empty();
        }
        switch (surface) {
            case FLOOR -> {
                for (int i = 0; i < 96 && m.getY() > minY; i++) {
                    m.move(0, -1, 0);
                    if (!isWater(context, m)) {
                        return Stream.of(m.above().immutable());
                    }
                }
                return Stream.empty();
            }
            case CEILING -> {
                for (int i = 0; i < 96; i++) {
                    m.move(0, 1, 0);
                    if (!isWater(context, m)) {
                        return Stream.of(m.below().immutable());
                    }
                }
                return Stream.empty();
            }
            default -> {
                return Stream.of(m.immutable());
            }
        }
    }

    private static boolean isWater(PlacementContext context, BlockPos pos) {
        return context.getBlockState(pos).getFluidState().is(FluidTags.WATER);
    }

    @Override
    public PlacementModifierType<?> type() {
        return BORegistries.CAVITY.get();
    }
}
