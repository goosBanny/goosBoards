package me.goosbanny.goosboards.raycast.spatial.impl;

import me.goosbanny.goosboards.display.DisplayPlane;
import me.goosbanny.goosboards.raycast.Vector3d;
import me.goosbanny.goosboards.raycast.spatial.DisplaySpatialIndex;

import it.unimi.dsi.fastutil.longs.Long2ObjectMap;
import it.unimi.dsi.fastutil.longs.Long2ObjectMaps;
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Ultra-fast spatial partitioning index utilizing packed 64-bit primitive long
 * bucket keys
 * and FastUtil primitive Long2Object maps, eliminating all BucketKey object
 * allocations.
 */
public class ChunkBucketSpatialIndex implements DisplaySpatialIndex {
    public static final double DEFAULT_BUCKET_SIZE = 32.0;

    private final double bucketSize;
    private final Map<String, Long2ObjectMap<CopyOnWriteArrayList<DisplayPlane>>> worldBuckets = new ConcurrentHashMap<>();
    private final Map<UUID, DisplayPlane> planesById = new ConcurrentHashMap<>();

    public ChunkBucketSpatialIndex() {
        this(DEFAULT_BUCKET_SIZE);
    }

    public ChunkBucketSpatialIndex(double bucketSize) {
        this.bucketSize = bucketSize > 0.0 ? bucketSize : DEFAULT_BUCKET_SIZE;
    }

    public static final int BIAS_XZ = 0x200000; // 2^21 bias offset
    public static final int BIAS_Y = 0x80000;   // 2^19 bias offset

    /**
     * Bit-packs 3D chunk-bucket coordinates into a single primitive 64-bit long.
     * x (22 bits), y (20 bits), z (22 bits) with signed coordinate offset bias.
     */
    public static long packBucketKey(int x, int y, int z) {
        long bx = ((long) (x + BIAS_XZ)) & 0x3FFFFFL;
        long by = ((long) (y + BIAS_Y)) & 0xFFFFFL;
        long bz = ((long) (z + BIAS_XZ)) & 0x3FFFFFL;
        return (bx << 42) | (by << 20) | bz;
    }

    /**
     * Thread-safe map lookup/creation using FastUtil synchronized wrapper.
     * Note: COWAL lists provide thread-safe iteration snapshots while synchronized
     * map provides
     * thread-safe bucket mutation.
     */
    private Long2ObjectMap<CopyOnWriteArrayList<DisplayPlane>> getOrCreateWorldMap(String world) {
        String key = world != null ? world.toLowerCase(Locale.ROOT) : "world";
        return worldBuckets.computeIfAbsent(key, k -> Long2ObjectMaps.synchronize(new Long2ObjectOpenHashMap<>(128)));
    }

    private Long2ObjectMap<CopyOnWriteArrayList<DisplayPlane>> getWorldMap(String world) {
        if (world == null)
            return null;
        return worldBuckets.get(world.toLowerCase(Locale.ROOT));
    }

    @Override
    public void register(DisplayPlane plane) {
        if (plane == null) {
            return;
        }
        unregister(plane.id());
        planesById.put(plane.id(), plane);

        Vector3d c0 = plane.topLeft();
        Vector3d c1 = c0.add(plane.rightUnit().multiply(plane.widthBlocks()));
        Vector3d c2 = c0.add(plane.downUnit().multiply(plane.heightBlocks()));
        Vector3d c3 = c1.add(plane.downUnit().multiply(plane.heightBlocks()));

        double minX = Math.min(Math.min(c0.x(), c1.x()), Math.min(c2.x(), c3.x()));
        double maxX = Math.max(Math.max(c0.x(), c1.x()), Math.max(c2.x(), c3.x()));
        double minY = Math.min(Math.min(c0.y(), c1.y()), Math.min(c2.y(), c3.y()));
        double maxY = Math.max(Math.max(c0.y(), c1.y()), Math.max(c2.y(), c3.y()));
        double minZ = Math.min(Math.min(c0.z(), c1.z()), Math.min(c2.z(), c3.z()));
        double maxZ = Math.max(Math.max(c0.z(), c1.z()), Math.max(c2.z(), c3.z()));

        int minBx = (int) Math.floor(minX / bucketSize);
        int maxBx = (int) Math.floor(maxX / bucketSize);
        int minBy = (int) Math.floor(minY / bucketSize);
        int maxBy = (int) Math.floor(maxY / bucketSize);
        int minBz = (int) Math.floor(minZ / bucketSize);
        int maxBz = (int) Math.floor(maxZ / bucketSize);

        Long2ObjectMap<CopyOnWriteArrayList<DisplayPlane>> map = getOrCreateWorldMap(plane.worldName());
        for (int bx = minBx; bx <= maxBx; bx++) {
            for (int by = minBy; by <= maxBy; by++) {
                for (int bz = minBz; bz <= maxBz; bz++) {
                    long key = packBucketKey(bx, by, bz);
                    map.computeIfAbsent(key, k -> new CopyOnWriteArrayList<>()).addIfAbsent(plane);
                }
            }
        }
    }

