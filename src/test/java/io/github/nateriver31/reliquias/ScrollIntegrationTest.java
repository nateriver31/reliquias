package io.github.nateriver31.reliquias;

import static org.junit.jupiter.api.Assertions.*;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.BlockFace;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.ServerMock;
import org.mockbukkit.mockbukkit.entity.PlayerMock;
import org.mockbukkit.mockbukkit.world.WorldMock;

/** Paper-facing smoke tests; chunk generation and teleportAsync still require a real-server acceptance test. */
class ScrollIntegrationTest {
    ServerMock server;
    NetherRoofPlugin plugin;
    PlayerMock player;
    WorldMock nether;
    ScrollItems items;

    @BeforeEach void start() {
        server = MockBukkit.mock();
        plugin = MockBukkit.load(NetherRoofPlugin.class);
        assertTrue(plugin.isEnabled());
        items = new ScrollItems(plugin);
        player = server.addPlayer();
        player.setOp(true);
        nether = new WorldMock(Material.AIR, 0, 320, 0);
        nether.setName("custom_nether");
        nether.setEnvironment(World.Environment.NETHER);
        server.addWorld(nether);
        player.teleport(new Location(nether, -0.25, 120, 16.5));
        while (player.nextMessage() != null) { /* discard any join messages */ }
    }

    @AfterEach void stop() { MockBukkit.unmock(); }

    PlayerInteractEvent click(EquipmentSlot hand) {
        var event = new PlayerInteractEvent(player, Action.RIGHT_CLICK_AIR, player.getInventory().getItem(hand), null, BlockFace.SELF, hand);
        server.getPluginManager().callEvent(event);
        return event;
    }

    @Test void nativeAndRenamedPatternsAreNotMagicItems() {
        ItemStack vanilla = new ItemStack(Material.FLOWER_BANNER_PATTERN);
        var meta = vanilla.getItemMeta();
        meta.displayName(Component.text("Pergaminho da Flor"));
        vanilla.setItemMeta(meta);
        assertNull(items.identify(vanilla));
        player.getInventory().setItemInMainHand(vanilla);
        click(EquipmentSlot.HAND);
        assertEquals(1, player.getInventory().getItemInMainHand().getAmount());
        assertNull(player.nextActionBar());
        assertEquals(Material.FLOWER_BANNER_PATTERN, items.create(ScrollItems.Scroll.FLOWER, 1).getType());
        assertEquals(Material.CREEPER_BANNER_PATTERN, items.create(ScrollItems.Scroll.CREEPER, 1).getType());
    }

    @Test void rightClickAirConsumesExactlyOneImmediatelyAndShowsRedThree() {
        player.getInventory().setItemInMainHand(items.create(ScrollItems.Scroll.FLOWER, 3));
        click(EquipmentSlot.HAND);
        assertEquals(2, player.getInventory().getItemInMainHand().getAmount());
        assertEquals(Component.text("3", NamedTextColor.RED), player.nextActionBar());
        assertEquals(120, player.getLocation().getY(), "No immediate teleport before the countdown or preparation");
    }

    @Test void simultaneousHandPacketsCannotConsumeTwoScrolls() {
        player.getInventory().setItemInMainHand(items.create(ScrollItems.Scroll.FLOWER, 3));
        player.getInventory().setItemInOffHand(items.create(ScrollItems.Scroll.FLOWER, 3));
        click(EquipmentSlot.HAND);
        click(EquipmentSlot.OFF_HAND);
        assertEquals(2, player.getInventory().getItemInMainHand().getAmount());
        assertEquals(3, player.getInventory().getItemInOffHand().getAmount());
        assertNotNull(player.nextActionBar());
        assertNull(player.nextActionBar());
    }

    @Test void explicitInteractionDenialDoesNotActivateOrConsume() {
        player.getInventory().setItemInMainHand(items.create(ScrollItems.Scroll.FLOWER, 2));
        var event = new PlayerInteractEvent(player, Action.RIGHT_CLICK_AIR, player.getInventory().getItemInMainHand(), null, BlockFace.SELF, EquipmentSlot.HAND);
        event.setCancelled(true);
        server.getPluginManager().callEvent(event);
        assertEquals(2, player.getInventory().getItemInMainHand().getAmount());
        assertNull(player.nextActionBar());
    }

    @Test void overworldUseDoesNotConsumeTheFlower() {
        player.teleport(new Location(server.addSimpleWorld("overworld"), 0, 64, 0));
        player.getInventory().setItemInMainHand(items.create(ScrollItems.Scroll.FLOWER, 2));
        click(EquipmentSlot.HAND);
        assertEquals(2, player.getInventory().getItemInMainHand().getAmount());
        assertNull(player.nextActionBar());
    }

    @Test void expansionBelow221ShowsTheRequiredMessageAndDoesNotConsume() {
        player.teleport(new Location(nether, 0, 220.99, 0));
        player.getInventory().setItemInMainHand(items.create(ScrollItems.Scroll.CREEPER, 1));
        click(EquipmentSlot.HAND);
        assertEquals(1, player.getInventory().getItemInMainHand().getAmount());
        assertTrue(player.nextMessage().contains("Você precisa estar no teto do Nether para utilizar este pergaminho!"));
        assertNull(player.nextActionBar());
    }

    @Test void expansionOnHighBuildingsHasNoArtificial255HeightLimitAndDoesNotTeleport() {
        Location high = new Location(nether, 4.5, 300, 4.5, 180, 0);
        player.teleport(high);
        player.getInventory().setItemInMainHand(items.create(ScrollItems.Scroll.CREEPER, 1));
        click(EquipmentSlot.HAND);
        assertEquals(1, player.getInventory().getItemInMainHand().getAmount());
        assertEquals(high, player.getLocation());
        assertEquals(Component.text("Preparando a expansão de 5×5 chunks…", NamedTextColor.GOLD), player.nextActionBar());
    }

    @Test void protectionPluginsCanVetoBeforeAnyBlockChange() throws Exception {
        server.getPluginManager().registerEvents(new Listener() {
            @EventHandler public void deny(RoofBlockChangeEvent event) { event.setCancelled(true); }
        }, plugin);
        var block = nether.getBlockAt(0, 219, 0);
        assertFalse(new Protection(plugin).allows(player, block, Material.BEDROCK));
        assertEquals(Material.AIR, block.getType());
    }
}
