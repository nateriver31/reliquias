package io.github.nateriver31.reliquias;

import io.github.nateriver31.reliquias.core.RoofEditor;
import org.bukkit.Chunk;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;

final class PaperBlocks implements RoofEditor.Blocks {
    private final Chunk chunk;
    private final Player actor;
    private final Protection protection;
    private final CoreProtectLog audit;

    PaperBlocks(Chunk chunk, Player actor, Protection protection, CoreProtectLog audit) {
        this.chunk = chunk;
        this.actor = actor;
        this.protection = protection;
        this.audit = audit;
    }

    static RoofEditor.Kind kind(Material type) {
        return type.isAir() ? RoofEditor.Kind.AIR : type == Material.BEDROCK ? RoofEditor.Kind.BEDROCK : RoofEditor.Kind.OTHER;
    }

    @Override public RoofEditor.Kind get(int x, int y, int z) {
        return kind(chunk.getBlock(x, y, z).getType());
    }

    @Override public boolean replace(int x, int y, int z, RoofEditor.Kind expected, RoofEditor.Kind replacement) {
        Block block = chunk.getBlock(x, y, z);
        Material type = replacement == RoofEditor.Kind.AIR ? Material.AIR : Material.BEDROCK;
        if (kind(block.getType()) != expected || !protection.allows(actor, block, type)) return false;
        // A veto listener can change the block. Never overwrite its change.
        if (kind(block.getType()) != expected) return false;
        var data = type.createBlockData();
        audit.change(actor.getName(), block, data);
        block.setBlockData(data, false);
        return true;
    }
}
