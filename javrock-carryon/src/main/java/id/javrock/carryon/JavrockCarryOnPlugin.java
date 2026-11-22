package id.javrock.carryon;

import org.bukkit.command.PluginCommand;
import org.bukkit.plugin.java.JavaPlugin;

public class JavrockCarryOnPlugin extends JavaPlugin {

    private CarryManagerService carryService;

    @Override
    public void onEnable() {
        saveDefaultConfig();

        this.carryService = new CarryManagerService(this);
        this.carryService.start();

        getServer().getPluginManager().registerEvents(new CarryInteractionListener(this, carryService), this);

        PluginCommand cmd = getCommand("jcarry");
        if (cmd != null) {
            CarryCommandExecutor executor = new CarryCommandExecutor(this, carryService);
            cmd.setExecutor(executor);
            cmd.setTabCompleter(executor);
        }

        getLogger().info("JavrockCarryOn aktif untuk Paper 1.21.11.");
    }

    @Override
    public void onDisable() {
        if (carryService != null) {
            carryService.cleanupAll();
        }
        getLogger().info("JavrockCarryOn dinonaktifkan.");
    }

    public void reloadPluginConfig() {
        reloadConfig();
        if (carryService != null) {
            carryService.reloadSettings();
        }
    }
}