    /**
     * Unregisters a display plane by UUID from all chunk buckets.
     * <p>
     * <b>State Invariant & Bucket Integrity:</b>
     * This method retrieves the original {@link DisplayPlane} record stored in
     * {@code planesById}
     * at registration time to compute the exact AABB bucket keys to clear. Because
     * {@link DisplayPlane}
     * is an immutable record, this guarantees that unregistration cannot
     * desynchronize or leak zombie
     * bucket entries even if external references are manipulated.
     */
    @Override
    public void unregister(UUID displayId) {
        if (displayId == null) {
            return;
        }
        DisplayPlane removed = planesById.remove(displayId);
        if (removed != null) {
            Vector3d c0 = removed.topLeft();
            Vector3d c1 = c0.add(removed.rightUnit().multiply(removed.widthBlocks()));
            Vector3d c2 = c0.add(removed.downUnit().multiply(removed.heightBlocks()));
            Vector3d c3 = c1.add(removed.downUnit().multiply(removed.heightBlocks()));

            double minX = Math.min(Math.min(c0.x(), c1.x()), Math.min(c2.x(), c3.x()));
            double maxX = Math.max(Math.max(c0.x(), c1.x()), Math.max(c2.x(), c3.x()));
            double minY = Math.min(Math.min(c0.y(), c1.y()), Math.min(c2.y(), c3.y()));
            double maxY = Math.max(Math.max(c0.y(), c1.y()), Math.max(c2.y(), c3.y()));
            double minZ = Math.min(Math.min(c0.z(), c1.z()), Math.min(c2.z(), c3.z()));
            double maxZ = Math.max(Math.max(c0.z(), c1.z()), Math.max(c2.z(), c3.z()));

            int minBx = (int) Math.floor(minX / bucketSize);
            int maxBx = (int) Math.floor(maxX / bucketSize);
            int minBy = (int) Math.floor(minY / bucketSize);
            int maxBy = (int) Math.floor(maxY / bucketSize);
            int minBz = (int) Math.floor(minZ / bucketSize);
            int maxBz = (int) Math.floor(maxZ / bucketSize);

            Long2ObjectMap<CopyOnWriteArrayList<DisplayPlane>> map = getWorldMap(removed.worldName());
            if (map != null) {
                for (int bx = minBx; bx <= maxBx; bx++) {
                    for (int by = minBy; by <= maxBy; by++) {
                        for (int bz = minBz; bz <= maxBz; bz++) {
                            long key = packBucketKey(bx, by, bz);
                            map.computeIfPresent(key, (k, list) -> {
                                list.removeIf(p -> p.id().equals(displayId));
                                return list.isEmpty() ? null : list;
                            });
                        }
                    }
                }
            }
        }
    }

    @Override
    public List<DisplayPlane> nearbyBoards(Vector3d position, double maxRadius, String worldName) {
        List<DisplayPlane> result = new ArrayList<>();
        nearbyBoards(position, maxRadius, worldName, result, new HashSet<>());
        return result;
    }

    @Override
    public void nearbyBoards(
            Vector3d position,
            double maxRadius,
            String worldName,
            List<DisplayPlane> outResult,
            Set<UUID> seenScratch) {
        if (outResult == null) {
            return;
        }
        outResult.clear();
        if (seenScratch != null) {
            seenScratch.clear();
        }

        if (position == null || worldName == null || maxRadius <= 0.0) {
            return;
        }

        Long2ObjectMap<CopyOnWriteArrayList<DisplayPlane>> map = getWorldMap(worldName);
        if (map == null || map.isEmpty()) {
            return;
        }

        int minBx = (int) Math.floor((position.x() - maxRadius) / bucketSize);
        int maxBx = (int) Math.floor((position.x() + maxRadius) / bucketSize);
        int minBy = (int) Math.floor((position.y() - maxRadius) / bucketSize);
        int maxBy = (int) Math.floor((position.y() + maxRadius) / bucketSize);
        int minBz = (int) Math.floor((position.z() - maxRadius) / bucketSize);
        int maxBz = (int) Math.floor((position.z() + maxRadius) / bucketSize);

        Set<UUID> seen = seenScratch != null ? seenScratch : new HashSet<>();

        for (int bx = minBx; bx <= maxBx; bx++) {
            for (int by = minBy; by <= maxBy; by++) {
                for (int bz = minBz; bz <= maxBz; bz++) {
                    long key = packBucketKey(bx, by, bz);
                    CopyOnWriteArrayList<DisplayPlane> list = map.get(key);
                    if (list != null) {
                        int listSize = list.size();
                        for (int i = 0; i < listSize; i++) {
                            DisplayPlane plane = list.get(i);
                            if (seen.add(plane.id())) {
                                if (plane.worldName().equalsIgnoreCase(worldName)) {
                                    double effectiveRadius = maxRadius + plane.boundingRadius();
                                    Vector3d center = plane.center();
                                    if (center != null && position.distanceSquared(center.x(), center.y(),
                                            center.z()) <= effectiveRadius * effectiveRadius) {
                                        outResult.add(plane);
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    public double getBucketSize() {
        return bucketSize;
    }

    public DisplayPlane getPlane(UUID displayId) {
        return planesById.get(displayId);
    }
}
