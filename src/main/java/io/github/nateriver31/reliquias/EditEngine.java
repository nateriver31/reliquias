package io.github.nateriver31.reliquias;

import io.github.nateriver31.reliquias.core.ChunkJournal;
import io.github.nateriver31.reliquias.core.ChunkKey;
import io.github.nateriver31.reliquias.core.ChunkQueue;
import io.github.nateriver31.reliquias.core.RoofEditor;
import io.github.nateriver31.reliquias.core.RoofRules;
import java.util.ArrayDeque;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.logging.Level;
import org.bukkit.Chunk;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;

final class EditEngine implements AutoCloseable {
    private record Active(ChunkQueue.Work work, Chunk chunk, RoofEditor editor) { }

    private final JavaPlugin plugin;
    private final ChunkJournal journal;
    private final Protection protection;
    private final CoreProtectLog audit;
    private final ChunkTickets chunkTickets;
    private final ChunkQueue queue;
    private final ArrayDeque<Active> ready = new ArrayDeque<>();
    private final Set<Chunk> tickets = new HashSet<>();
    private final BukkitTask ticker;
    private final int maxChecks;
    private final int maxActive;
    private final long tickNanos;
    private int activeCount;
    private boolean closed;

    EditEngine(JavaPlugin plugin, ChunkJournal journal, Protection protection, CoreProtectLog audit, ChunkTickets chunkTickets) {
        this.plugin = plugin;
        this.journal = journal;
        this.protection = protection;
        this.audit = audit;
        this.chunkTickets = chunkTickets;
        maxChecks = Math.clamp(plugin.getConfig().getInt("edicao.verificacoes-por-tick", 4096), 128, 32768);
        maxActive = Math.clamp(plugin.getConfig().getInt("edicao.chunks-carregando", 2), 1, 4);
        double millis = plugin.getConfig().getDouble("edicao.milissegundos-por-tick", 2.0);
        tickNanos = (long) (Math.clamp(Double.isFinite(millis) ? millis : 2.0, 0.1, 5.0) * 1_000_000);
        int capacity = Math.clamp(plugin.getConfig().getInt("edicao.maximo-chunks-pendentes", 512), 49, 4096);
        queue = new ChunkQueue(journal::contains, capacity);
        ticker = plugin.getServer().getScheduler().runTaskTimer(plugin, this::tick, 1, 1);
    }

    CompletableFuture<List<ChunkQueue.Result>> prepare(Player actor, List<ChunkKey> keys) {
        if (closed || !journal.healthy()) throw new IllegalStateException("Registro de chunks indisponível; contate a administração.");
        return queue.request(keys, actor.getUniqueId());
    }

    boolean completed(ChunkKey key) { return journal.contains(key); }
    boolean open() { return !closed; }

    private void tick() {
        if (closed) return;
        if (!journal.healthy()) {
            close();
            plugin.getLogger().severe("Edição suspensa: não é seguro continuar sem registro persistente.");
            return;
        }
        long deadline = System.nanoTime() + tickNanos;
        while (activeCount < maxActive && System.nanoTime() < deadline) {
            ChunkQueue.Work work = queue.poll();
            if (work == null) break;
            start(work);
        }
        int remaining = maxChecks;
        while (!ready.isEmpty() && remaining > 0 && System.nanoTime() < deadline) {
            Active active = ready.removeFirst();
            try {
                Player actor = plugin.getServer().getPlayer(active.work().actor());
                if (actor == null || !actor.isOnline() || !actor.getWorld().equals(active.chunk().getWorld())) {
                    finish(active.work(), active.chunk(), ChunkQueue.Result.FAILED);
                    continue;
                }
                remaining -= active.editor().step(Math.min(256, remaining), deadline);
                switch (active.editor().outcome()) {
                    case RUNNING -> ready.addLast(active);
                    case FLOOR_CONFLICT -> finish(active.work(), active.chunk(), ChunkQueue.Result.FLOOR_CONFLICT);
                    case PROTECTED -> finish(active.work(), active.chunk(), ChunkQueue.Result.PROTECTED);
                    case COMPLETE -> journal.record(active.work().key()).whenComplete((ignored, error) -> onMain(() -> {
                        if (closed) return;
                        if (error != null) plugin.getLogger().log(Level.SEVERE, "Falha ao persistir " + active.work().key(), error);
                        finish(active.work(), active.chunk(), error == null ? ChunkQueue.Result.COMPLETE : ChunkQueue.Result.FAILED);
                    }));
                }
            } catch (RuntimeException e) {
                plugin.getLogger().log(Level.SEVERE, "Edição interrompida em " + active.work().key(), e);
                finish(active.work(), active.chunk(), ChunkQueue.Result.FAILED);
            }
        }
    }

    private void start(ChunkQueue.Work work) {
        World world = plugin.getServer().getWorld(work.key().world());
        Player actor = plugin.getServer().getPlayer(work.actor());
        if (world == null || world.getEnvironment() != World.Environment.NETHER || world.getMinHeight() > RoofRules.FLOOR_BOTTOM
                || world.getMaxHeight() <= RoofRules.ROOF_MAX || actor == null || !actor.isOnline() || !actor.getWorld().equals(world)) {
            queue.finish(work, ChunkQueue.Result.FAILED);
            return;
        }
        activeCount++;
        try {
            // Only selected coordinates are requested. Chunk generation and I/O use Paper's async API.
            world.getChunkAtAsync(work.key().x(), work.key().z(), true).whenComplete((chunk, error) -> onMain(() -> {
                if (closed) return;
                if (error != null || chunk == null) {
                    if (error != null) plugin.getLogger().log(Level.WARNING, "Falha ao carregar " + work.key(), error);
                    finish(work, null, ChunkQueue.Result.FAILED);
                    return;
                }
                try {
                    chunkTickets.acquire(chunk);
                    tickets.add(chunk);
                    ready.addLast(new Active(work, chunk, new RoofEditor(new PaperBlocks(chunk, actor, protection, audit))));
                } catch (RuntimeException e) {
                    plugin.getLogger().log(Level.WARNING, "Falha ao manter chunk carregado", e);
                    finish(work, chunk, ChunkQueue.Result.FAILED);
                }
            }));
        } catch (RuntimeException e) {
            plugin.getLogger().log(Level.WARNING, "Falha ao solicitar chunk", e);
            finish(work, null, ChunkQueue.Result.FAILED);
        }
    }

    void onMain(Runnable action) {
        if (!plugin.isEnabled()) return;
        if (plugin.getServer().isPrimaryThread()) action.run();
        else {
            try {
                plugin.getServer().getScheduler().runTask(plugin, () -> { if (plugin.isEnabled()) action.run(); });
            } catch (org.bukkit.plugin.IllegalPluginAccessException ignored) {
                // Shutdown cancels the queue and releases every ticket on the server thread.
            }
        }
    }

    private void finish(ChunkQueue.Work work, Chunk chunk, ChunkQueue.Result result) {
        try {
            if (chunk != null && tickets.remove(chunk)) chunkTickets.release(chunk);
        } finally {
            activeCount--;
            queue.finish(work, result);
        }
    }

    @Override public void close() {
        if (closed) return;
        closed = true;
        ticker.cancel();
        ready.clear();
        tickets.forEach(chunkTickets::release);
        tickets.clear();
        queue.cancelAll();
    }
}
