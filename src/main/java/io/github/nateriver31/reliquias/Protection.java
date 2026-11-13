package io.github.nateriver31.reliquias;

import java.lang.reflect.Array;
import java.lang.reflect.Method;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;

final class Protection {
    private final Plugin plugin;
    private Object query;
    private Object worldGuard;
    private Method wrap;
    private Method adapt;
    private Method testBuild;
    private Object breakFlags;
    private Object placeFlags;

    Protection(Plugin plugin) throws ReflectiveOperationException {
        this.plugin = plugin;
        Plugin wg = plugin.getServer().getPluginManager().getPlugin("WorldGuard");
        if (wg == null) return;
        if (!wg.isEnabled()) throw new IllegalStateException("WorldGuard está instalado, mas indisponível");
        ClassLoader loader = wg.getClass().getClassLoader();
        Class<?> main = loader.loadClass("com.sk89q.worldguard.WorldGuard");
        Object instance = main.getMethod("getInstance").invoke(null);
        Object platform = main.getMethod("getPlatform").invoke(instance);
        Object container = platform.getClass().getMethod("getRegionContainer").invoke(platform);
        query = container.getClass().getMethod("createQuery").invoke(container);
        Class<?> location = loader.loadClass("com.sk89q.worldedit.util.Location");
        Class<?> player = loader.loadClass("com.sk89q.worldguard.LocalPlayer");
        Class<?> flag = loader.loadClass("com.sk89q.worldguard.protection.flags.StateFlag");
        Class<?> flags = loader.loadClass("com.sk89q.worldguard.protection.flags.Flags");
        breakFlags = Array.newInstance(flag, 1);
        placeFlags = Array.newInstance(flag, 1);
        Array.set(breakFlags, 0, flags.getField("BLOCK_BREAK").get(null));
        Array.set(placeFlags, 0, flags.getField("BLOCK_PLACE").get(null));
        testBuild = query.getClass().getMethod("testBuild", location, player, breakFlags.getClass());
        adapt = loader.loadClass("com.sk89q.worldedit.bukkit.BukkitAdapter").getMethod("adapt", Location.class);
        wrap = wg.getClass().getMethod("wrapPlayer", Player.class);
        worldGuard = wg;
        plugin.getLogger().info("Proteção WorldGuard habilitada (build, block-break e block-place; sem bypass automático).");
    }

    boolean allows(Player player, Block block, Material replacement) {
        if (!player.isOnline() || !player.getWorld().equals(block.getWorld())) return false;
        if (query != null) {
            try {
                if (!((Plugin) worldGuard).isEnabled()) return false;
                Object flags = replacement == Material.AIR ? breakFlags : placeFlags;
                if (!(boolean) testBuild.invoke(query, adapt.invoke(null, block.getLocation()), wrap.invoke(worldGuard, player), flags)) {
                    return false;
                }
            } catch (ReflectiveOperationException e) {
                throw new IllegalStateException("WorldGuard falhou; edição interrompida por segurança", e);
            }
        }
        RoofBlockChangeEvent event = new RoofBlockChangeEvent(player, block, replacement);
        plugin.getServer().getPluginManager().callEvent(event);
        return !event.isCancelled();
    }
}
