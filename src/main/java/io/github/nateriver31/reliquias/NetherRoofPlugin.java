package io.github.nateriver31.reliquias;

import io.github.nateriver31.reliquias.core.ChunkJournal;
import java.util.Objects;
import java.util.logging.Level;
import org.bukkit.plugin.java.JavaPlugin;

public final class NetherRoofPlugin extends JavaPlugin {
    private ChunkJournal journal;
    private ChunkTickets tickets;
    private EditEngine engine;
    private ScrollListener listener;

    @Override public void onEnable() {
        try {
            saveDefaultConfig();
            Protection protection = new Protection(this);
            CoreProtectLog audit = new CoreProtectLog(this);
            journal = new ChunkJournal(getDataFolder().toPath().resolve("chunks-concluidos.log"));
            tickets = new ChunkTickets(this);
            engine = new EditEngine(this, journal, protection, audit, tickets);
            ScrollItems items = new ScrollItems(this);
            listener = new ScrollListener(this, items, engine, new LandingService(engine, tickets, protection, audit));
            getServer().getPluginManager().registerEvents(listener, this);
            ScrollCommand command = new ScrollCommand(this, items);
            Objects.requireNonNull(getCommand("pergaminho")).setExecutor(command);
            Objects.requireNonNull(getCommand("pergaminho")).setTabCompleter(command);
            getLogger().info("Pronto para Paper 26.2. Chunks já concluídos: " + journal.size());
        } catch (Exception e) {
            getLogger().log(Level.SEVERE, "Inicialização interrompida; nenhuma proteção será contornada", e);
            getServer().getPluginManager().disablePlugin(this);
        }
    }

    @Override public void onDisable() {
        if (listener != null) listener.close();
        if (engine != null) engine.close();
        if (tickets != null) tickets.close();
        if (journal != null) {
            try { journal.close(); }
            catch (Exception e) { getLogger().log(Level.SEVERE, "Falha ao fechar registro de chunks", e); }
        }
    }
}
