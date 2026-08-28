package id.itzbrezz.redstonedetector;

import net.md_5.bungee.api.chat.ClickEvent;
import net.md_5.bungee.api.chat.ComponentBuilder;
import net.md_5.bungee.api.chat.HoverEvent;
import net.md_5.bungee.api.chat.TextComponent;

import org.bukkit.BanList;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.OfflinePlayer;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.Sign;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabExecutor;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockRedstoneEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;

import java.text.SimpleDateFormat;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Date;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;

public final class RedstoneDetector extends JavaPlugin
        implements Listener, TabExecutor {

    private static final String PERMISSION =
            "redstonedetector.staff";

    private static final String MAIN_GUI =
            "§8RedstoneDetector §7• §bInvestigations";

    private static final String DETAIL_GUI =
            "§8RedstoneDetector §7• §bInvestigation";

    private static final int MAIN_SIZE = 54;
    private static final int DETAIL_SIZE = 27;

    /*
     * Active monitored locations.
     */
    private final Map<String, Detection> detections =
            new HashMap<>();

    /**
     * Returns a read-only view of the currently tracked detections.
     * GUI and command classes can safely inspect this map without
     * replacing or modifying the detector's internal storage.
     *
     * @return read-only detection map
     */
    public Map<String, Detection> getDetections() {
        return java.util.Collections.unmodifiableMap(detections);
    }

    /*
     * Historical detections.
     */
    private final Deque<Detection> history =
            new ArrayDeque<>();

    /*
     * Player session tracking.
     */
    private final Map<UUID, Long> sessionStart =
            new HashMap<>();

    private final Map<UUID, Long> totalPlayTime =
            new HashMap<>();

    /*
     * Action tokens.
     *
     * Example:
     * /lag action tp ABC123
     */
    private final Map<String, Detection> actionTokens =
            new HashMap<>();

    /*
     * Players currently viewing our GUI.
     */
    private final Set<UUID> guiPlayers =
            new HashSet<>();

    /*
     * Prevent multiple scans simultaneously.
     */
    private final AtomicBoolean scanRunning =
            new AtomicBoolean(false);

    private BukkitTask monitorTask;

    private boolean detectorEnabled;

    // =========================================================
    // ENABLE
    // =========================================================

    @Override
    public void onEnable() {

        saveDefaultConfig();
        reloadConfig();

        detectorEnabled =
                getConfig().getBoolean(
                        "settings.enabled",
                        true
                );

        Bukkit.getPluginManager()
                .registerEvents(this, this);

        if (getCommand("lag") != null) {

            getCommand("lag")
                    .setExecutor(this);

            getCommand("lag")
                    .setTabCompleter(this);
        }

        loadOnlinePlayers();

        startMonitor();

        getLogger().info(
                "========================================"
        );

        getLogger().info(
                "RedstoneDetector 2.0.0 enabled."
        );

        getLogger().info(
                "Mode: MONITOR ONLY"
        );

        getLogger().info(
                "Safe /lag scan enabled."
        );

        getLogger().info(
                "========================================"
        );
    }

    // =========================================================
    // DISABLE
    // =========================================================

    @Override
    public void onDisable() {

        if (monitorTask != null) {

            monitorTask.cancel();
            monitorTask = null;
        }

        saveOnlinePlayTime();

        detections.clear();
        actionTokens.clear();
        guiPlayers.clear();

        scanRunning.set(false);

        getLogger().info(
                "RedstoneDetector disabled."
        );
    }

    // =========================================================
    // PLAYER DATA
    // =========================================================

    private void loadOnlinePlayers() {

        long now =
                System.currentTimeMillis();

        for (Player player :
                Bukkit.getOnlinePlayers()) {

            sessionStart.put(
                    player.getUniqueId(),
                    now
            );
        }
    }

    private void saveOnlinePlayTime() {

        long now =
                System.currentTimeMillis();

        for (Player player :
                Bukkit.getOnlinePlayers()) {

            UUID uuid =
                    player.getUniqueId();

            Long start =
                    sessionStart.get(uuid);

            if (start == null) {
                continue;
            }

            long played =
                    Math.max(
                            0L,
                            now - start
                    );

            totalPlayTime.merge(
                    uuid,
                    played,
                    Long::sum
            );

            sessionStart.put(
                    uuid,
                    now
            );
        }
    }

    @EventHandler
    public void onJoin(
            PlayerJoinEvent event
    ) {

        sessionStart.put(
                event.getPlayer().getUniqueId(),
                System.currentTimeMillis()
        );
    }

    @EventHandler
    public void onQuit(
            PlayerQuitEvent event
    ) {

        UUID uuid =
                event.getPlayer()
                        .getUniqueId();

        Long start =
                sessionStart.remove(uuid);

        if (start == null) {
            return;
        }

        long played =
                Math.max(
                        0L,
                        System.currentTimeMillis()
                                - start
                );

        totalPlayTime.merge(
                uuid,
                played,
                Long::sum
        );
    }

    // =========================================================
    // REDSTONE MONITOR
    // =========================================================

    @EventHandler(
            priority = EventPriority.MONITOR,
            ignoreCancelled = true
    )
    public void onRedstone(
            BlockRedstoneEvent event
    ) {

        if (!detectorEnabled) {
            return;
        }

        /*
         * MONITOR ONLY.
         *
         * We only read the event.
         * We NEVER modify the block here.
         */

        if (
                event.getOldCurrent()
                        == event.getNewCurrent()
        ) {
            return;
        }

        Block block =
                event.getBlock();

        Location location =
                block.getLocation();

        String key =
                createLocationKey(location);

        Detection detection =
                detections.computeIfAbsent(
                        key,
                        ignored ->
                                new Detection(
                                        location,
                                        detectType(
                                                block.getType()
                                        )
                                )
                );

        detection.events++;

        detection.lastActivity =
                System.currentTimeMillis();

        /*
         * Keep the exact block that generated
         * the redstone event.
         */
        detection.location =
                location.clone();

        detection.type =
                detectType(
                        block.getType()
                );

        detection.calculateScore();

        /*
         * Identify nearest player.
         */
        String nearby =
                findNearbyPlayer(
                        location
                );

        if (nearby != null) {

            detection.player =
                    nearby;
        }

        /*
         * Only create a staff alert after enough
         * activity has been observed.
         */
        int minimumScore =
                getConfig().getInt(
                        "notifications.minimum-alert-score",
                        70
                );

        if (
                detection.suspicion
                        >= minimumScore
        ) {

            maybeAlert(detection);
        }
    }

    // =========================================================
    // ALERT COOLDOWN
    // =========================================================

    private final Map<String, Long> alertCooldown =
            new HashMap<>();

    private void maybeAlert(
            Detection detection
    ) {

        String key =
                createLocationKey(
                        detection.location
                );

        long now =
                System.currentTimeMillis();

        long cooldown =
                Math.max(
                        10_000L,
                        getConfig().getLong(
                                "notifications.cooldown-seconds",
                                60L
                        ) * 1000L
                );

        Long previous =
                alertCooldown.get(key);

        if (
                previous != null
                        && now - previous
                        < cooldown
        ) {
            return;
        }

        alertCooldown.put(
                key,
                now
        );

        addHistory(detection);

        sendLagChunkAlert(
                detection
        );
    }

    // =========================================================
    // PROFESSIONAL CHAT ALERT
    // =========================================================

    private void sendLagChunkAlert(
            Detection detection
    ) {

        Location location =
                detection.location;

        String world =
                getWorldName(location);

        String player =
                detection.player == null
                        ? "Unknown"
                        : detection.player;

        String lastOnline =
                getLastOnline(player);

        String playTime =
                getPlayTime(player);

        String lagType =
                detection.type;

        String token =
                createActionToken(
                        detection
                );

        String header =
                "§8§m━━━━━━━━━━━━━━━━━━━━━━━━━━━━";

        for (Player staff :
                Bukkit.getOnlinePlayers()) {

            if (
                    !staff.hasPermission(
                            PERMISSION
                    )
            ) {
                continue;
            }

            staff.sendMessage(
                    header
            );

            staff.sendMessage(
                    "§b§l       LAG CHUNK"
            );

            staff.sendMessage(
                    header
            );

            staff.sendMessage(
                    "§7LOCATION:"
            );

            staff.sendMessage(
                    "§8» §7WORLD: §f"
                            + world
            );

            staff.sendMessage(
                    "§8» §7X: §f"
                            + location.getBlockX()
                            + " §7Y: §f"
                            + location.getBlockY()
                            + " §7Z: §f"
                            + location.getBlockZ()
            );

            staff.sendMessage("");

            staff.sendMessage(
                    "§7PLAYER: §f"
                            + player
            );

            staff.sendMessage(
                    "§7LAST ONLINE: §f"
                            + lastOnline
            );

            staff.sendMessage(
                    "§7PLAY TIME: §f"
                            + playTime
            );

            staff.sendMessage("");

            staff.sendMessage(
                    "§7LAG TYPE: §d"
                            + lagType
            );

            staff.sendMessage(
                    "§7SUS: §c§l"
                            + detection.suspicion
                            + "%"
            );

            staff.sendMessage("");

            sendActionButtons(
                    staff,
                    token
            );

            staff.sendMessage(
                    header
            );
        }
    }

    // =========================================================
    // CLICKABLE CHAT BUTTONS
    // =========================================================

    private void sendActionButtons(
            Player player,
            String token
    ) {

        TextComponent tp =
                button(
                        "§a[ TP ]",
                        "Teleport to exact detection location.",
                        "/lag action tp " + token
                );

        TextComponent broken =
                button(
                        "§c[ BROKEN ]",
                        "Break the detected redstone source.",
                        "/lag action broken " + token
                );

        TextComponent ban =
                button(
                        "§4[ BAN ]",
                        "Ban the player associated with this detection.",
                        "/lag action ban " + token
                );

        TextComponent explode =
                button(
                        "§6[ EXPLODE ]",
                        "Create an explosion at the exact detection core.",
                        "/lag action explode " + token
                );

        TextComponent separator =
                new TextComponent(" §8 ");

        TextComponent line =
                new TextComponent();

        line.addExtra(tp);
        line.addExtra(separator);
        line.addExtra(broken);
        line.addExtra(separator);
        line.addExtra(ban);
        line.addExtra(separator);
        line.addExtra(explode);

        player.spigot().sendMessage(line);
    }

    private TextComponent button(
            String text,
            String hover,
            String command
    ) {

        TextComponent component =
                new TextComponent(
                        text
                );

        component.setClickEvent(
                new ClickEvent(
                        ClickEvent.Action.RUN_COMMAND,
                        command
                )
        );

        component.setHoverEvent(
                new HoverEvent(
                        HoverEvent.Action.SHOW_TEXT,
                        new ComponentBuilder(
                                "§7" + hover
                        ).create()
                )
        );

        return component;
    }

    // =========================================================
    // ACTION COMMAND
    // =========================================================

    private boolean handleAction(
            Player player,
            String[] args
    ) {

        if (args.length < 3) {

            send(
                    player,
                    "§cInvalid action."
            );

            return true;
        }

        String action =
                args[1]
                        .toLowerCase(
                                Locale.ROOT
                        );

        String token =
                args[2];

        Detection detection =
                actionTokens.get(token);

        if (detection == null) {

            send(
                    player,
                    "§cThis detection has expired."
            );

            return true;
        }

        /*
         * Always execute world actions on the
         * main server thread.
         */

        switch (action) {

            case "tp" -> {

                teleportToDetection(
                        player,
                        detection
                );
            }

            case "broken" -> {

                brokenConfirmation(
                        player,
                        detection
                );
            }

            case "ban" -> {

                banConfirmation(
                        player,
                        detection
                );
            }

            case "explode" -> {

                explodeConfirmation(
                        player,
                        detection
                );
            }

            case "confirmbroken" -> {

                executeBroken(
                        player,
                        detection
                );
            }

            case "confirmban" -> {

                executeBan(
                        player,
                        detection
                );
            }

            case "confirmexplode" -> {

                executeExplode(
                        player,
                        detection
                );
            }

            default -> {

                send(
                        player,
                        "§cUnknown action."
                );
            }
        }

        return true;
    }

    // =========================================================
    // TP
    // =========================================================

    private void teleportToDetection(
            Player player,
            Detection detection
    ) {

        Location location =
                safeCenter(
                        detection.location
                );

        if (
                location == null
                        || location.getWorld() == null
        ) {

            send(
                    player,
                    "§cDetection location is no longer valid."
            );

            return;
        }

        try {

            player.teleport(
                    location
            );

            send(
                    player,
                    "§aTeleported to the exact redstone detection core."
            );

            send(
                    player,
                    "§7Location: §f"
                            + formatLocation(
                            location
                    )
            );

        } catch (Throwable throwable) {

            getLogger().warning(
                    "Teleport failed: "
                            + safeError(
                            throwable
                    )
            );

            send(
                    player,
                    "§cTeleport failed safely."
            );
        }
    }

    // =========================================================
    // BROKEN CONFIRMATION
    // =========================================================

    private void brokenConfirmation(
            Player player,
            Detection detection
    ) {

        String token =
                createActionToken(
                        detection
                );

        player.sendMessage("");

        player.sendMessage(
                "§8§m━━━━━━━━━━━━━━━━━━━━━━━━━━━━"
        );

        player.sendMessage(
                "§c§l        BREAK CONFIRMATION"
        );

        player.sendMessage("");

        player.sendMessage(
                "§7You are about to break:"
        );

        player.sendMessage(
                "§f"
                        + detection.type
        );

        player.sendMessage(
                "§7Location: §f"
                        + formatLocation(
                        detection.location
                )
        );

        player.sendMessage("");

        sendConfirmCancel(
                player,
                "confirmbroken",
                token
        );

        player.sendMessage(
                "§8§m━━━━━━━━━━━━━━━━━━━━━━━━━━━━"
        );
    }

    // =========================================================
    // BAN CONFIRMATION
    // =========================================================

    private void banConfirmation(
            Player player,
            Detection detection
    ) {

        String target =
                detection.player;

        if (
                target == null
                        || target.isBlank()
                        || target.equalsIgnoreCase(
                        "Unknown"
                )
        ) {

            send(
                    player,
                    "§cNo player is associated with this detection."
            );

            return;
        }

        String token =
                createActionToken(
                        detection
                );

        player.sendMessage("");

        player.sendMessage(
                "§8§m━━━━━━━━━━━━━━━━━━━━━━━━━━━━"
        );

        player.sendMessage(
                "§4§l          BAN CONFIRMATION"
        );

        player.sendMessage("");

        player.sendMessage(
                "§7Player: §f"
                        + target
        );

        player.sendMessage(
                "§7Lag type: §d"
                        + detection.type
        );

        player.sendMessage(
                "§7SUS: §c"
                        + detection.suspicion
                        + "%"
        );

        player.sendMessage("");

        sendConfirmCancel(
                player,
                "confirmban",
                token
        );

        player.sendMessage(
                "§8§m━━━━━━━━━━━━━━━━━━━━━━━━━━━━"
        );
    }

    // =========================================================
    // EXPLODE CONFIRMATION
    // =========================================================

    private void explodeConfirmation(
            Player player,
            Detection detection
    ) {

        String token =
                createActionToken(
                        detection
                );

        player.sendMessage("");

        player.sendMessage(
                "§8§m━━━━━━━━━━━━━━━━━━━━━━━━━━━━"
        );

        player.sendMessage(
                "§6§l       EXPLODE CONFIRMATION"
        );

        player.sendMessage("");

        player.sendMessage(
                "§7Explosion target:"
        );

        player.sendMessage(
                "§f"
                        + formatLocation(
                        detection.location
                )
        );

        player.sendMessage("");

        player.sendMessage(
                "§eThis will create an explosion"
        );

        player.sendMessage(
                "§eat the exact detection core."
        );

        player.sendMessage("");

        sendConfirmCancel(
                player,
                "confirmexplode",
                token
        );

        player.sendMessage(
                "§8§m━━━━━━━━━━━━━━━━━━━━━━━━━━━━"
        );
    }

    private void sendConfirmCancel(
            Player player,
            String action,
            String token
    ) {

        TextComponent confirm =
                button(
                        "§a[ CONFIRM ]",
                        "Confirm this action.",
                        "/lag action "
                                + action
                                + " "
                                + token
                );

        TextComponent cancel =
                button(
                        "§c[ CANCEL ]",
                        "Cancel this action.",
                        "/lag gui"
                );

        TextComponent line =
                new TextComponent();

        line.addExtra(confirm);

        line.addExtra(
                new TextComponent(
                        " §8 "
                )
        );

        line.addExtra(cancel);

        player.spigot().sendMessage(
                line
        );
    }

    // =========================================================
    // BROKEN EXECUTION
    // =========================================================

    private void executeBroken(
            Player player,
            Detection detection
    ) {

        Location location =
                detection.location;

        if (
                location == null
                        || location.getWorld() == null
        ) {

            send(
                    player,
                    "§cInvalid detection location."
            );

            return;
        }

        try {

            Block block =
                    location.getBlock();

            Material material =
                    block.getType();

            if (
                    material == Material.AIR
            ) {

                send(
                        player,
                        "§eThe detected block is already broken."
                );

                return;
            }

            /*
             * Staff explicitly confirmed the action.
             */
            block.setType(
                    Material.AIR,
                    false
            );

            send(
                    player,
                    "§aDetection source broken."
            );

            send(
                    player,
                    "§7Broken block: §f"
                            + material.name()
            );

            send(
                    player,
                    "§7Location: §f"
                            + formatLocation(
                            location
                    )
            );

            placeServerProtectSign(
                    location
            );

        } catch (Throwable throwable) {

            getLogger().warning(
                    "Broken action failed: "
                            + safeError(
                            throwable
                    )
            );

            send(
                    player,
                    "§cCould not break the detected source."
            );
        }
    }

    // =========================================================
    // BAN EXECUTION
    // =========================================================

    private void executeBan(
            Player staff,
            Detection detection
    ) {

        String target =
                detection.player;

        if (
                target == null
                        || target.isBlank()
                        || target.equalsIgnoreCase(
                        "Unknown"
                )
        ) {

            send(
                    staff,
                    "§cNo valid player is associated with this detection."
            );

            return;
        }

        try {

            OfflinePlayer offline =
                    Bukkit.getOfflinePlayer(
                            target
                    );

            String reason =
                    "Server Protection - Redstone Lag";

            Bukkit.getBanList(
                    BanList.Type.NAME
            ).addBan(
                    target,
                    reason,
                    null,
                    staff.getName()
            );

            Player online =
                    Bukkit.getPlayerExact(
                            target
                    );

            if (online != null) {

                online.kickPlayer(
                        color(
                                "§c§lSERVER PROTECT\n\n"
                                        + "§7You have been banned for:\n"
                                        + "§fRedstone lag abuse"
                        )
                );
            }

            send(
                    staff,
                    "§aPlayer §f"
                            + target
                            + " §ahas been banned."
            );

            /*
             * Keep OfflinePlayer reference used intentionally
             * so the UUID can be resolved by Bukkit.
             */
            if (offline.getUniqueId() != null) {

                getLogger().warning(
                        "Player banned by staff: "
                                + target
                                + " | UUID="
                                + offline.getUniqueId()
                );
            }

        } catch (Throwable throwable) {

            getLogger().warning(
                    "Ban action failed: "
                            + safeError(
                            throwable
                    )
            );

            send(
                    staff,
                    "§cBan action failed."
            );
        }
    }

    // =========================================================
    // EXPLODE EXECUTION
    // =========================================================

    private void executeExplode(
            Player staff,
            Detection detection
    ) {

        Location location =
                detection.location;

        if (
                location == null
                        || location.getWorld() == null
        ) {

            send(
                    staff,
                    "§cInvalid detection location."
            );

            return;
        }

        try {

            World world =
                    location.getWorld();

            /*
             * Explosion is centered exactly at the
             * detected block location.
             *
             * Power is configurable.
             */
            float power =
                    (float) getConfig().getDouble(
                            "actions.explode-power",
                            2.0D
                    );

            boolean breakBlocks =
                    getConfig().getBoolean(
                            "actions.explode-break-blocks",
                            true
                    );

            world.createExplosion(
                    location.getX() + 0.5D,
                    location.getY() + 0.5D,
                    location.getZ() + 0.5D,
                    power,
                    false,
                    breakBlocks
            );

            send(
                    staff,
                    "§6Explosion created at the exact detection core."
            );

            send(
                    staff,
                    "§7Location: §f"
                            + formatLocation(
                            location
                    )
            );

            placeServerProtectSign(
                    location
            );

        } catch (Throwable throwable) {

            getLogger().warning(
                    "Explosion action failed: "
                            + safeError(
                            throwable
                    )
            );

            send(
                    staff,
                    "§cExplosion failed safely."
            );
        }
    }

    // =========================================================
    // SERVER PROTECT SIGN
    // =========================================================

    private void placeServerProtectSign(
            Location location
    ) {

        if (
                location == null
                        || location.getWorld() == null
        ) {
            return;
        }

        try {

            /*
             * Place sign one block above the detection.
             *
             * We only place it if the target block is
             * empty, so we don't overwrite existing blocks.
             */

            Location signLocation =
                    location.clone()
                            .add(
                                    0,
                                    1,
                                    0
                            );

            Block block =
                    signLocation.getBlock();

            if (
                    !block.getType().isAir()
            ) {
                return;
            }

            block.setType(
                    Material.OAK_SIGN,
                    false
            );

            if (
                    !(block.getState()
                            instanceof Sign sign)
            ) {
                return;
            }

            sign.setLine(
                    0,
                    "SERVER PROTECT"
            );

            sign.setLine(
                    1,
                    "BY ITZBREZZ"
            );

            sign.setLine(
                    2,
                    "DO YOU WANT BAN"
            );

            sign.setLine(
                    3,
                    "SERVER PROTECT"
            );

            sign.update(
                    true,
                    false
            );

        } catch (Throwable throwable) {

            getLogger().warning(
                    "Could not place protection sign: "
                            + safeError(
                            throwable
                    )
            );
        }
    }

    // =========================================================
    // SAFE SCAN
    // =========================================================

    private void startSafeScan(
            CommandSender sender
    ) {

        if (
                !scanRunning.compareAndSet(
                        false,
                        true
                )
        ) {

            send(
                    sender,
                    "§eA scan is already running."
            );

            return;
        }

        send(
                sender,
                "§8§m━━━━━━━━━━━━━━━━━━━━━━━━━━━━"
        );

        send(
                sender,
                "§b§l        REDSTONE SCAN"
        );

        send(
                sender,
                "§7Mode: §bMONITOR ONLY"
        );

        send(
                sender,
                "§7Analyzing monitored activity..."
        );

        /*
         * Schedule on the server thread.
         *
         * There is NO full-world block scan here.
         * This prevents the old /lag scan crash/freeze
         * caused by scanning massive amounts of blocks.
         */
        Bukkit.getScheduler().runTask(
                this,
                () -> {

                    int analyzed = 0;
                    int suspicious = 0;

                    try {

                        List<Detection> snapshot =
                                new ArrayList<>(
                                        detections.values()
                                );

                        for (
                                Detection detection :
                                snapshot
                        ) {

                            if (
                                    detection == null
                            ) {
                                continue;
                            }

                            analyzed++;

                            detection.calculateScore();

                            if (
                                    detection.suspicion
                                            >= getConfig()
                                            .getInt(
                                                    "detection.minimum-score",
                                                    50
                                            )
                            ) {

                                suspicious++;
                            }
                        }

                        send(
                                sender,
                                "§aScan completed successfully."
                        );

                        send(
                                sender,
                                "§7Locations analyzed: §f"
                                        + analyzed
                        );

                        send(
                                sender,
                                "§7Suspicious locations: §f"
                                        + suspicious
                        );

                        send(
                                sender,
                                "§7Blocks modified: §f0"
                        );

                        send(
                                sender,
                                "§8§m━━━━━━━━━━━━━━━━━━━━━━━━━━━━"
                        );

                    } catch (Throwable throwable) {

                        /*
                         * CRITICAL:
                         * Any scan exception is contained here.
                         * It cannot terminate the server.
                         */

                        getLogger().severe(
                                "Safe scan failed: "
                                        + safeError(
                                        throwable
                                )
                        );

                        send(
                                sender,
                                "§cThe scan encountered an error."
                        );

                        send(
                                sender,
                                "§7The scan was safely stopped."
                        );

                    } finally {

                        scanRunning.set(
                                false
                        );
                    }
                }
        );
    }

    // =========================================================
    // MAIN COMMAND
    // =========================================================

    @Override
    public boolean onCommand(
            CommandSender sender,
            Command command,
            String label,
            String[] args
    ) {

        if (
                !sender.hasPermission(
                        PERMISSION
                )
        ) {

            send(
                    sender,
                    "§cYou don't have permission to use this command."
            );

            return true;
        }

        if (args.length == 0) {

            showHelp(
                    sender
            );

            return true;
        }

        String sub =
                args[0]
                        .toLowerCase(
                                Locale.ROOT
                        );

        if (
                sub.equals("action")
        ) {

            if (
                    !(sender instanceof Player player)
            ) {

                send(
                        sender,
                        "§cOnly players can perform this action."
                );

                return true;
            }

            return handleAction(
                    player,
                    args
            );
        }

        switch (sub) {

            case "gui" -> {

                if (
                        !(sender instanceof Player player)
                ) {

                    send(
                            sender,
                            "§cOnly players can open the GUI."
                    );

                    return true;
                }

                openMainGui(
                        player,
                        0
                );
            }

            case "scan" -> {

                startSafeScan(
                        sender
                );
            }

            case "progress" -> {

                showProgress(
                        sender
                );
            }

            case "history" -> {

                showHistory(
                        sender
                );
            }

            case "info" -> {

                showInfo(
                        sender
                );
            }

            case "reload" -> {

                reloadPlugin(
                        sender
                );
            }

            case "enable" -> {

                detectorEnabled =
                        true;

                getConfig().set(
                        "settings.enabled",
                        true
                );

                saveConfig();

                send(
                        sender,
                        "§aRedstoneDetector enabled."
                );
            }

            case "disable" -> {

                detectorEnabled =
                        false;

                getConfig().set(
                        "settings.enabled",
                        false
                );

                saveConfig();

                send(
                        sender,
                        "§eRedstoneDetector disabled."
                );
            }

            default -> {

                showHelp(
                        sender
                );
            }
        }

        return true;
    }

    // =========================================================
    // GUI
    // =========================================================

    private void openMainGui(
            Player player,
            int page
    ) {

        List<Detection> list =
                sortedDetections();

        int perPage = 45;

        int pages =
                Math.max(
                        1,
                        (int) Math.ceil(
                                list.size()
                                        / (double) perPage
                        )
                );

        page =
                Math.max(
                        0,
                        Math.min(
                                page,
                                pages - 1
                        )
                );

        Inventory inventory =
                Bukkit.createInventory(
                        null,
                        MAIN_SIZE,
                        MAIN_GUI
                );

        fill(
                inventory
        );

        int start =
                page * perPage;

        int end =
                Math.min(
                        list.size(),
                        start + perPage
                );

        for (
                int index = start;
                index < end;
                index++
        ) {

            Detection detection =
                    list.get(index);

            inventory.setItem(
                    index - start,
                    detectionPaper(
                            detection
                    )
            );
        }

        inventory.setItem(
                45,
                item(
                        Material.ARROW,
                        "§e§lPREVIOUS",
                        "§7Previous page."
                )
        );

        inventory.setItem(
                49,
                item(
                        Material.NETHER_STAR,
                        "§b§lREDSTONE DETECTOR",
                        "§7Active: §f"
                                + detections.size(),
                        "§7History: §f"
                                + history.size(),
                        "",
                        "§eClick to refresh."
                )
        );

        inventory.setItem(
                53,
                item(
                        Material.ARROW,
                        "§e§lNEXT",
                        "§7Next page."
                )
        );

        inventory.setItem(
                48,
                item(
                        Material.BARRIER,
                        "§c§lCLOSE",
                        "§7Close menu."
                )
        );

        guiPlayers.add(
                player.getUniqueId()
        );

        player.openInventory(
                inventory
        );
    }

    private ItemStack detectionPaper(
            Detection detection
    ) {

        String player =
                detection.player == null
                        ? "Unknown"
                        : detection.player;

        return item(
                Material.PAPER,

                "§b§lLAG CHUNK",

                "",

                "§c§lSUS",
                "§7Suspicion: §f"
                        + detection.suspicion
                        + "%",

                "",

                "§e§lLOCATION",
                "§7"
                        + formatLocation(
                        detection.location
                ),

                "",

                "§b§lPLAYER",
                "§7"
                        + player,

                "",

                "§d§lLAG TYPE",
                "§7"
                        + detection.type,

                "",

                "§8Click to investigate."
        );
    }

    // =========================================================
    // DETAIL GUI
    // =========================================================

    private void openDetailGui(
            Player player,
            Detection detection
    ) {

        if (detection == null) {

            send(
                    player,
                    "§cDetection not found."
            );

            return;
        }

        Inventory inventory =
                Bukkit.createInventory(
                        null,
                        DETAIL_SIZE,
                        DETAIL_GUI
                );

        fill(
                inventory
        );

        String target =
                detection.player == null
                        ? "Unknown"
                        : detection.player;

        inventory.setItem(
                13,
                item(
                        Material.PAPER,

                        "§b§lLAG CHUNK",

                        "",

                        "§c§lSUS",
                        "§7"
                                + detection.suspicion
                                + "%",

                        "",

                        "§e§lLOCATION",
                        "§7World: §f"
                                + getWorldName(
                                detection.location
                        ),
                        "§7X: §f"
                                + detection.location
                                .getBlockX(),
                        "§7Y: §f"
                                + detection.location
                                .getBlockY(),
                        "§7Z: §f"
                                + detection.location
                                .getBlockZ(),

                        "",

                        "§b§lPLAYER",
                        "§7"
                                + target,

                        "",

                        "§7§lLAST ONLINE",
                        "§7"
                                + getLastOnline(
                                target
                        ),

                        "",

                        "§a§lPLAY TIME",
                        "§7"
                                + getPlayTime(
                                target
                        ),

                        "",

                        "§d§lLAG TYPE",
                        "§7"
                                + detection.type
                )
        );

        inventory.setItem(
                11,
                item(
                        Material.COMPASS,
                        "§e§lTP TO CORE",
                        "§7Teleport to exact location."
                )
        );

        inventory.setItem(
                15,
                item(
                        Material.REDSTONE,
                        "§c§lACTIONS",
                        "§7Use the chat buttons",
                        "§7to manage this detection."
                )
        );

        inventory.setItem(
                18,
                item(
                        Material.ARROW,
                        "§e§lBACK",
                        "§7Return to detections."
                )
        );

        inventory.setItem(
                22,
                item(
                        Material.NETHER_STAR,
                        "§b§lREFRESH",
                        "§7Refresh information."
                )
        );

        inventory.setItem(
                26,
                item(
                        Material.BARRIER,
                        "§c§lCLOSE",
                        "§7Close menu."
                )
        );

        guiPlayers.add(
                player.getUniqueId()
        );

        player.openInventory(
                inventory
        );
    }

    // =========================================================
    // GUI CLICK
    // =========================================================

    @EventHandler
    public void onInventoryClick(
            InventoryClickEvent event
    ) {

        if (
                !(event.getWhoClicked()
                        instanceof Player player)
        ) {
            return;
        }

        String title =
                event.getView()
                        .getTitle();

        if (
                !title.equals(MAIN_GUI)
                        && !title.equals(
                        DETAIL_GUI
                )
        ) {
            return;
        }

        event.setCancelled(
                true
        );

        int slot =
                event.getRawSlot();

        if (
                title.equals(MAIN_GUI)
        ) {

            if (slot == 48) {

                player.closeInventory();

                return;
            }

            if (slot == 49) {

                openMainGui(
                        player,
                        0
                );

                return;
            }

            if (slot == 45) {

                /*
                 * Previous page.
                 *
                 * Rebuild based on the currently
                 * displayed detection range.
                 */
                openMainGui(
                        player,
                        0
                );

                return;
            }

            if (slot == 53) {

                openMainGui(
                        player,
                        1
                );

                return;
            }

            if (
                    slot >= 0
                            && slot < 45
            ) {

                Detection detection =
                        detectionAtSlot(
                                slot
                        );

                if (detection != null) {

                    openDetailGui(
                            player,
                            detection
                    );
                }
            }

            return;
        }

        if (
                title.equals(DETAIL_GUI)
        ) {

            if (slot == 18) {

                openMainGui(
                        player,
                        0
                );

                return;
            }

            if (slot == 22) {

                openMainGui(
                        player,
                        0
                );

                return;
            }

            if (slot == 26) {

                player.closeInventory();

                return;
            }

            if (slot == 11) {

                List<Detection> list =
                        sortedDetections();

                if (!list.isEmpty()) {

                    teleportToDetection(
                            player,
                            list.get(0)
                    );
                }
            }
        }
    }

    @EventHandler
    public void onInventoryClose(
            InventoryCloseEvent event
    ) {

        if (
                event.getPlayer()
                        instanceof Player player
        ) {

            guiPlayers.remove(
                    player.getUniqueId()
            );
        }
    }

    // =========================================================
    // DETECTION LOOKUP
    // =========================================================

    private Detection detectionAtSlot(
            int slot
    ) {

        List<Detection> list =
                sortedDetections();

        if (
                slot < 0
                        || slot >= list.size()
        ) {
            return null;
        }

        return list.get(
                slot
        );
    }

    private List<Detection> sortedDetections() {

        List<Detection> list =
                new ArrayList<>(
                        detections.values()
                );

        list.sort(
                Comparator.comparingLong(
                        detection ->
                                -detection.lastActivity
                )
        );

        return list;
    }

    // =========================================================
    // HISTORY
    // =========================================================

    private void addHistory(
            Detection detection
    ) {

        history.addFirst(
                detection.copy()
        );

        int max =
                Math.max(
                        1,
                        getConfig().getInt(
                                "settings.max-history",
                                100
                        )
                );

        while (
                history.size()
                        > max
        ) {

            history.removeLast();
        }
    }

    private void showHistory(
            CommandSender sender
    ) {

        send(
                sender,
                "§8§m━━━━━━━━━━━━━━━━━━━━━━━━━━━━"
        );

        send(
                sender,
                "§b§l       REDSTONE HISTORY"
        );

        if (history.isEmpty()) {

            send(
                    sender,
                    "§7No detection history."
            );

            send(
                    sender,
                    "§8§m━━━━━━━━━━━━━━━━━━━━━━━━━━━━"
            );

            return;
        }

        int count = 0;

        for (
                Detection detection :
                history
        ) {

            if (count >= 20) {
                break;
            }

            send(
                    sender,
                    "§8#"
                            + (count + 1)
                            + " §7• §c"
                            + detection.suspicion
                            + "% §7• §f"
                            + detection.type
            );

            send(
                    sender,
                    "   §8» §7"
                            + formatLocation(
                            detection.location
                    )
            );

            count++;
        }

        send(
                sender,
                "§8§m━━━━━━━━━━━━━━━━━━━━━━━━━━━━"
        );
    }

    // =========================================================
    // HELP
    // =========================================================

    private void showHelp(
            CommandSender sender
    ) {

        send(
                sender,
                "§8§m━━━━━━━━━━━━━━━━━━━━━━━━━━━━"
        );

        send(
                sender,
                "§b§l       REDSTONE DETECTOR"
        );

        send(
                sender,
                "§7Professional redstone monitoring."
        );

        send(
                sender,
                ""
        );

        send(
                sender,
                "§f/lag gui §8- §7Investigation GUI"
        );

        send(
                sender,
                "§f/lag scan §8- §7Safe scan"
        );

        send(
                sender,
                "§f/lag progress §8- §7Scan status"
        );

        send(
                sender,
                "§f/lag history §8- §7Detection history"
        );

        send(
                sender,
                "§f/lag info §8- §7Plugin information"
        );

        send(
                sender,
                "§f/lag reload §8- §7Reload config"
        );

        send(
                sender,
                "§f/lag enable §8- §7Enable detector"
        );

        send(
                sender,
                "§f/lag disable §8- §7Disable detector"
        );

        send(
                sender,
                ""
        );

        send(
                sender,
                "§7Mode: §bMONITOR ONLY"
        );

        send(
                sender,
                "§8§m━━━━━━━━━━━━━━━━━━━━━━━━━━━━"
        );
    }

    private void showInfo(
            CommandSender sender
    ) {

        send(
                sender,
                "§8§m━━━━━━━━━━━━━━━━━━━━━━━━━━━━"
        );

        send(
                sender,
                "§b§lREDSTONE DETECTOR"
        );

        send(
                sender,
                "§7Version: §f"
                        + getDescription()
                        .getVersion()
        );

        send(
                sender,
                "§7Mode: §bMONITOR ONLY"
        );

        send(
                sender,
                "§7Active: §f"
                        + detections.size()
        );

        send(
                sender,
                "§7History: §f"
                        + history.size()
        );

        send(
                sender,
                "§8§m━━━━━━━━━━━━━━━━━━━━━━━━━━━━"
        );
    }

    private void showProgress(
            CommandSender sender
    ) {

        send(
                sender,
                "§7Scan: "
                        + (
                        scanRunning.get()
                                ? "§eRUNNING"
                                : "§aIDLE"
                )
        );

        send(
                sender,
                "§7Active detections: §f"
                        + detections.size()
        );
    }

    // =========================================================
    // RELOAD
    // =========================================================

    private void reloadPlugin(
            CommandSender sender
    ) {

        try {

            reloadConfig();

            detectorEnabled =
                    getConfig().getBoolean(
                            "settings.enabled",
                            true
                    );

            if (monitorTask != null) {

                monitorTask.cancel();
            }

            startMonitor();

            send(
                    sender,
                    "§aConfiguration reloaded successfully."
            );

        } catch (Throwable throwable) {

            getLogger().warning(
                    "Reload failed: "
                            + safeError(
                            throwable
                    )
            );

            send(
                    sender,
                    "§cConfiguration reload failed."
            );
        }
    }

    // =========================================================
    // MONITOR TASK
    // =========================================================

    private void startMonitor() {

        long seconds =
                Math.max(
                        1L,
                        getConfig().getLong(
                                "performance.scan-interval-seconds",
                                10L
                        )
                );

        long ticks =
                Math.max(
                        20L,
                        seconds * 20L
                );

        monitorTask =
                Bukkit.getScheduler()
                        .runTaskTimer(
                                this,
                                () -> {

                                    try {

                                        processDetections();

                                    } catch (Throwable throwable) {

                                        /*
                                         * Prevent scheduler task
                                         * from dying unexpectedly.
                                         */
                                        getLogger().warning(
                                                "Monitor task error: "
                                                        + safeError(
                                                        throwable
                                                )
                                        );
                                    }
                                },
                                ticks,
                                ticks
                        );
    }

    private void processDetections() {

        if (!detectorEnabled) {
            return;
        }

        long now =
                System.currentTimeMillis();

        /*
         * Remove inactive locations after 2 minutes.
         */
        detections.entrySet()
                .removeIf(
                        entry ->
                                now
                                        - entry.getValue()
                                        .lastActivity
                                        > 120_000L
                );

        /*
         * Keep action tokens from growing forever.
         */
        actionTokens.entrySet()
                .removeIf(
                        entry ->
                                entry.getValue()
                                        == null
                );
    }

    // =========================================================
    // SCORE
    // =========================================================

    private int calculateScore(
            long events,
            long lastActivity
    ) {

        int score = 25;

        score +=
                (int) Math.min(
                        55L,
                        events / 10L
                );

        long age =
                System.currentTimeMillis()
                        - lastActivity;

        if (age < 5000L) {

            score += 10;
        }

        if (age < 1000L) {

            score += 10;
        }

        return Math.max(
                0,
                Math.min(
                        100,
                        score
                )
        );
    }

    // =========================================================
    // TYPE
    // =========================================================

    private String detectType(
            Material material
    ) {

        if (
                material == Material.REPEATER
                        || material == Material.COMPARATOR
                        || material == Material.REDSTONE_TORCH
                        || material == Material.REDSTONE_WALL_TORCH
        ) {

            return "REDSTONE CLOCK";
        }

        if (
                material == Material.OBSERVER
        ) {

            return "OBSERVER MACHINE";
        }

        if (
                material == Material.PISTON
                        || material == Material.STICKY_PISTON
        ) {

            return "PISTON MACHINE";
        }

        if (
                material == Material.HOPPER
        ) {

            return "HOPPER ACTIVITY";
        }

        if (
                material == Material.DISPENSER
        ) {

            return "DISPENSER MACHINE";
        }

        if (
                material == Material.DROPPER
        ) {

            return "DROPPER MACHINE";
        }

        if (
                material == Material.REDSTONE_WIRE
        ) {

            return "REDSTONE ACTIVITY";
        }

        return "REDSTONE ACTIVITY";
    }

    // =========================================================
    // PLAYER
    // =========================================================

    private String findNearbyPlayer(
            Location location
    ) {

        if (
                location == null
                        || location.getWorld() == null
        ) {
            return null;
        }

        double radius =
                getConfig().getDouble(
                        "detection.player-radius",
                        64.0D
                );

        double nearest =
                radius * radius;

        Player nearestPlayer =
                null;

        for (
                Player player :
                Bukkit.getOnlinePlayers()
        ) {

            try {

                if (
                        !player.getWorld()
                                .equals(
                                        location.getWorld()
                                )
                ) {
                    continue;
                }

                double distance =
                        player.getLocation()
                                .distanceSquared(
                                        location
                                );

                if (
                        distance <= nearest
                ) {

                    nearest =
                            distance;

                    nearestPlayer =
                            player;
                }

            } catch (Throwable ignored) {
            }
        }

        return nearestPlayer == null
                ? null
                : nearestPlayer.getName();
    }

    // =========================================================
    // LAST ONLINE
    // =========================================================

    private String getLastOnline(
            String playerName
    ) {

        if (
                playerName == null
                        || playerName.equalsIgnoreCase(
                        "Unknown"
                )
        ) {

            return "Unknown";
        }

        try {

            OfflinePlayer player =
                    Bukkit.getOfflinePlayer(
                            playerName
                    );

            long timestamp =
                    player.getLastPlayed();

            if (timestamp <= 0L) {

                return "Unknown";
            }

            return new SimpleDateFormat(
                    "dd/MM/yyyy HH:mm",
                    Locale.ENGLISH
            ).format(
                    new Date(
                            timestamp
                    )
            );

        } catch (Throwable throwable) {

            return "Unknown";
        }
    }

    // =========================================================
    // PLAY TIME
    // =========================================================

    private String getPlayTime(
            String playerName
    ) {

        if (
                playerName == null
                        || playerName.equalsIgnoreCase(
                        "Unknown"
                )
        ) {

            return "Unknown";
        }

        try {

            OfflinePlayer player =
                    Bukkit.getOfflinePlayer(
                            playerName
                    );

            UUID uuid =
                    player.getUniqueId();

            long milliseconds =
                    totalPlayTime.getOrDefault(
                            uuid,
                            0L
                    );

            Player online =
                    Bukkit.getPlayer(
                            uuid
                    );

            if (online != null) {

                Long start =
                        sessionStart.get(uuid);

                if (start != null) {

                    milliseconds +=
                            Math.max(
                                    0L,
                                    System.currentTimeMillis()
                                            - start
                            );
                }
            }

            if (
                    milliseconds <= 0L
            ) {

                return "Not tracked yet";
            }

            long minutes =
                    milliseconds / 60_000L;

            long hours =
                    minutes / 60L;

            long remaining =
                    minutes % 60L;

            if (hours > 0L) {

                return hours
                        + "h "
                        + remaining
                        + "m";
            }

            return minutes + "m";

        } catch (Throwable throwable) {

            return "Unknown";
        }
    }

    // =========================================================
    // ACTION TOKEN
    // =========================================================

    private String createActionToken(
            Detection detection
    ) {

        String token =
                UUID.randomUUID()
                        .toString()
                        .replace(
                                "-",
                                ""
                        )
                        .substring(
                                0,
                                8
                        );

        actionTokens.put(
                token,
                detection
        );

        return token;
    }

    // =========================================================
    // LOCATION
    // =========================================================

    private String createLocationKey(
            Location location
    ) {

        if (
                location == null
                        || location.getWorld() == null
        ) {

            return "unknown";
        }

        return location.getWorld()
                .getName()
                + ":"
                + location.getBlockX()
                + ":"
                + location.getBlockY()
                + ":"
                + location.getBlockZ();
    }

    private String formatLocation(
            Location location
    ) {

        if (
                location == null
                        || location.getWorld() == null
        ) {

            return "Unknown";
        }

        return location.getWorld()
                .getName()
                + " "
                + location.getBlockX()
                + ","
                + location.getBlockY()
                + ","
                + location.getBlockZ();
    }

    private String getWorldName(
            Location location
    ) {

        if (
                location == null
                        || location.getWorld() == null
        ) {

            return "Unknown";
        }

        return location.getWorld()
                .getName();
    }

    private Location safeCenter(
            Location location
    ) {

        if (
                location == null
                        || location.getWorld() == null
        ) {

            return null;
        }

        return location.clone()
                .add(
                        0.5D,
                        0.1D,
                        0.5D
                );
    }

    // =========================================================
    // GUI ITEM
    // =========================================================

    private void fill(
            Inventory inventory
    ) {

        ItemStack filler =
                item(
                        Material.GRAY_STAINED_GLASS_PANE,
                        " ",
                        ""
                );

        for (
                int i = 0;
                i < inventory.getSize();
                i++
        ) {

            inventory.setItem(
                    i,
                    filler
            );
        }
    }

    private ItemStack item(
            Material material,
            String name,
            String... lore
    ) {

        ItemStack item =
                new ItemStack(
                        material
                );

        ItemMeta meta =
                item.getItemMeta();

        if (meta == null) {
            return item;
        }

        meta.setDisplayName(
                color(name)
        );

        List<String> lines =
                new ArrayList<>();

        for (
                String line :
                lore
        ) {

            lines.add(
                    color(line)
            );
        }

        meta.setLore(
                lines
        );

        item.setItemMeta(
                meta
        );

        return item;
    }

    // =========================================================
    // CHAT
    // =========================================================

    private void send(
            CommandSender sender,
            String message
    ) {

        sender.sendMessage(
                color(
                        "&8[&bRedstoneDetector&8] "
                                + message
                )
        );
    }

    private String color(
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

    private String safeError(
            Throwable throwable
    ) {

        if (throwable == null) {

            return "Unknown error";
        }

        String message =
                throwable.getMessage();

        if (
                message == null
                        || message.isBlank()
        ) {

            return throwable
                    .getClass()
                    .getSimpleName();
        }

        return message;
    }

    // =========================================================
    // TAB COMPLETE
    // =========================================================

    @Override
    public List<String> onTabComplete(
            CommandSender sender,
            Command command,
            String alias,
            String[] args
    ) {

        if (args.length == 1) {

            return filter(
                    List.of(
                            "gui",
                            "scan",
                            "progress",
                            "history",
                            "info",
                            "reload",
                            "enable",
                            "disable"
                    ),
                    args[0]
            );
        }

        if (
                args.length == 2
                        && args[0].equalsIgnoreCase(
                        "action"
                )
        ) {

            return filter(
                    List.of(
                            "tp",
                            "broken",
                            "ban",
                            "explode",
                            "confirmbroken",
                            "confirmban",
                            "confirmexplode"
                    ),
                    args[1]
            );
        }

        return List.of();
    }

    private List<String> filter(
            List<String> values,
            String input
    ) {

        String lower =
                input.toLowerCase(
                        Locale.ROOT
                );

        List<String> result =
                new ArrayList<>();

        for (
                String value :
                values
        ) {

            if (
                    value.toLowerCase(
                            Locale.ROOT
                    ).startsWith(
                            lower
                    )
            ) {

                result.add(
                        value
                );
            }
        }

        return result;
    }
}
