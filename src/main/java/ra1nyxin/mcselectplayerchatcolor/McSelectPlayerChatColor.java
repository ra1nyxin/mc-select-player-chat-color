package ra1nyxin.mcselectplayerchatcolor;

import io.papermc.paper.chat.ChatRenderer;
import io.papermc.paper.event.player.AsyncChatEvent;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.PluginCommand;
import org.bukkit.command.TabCompleter;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.io.IOException;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;

public final class McSelectPlayerChatColor extends JavaPlugin
        implements Listener, CommandExecutor, TabCompleter {
    private static final PlainTextComponentSerializer PLAIN_TEXT = PlainTextComponentSerializer.plainText();
    private static final String COMMAND_PERMISSION = "mc-select-player-chat-color.admin";

    private final Map<UUID, NamedTextColor> playerColors = new ConcurrentHashMap<>();
    private List<String> colorNames;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        colorNames = NamedTextColor.NAMES.keys().stream()
                .sorted(Comparator.naturalOrder())
                .toList();
        loadPlayerColors();

        getServer().getPluginManager().registerEvents(this, this);
        PluginCommand pluginCommand = getCommand("mc-select-player-chat-color");
        if (pluginCommand == null) {
            getLogger().severe("plugin.yml 中缺少命令声明，插件已停用。");
            getServer().getPluginManager().disablePlugin(this);
            return;
        }
        pluginCommand.setExecutor(this);
        pluginCommand.setTabCompleter(this);
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onAsyncChat(AsyncChatEvent event) {
        NamedTextColor color = playerColors.get(event.getPlayer().getUniqueId());
        if (color == null) {
            return;
        }

        event.renderer(ChatRenderer.viewerUnaware((source, sourceDisplayName, message) ->
                Component.text("<" + source.getName() + "> " + PLAIN_TEXT.serialize(message), color)));
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!sender.hasPermission(COMMAND_PERMISSION)) {
            sender.sendMessage(Component.text("你没有设置玩家聊天颜色的权限。", NamedTextColor.RED));
            return true;
        }
        if (args.length != 2) {
            sendUsage(sender, label);
            return true;
        }

        OfflinePlayer target = findKnownPlayer(args[0]);
        if (target == null) {
            sender.sendMessage(Component.text("找不到在线或已缓存的玩家：" + args[0], NamedTextColor.RED));
            return true;
        }

        String requestedColor = args[1].toLowerCase(Locale.ROOT);
        if (requestedColor.equals("reset")) {
            if (playerColors.remove(target.getUniqueId()) == null) {
                sender.sendMessage(Component.text(target.getName() + " 当前没有设置聊天颜色。", NamedTextColor.YELLOW));
                return true;
            }
            if (savePlayerColors()) {
                sender.sendMessage(Component.text("已恢复 " + displayName(target) + " 的默认聊天颜色。", NamedTextColor.GREEN));
            } else {
                sender.sendMessage(Component.text("颜色已在本次运行中恢复，但保存配置失败；请查看控制台。", NamedTextColor.RED));
            }
            return true;
        }

        NamedTextColor color = NamedTextColor.NAMES.value(requestedColor);
        if (color == null) {
            sender.sendMessage(Component.text("不支持的颜色：" + args[1], NamedTextColor.RED));
            sendUsage(sender, label);
            return true;
        }

        playerColors.put(target.getUniqueId(), color);
        if (savePlayerColors()) {
            sender.sendMessage(Component.text("已将 " + displayName(target) + " 的聊天颜色设为 ", NamedTextColor.GREEN)
                    .append(Component.text(color.name(), color)));
        } else {
            sender.sendMessage(Component.text("颜色已在本次运行中设置，但保存配置失败；请查看控制台。", NamedTextColor.RED));
        }
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (!sender.hasPermission(COMMAND_PERMISSION)) {
            return List.of();
        }
        if (args.length == 1) {
            String prefix = args[0].toLowerCase(Locale.ROOT);
            return getServer().getOnlinePlayers().stream()
                    .map(Player::getName)
                    .filter(name -> name.toLowerCase(Locale.ROOT).startsWith(prefix))
                    .sorted(String.CASE_INSENSITIVE_ORDER)
                    .toList();
        }
        if (args.length == 2) {
            String prefix = args[1].toLowerCase(Locale.ROOT);
            List<String> suggestions = new ArrayList<>(colorNames);
            suggestions.add("reset");
            return suggestions.stream().filter(value -> value.startsWith(prefix)).toList();
        }
        return List.of();
    }

    private OfflinePlayer findKnownPlayer(String name) {
        Player online = getServer().getPlayerExact(name);
        return online != null ? online : getServer().getOfflinePlayerIfCached(name);
    }

    private void loadPlayerColors() {
        ConfigurationSection section = getConfig().getConfigurationSection("player-colors");
        if (section == null) {
            return;
        }
        for (String key : section.getKeys(false)) {
            try {
                UUID playerId = UUID.fromString(key);
                String colorName = section.getString(key);
                NamedTextColor color = colorName == null ? null
                        : NamedTextColor.NAMES.value(colorName.toLowerCase(Locale.ROOT));
                if (color == null) {
                    getLogger().warning("已忽略无效聊天颜色配置：" + key);
                    continue;
                }
                playerColors.put(playerId, color);
            } catch (IllegalArgumentException exception) {
                getLogger().warning("已忽略无效玩家 UUID 配置：" + key);
            }
        }
    }

    private boolean savePlayerColors() {
        getConfig().set("player-colors", null);
        ConfigurationSection section = getConfig().createSection("player-colors");
        playerColors.forEach((playerId, color) -> section.set(playerId.toString(), color.name()));

        File target = new File(getDataFolder(), "config.yml");
        File temporary = new File(getDataFolder(), "config.yml.tmp");
        try {
            getConfig().save(temporary);
            try {
                Files.move(temporary.toPath(), target.toPath(),
                        StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
            } catch (AtomicMoveNotSupportedException exception) {
                Files.move(temporary.toPath(), target.toPath(), StandardCopyOption.REPLACE_EXISTING);
            }
            return true;
        } catch (IOException exception) {
            getLogger().log(Level.SEVERE, "无法保存玩家聊天颜色配置", exception);
            try {
                Files.deleteIfExists(temporary.toPath());
            } catch (IOException cleanupException) {
                getLogger().log(Level.WARNING, "无法清理临时配置文件", cleanupException);
            }
            return false;
        }
    }

    private void sendUsage(CommandSender sender, String label) {
        sender.sendMessage(Component.text("用法：/" + label + " <玩家> <颜色|reset>", NamedTextColor.YELLOW));
        sender.sendMessage(Component.text("可用颜色：" + String.join(", ", colorNames), NamedTextColor.GRAY));
    }

    private static String displayName(OfflinePlayer player) {
        return player.getName() == null ? player.getUniqueId().toString() : player.getName();
    }
}
