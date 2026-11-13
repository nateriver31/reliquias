package io.github.nateriver31.reliquias;

import io.github.nateriver31.reliquias.core.ChunkQueue;
import io.github.nateriver31.reliquias.core.Cooldowns;
import io.github.nateriver31.reliquias.core.RoofRules;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.logging.Level;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.event.Event;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerChangedWorldEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerTeleportEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;

final class ScrollListener implements Listener, AutoCloseable {
    private final JavaPlugin plugin;
    private final ScrollItems items;
    private final EditEngine engine;
    private final LandingService landing;
    private final Cooldowns cooldowns = new Cooldowns();
    private final Map<UUID, Pending> ascents = new HashMap<>();
    private final Map<UUID, Integer> clicks = new HashMap<>();

    private static final class Pending {
        final Location origin;
        BukkitTask timer;
        LandingService.Plan plan;
        int count = 3;
        boolean regionReady;
        boolean countdownDone;
        boolean arriving;
        Pending(Location origin) { this.origin = origin; }
    }

    ScrollListener(JavaPlugin plugin, ScrollItems items, EditEngine engine, LandingService landing) {
        this.plugin = plugin;
        this.items = items;
        this.engine = engine;
        this.landing = landing;
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void interact(PlayerInteractEvent event) {
        if (event.getAction() != Action.RIGHT_CLICK_AIR && event.getAction() != Action.RIGHT_CLICK_BLOCK) return;
        if (event.getHand() == null || event.useItemInHand() == Event.Result.DENY) return;
        if (event.getAction() == Action.RIGHT_CLICK_BLOCK && event.useInteractedBlock() == Event.Result.DENY) return;
        ScrollItems.Scroll scroll = items.identify(event.getItem());
        if (scroll == null) return;
        event.setCancelled(true);
        Player player = event.getPlayer();
        int tick = plugin.getServer().getCurrentTick();
        Integer previous = clicks.put(player.getUniqueId(), tick);
        if (previous != null && previous == tick) return; // main/offhand packets must not double-activate
        if (player.isDead()) return;
        if (!player.hasPermission(scroll == ScrollItems.Scroll.FLOWER ? "reliquias.flor" : "reliquias.creeper")) {
            error(player, "Você não tem permissão para utilizar este pergaminho.");
            return;
        }
        Location origin = player.getLocation().clone(); // X/Z and chunk are captured once, before any waiting
        if (!validWorld(origin.getWorld())) {
            error(player, "Este pergaminho só pode ser utilizado no Nether permitido.");
            return;
        }
        if (origin.getWorld().getMinHeight() > RoofRules.FLOOR_BOTTOM || origin.getWorld().getMaxHeight() <= RoofRules.ROOF_MAX) {
            error(player, "Este mundo não suporta a edição fixa entre Y=219 e Y=255.");
            return;
        }
        if (scroll == ScrollItems.Scroll.FLOWER) flower(player, event.getHand(), origin);
        else expand(player, origin);
    }

    private boolean validWorld(World world) {
        List<String> allowed = plugin.getConfig().getStringList("mundos-permitidos");
        return world.getEnvironment() == World.Environment.NETHER && (allowed.isEmpty() || allowed.contains(world.getName()));
    }

    private void flower(Player player, EquipmentSlot hand, Location origin) {
        UUID id = player.getUniqueId();
        if (ascents.containsKey(id)) {
            error(player, "Seu acesso ao teto já está em preparação.");
            return;
        }
        ItemStack current = player.getInventory().getItem(hand);
        if (items.identify(current) != ScrollItems.Scroll.FLOWER) return;
        // All game modes consume exactly one, immediately. No recovery/refund path exists.
        if (current.getAmount() == 1) player.getInventory().setItem(hand, null);
        else {
            ItemStack rest = current.clone();
            rest.setAmount(current.getAmount() - 1);
            player.getInventory().setItem(hand, rest);
        }
        Pending pending = new Pending(origin);
        ascents.put(id, pending);
        action(player, "3", NamedTextColor.RED);
        pending.timer = plugin.getServer().getScheduler().runTaskTimer(plugin, () -> {
            if (!active(player, pending)) { cancel(id, pending); return; }
            pending.count--;
            if (pending.count == 2) action(player, "2", NamedTextColor.YELLOW);
            else if (pending.count == 1) action(player, "1", NamedTextColor.GREEN);
            else {
                pending.countdownDone = true;
                pending.timer.cancel();
                action(player, "Aguardando a preparação do teto…", NamedTextColor.GOLD);
                arrive(player, pending);
            }
        }, 20, 20);
        try {
            engine.prepare(player, RoofRules.region(origin.getWorld().getUID(), origin.getX(), origin.getZ(), 3))
                    .whenComplete((results, error) -> engine.onMain(() -> {
                        if (!active(player, pending)) { cancel(id, pending); return; }
                        if (error != null || results.stream().anyMatch(r -> r != ChunkQueue.Result.COMPLETE)) {
                            fail(player, "Não foi possível preparar toda a região: há conflitos, proteção ou erro. O pergaminho já foi consumido.");
                            return;
                        }
                        pending.regionReady = true;
                        arrive(player, pending);
                    }));
        } catch (RuntimeException e) {
            fail(player, e.getMessage() + " O pergaminho já foi consumido.");
        }
    }

    private boolean active(Player player, Pending pending) {
        return ascents.get(player.getUniqueId()) == pending && player.isOnline() && !player.isDead()
                && player.getWorld().equals(pending.origin.getWorld());
    }

    private void arrive(Player player, Pending pending) {
        if (!pending.regionReady || !pending.countdownDone || pending.arriving || !active(player, pending)) return;
        pending.arriving = true;
        landing.load(pending.origin).whenComplete((plan, error) -> engine.onMain(() -> {
            if (!active(player, pending)) {
                if (plan != null) plan.close();
                cancel(player.getUniqueId(), pending);
                return;
            }
            if (error != null) {
                fail(player, "Não foi possível carregar o destino. O pergaminho já foi consumido.");
                return;
            }
            pending.plan = plan;
            try {
                Location destination = landing.destination(plan, player, pending.origin);
                if (destination == null || landing.chestSpot(plan, player, destination) == null) {
                    fail(player, "Não há chegada segura com espaço acessível para um baú. Nenhuma construção foi removida.");
                    return;
                }
                player.teleportAsync(destination, PlayerTeleportEvent.TeleportCause.PLUGIN)
                        .whenComplete((teleported, teleportError) -> engine.onMain(() -> {
                            if (!active(player, pending)) { cancel(player.getUniqueId(), pending); return; }
                            try {
                                if (teleportError != null || !Boolean.TRUE.equals(teleported)) {
                                    error(player, "O teleporte foi cancelado ou falhou. O pergaminho não será devolvido.");
                                } else if (player.getLocation().distanceSquared(destination) > 4) {
                                    error(player, "O destino foi alterado por outro plugin; o baú não foi colocado.");
                                } else if (!landing.placeChest(plan, player)) {
                                    error(player, "Você chegou ao teto, mas a colocação segura do baú foi impedida. Avise a administração.");
                                } else {
                                    action(player, "Teto liberado! Obsidianas e isqueiro estão no baú próximo.", NamedTextColor.GREEN);
                                }
                            } catch (RuntimeException e) {
                                plugin.getLogger().log(Level.SEVERE, "Falha na chegada de " + player.getName(), e);
                                error(player, "Falha ao colocar o baú. Avise a administração.");
                            } finally { cancel(player.getUniqueId(), pending); }
                        }));
            } catch (RuntimeException e) {
                plugin.getLogger().log(Level.WARNING, "Falha ao preparar chegada", e);
                fail(player, "Não foi possível preparar uma chegada segura. O pergaminho já foi consumido.");
            }
        }));
    }

    private void expand(Player player, Location origin) {
        if (origin.getY() < RoofRules.ROOF_MIN) {
            error(player, "Você precisa estar no teto do Nether para utilizar este pergaminho!");
            return;
        }
        long left = cooldowns.secondsLeft(player.getUniqueId());
        if (left > 0) { error(player, "Aguarde " + left + " segundos para expandir novamente."); return; }
        try {
            var future = engine.prepare(player, RoofRules.region(origin.getWorld().getUID(), origin.getX(), origin.getZ(), 2));
            cooldowns.start(player.getUniqueId());
            action(player, "Preparando a expansão de 5×5 chunks…", NamedTextColor.GOLD);
            future.whenComplete((results, failure) -> engine.onMain(() -> {
                if (!player.isOnline()) return;
                if (failure != null) { error(player, "Falha na expansão; avise a administração."); return; }
                long complete = results.stream().filter(r -> r == ChunkQueue.Result.COMPLETE).count();
                if (complete == 25) action(player, "Expansão concluída! Chão e teto preparados.", NamedTextColor.GREEN);
                else error(player, "Expansão parcial: " + complete + "/25 chunks prontos; os demais têm conflitos, proteção ou erro e não foram registrados.");
            }));
        } catch (IllegalStateException e) { error(player, e.getMessage()); }
    }

    private static void action(Player player, String text, NamedTextColor color) { player.sendActionBar(Component.text(text, color)); }
    private static void error(Player player, String text) { player.sendMessage(Component.text(text, NamedTextColor.RED)); }
    private void fail(Player player, String message) { cancel(player.getUniqueId()); error(player, message); }

    private void cancel(UUID id) {
        Pending pending = ascents.remove(id);
        if (pending == null) return;
        if (pending.timer != null) pending.timer.cancel();
        if (pending.plan != null) pending.plan.close();
    }

    private void cancel(UUID id, Pending expected) {
        if (ascents.get(id) == expected) cancel(id);
    }

    @EventHandler public void quit(PlayerQuitEvent event) {
        cancel(event.getPlayer().getUniqueId());
        clicks.remove(event.getPlayer().getUniqueId());
        // The expansion cooldown intentionally survives reconnecting during these 30 seconds.
    }
    @EventHandler public void changedWorld(PlayerChangedWorldEvent event) { cancel(event.getPlayer().getUniqueId()); }
    @Override public void close() { List.copyOf(ascents.keySet()).forEach(this::cancel); }
}
