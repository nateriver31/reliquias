package io.github.nateriver31.reliquias.core;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;

public final class RoofRules {
    public static final int FLOOR_BOTTOM = 219;
    public static final int FLOOR_TOP = 220;
    public static final int ROOF_MIN = 221;
    public static final int ROOF_MAX = 255;

    private RoofRules() { }

    public static List<ChunkKey> region(UUID world, double x, double z, int radius) {
        if (radius < 0 || radius > 3) throw new IllegalArgumentException("Invalid radius");
        int cx = ((int) Math.floor(x)) >> 4;
        int cz = ((int) Math.floor(z)) >> 4;
        var result = new ArrayList<ChunkKey>();
        for (int dx = -radius; dx <= radius; dx++) {
            for (int dz = -radius; dz <= radius; dz++) {
                result.add(new ChunkKey(world, cx + dx, cz + dz));
            }
        }
        // The destination chunk is prepared first. Direction/yaw never enters the calculation.
        result.sort(Comparator.comparingInt(k -> Math.max(Math.abs(k.x() - cx), Math.abs(k.z() - cz))));
        return List.copyOf(result);
    }
}
