package io.github.nateriver31.reliquias;

import java.util.HashMap;
import java.util.Map;
import org.bukkit.Chunk;
import org.bukkit.plugin.Plugin;

/** Paper tickets are per plugin, so concurrent operations need reference counts. Main thread only. */
final class ChunkTickets implements AutoCloseable {
    private final Plugin plugin;
    private final Map<Chunk, Integer> held = new HashMap<>();
    ChunkTickets(Plugin plugin) { this.plugin = plugin; }
    void acquire(Chunk chunk) {
        if (!held.containsKey(chunk)) chunk.addPluginChunkTicket(plugin);
        held.merge(chunk, 1, Integer::sum);
    }
    void release(Chunk chunk) {
        Integer count = held.get(chunk);
        if (count == null) return;
        if (count > 1) held.put(chunk, count - 1);
        else {
            held.remove(chunk);
            chunk.removePluginChunkTicket(plugin);
        }
    }
    @Override public void close() {
        held.keySet().forEach(c -> c.removePluginChunkTicket(plugin));
        held.clear();
    }
}
