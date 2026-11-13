package io.github.nateriver31.reliquias.core;

import java.io.BufferedReader;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.channels.FileLock;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.zip.CRC32;

/** Append-only, checksummed completion log. No world or Bukkit calls run on the I/O thread. */
public final class ChunkJournal implements AutoCloseable {
    private static final String HEADER = "reliquias-roof-v1";
    private final Set<ChunkKey> completed = ConcurrentHashMap.newKeySet();
    private final FileChannel channel;
    private final FileLock lock;
    private final ExecutorService writer = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "reliquias-chunk-journal");
        t.setDaemon(true);
        return t;
    });
    private volatile Throwable failure;

    public ChunkJournal(Path path) throws IOException {
        Files.createDirectories(path.toAbsolutePath().getParent());
        channel = FileChannel.open(path, StandardOpenOption.CREATE, StandardOpenOption.READ, StandardOpenOption.WRITE);
        FileLock acquired = null;
        try {
            acquired = channel.tryLock();
            if (acquired == null) throw new IOException("Registro já está em uso: " + path);
            if (channel.size() == 0) {
                write(HEADER + "\n");
                channel.force(true);
            }
            try (BufferedReader reader = Files.newBufferedReader(path, StandardCharsets.UTF_8)) {
                if (!HEADER.equals(reader.readLine())) throw new IOException("Cabeçalho inválido no registro");
            }
            trimInterruptedAppend();
            try (BufferedReader reader = Files.newBufferedReader(path, StandardCharsets.UTF_8)) {
                if (!HEADER.equals(reader.readLine())) throw new IOException("Cabeçalho inválido no registro");
                String line;
                int number = 1;
                while ((line = reader.readLine()) != null) {
                    number++;
                    int split = line.lastIndexOf(',');
                    if (split < 0 || !checksum(line.substring(0, split)).equals(line.substring(split + 1))) {
                        throw new IOException("Registro corrompido na linha " + number + "; restaure o backup");
                    }
                    String[] fields = line.substring(0, split).split(",", -1);
                    try {
                        if (fields.length != 3) throw new IllegalArgumentException();
                        completed.add(new ChunkKey(UUID.fromString(fields[0]), Integer.parseInt(fields[1]), Integer.parseInt(fields[2])));
                    } catch (IllegalArgumentException e) {
                        throw new IOException("Chunk inválido na linha " + number, e);
                    }
                }
            }
            channel.position(channel.size());
            lock = acquired;
        } catch (Throwable e) {
            if (acquired != null) acquired.release();
            channel.close();
            writer.shutdownNow();
            throw e;
        }
    }

    private void trimInterruptedAppend() throws IOException {
        long size = channel.size();
        ByteBuffer b = ByteBuffer.allocate(1);
        long end = size;
        while (end > 0) {
            b.clear();
            if (channel.read(b, end - 1) != 1) throw new IOException("Não foi possível ler o registro");
            if (b.array()[0] == '\n') break;
            end--;
        }
        if (end == 0) throw new IOException("Registro sem cabeçalho completo");
        if (end < size) {
            channel.truncate(end);
            channel.force(true);
        }
    }

    private static String checksum(String payload) {
        CRC32 crc = new CRC32();
        crc.update(payload.getBytes(StandardCharsets.UTF_8));
        return Long.toHexString(crc.getValue());
    }

    private void write(String value) throws IOException {
        ByteBuffer bytes = StandardCharsets.UTF_8.encode(value);
        while (bytes.hasRemaining()) channel.write(bytes);
    }

    public boolean contains(ChunkKey key) { return completed.contains(key); }
    public boolean healthy() { return failure == null && !writer.isShutdown(); }
    public int size() { return completed.size(); }

    public CompletableFuture<Void> record(ChunkKey key) {
        if (!healthy()) return CompletableFuture.failedFuture(new IOException("Registro indisponível", failure));
        return CompletableFuture.runAsync(() -> {
            if (failure != null) throw new IllegalStateException("Registro indisponível", failure);
            if (completed.contains(key)) return;
            try {
                String payload = key.world() + "," + key.x() + "," + key.z();
                write(payload + "," + checksum(payload) + "\n");
                channel.force(true);
                completed.add(key); // visible only after the complete transformation AND durable append
            } catch (IOException e) {
                failure = e;
                throw new IllegalStateException("Falha ao persistir chunk", e);
            }
        }, writer);
    }

    @Override
    public void close() throws IOException {
        writer.shutdown();
        try {
            if (!writer.awaitTermination(10, TimeUnit.SECONDS)) {
                writer.shutdownNow();
                throw new IOException("Timeout ao salvar o registro");
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IOException("Salvamento interrompido", e);
        } finally {
            lock.release();
            channel.close();
        }
    }
}
