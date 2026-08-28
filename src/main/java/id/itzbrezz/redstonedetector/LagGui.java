package id.itzbrezz.redstonedetector;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public final class LagGui implements Listener {

    private final RedstoneDetector plugin;

    private static final String MAIN_TITLE =
            color("&8RedstoneDetector &7• &bInvestigations");

    private static final String DETAIL_TITLE =
            color("&8Detection &7• &bInvestigation");

    public LagGui(RedstoneDetector plugin) {
        this.plugin = plugin;

        Bukkit.getPluginManager().registerEvents(this, plugin);
    }

    /**
     * Open main investigation GUI.
     */
    public void openMain(Player player) {

        Inventory inventory = Bukkit.createInventory(
                null,
                54,
                MAIN_TITLE
        );

        fillBackground(inventory);

        /*
         * Detection data will be connected to the detector
         * in the next integration file.
         */
        List<Detection> detections = plugin.getDetections();

        if (detections == null || detections.isEmpty()) {

            inventory.setItem(
                    22,
                    createItem(
                            Material.PAPER,
                            "&b&lNo Active Detections",
                            "&7",
                            "&7Tidak ada aktivitas redstone",
                            "&7mencurigakan yang terdeteksi.",
                            "&7",
                            "&8Gunakan &f/lag scan",
                            "&8untuk melakukan pemeriksaan."
                    )
            );

        } else {

            int slot = 10;

            for (int i = 0; i < detections.size(); i++) {

                if (slot >= 44) {
                    break;
                }

                Detection detection = detections.get(i);

                if (detection == null) {
                    continue;
                }

                inventory.setItem(
                        slot,
                        createDetectionItem(detection)
                );

                slot++;

                /*
                 * Skip decorative border slots.
                 */
                if (slot == 17) {
                    slot = 19;
                }

                if (slot == 26) {
                    slot = 28;
                }

                if (slot == 35) {
                    slot = 37;
                }
            }
        }

        inventory.setItem(
                49,
                createItem(
                        Material.NETHER_STAR,
                        "&b&lRefresh",
                        "&7",
                        "&8Klik untuk memperbarui data."
                )
        );

        inventory.setItem(
                53,
                createItem(
                        Material.BARRIER,
                        "&c&lClose",
                        "&7",
                        "&8Klik untuk menutup menu."
                )
        );

        player.openInventory(inventory);
    }

    /**
     * Create detection item.
     */
    private ItemStack createDetectionItem(
            Detection detection
    ) {

        int score = Math.max(
                0,
                Math.min(100, detection.getSuspicion())
        );

        String scoreColor = getScoreColor(score);

        String location = formatLocation(
                detection.getLocation()
        );

        String playerName =
                detection.getPlayer() != null
                        ? detection.getPlayer()
                        : "Unknown";

        List<String> lore = new ArrayList<>();

        lore.add(color("&7"));
        lore.add(color(
                "&c&lSUS &8» "
                        + scoreColor
                        + score
                        + "%"
        ));

        lore.add(color(
                "&e&lLOKASI &8» &f"
                        + location
        ));

        lore.add(color(
                "&b&lPLAYER &8» &f"
                        + playerName
        ));

        lore.add(color(
                "&d&lTYPE &8» &f"
                        + detection.getType()
        ));

        lore.add(color("&7"));
        lore.add(color("&8Click untuk melihat detail."));
        lore.add(color("&7"));

        return createItem(
                Material.PAPER,
                "&b&lRedstone Detection",
                lore.toArray(new String[0])
        );
    }

    /**
     * Open detailed investigation screen.
     */
    private void openDetail(
            Player player,
            Detection detection
    ) {

        if (detection == null) {
            openMain(player);
            return;
        }

        Inventory inventory = Bukkit.createInventory(
                null,
                27,
                DETAIL_TITLE
        );

        fillBackground(inventory);

        int score = Math.max(
                0,
                Math.min(
                        100,
                        detection.getSuspicion()
                )
        );

        String scoreColor =
                getScoreColor(score);

        String location =
                formatLocation(
                        detection.getLocation()
                );

        String playerName =
                detection.getPlayer() != null
                        ? detection.getPlayer()
                        : "Unknown";

        PlayerData playerData =
                getPlayerData(detection);

        String lastOnline =
                "Tidak diketahui";

        String playTime =
                "Tidak diketahui";

        if (playerData != null) {

            lastOnline =
                    playerData.getLastOnlineText();

            playTime =
                    playerData.getPlayTimeText();
        }

        /*
         * Main investigation paper.
         */
        inventory.setItem(
                13,
                createItem(
                        Material.PAPER,

                        "&b&lREDSTONE INVESTIGATION",

                        "&7",

                        "&c&lSUS",
                        "&8» " + scoreColor + score + "%",

                        "&7",

                        "&e&lLOKASI",
                        "&8» &f" + location,

                        "&7",

                        "&b&lPLAYER",
                        "&8» &f" + playerName,

                        "&7",

                        "&7&lTERAKHIR ONLINE",
                        "&8» &f" + lastOnline,

                        "&7",

                        "&a&lPLAY TIME",
                        "&8» &f" + playTime,

                        "&7",

                        "&d&lTYPE",
                        "&8» &f" + detection.getType()
                )
        );

        /*
         * Back button.
         */
        inventory.setItem(
                18,
                createItem(
                        Material.ARROW,
                        "&e&lBack",
                        "&7",
                        "&8Kembali ke daftar detection."
                )
        );

        /*
         * Close button.
         */
        inventory.setItem(
                26,
                createItem(
                        Material.BARRIER,
                        "&c&lClose",
                        "&7",
                        "&8Tutup investigation."
                )
        );

        player.openInventory(inventory);
    }

    /**
     * Try to obtain player data.
     */
    private PlayerData getPlayerData(
            Detection detection
    ) {

        if (detection.getPlayerUuid() == null) {
            return null;
        }

        try {
            return new PlayerData(
                    detection.getPlayerUuid()
            );
        } catch (Throwable ignored) {
            return null;
        }
    }

    /**
     * Handle GUI clicks.
     */
    @EventHandler
    public void onInventoryClick(
            InventoryClickEvent event
    ) {

        if (!(event.getWhoClicked() instanceof Player player)) {
            return;
        }

        String title =
                event.getView().getTitle();

        if (
                !title.equals(MAIN_TITLE)
                        && !title.equals(DETAIL_TITLE)
        ) {
            return;
        }

        event.setCancelled(true);

        int slot =
                event.getRawSlot();

        if (slot < 0) {
            return;
        }

        /*
         * MAIN GUI
         */
        if (title.equals(MAIN_TITLE)) {

            if (slot == 49) {

                openMain(player);
                return;
            }

            if (slot == 53) {

                player.closeInventory();
                return;
            }

            ItemStack item =
                    event.getCurrentItem();

            if (
                    item == null
                            || item.getType() != Material.PAPER
            ) {
                return;
            }

            Detection detection =
                    findDetectionBySlot(slot);

            if (detection != null) {
                openDetail(
                        player,
                        detection
                );
            }

            return;
        }

        /*
         * DETAIL GUI
         */
        if (title.equals(DETAIL_TITLE)) {

            if (slot == 18) {

                openMain(player);
                return;
            }

            if (slot == 26) {

                player.closeInventory();
            }
        }
    }

    /**
     * Find detection corresponding to GUI slot.
     */
    private Detection findDetectionBySlot(
            int targetSlot
    ) {

        List<Detection> detections =
                plugin.getDetections();

        if (detections == null) {
            return null;
        }

        int slot = 10;

        for (Detection detection : detections) {

            if (detection == null) {
                continue;
            }

            if (slot == targetSlot) {
                return detection;
            }

            slot++;

            if (slot == 17) {
                slot = 19;
            }

            if (slot == 26) {
                slot = 28;
            }

            if (slot == 35) {
                slot = 37;
            }

            if (slot >= 44) {
                break;
            }
        }

        return null;
    }

    /**
     * Fill GUI background.
     */
    private void fillBackground(
            Inventory inventory
    ) {

        ItemStack filler =
                createItem(
                        Material.GRAY_STAINED_GLASS_PANE,
                        " "
                );

        for (int i = 0;
             i < inventory.getSize();
             i++) {

            inventory.setItem(
                    i,
                    filler
            );
        }
    }

    /**
     * Create ItemStack.
     */
    private ItemStack createItem(
            Material material,
            String name,
            String... lore
    ) {

        ItemStack item =
                new ItemStack(material);

        ItemMeta meta =
                item.getItemMeta();

        if (meta == null) {
            return item;
        }

        meta.setDisplayName(
                color(name)
        );

        if (lore.length > 0) {

            List<String> lines =
                    new ArrayList<>();

            for (String line : lore) {
                lines.add(color(line));
            }

            meta.setLore(lines);
        }

        item.setItemMeta(meta);

        return item;
    }

    /**
     * Format location.
     */
    private String formatLocation(
            org.bukkit.Location location
    ) {

        if (location == null) {
            return "Unknown";
        }

        if (location.getWorld() == null) {
            return "Unknown";
        }

        return location.getWorld().getName()
                + " "
                + location.getBlockX()
                + ", "
                + location.getBlockY()
                + ", "
                + location.getBlockZ();
    }

    /**
     * Score color.
     */
    private String getScoreColor(
            int score
    ) {

        if (score >= 95) {
            return "&4";
        }

        if (score >= 85) {
            return "&c";
        }

        if (score >= 70) {
            return "&6";
        }

        if (score >= 50) {
            return "&e";
        }

        return "&a";
    }

    /**
     * Color helper.
     */
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
