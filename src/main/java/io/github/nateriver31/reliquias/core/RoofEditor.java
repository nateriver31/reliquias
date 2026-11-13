package io.github.nateriver31.reliquias.core;

/** Incremental transformation; the Paper adapter is called exclusively on the server thread. */
public final class RoofEditor {
    public enum Kind { AIR, BEDROCK, OTHER }
    public enum Outcome { RUNNING, COMPLETE, FLOOR_CONFLICT, PROTECTED }

    public interface Blocks {
        Kind get(int x, int y, int z);
        /** Must recheck permissions and the current block before changing it. */
        boolean replace(int x, int y, int z, Kind expected, Kind replacement);
    }

    private final Blocks blocks;
    private int cursor;
    private boolean preflight = true;
    private Outcome outcome = Outcome.RUNNING;

    public RoofEditor(Blocks blocks) {
        this.blocks = blocks;
    }

    public Outcome outcome() { return outcome; }

    public int step(int maxChecks, long deadlineNanos) {
        if (maxChecks < 1) throw new IllegalArgumentException("Budget must be positive");
        int checks = 0;
        while (outcome == Outcome.RUNNING && checks < maxChecks
                && (checks == 0 || System.nanoTime() < deadlineNanos)) {
            int x = cursor & 15;
            int z = (cursor >> 4) & 15;
            int y = RoofRules.FLOOR_BOTTOM + (cursor >> 8);
            Kind old = blocks.get(x, y, z);
            checks++;
            if (preflight) {
                // Do not remove any roof in a chunk whose two floor layers cannot be built.
                if (old == Kind.OTHER) {
                    outcome = Outcome.FLOOR_CONFLICT;
                    break;
                }
            } else if (y <= RoofRules.FLOOR_TOP) {
                if (old == Kind.OTHER) {
                    outcome = Outcome.FLOOR_CONFLICT;
                    break;
                }
                if (old == Kind.AIR && !blocks.replace(x, y, z, Kind.AIR, Kind.BEDROCK)) {
                    outcome = Outcome.PROTECTED;
                    break;
                }
            } else if (old == Kind.BEDROCK && !blocks.replace(x, y, z, Kind.BEDROCK, Kind.AIR)) {
                outcome = Outcome.PROTECTED;
                break;
            }
            cursor++;
            if (preflight && cursor == 512) {
                preflight = false;
                cursor = 0;
            } else if (!preflight && cursor == 37 * 256) {
                outcome = Outcome.COMPLETE;
            }
        }
        return checks;
    }
}
