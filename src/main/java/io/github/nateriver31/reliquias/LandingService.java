package io.github.nateriver31.reliquias;

import io.github.nateriver31.reliquias.core.ChunkKey;
import io.github.nateriver31.reliquias.core.RoofRules;
import java.util.ArrayList;
import java.util.ArrayDeque;
import java.util.HashSet;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import org.bukkit.Chunk;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.Chest;
import org.bukkit.block.BlockFace;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.util.BoundingBox;

final class LandingService {
    private static final BlockFace[] SIDES = {BlockFace.NORTH, BlockFace.SOUTH, BlockFace.EAST, BlockFace.WEST};
    private final EditEngine engine;
    private final ChunkTickets tickets;
    private final Protection protection;
    private final CoreProtectLog audit;

    LandingService(EditEngine engine, ChunkTickets tickets, Protection protection, CoreProtectLog audit) {
        this.engine = engine;
        this.tickets = tickets;
        this.protection = protection;
        this.audit = audit;
    }

    final class Plan implements AutoCloseable {
        private final World world;
        private final List<Chunk> loaded = new ArrayList<>();
        private boolean closed;
        Plan(World world) { this.world = world; }
        boolean available(int x, int z) {
            return !closed && world.isChunkLoaded(x >> 4, z >> 4)
                    && engine.completed(new ChunkKey(world.getUID(), x >> 4, z >> 4));
        }
        @Override public void close() {
            if (closed) return;
            closed = true;
            loaded.forEach(tickets::release);
            loaded.clear();
        }
    }

    CompletableFuture<Plan> load(Location origin) {
        Plan plan = new Plan(origin.getWorld());
        var result = new CompletableFuture<Plan>();
        loadNext(plan, RoofRules.region(origin.getWorld().getUID(), origin.getX(), origin.getZ(), 1), 0, result);
        return result;
    }

    private void loadNext(Plan plan, List<ChunkKey> keys, int index, CompletableFuture<Plan> result) {
        if (!engine.open()) {
            plan.close();
            result.completeExceptionally(new IllegalStateException("Edição suspensa"));
            return;
        }
        if (index == keys.size()) {
            result.complete(plan);
            return;
        }
        ChunkKey key = keys.get(index);
        if (!engine.completed(key)) {
            plan.close();
            result.completeExceptionally(new IllegalStateException("Região de chegada incompleta"));
            return;
        }
        // Reload, but never generate, only already-prepared chunks in the original 7×7 region.
        try {
            plan.world.getChunkAtAsync(key.x(), key.z(), false).whenComplete((chunk, error) -> engine.onMain(() -> {
                if (!engine.open() || error != null || chunk == null) {
                    plan.close();
                    result.completeExceptionally(new IllegalStateException("Não foi possível carregar a chegada", error));
                } else {
                    try {
                        tickets.acquire(chunk);
                        plan.loaded.add(chunk);
                        loadNext(plan, keys, index + 1, result);
                    } catch (RuntimeException e) {
                        plan.close();
                        result.completeExceptionally(e);
                    }
                }
            }));
        } catch (RuntimeException e) {
            plan.close();
            result.completeExceptionally(e);
        }
    }

    Location destination(Plan plan, Player player, Location origin) {
        double half = Math.max(0.6, player.getWidth()) / 2;
        double height = Math.max(1.8, player.getHeight());
        for (int y = RoofRules.ROOF_MIN; y + height < plan.world.getMaxHeight(); y++) {
            Location candidate = new Location(plan.world, origin.getX(), y, origin.getZ(), origin.getYaw(), origin.getPitch());
            if (!plan.world.getWorldBorder().isInside(candidate)) return null;
            if (clearStandingSpace(plan, candidate, half, height)) return candidate;
        }
        return null;
    }

    private boolean clearStandingSpace(Plan plan, Location at, double half, double height) {
        int minX = (int) Math.floor(at.getX() - half);
        int maxX = (int) Math.floor(at.getX() + half - 0.00001);
        int minZ = (int) Math.floor(at.getZ() - half);
        int maxZ = (int) Math.floor(at.getZ() + half - 0.00001);
        // Do not read/generate outside the leased, prepared neighborhood for unusually scaled players.
        if (maxX - minX > 16 || maxZ - minZ > 16 || height > 32) return false;
        for (int x = minX; x <= maxX; x++) for (int z = minZ; z <= maxZ; z++) {
            if (!plan.available(x, z) || !safeSupport(plan.world.getBlockAt(x, at.getBlockY() - 1, z))) return false;
            for (int y = at.getBlockY(); y < at.getY() + height; y++) {
                if (!plan.world.getBlockAt(x, y, z).getType().isAir()) return false;
            }
        }
        return true;
    }

    private static boolean safeSupport(Block block) {
        // Conservative: full, non-damaging solid blocks; never fluids, cactus or partial shapes.
        return block.getType().isOccluding() && block.getType() != Material.MAGMA_BLOCK;
    }

    Block chestSpot(Plan plan, Player player, Location near) {
        // Search only walkable, connected cells. A chest across a wall is not "accessible".
        record Cell(int x, int z) { }
        Cell start = new Cell(near.getBlockX(), near.getBlockZ());
        var pending = new ArrayDeque<Cell>();
        var visited = new HashSet<Cell>();
        pending.add(start);
        visited.add(start);
        int y = near.getBlockY();
        double half = Math.max(0.6, player.getWidth()) / 2;
        double height = Math.max(1.8, player.getHeight());
        while (!pending.isEmpty()) {
            Cell cell = pending.removeFirst();
            int x = cell.x();
            int z = cell.z();
            Block block = plan.world.getBlockAt(x, y, z);
            BoundingBox box = new BoundingBox(x, y, z, x + 1, y + 1, z + 1);
            boolean overlapsArrival = box.overlaps(new BoundingBox(near.getX() - half, y, near.getZ() - half,
                    near.getX() + half, y + height, near.getZ() + half));
            boolean overlapsPlayer = player.getWorld().equals(plan.world) && player.getBoundingBox().overlaps(box);
            boolean adjacentChest = false;
            for (BlockFace side : SIDES) {
                int nx = x + side.getModX();
                int nz = z + side.getModZ();
                if (!plan.available(nx, nz)) { adjacentChest = true; continue; }
                Material type = plan.world.getBlockAt(nx, y, nz).getType();
                if (type == Material.CHEST || type == Material.TRAPPED_CHEST) adjacentChest = true;
                Cell neighbor = new Cell(nx, nz);
                if (Math.abs(nx - start.x()) > 4 || Math.abs(nz - start.z()) > 4 || !visited.add(neighbor)) continue;
                Location center = new Location(plan.world, nx + 0.5, y, nz + 0.5);
                if (plan.world.getWorldBorder().isInside(center) && clearStandingSpace(plan, center, half, height)) pending.addLast(neighbor);
            }
            if (!cell.equals(start) && !overlapsArrival && !overlapsPlayer && !adjacentChest
                    && protection.allows(player, block, Material.CHEST) && block.getType().isAir()) return block;
        }
        return null;
    }

    boolean placeChest(Plan plan, Player player) {
        Block block = chestSpot(plan, player, player.getLocation());
        if (block == null) return false;
        audit.change(player.getName(), block, Material.CHEST.createBlockData());
        if (!block.getType().isAir()) return false;
        block.setType(Material.CHEST, false);
        if (!(block.getState() instanceof Chest chest)) return false;
        chest.getBlockInventory().setItem(0, new ItemStack(Material.OBSIDIAN, 10));
        chest.getBlockInventory().setItem(1, new ItemStack(Material.FLINT_AND_STEEL));
        return true;
    }
}
