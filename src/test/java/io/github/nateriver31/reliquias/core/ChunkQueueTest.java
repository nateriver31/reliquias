package io.github.nateriver31.reliquias.core;

import static org.junit.jupiter.api.Assertions.*;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class ChunkQueueTest {
    final UUID world = UUID.randomUUID();
    final UUID actor = UUID.randomUUID();

    @Test void overlappingSevenBySevenRequestsShareExactlyOneWriterPerChunk() {
        ChunkQueue queue = new ChunkQueue(k -> false, 100);
        var first = queue.request(RoofRules.region(world, 0, 0, 3), actor);
        var second = queue.request(RoofRules.region(world, 16, 0, 3), UUID.randomUUID());
        Set<ChunkKey> claimed = new HashSet<>();
        ChunkQueue.Work work;
        while ((work = queue.poll()) != null) {
            assertTrue(claimed.add(work.key()), "Overlapping operations must share a single job");
            assertFalse(first.isDone() && second.isDone());
            queue.finish(work, ChunkQueue.Result.COMPLETE);
        }
        assertEquals(56, claimed.size()); // 49 + one new row of 7
        assertEquals(49, first.join().size());
        assertEquals(49, second.join().size());
        assertTrue(second.join().stream().allMatch(r -> r == ChunkQueue.Result.COMPLETE));
    }

    @Test void completedChunksAreNeverScheduledAgainEvenWithLaterBuildings() {
        Set<ChunkKey> saved = new HashSet<>(RoofRules.region(world, 0, 0, 3));
        ChunkQueue queue = new ChunkQueue(saved::contains, 49);
        var result = queue.request(RoofRules.region(world, 0, 0, 2), actor);
        assertTrue(result.isDone());
        assertEquals(25, result.join().size());
        assertNull(queue.poll());
    }

    @Test void unsuccessfulChunksCanBeRetriedAndAreNeverMistakenForComplete() {
        ChunkQueue queue = new ChunkQueue(k -> false, 49);
        var keys = List.of(new ChunkKey(world, -1, 2));
        var first = queue.request(keys, actor);
        var work = queue.poll();
        queue.finish(work, ChunkQueue.Result.FLOOR_CONFLICT);
        assertEquals(List.of(ChunkQueue.Result.FLOOR_CONFLICT), first.join());
        var retry = queue.request(keys, actor);
        assertFalse(retry.isDone());
        assertNotSame(work, queue.poll());
    }

    @Test void backpressureRejectsTheEntireRequestWithoutOrphanedJobs() {
        ChunkQueue queue = new ChunkQueue(k -> false, 49);
        var result = queue.request(RoofRules.region(world, 0, 0, 3), actor);
        assertThrows(IllegalStateException.class, () -> queue.request(RoofRules.region(world, 16, 0, 3), actor));
        int count = 0;
        while (queue.poll() != null) count++;
        assertEquals(49, count);
        queue.cancelAll();
        assertEquals(49, result.join().stream().filter(r -> r == ChunkQueue.Result.FAILED).count());
    }

    @Test void regionsAreCenteredAtActivationWithCorrectNegativeChunkCoordinates() {
        var flower = RoofRules.region(world, -0.01, -16.01, 3);
        assertEquals(49, flower.size());
        assertEquals(new ChunkKey(world, -1, -2), flower.getFirst());
        assertEquals(7, flower.stream().map(ChunkKey::x).distinct().count());
        assertEquals(7, flower.stream().map(ChunkKey::z).distinct().count());
        assertTrue(flower.contains(new ChunkKey(world, -4, -5)));
        assertTrue(flower.contains(new ChunkKey(world, 2, 1)));
        assertEquals(25, RoofRules.region(world, -0.01, -16.01, 2).size());
    }

    @Test void sameCoordinatesInDifferentWorldsHaveSeparateLocksAndRecords() {
        ChunkQueue queue = new ChunkQueue(k -> false, 49);
        queue.request(List.of(new ChunkKey(world, 0, 0)), actor);
        queue.request(List.of(new ChunkKey(UUID.randomUUID(), 0, 0)), actor);
        assertNotEquals(queue.poll().key(), queue.poll().key());
    }
}
