package io.github.nateriver31.reliquias.core;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.function.Predicate;

/** Server-thread confined: shared futures are also the per-chunk exclusive locks. */
public final class ChunkQueue {
    public enum Result { COMPLETE, FLOOR_CONFLICT, PROTECTED, FAILED }
    public record Work(ChunkKey key, UUID actor, CompletableFuture<Result> result) { }

    private final Predicate<ChunkKey> completed;
    private final int capacity;
    private final Map<ChunkKey, Work> inFlight = new HashMap<>();
    private final ArrayDeque<Work> pending = new ArrayDeque<>();

    public ChunkQueue(Predicate<ChunkKey> completed, int capacity) {
        this.completed = completed;
        this.capacity = capacity;
    }

    public CompletableFuture<List<Result>> request(List<ChunkKey> keys, UUID actor) {
        List<ChunkKey> unique = keys.stream().distinct().toList();
        long added = unique.stream().filter(k -> !completed.test(k) && !inFlight.containsKey(k)).count();
        if (inFlight.size() + added > capacity) throw new IllegalStateException("Fila de edição cheia. Tente novamente em instantes.");
        var futures = new ArrayList<CompletableFuture<Result>>();
        for (ChunkKey key : unique) {
            if (completed.test(key)) {
                futures.add(CompletableFuture.completedFuture(Result.COMPLETE));
                continue;
            }
            Work work = inFlight.computeIfAbsent(key, k -> {
                Work created = new Work(k, actor, new CompletableFuture<>());
                pending.addLast(created);
                return created;
            });
            futures.add(work.result());
        }
        return CompletableFuture.allOf(futures.toArray(CompletableFuture[]::new))
                .thenApply(ignored -> futures.stream().map(CompletableFuture::join).toList());
    }

    public Work poll() { return pending.pollFirst(); }

    public void finish(Work work, Result result) {
        if (inFlight.remove(work.key(), work)) work.result().complete(result);
    }

    public void cancelAll() {
        var work = List.copyOf(inFlight.values());
        inFlight.clear();
        pending.clear();
        work.forEach(w -> w.result().complete(Result.FAILED));
    }
}
