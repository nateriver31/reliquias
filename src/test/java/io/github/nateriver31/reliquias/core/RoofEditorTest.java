package io.github.nateriver31.reliquias.core;

import static org.junit.jupiter.api.Assertions.*;
import static io.github.nateriver31.reliquias.core.RoofEditor.Kind.*;
import java.util.HashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;

class RoofEditorTest {
    static final class World implements RoofEditor.Blocks {
        final Map<String, RoofEditor.Kind> cells = new HashMap<>();
        int edits;
        int reads;
        int rejectY = -1;
        String key(int x, int y, int z) { return x + "," + y + "," + z; }
        void set(int x, int y, int z, RoofEditor.Kind kind) { cells.put(key(x, y, z), kind); }
        @Override public RoofEditor.Kind get(int x, int y, int z) {
            assertTrue(x >= 0 && x <= 15 && z >= 0 && z <= 15, "Must stay in the selected chunk");
            assertTrue(y >= 219 && y <= 255, "Must never read/write below 219 or above 255");
            reads++;
            return cells.getOrDefault(key(x, y, z), AIR);
        }
        @Override public boolean replace(int x, int y, int z, RoofEditor.Kind expected, RoofEditor.Kind replacement) {
            if (y == rejectY) return false;
            assertEquals(expected, get(x, y, z));
            if (y >= 221) {
                assertEquals(BEDROCK, expected, "Existing buildings above the floor must survive");
                assertFloor();
            }
            set(x, y, z, replacement);
            edits++;
            return true;
        }
        void assertFloor() {
            for (int x = 0; x < 16; x++) for (int z = 0; z < 16; z++) {
                assertEquals(BEDROCK, cells.get(key(x, 219, z)));
                assertEquals(BEDROCK, cells.get(key(x, 220, z)));
            }
        }
    }

    static void finish(RoofEditor editor) {
        int iterations = 0;
        while (editor.outcome() == RoofEditor.Outcome.RUNNING) {
            assertTrue(editor.step(73, Long.MAX_VALUE) <= 73);
            assertTrue(++iterations < 200, "Incremental edit must eventually finish");
        }
    }

    @Test void buildsBothLayersBeforeClearingAndPreservesEveryOtherRoofMaterial() {
        World world = new World();
        for (int x = 0; x < 16; x++) for (int z = 0; z < 16; z++) for (int y = 221; y <= 255; y++) {
            world.set(x, y, z, ((x + y + z) % 3 == 0) ? OTHER : BEDROCK);
        }
        Map<String, RoofEditor.Kind> original = Map.copyOf(world.cells);
        RoofEditor editor = new RoofEditor(world);
        finish(editor);
        assertEquals(RoofEditor.Outcome.COMPLETE, editor.outcome());
        world.assertFloor();
        original.forEach((key, kind) -> assertEquals(kind == BEDROCK ? AIR : OTHER, world.cells.get(key), key));
    }

    @Test void evenAnEntirelyEmptyChunkGetsBothMandatoryFloorLayers() {
        World world = new World();
        RoofEditor editor = new RoofEditor(world);
        finish(editor);
        world.assertFloor();
        assertEquals(512, world.edits);
    }

    @Test void floorConflictPreservesConstructionAndDoesNotRemoveAnyRoof() {
        World world = new World();
        world.set(15, 220, 15, OTHER);
        world.set(1, 255, 1, BEDROCK);
        RoofEditor editor = new RoofEditor(world);
        finish(editor);
        assertEquals(RoofEditor.Outcome.FLOOR_CONFLICT, editor.outcome());
        assertEquals(0, world.edits);
        assertEquals(BEDROCK, world.cells.get(world.key(1, 255, 1)));
    }

    @Test void constructionAddedBetweenTicksIsRecheckedAndNeverOverwritten() {
        World world = new World();
        RoofEditor editor = new RoofEditor(world);
        assertEquals(512, editor.step(512, Long.MAX_VALUE));
        world.set(0, 219, 0, OTHER);
        finish(editor);
        assertEquals(RoofEditor.Outcome.FLOOR_CONFLICT, editor.outcome());
        assertEquals(0, world.edits);
    }

    @Test void deniedFloorPlacementDoesNotReleaseTheOldRoof() {
        World world = new World();
        world.rejectY = 220;
        world.set(0, 255, 0, BEDROCK);
        RoofEditor editor = new RoofEditor(world);
        finish(editor);
        assertEquals(RoofEditor.Outcome.PROTECTED, editor.outcome());
        assertEquals(BEDROCK, world.cells.get(world.key(0, 255, 0)));
    }

    @Test void deniedBedrockRemovalIsIncompleteEvenWhenTheFloorIsFinished() {
        World world = new World();
        world.rejectY = 255;
        world.set(0, 255, 0, BEDROCK);
        RoofEditor editor = new RoofEditor(world);
        finish(editor);
        world.assertFloor();
        assertEquals(RoofEditor.Outcome.PROTECTED, editor.outcome());
    }

    @Test void expiresCooperativelyAndNeverTreatsPartialWorkAsComplete() {
        RoofEditor editor = new RoofEditor(new World());
        assertEquals(1, editor.step(4096, System.nanoTime() - 1));
        assertEquals(RoofEditor.Outcome.RUNNING, editor.outcome());
        assertThrows(IllegalArgumentException.class, () -> editor.step(0, Long.MAX_VALUE));
    }
}
