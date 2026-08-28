package id.itzbrezz.redstonedetector;

import org.bukkit.ChatColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.Locale;

public final class LagCommand implements CommandExecutor {

    private final RedstoneDetector plugin;
    private final SafeScanner scanner;
    private final LagGui gui;

    public LagCommand(
            RedstoneDetector plugin,
            SafeScanner scanner,
            LagGui gui
    ) {
        this.plugin = plugin;
        this.scanner = scanner;
        this.gui = gui;
    }

    @Override
    public boolean onCommand(
            CommandSender sender,
            Command command,
            String label,
            String[] args
    ) {

        if (!sender.hasPermission("redstonedetector.staff")) {
            sender.sendMessage(message(
                    "&cYou don't have permission to use this command."
            ));
            return true;
        }

        if (args.length == 0) {
            sendUsage(sender);
            return true;
        }

        String subCommand =
                args[0].toLowerCase(Locale.ROOT);

        switch (subCommand) {

            case "gui":
                handleGui(sender);
                break;

            case "scan":
                handleScan(sender);
                break;

            case "progress":
                handleProgress(sender);
                break;

            case "history":
                handleHistory(sender);
                break;

            case "reload":
                handleReload(sender);
                break;

            case "info":
                handleInfo(sender);
                break;

            case "stop":
            case "cancel":
                handleStop(sender);
                break;

            default:
                sender.sendMessage(message(
                        "&cUnknown subcommand. Use &f/lag &cfor help."
                ));
                break;
        }

        return true;
    }

    /**
     * /lag gui
     */
    private void handleGui(
            CommandSender sender
    ) {

        if (!(sender instanceof Player player)) {

            sender.sendMessage(message(
                    "&cOnly players can open the GUI."
            ));

            return;
        }

        try {

            gui.openMain(player);

        } catch (Throwable throwable) {

            plugin.getLogger().warning(
                    "[GUI] Failed to open GUI safely: "
                            + throwable.getMessage()
            );

            player.sendMessage(message(
                    "&cFailed to open the investigation GUI."
            ));
        }
    }

    /**
     * /lag scan
     */
    private void handleScan(
            CommandSender sender
    ) {

        if (scanner.isRunning()) {

            sender.sendMessage(message(
                    "&eA safe scan is already running."
            ));

            return;
        }

        boolean started;

        try {

            started = scanner.start();

        } catch (Throwable throwable) {

            plugin.getLogger().severe(
                    "[SafeScan] Unexpected command error: "
                            + throwable.getMessage()
            );

            sender.sendMessage(message(
                    "&cThe scan could not be started safely."
            ));

            return;
        }

        if (!started) {

            sender.sendMessage(message(
                    "&cThe scan could not be started."
            ));

            return;
        }

        sender.sendMessage(message(
                "&b&lSAFE SCAN &8» &7Scan started."
        ));

        sender.sendMessage(message(
                "&8» &7Mode: &fMONITOR ONLY"
        ));

        sender.sendMessage(message(
                "&8» &7Performance protection: &aENABLED"
        ));

        sender.sendMessage(message(
                "&8» &7Use &f/lag progress &7to check progress."
        ));
    }

    /**
     * /lag progress
     */
    private void handleProgress(
            CommandSender sender
    ) {

        int processed =
                scanner.getProcessedChunks();

        int total =
                scanner.getTotalChunks();

        int detections =
                scanner.getDetections();

        double progress =
                scanner.getProgress();

        String status =
                scanner.isRunning()
                        ? "&aRUNNING"
                        : "&7IDLE";

        sender.sendMessage("");
        sender.sendMessage(
                color("&8&m----------------------------------------")
        );
        sender.sendMessage(
                color("&b&lREDSTONE SCAN")
        );
        sender.sendMessage("");

        sender.sendMessage(
                color("&7Status: &f")
                        + color(status)
        );

        sender.sendMessage(
                color("&7Progress: &b")
                        + String.format(
                                Locale.US,
                                "%.1f",
                                progress
                        )
                        + "%"
        );

        sender.sendMessage(
                color("&7Chunks: &f")
                        + processed
                        + "&7/&f"
                        + total
        );

        sender.sendMessage(
                color("&7Detections: &c")
                        + detections
        );

        sender.sendMessage("");
        sender.sendMessage(
                color("&8&m----------------------------------------")
        );
        sender.sendMessage("");
    }

