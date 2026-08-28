package id.itzbrezz.redstonedetector;

import org.bukkit.Bukkit;
import org.bukkit.Chunk;
import org.bukkit.World;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

public final class SafeScanner {

    private final JavaPlugin plugin;

    private final AtomicBoolean running = new AtomicBoolean(false);

    private BukkitTask task;

    private int processedChunks;
    private int totalChunks;
    private int processedBlocks;
    private int detections;

    public SafeScanner(JavaPlugin plugin) {
        this.plugin = plugin;
    }

    /**
     * Returns whether a scan is currently running.
     */
    public boolean isRunning() {
        return running.get();
    }

    public int getProcessedChunks() {
        return processedChunks;
    }

    public int getTotalChunks() {
        return totalChunks;
    }

    public int getProcessedBlocks() {
        return processedBlocks;
    }

    public int getDetections() {
        return detections;
    }

    /**
     * Start a safe scan.
     *
     * The scanner only processes chunks which are already loaded.
     * It never force-loads chunks.
     */
    public boolean start() {

        if (!plugin.getConfig().getBoolean(
                "settings.scan.enabled",
                true
        )) {
            return false;
        }

        if (!running.compareAndSet(false, true)) {
            return false;
        }

        processedChunks = 0;
        totalChunks = 0;
        processedBlocks = 0;
        detections = 0;

        try {

            List<Chunk> chunks = collectLoadedChunks();

            totalChunks = chunks.size();

            if (chunks.isEmpty()) {
                running.set(false);

                plugin.getLogger().info(
                        "[SafeScan] No loaded chunks available."
                );

                return true;
            }

            task = Bukkit.getScheduler().runTaskTimer(
                    plugin,
                    () -> processTick(chunks),
                    1L,
                    1L
            );

            plugin.getLogger().info(
                    "[SafeScan] Scan started. Loaded chunks: "
                            + totalChunks
            );

            return true;

        } catch (Throwable throwable) {

            running.set(false);

            plugin.getLogger().severe(
                    "[SafeScan] Failed to start scan safely: "
                            + throwable.getClass().getSimpleName()
                            + ": "
                            + throwable.getMessage()
            );

            return false;
        }
    }

    /**
     * Gets already-loaded chunks only.
     *
     * IMPORTANT:
     * We intentionally do NOT call:
     *
     * chunk.load()
     * world.loadChunk()
     * world.getChunkAt()
     *
     * because those operations can cause unwanted chunk loading.
     */
    private List<Chunk> collectLoadedChunks() {

        List<Chunk> result = new ArrayList<>();

        int maxChunks = Math.max(
                1,
                plugin.getConfig().getInt(
                        "settings.scan.max-chunks",
                        256
                )
        );

        for (World world : Bukkit.getWorlds()) {

            if (world == null) {
                continue;
            }

            Chunk[] loadedChunks;

            try {
                loadedChunks = world.getLoadedChunks();
            } catch (Throwable throwable) {

                plugin.getLogger().warning(
                        "[SafeScan] Could not read loaded chunks from world: "
                                + world.getName()
                );

                continue;
            }

            for (Chunk chunk : loadedChunks) {

                if (chunk == null) {
                    continue;
                }

                if (result.size() >= maxChunks) {
                    return result;
                }

                if (!chunk.isLoaded()) {
                    continue;
                }

                result.add(chunk);
            }
        }

        return result;
    }

