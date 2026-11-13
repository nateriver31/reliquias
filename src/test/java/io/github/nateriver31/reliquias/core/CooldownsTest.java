package io.github.nateriver31.reliquias.core;

import static org.junit.jupiter.api.Assertions.*;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;
import org.junit.jupiter.api.Test;

class CooldownsTest {
    @Test void thirtySecondsAreIndividualAndUseMonotonicTime() {
        AtomicLong clock = new AtomicLong(1_000_000_000);
        Cooldowns cooldowns = new Cooldowns(clock::get);
        UUID first = UUID.randomUUID();
        UUID second = UUID.randomUUID();
        cooldowns.start(first);
        assertEquals(30, cooldowns.secondsLeft(first));
        assertEquals(0, cooldowns.secondsLeft(second));
        clock.addAndGet(29_999_999_999L);
        assertEquals(1, cooldowns.secondsLeft(first));
        cooldowns.start(second);
        clock.incrementAndGet();
        assertEquals(0, cooldowns.secondsLeft(first));
        assertEquals(30, cooldowns.secondsLeft(second));
    }
}
