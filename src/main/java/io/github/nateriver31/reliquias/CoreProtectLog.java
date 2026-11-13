package io.github.nateriver31.reliquias;

import java.lang.reflect.Method;
import org.bukkit.Location;
import org.bukkit.block.Block;
import org.bukkit.block.data.BlockData;
import org.bukkit.plugin.Plugin;

/** Uses the documented CoreProtect BlockData API; never registers fake vanilla block events. */
final class CoreProtectLog {
    private final Plugin coreProtect;
    private final Object api;
    private final Method enabled;
    private final Method removal;
    private final Method placement;

    CoreProtectLog(Plugin plugin) throws ReflectiveOperationException {
        coreProtect = plugin.getServer().getPluginManager().getPlugin("CoreProtect");
        if (coreProtect == null) {
            api = null;
            enabled = removal = placement = null;
            plugin.getLogger().info("CoreProtect ausente: auditoria externa de blocos indisponível.");
            return;
        }
        if (!coreProtect.isEnabled()) throw new IllegalStateException("CoreProtect instalado, mas desativado");
        api = coreProtect.getClass().getMethod("getAPI").invoke(coreProtect);
        enabled = api.getClass().getMethod("isEnabled");
        removal = api.getClass().getMethod("logRemoval", String.class, Location.class, BlockData.class);
        placement = api.getClass().getMethod("logPlacement", String.class, Location.class, BlockData.class);
        if (!(boolean) enabled.invoke(api)) throw new IllegalStateException("API do CoreProtect está desativada");
        int version = (int) api.getClass().getMethod("APIVersion").invoke(api);
        plugin.getLogger().info("Auditoria CoreProtect habilitada, API " + version + ".");
    }

    void change(String actor, Block block, BlockData replacement) {
        if (api == null) return;
        try {
            if (!coreProtect.isEnabled() || !(boolean) enabled.invoke(api)) {
                throw new IllegalStateException("CoreProtect indisponível; edição interrompida");
            }
            if (!block.getType().isAir() && !(boolean) removal.invoke(api, actor, block.getLocation(), block.getBlockData())) {
                throw new IllegalStateException("CoreProtect recusou o registro de remoção");
            }
            if (!replacement.getMaterial().isAir() && !(boolean) placement.invoke(api, actor, block.getLocation(), replacement)) {
                throw new IllegalStateException("CoreProtect recusou o registro de colocação");
            }
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException("Falha na API do CoreProtect", e);
        }
    }
}