    /**
     * Processes a small amount of work every server tick.
     */
    private void processTick(List<Chunk> chunks) {

        if (!running.get()) {
            stop();
            return;
        }

        try {

            /*
             * TPS protection.
             *
             * If the server is already under heavy load,
             * stop the scan rather than making the situation worse.
             */
            if (isServerUnderLoad()) {

                plugin.getLogger().warning(
                        "[SafeScan] Scan stopped because server TPS "
                                + "is below the configured safety threshold."
                );

                stop();

                return;
            }

            long startTime = System.nanoTime();

            long timeBudgetMs = Math.max(
                    1L,
                    plugin.getConfig().getLong(
                            "settings.scan.time-budget-ms",
                            8L
                    )
            );

            int blocksPerTick = Math.max(
                    1,
                    plugin.getConfig().getInt(
                            "settings.scan.blocks-per-tick",
                            2500
                    )
            );

            int blocksThisTick = 0;

            while (
                    processedChunks < chunks.size()
                            && blocksThisTick < blocksPerTick
            ) {

                /*
                 * Time budget protection.
                 */
                long elapsedMs =
                        (System.nanoTime() - startTime) / 1_000_000L;

                if (elapsedMs >= timeBudgetMs) {
                    break;
                }

                Chunk chunk = chunks.get(processedChunks);

                /*
                 * The chunk could have been unloaded after
                 * the scan started.
                 */
                if (chunk == null || !chunk.isLoaded()) {

                    processedChunks++;

                    continue;
                }

                int remaining =
                        blocksPerTick - blocksThisTick;

                int inspected = analyzeChunk(
                        chunk,
                        remaining
                );

                if (inspected < 0) {
                    inspected = 0;
                }

                blocksThisTick += inspected;
                processedBlocks += inspected;

                processedChunks++;
            }

            /*
             * Everything has been processed.
             */
            if (processedChunks >= chunks.size()) {
                finish();
            }

        } catch (Throwable throwable) {

            /*
             * VERY IMPORTANT:
             *
             * Never allow an exception from the detector
             * to propagate into the Minecraft server tick.
             */
            plugin.getLogger().severe(
                    "[SafeScan] Scan failed safely."
            );

            plugin.getLogger().severe(
                    "Reason: "
                            + throwable.getClass().getName()
                            + ": "
                            + throwable.getMessage()
            );

            stop();
        }
    }

    /**
     * Analyze one loaded chunk.
     *
     * This method is intentionally conservative.
     *
     * DO NOT:
     * - break blocks
     * - change blocks
     * - disable redstone
     * - modify redstone components
     * - force-load chunks
     */
    private int analyzeChunk(
            Chunk chunk,
            int limit
    ) {

        if (chunk == null || !chunk.isLoaded()) {
            return 0;
        }

        /*
         * ========================================================
         * REDSTONE ANALYSIS HOOK
         * ========================================================
         *
         * Connect your existing RedstoneDetector analysis here.
         *
         * The important part is that this method MUST respect
         * the supplied limit and MUST NOT perform destructive
         * operations.
         *
         * For now we only report a safe unit of work.
         */

        return Math.min(limit, 1);
    }

    /**
     * Check whether server performance is too low for scanning.
     */
    private boolean isServerUnderLoad() {

        boolean protection = plugin.getConfig().getBoolean(
                "settings.scan.tps-protection.enabled",
                true
        );

        if (!protection) {
            return false;
        }

        double minimumTps = plugin.getConfig().getDouble(
                "settings.scan.tps-protection.minimum-tps",
                15.0
        );

        try {

            double[] tps = Bukkit.getTPS();

            if (tps.length == 0) {
                return false;
            }

            double currentTps = tps[0];

            return currentTps < minimumTps;

        } catch (Throwable ignored) {

            /*
             * TPS could not be read.
         * Don't crash the scanner.
         */
            return false;
        }
    }

    /**
     * Finish the scan normally.
     */
    private void finish() {

        if (!running.compareAndSet(true, false)) {
            return;
        }

        cancelTask();

        plugin.getLogger().info(
                "[SafeScan] Scan completed successfully."
        );

        plugin.getLogger().info(
                "[SafeScan] Chunks: "
                        + processedChunks
                        + "/"
                        + totalChunks
        );

        plugin.getLogger().info(
                "[SafeScan] Blocks inspected: "
                        + processedBlocks
        );

        plugin.getLogger().info(
                "[SafeScan] Detections: "
                        + detections
        );
    }

    /**
     * Stop the scan safely.
     */
    public void stop() {

        running.set(false);

        cancelTask();
    }

    private void cancelTask() {

        if (task != null) {

            try {
                task.cancel();
            } catch (Throwable ignored) {
                // Intentionally ignored.
            }

            task = null;
        }
    }

    /**
     * Increase detection count safely.
     */
    public void addDetection() {

        if (detections < Integer.MAX_VALUE) {
            detections++;
        }
    }

    /**
     * Get progress from 0-100.
     */
    public double getProgress() {

        if (totalChunks <= 0) {
            return 0.0;
        }

        double progress =
                ((double) processedChunks / totalChunks) * 100.0;

        return Math.min(
                100.0,
                Math.max(0.0, progress)
        );
    }
          }
