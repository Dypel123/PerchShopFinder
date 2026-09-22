package me.perch.shopfinder.utils;

import me.perch.shopfinder.models.FoundShopItemModel;
import org.bukkit.Location;
import org.bukkit.Material;

import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Function;

public final class PlayerShopRandomizer {

    private static final long RANDOMIZATION_DURATION_MILLIS =
            10L * 60L * 1000L;

    private static final ConcurrentMap<UUID, SeedEntry> PLAYER_SEEDS =
            new ConcurrentHashMap<>();

    private static final AtomicInteger CLEANUP_COUNTER =
            new AtomicInteger();

    private PlayerShopRandomizer() {
    }

    /**
     * Returns the player's current randomization seed.
     * A new seed is generated after ten minutes.
     */
    public static long getCurrentSeed(UUID playerId) {
        long now = System.currentTimeMillis();

        SeedEntry entry = PLAYER_SEEDS.compute(
                playerId,
                (uuid, existing) -> {
                    if (existing == null || now >= existing.expiresAt) {
                        return new SeedEntry(
                                ThreadLocalRandom.current().nextLong(),
                                now + RANDOMIZATION_DURATION_MILLIS
                        );
                    }

                    return existing;
                }
        );

        // Occasionally remove entries belonging to inactive players.
        if ((CLEANUP_COUNTER.incrementAndGet() & 255) == 0) {
            PLAYER_SEEDS.entrySet().removeIf(
                    cached -> now >= cached.getValue().expiresAt
            );
        }

        return entry.seed;
    }

    public static void sortMaterials(
            List<Material> materials,
            long seed,
            String context
    ) {
        sortByStableRandomKey(
                materials,
                seed,
                context,
                Material::name
        );
    }

    public static void sortShops(
            List<FoundShopItemModel> shops,
            long seed,
            String context
    ) {
        sortByStableRandomKey(
                shops,
                seed,
                context,
                PlayerShopRandomizer::getStableShopKey
        );
    }

    private static <T> void sortByStableRandomKey(
            List<T> values,
            long seed,
            String context,
            Function<T, String> stableKeyFunction
    ) {
        long contextHash = hashString(context);

        Map<T, Long> randomRanks = new IdentityHashMap<>();
        Map<T, String> stableKeys = new IdentityHashMap<>();

        for (T value : values) {
            String stableKey = stableKeyFunction.apply(value);
            stableKeys.put(value, stableKey);

            long rank = mix64(
                    seed
                            ^ contextHash
                            ^ hashString(stableKey)
            );

            randomRanks.put(value, rank);
        }

        values.sort((left, right) -> {
            int randomComparison = Long.compareUnsigned(
                    randomRanks.get(left),
                    randomRanks.get(right)
            );

            if (randomComparison != 0) {
                return randomComparison;
            }

            // Extremely unlikely hash collision.
            return stableKeys.get(left)
                    .compareTo(stableKeys.get(right));
        });
    }

    private static String getStableShopKey(
            FoundShopItemModel shop
    ) {
        Location location = shop.getShopLocation();

        if (location == null) {
            return "missing-location:"
                    + shop.getShopOwner()
                    + ":"
                    + shop.getItem().getType().name();
        }

        String worldId = location.getWorld() == null
                ? "unknown-world"
                : location.getWorld().getUID().toString();

        return worldId
                + ":"
                + location.getBlockX()
                + ":"
                + location.getBlockY()
                + ":"
                + location.getBlockZ();
    }

    private static long hashString(String value) {
        long hash = 0xcbf29ce484222325L;

        for (int index = 0; index < value.length(); index++) {
            hash ^= value.charAt(index);
            hash *= 0x100000001b3L;
        }

        return hash;
    }

    private static long mix64(long value) {
        value ^= value >>> 30;
        value *= 0xbf58476d1ce4e5b9L;
        value ^= value >>> 27;
        value *= 0x94d049bb133111ebL;
        value ^= value >>> 31;
        return value;
    }

    private static final class SeedEntry {

        private final long seed;
        private final long expiresAt;

        private SeedEntry(long seed, long expiresAt) {
            this.seed = seed;
            this.expiresAt = expiresAt;
        }
    }
}