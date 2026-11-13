package io.github.nateriver31.reliquias.core;

import java.time.Duration;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.function.LongSupplier;

public final class Cooldowns {
    private static final long DURATION = Duration.ofSeconds(30).toNanos();
    private final Map<UUID, Long> starts = new HashMap<>();
    private final LongSupplier clock;

    public Cooldowns() { this(System::nanoTime); }
    public Cooldowns(LongSupplier clock) { this.clock = clock; }

    public long secondsLeft(UUID player) {
        Long start = starts.get(player);
        if (start == null) return 0;
        long left = DURATION - (clock.getAsLong() - start);
        if (left <= 0) {
            starts.remove(player);
            return 0;
        }
        return (left + 999_999_999L) / 1_000_000_000L;
    }

    public void start(UUID player) {
        long now = clock.getAsLong();
        starts.entrySet().removeIf(e -> now - e.getValue() >= DURATION);
        starts.put(player, now);
    }
}
