package io.github.nateriver31.reliquias.core;

import static org.junit.jupiter.api.Assertions.*;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class ChunkJournalTest {
    @TempDir Path directory;

    @Test void durableCompletionSurvivesReopenAndPreservesWorldAndSignedCoordinates() throws Exception {
        Path file = directory.resolve("chunks.log");
        ChunkKey key = new ChunkKey(UUID.randomUUID(), -131, 4321);
        try (ChunkJournal journal = new ChunkJournal(file)) {
            assertFalse(journal.contains(key));
            journal.record(key).get();
            assertTrue(journal.contains(key));
            journal.record(key).get();
        }
        assertEquals(2, Files.readAllLines(file).size(), "Header plus one record, without duplicates");
        try (ChunkJournal restored = new ChunkJournal(file)) {
            assertTrue(restored.contains(key));
            assertFalse(restored.contains(new ChunkKey(UUID.randomUUID(), key.x(), key.z())));
        }
    }

    @Test void interruptedFinalAppendIsDiscardedWithoutLosingEarlierCompletions() throws Exception {
        Path file = directory.resolve("chunks.log");
        ChunkKey key = new ChunkKey(UUID.randomUUID(), 1, 2);
        try (ChunkJournal journal = new ChunkJournal(file)) { journal.record(key).get(); }
        long validSize = Files.size(file);
        Files.writeString(file, "incomplete-next-record", StandardOpenOption.APPEND);
        try (ChunkJournal restored = new ChunkJournal(file)) {
            assertTrue(restored.contains(key));
            assertEquals(validSize, Files.size(file));
            restored.record(new ChunkKey(key.world(), 3, 4)).get();
        }
        try (ChunkJournal restored = new ChunkJournal(file)) { assertEquals(2, restored.size()); }
    }

    @Test void corruptCompleteRecordFailsClosedInsteadOfReprocessingPlayerBuildings() throws Exception {
        Path file = directory.resolve("chunks.log");
        Files.writeString(file, "reliquias-roof-v1\ninvalid,1,2,badcrc\n");
        byte[] original = Files.readAllBytes(file);
        assertThrows(IOException.class, () -> new ChunkJournal(file));
        assertArrayEquals(original, Files.readAllBytes(file));
    }

    @Test void anUnknownFormatIsNotSilentlyReplaced() throws Exception {
        Path file = directory.resolve("chunks.log");
        Files.writeString(file, "future-format\n");
        assertThrows(IOException.class, () -> new ChunkJournal(file));
        assertEquals("future-format\n", Files.readString(file));
    }

    @Test void refusesConcurrentWritersAndDrainsPendingWritesOnClose() throws Exception {
        Path file = directory.resolve("chunks.log");
        ChunkKey key = new ChunkKey(UUID.randomUUID(), 0, 0);
        try (ChunkJournal journal = new ChunkJournal(file)) {
            assertThrows(Exception.class, () -> new ChunkJournal(file));
            journal.record(key);
        }
        try (ChunkJournal restored = new ChunkJournal(file)) { assertTrue(restored.contains(key)); }
    }
}