    /**
     * /lag history
     */
    private void handleHistory(
            CommandSender sender
    ) {

        int count = 0;

        try {

            if (plugin.getDetections() != null) {
                count = plugin.getDetections().size();
            }

        } catch (Throwable ignored) {
            // Keep command safe.
        }

        sender.sendMessage("");
        sender.sendMessage(
                color("&8&m----------------------------------------")
        );
        sender.sendMessage(
                color("&b&lDETECTION HISTORY")
        );
        sender.sendMessage("");

        if (count == 0) {

            sender.sendMessage(
                    color("&7No detections have been recorded.")
            );

        } else {

            sender.sendMessage(
                    color("&7Recorded detections: &f")
                            + count
            );

            sender.sendMessage("");

            sender.sendMessage(
                    color("&7Open &f/lag gui &7to investigate.")
            );
        }

        sender.sendMessage("");
        sender.sendMessage(
                color("&8&m----------------------------------------")
        );
        sender.sendMessage("");
    }

    /**
     * /lag reload
     */
    private void handleReload(
            CommandSender sender
    ) {

        try {

            plugin.reloadConfig();

            sender.sendMessage(message(
                    "&aConfiguration reloaded successfully."
            ));

        } catch (Throwable throwable) {

            plugin.getLogger().severe(
                    "[Config] Reload failed: "
                            + throwable.getMessage()
            );

            sender.sendMessage(message(
                    "&cConfiguration reload failed."
            ));
        }
    }

    /**
     * /lag info
     */
    private void handleInfo(
            CommandSender sender
    ) {

        sender.sendMessage("");
        sender.sendMessage(
                color("&8&m----------------------------------------")
        );
        sender.sendMessage(
                color("&b&lREDSTONEDETECTOR")
        );
        sender.sendMessage(
                color("&7Professional Redstone Diagnostics")
        );
        sender.sendMessage("");

        sender.sendMessage(
                color("&7Version: &f")
                        + plugin.getDescription().getVersion()
        );

        sender.sendMessage(
                color("&7Mode: &bMONITOR ONLY")
        );

        sender.sendMessage(
                color("&7Safe Scanner: ")
                        + (
                        scanner.isRunning()
                                ? color("&aRUNNING")
                                : color("&7IDLE")
                )
        );

        sender.sendMessage("");
        sender.sendMessage(
                color("&7The detector does not disable,")
        );
        sender.sendMessage(
                color("&7break or limit redstone.")
        );

        sender.sendMessage("");
        sender.sendMessage(
                color("&8&m----------------------------------------")
        );
        sender.sendMessage("");
    }

    /**
     * /lag stop
     */
    private void handleStop(
            CommandSender sender
    ) {

        if (!scanner.isRunning()) {

            sender.sendMessage(message(
                    "&7There is no active scan."
            ));

            return;
        }

        try {

            scanner.stop();

            sender.sendMessage(message(
                    "&eSafe scan stopped."
            ));

        } catch (Throwable throwable) {

            plugin.getLogger().warning(
                    "[SafeScan] Failed to stop scan: "
                            + throwable.getMessage()
            );

            sender.sendMessage(message(
                    "&cThe scan could not be stopped normally."
            ));
        }
    }

    /**
     * Command usage.
     */
    private void sendUsage(
            CommandSender sender
    ) {

        sender.sendMessage("");
        sender.sendMessage(
                color("&8&m----------------------------------------")
        );
        sender.sendMessage(
                color("&b&lREDSTONEDETECTOR &7• &fCommands")
        );
        sender.sendMessage("");

        sender.sendMessage(
                color("&b/lag gui")
                        + color(" &8- &7Open investigation GUI")
        );

        sender.sendMessage(
                color("&b/lag scan")
                        + color(" &8- &7Start safe scan")
        );

        sender.sendMessage(
                color("&b/lag progress")
                        + color(" &8- &7View scan progress")
        );

        sender.sendMessage(
                color("&b/lag history")
                        + color(" &8- &7View detection history")
        );

        sender.sendMessage(
                color("&b/lag stop")
                        + color(" &8- &7Stop active scan")
        );

        sender.sendMessage(
                color("&b/lag reload")
                        + color(" &8- &7Reload configuration")
        );

        sender.sendMessage(
                color("&b/lag info")
                        + color(" &8- &7Plugin information")
        );

        sender.sendMessage("");
        sender.sendMessage(
                color("&8&m----------------------------------------")
        );
        sender.sendMessage("");
    }

    private String message(
            String text
    ) {

        String prefix =
                plugin.getConfig().getString(
                        "messages.prefix",
                        "&8[&bRedstoneDetector&8] "
                );

        return color(prefix + text);
    }

    private static String color(
            String text
    ) {

        if (text == null) {
            return "";
        }

        return ChatColor.translateAlternateColorCodes(
                '&',
                text
        );
    }
              }
