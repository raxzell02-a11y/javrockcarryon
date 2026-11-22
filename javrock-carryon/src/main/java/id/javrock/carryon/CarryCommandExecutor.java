package id.javrock.carryon;

import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.stream.Collectors;

public class CarryCommandExecutor implements CommandExecutor, TabCompleter {

    private static final LegacyComponentSerializer LEGACY = LegacyComponentSerializer.legacyAmpersand();

    private final JavrockCarryOnPlugin plugin;
    private final CarryManagerService carryService;

    public CarryCommandExecutor(JavrockCarryOnPlugin plugin, CarryManagerService carryService) {
        this.plugin = plugin;
        this.carryService = carryService;
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, @NotNull String[] args) {
        String prefix = plugin.getConfig().getString("messages.prefix", "&9&lᴊᴀᴠʀᴏᴄᴋ &8» ");
        if (!sender.hasPermission("javrockcarryon.admin")) {
            sender.sendMessage(LEGACY.deserialize(prefix + "&cᴋᴀᴍᴜ ᴛɪᴅᴀᴋ ᴍᴇᴍɪʟɪᴋɪ ɪᴢɪɴ."));
            return true;
        }

        if (args.length > 0 && args[0].equalsIgnoreCase("reload")) {
            plugin.reloadPluginConfig();
            String reloaded = plugin.getConfig().getString("messages.reloaded", "&bᴋᴏɴꜰɪɢᴜʀᴀꜱɪ ʙᴇʀʜᴀꜱɪʟ ᴅɪᴍᴜᴀᴛ ᴜʟᴀɴɢ.");
            sender.sendMessage(LEGACY.deserialize(prefix + reloaded));
            return true;
        }

        sender.sendMessage(LEGACY.deserialize(prefix + "&fɢᴜɴᴀᴋᴀɴ: &3/" + label + " reload"));
        return true;
    }

    @Override
    public @Nullable List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, @NotNull String[] args) {
        if (!sender.hasPermission("javrockcarryon.admin")) {
            return Collections.emptyList();
        }
        if (args.length == 1) {
            String partial = args[0].toLowerCase(Locale.ROOT);
            return List.of("reload").stream()
                    .filter(s -> s.startsWith(partial))
                    .collect(Collectors.toList());
        }
        return Collections.emptyList();
    }
}
