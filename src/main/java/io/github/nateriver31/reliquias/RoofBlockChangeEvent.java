package io.github.nateriver31.reliquias;

import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.Cancellable;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;
import org.jetbrains.annotations.NotNull;

/** Veto hook for protection plugins. Fired synchronously BEFORE each proposed change. */
public final class RoofBlockChangeEvent extends Event implements Cancellable {
    private static final HandlerList HANDLERS = new HandlerList();
    private final Player player;
    private final Block block;
    private final Material replacement;
    private boolean cancelled;

    public RoofBlockChangeEvent(Player player, Block block, Material replacement) {
        this.player = player;
        this.block = block;
        this.replacement = replacement;
    }

    public Player getPlayer() { return player; }
    public Block getBlock() { return block; }
    public Material getReplacement() { return replacement; }
    @Override public boolean isCancelled() { return cancelled; }
    @Override public void setCancelled(boolean value) { cancelled = value; }
    @Override public @NotNull HandlerList getHandlers() { return HANDLERS; }
    public static HandlerList getHandlerList() { return HANDLERS; }
}
