package id.itzbrezz.redstonedetector;

import org.bukkit.Location;

import java.util.UUID;

/**
 * Represents one redstone lag detection.
 *
 * This class is intentionally public so that:
 * - RedstoneDetector can create/update detections
 * - LagGui can display detections
 * - Other plugin classes can safely access detection data
 */
public class Detection {

    /**
     * Unique ID for this detection.
     */
    private final String id;

    /**
     * Exact location of the detected redstone source/core.
     */
    private Location location;

    /**
     * Player associated with the detection.
     *
     * May be null when no player could be identified.
     */
    private String player;

    /**
     * UUID of the associated player.
     *
     * May be null.
     */
    private UUID playerUuid;

    /**
     * Type of lag detected.
     *
     * Examples:
     * REDSTONE CLOCK
     * OBSERVER MACHINE
     * PISTON MACHINE
     * HOPPER ACTIVITY
     * DISPENSER MACHINE
     * DROPPER MACHINE
     * REDSTONE ACTIVITY
     * EXPLOSION
     */
    private String type;

    /**
     * Suspicion percentage from 0 to 100.
     */
    private int suspicion;

    /**
     * Number of redstone events observed.
     */
    private long events;

    /**
     * Number of activations/pulses observed.
     */
    private long activations;

    /**
     * Number of block events associated with this detection.
     */
    private long blockEvents;

    /**
     * Time of the first detected activity.
     */
    private long firstActivity;

    /**
     * Time of the most recent detected activity.
     */
    private long lastActivity;

    /**
     * Last time this detection generated a staff notification.
     */
    private long lastAlert;

    /**
     * Whether this detection has already alerted staff.
     */
    private boolean alerted;

    /**
     * Whether staff has manually investigated this detection.
     */
    private boolean investigated;

    /**
     * Whether the source has been manually broken.
     */
    private boolean broken;

    /**
     * Whether an explosion action has been executed.
     */
    private boolean exploded;

    /**
     * Whether the associated player has been banned.
     */
    private boolean banned;

    /**
     * Creates a new detection.
     *
     * @param location exact detection location
     * @param type detection type
     */
    public Detection(
            Location location,
            String type
    ) {

        this.id =
                UUID.randomUUID()
                        .toString()
                        .replace("-", "")
                        .substring(0, 8);

        this.location =
                location == null
                        ? null
                        : location.clone();

        this.type =
                type == null
                        ? "REDSTONE ACTIVITY"
                        : type;

        this.player = null;
        this.playerUuid = null;

        this.suspicion = 0;

        this.events = 0L;
        this.activations = 0L;
        this.blockEvents = 0L;

        long now =
                System.currentTimeMillis();

        this.firstActivity = now;
        this.lastActivity = now;
        this.lastAlert = 0L;

        this.alerted = false;
        this.investigated = false;
        this.broken = false;
        this.exploded = false;
        this.banned = false;
    }

    /**
     * Returns the unique detection ID.
     */
    public String getId() {
        return id;
    }

    /**
     * Returns a cloned location.
     *
     * Returning a clone prevents external code from
     * accidentally changing the internal location.
     */
    public Location getLocation() {

        return location == null
                ? null
                : location.clone();
    }

    /**
     * Updates the exact detection location.
     */
    public void setLocation(
            Location location
    ) {

        this.location =
                location == null
                        ? null
                        : location.clone();
    }

    /**
     * Returns the player name.
     */
    public String getPlayer() {
        return player;
    }

    /**
     * Sets the associated player name.
     */
    public void setPlayer(
            String player
    ) {

        this.player = player;
    }

    /**
     * Returns the associated player UUID.
     */
    public UUID getPlayerUuid() {
        return playerUuid;
    }

    /**
     * Sets the associated player UUID.
     */
    public void setPlayerUuid(
            UUID playerUuid
    ) {

        this.playerUuid =
                playerUuid;
    }

    /**
     * Returns the detection type.
     */
    public String getType() {
        return type;
    }

    /**
     * Sets the detection type.
     */
    public void setType(
            String type
    ) {

        this.type =
                type == null
                        ? "REDSTONE ACTIVITY"
                        : type;
    }

    /**
     * Returns suspicion percentage.
     */
    public int getSuspicion() {
        return suspicion;
    }

    /**
     * Sets suspicion percentage.
     *
     * Value is automatically clamped between 0 and 100.
     */
    public void setSuspicion(
            int suspicion
    ) {

        this.suspicion =
                Math.max(
                        0,
                        Math.min(
                                100,
                                suspicion
                        )
                );
    }

    /**
     * Returns number of redstone events.
     */
    public long getEvents() {
        return events;
    }

    /**
     * Sets number of redstone events.
     */
    public void setEvents(
            long events
    ) {

        this.events =
                Math.max(
                        0L,
                        events
                );
    }

    /**
     * Adds one redstone event.
     */
    public void incrementEvents() {

        this.events++;
        this.lastActivity =
                System.currentTimeMillis();
    }

    /**
     * Returns number of activations.
     */
    public long getActivations() {
        return activations;
    }

    /**
     * Sets number of activations.
     */
    public void setActivations(
            long activations
    ) {

        this.activations =
                Math.max(
                        0L,
                        activations
                );
    }

    /**
     * Adds one activation.
     */
    public void incrementActivations() {

        this.activations++;
        this.lastActivity =
                System.currentTimeMillis();
    }

    /**
     * Returns block event count.
     */
    public long getBlockEvents() {
        return blockEvents;
    }

    /**
     * Sets block event count.
     */
    public void setBlockEvents(
            long blockEvents
    ) {

        this.blockEvents =
                Math.max(
                        0L,
                        blockEvents
                );
    }

    /**
     * Adds one block event.
     */
    public void incrementBlockEvents() {

        this.blockEvents++;
        this.lastActivity =
                System.currentTimeMillis();
    }

    /**
     * Returns first activity timestamp.
     */
    public long getFirstActivity() {
        return firstActivity;
    }

    /**
     * Sets first activity timestamp.
     */
    public void setFirstActivity(
            long firstActivity
    ) {

        this.firstActivity =
                firstActivity;
    }

    /**
     * Returns last activity timestamp.
     */
    public long getLastActivity() {
        return lastActivity;
    }

    /**
     * Sets last activity timestamp.
     */
    public void setLastActivity(
            long lastActivity
    ) {

        this.lastActivity =
                lastActivity;
    }

    /**
     * Marks this detection as recently active.
     */
    public void touch() {

        long now =
                System.currentTimeMillis();

        if (firstActivity <= 0L) {

            firstActivity =
                    now;
        }

        lastActivity =
                now;
    }

    /**
     * Returns last alert timestamp.
     */
    public long getLastAlert() {
        return lastAlert;
    }

    /**
     * Sets last alert timestamp.
     */
    public void setLastAlert(
            long lastAlert
    ) {

        this.lastAlert =
                lastAlert;
    }

    /**
     * Returns whether staff was already alerted.
     */
    public boolean isAlerted() {
        return alerted;
    }

    /**
     * Sets alerted state.
     */
    public void setAlerted(
            boolean alerted
    ) {

        this.alerted =
                alerted;
    }

    /**
     * Returns whether staff investigated this detection.
     */
    public boolean isInvestigated() {
        return investigated;
    }

    /**
     * Sets investigated state.
     */
    public void setInvestigated(
            boolean investigated
    ) {

        this.investigated =
                investigated;
    }

    /**
     * Returns whether source was broken.
     */
    public boolean isBroken() {
        return broken;
    }

    /**
     * Sets broken state.
     */
    public void setBroken(
            boolean broken
    ) {

        this.broken =
                broken;
    }

    /**
     * Returns whether explosion action was executed.
     */
    public boolean isExploded() {
        return exploded;
    }

    /**
     * Sets exploded state.
     */
    public void setExploded(
            boolean exploded
    ) {

        this.exploded =
                exploded;
    }

    /**
     * Returns whether associated player was banned.
     */
    public boolean isBanned() {
        return banned;
    }

    /**
     * Sets banned state.
     */
    public void setBanned(
            boolean banned
    ) {

        this.banned =
                banned;
    }

    /**
     * Calculates suspicion based on activity.
     *
     * This is intentionally lightweight and does not
     * modify the world.
     */
    public void calculateSuspicion() {

        int score = 25;

        /*
         * Redstone events.
         */
        score +=
                (int) Math.min(
                        45L,
                        events / 10L
                );

        /*
         * Activations.
         */
        score +=
                (int) Math.min(
                        20L,
                        activations / 10L
                );

        /*
         * Recent activity.
         */
        long age =
                System.currentTimeMillis()
                        - lastActivity;

        if (age < 10_000L) {

            score += 5;
        }

        if (age < 2_000L) {

            score += 5;
        }

        setSuspicion(
                score
        );
    }

    /**
     * Returns how long this detection has been active.
     */
    public long getAgeMillis() {

        return Math.max(
                0L,
                System.currentTimeMillis()
                        - firstActivity
        );
    }

    /**
     * Returns how long ago the last activity happened.
     */
    public long getInactiveMillis() {

        return Math.max(
                0L,
                System.currentTimeMillis()
                        - lastActivity
        );
    }

    /**
     * Returns whether this detection is still active.
     */
    public boolean isActive() {

        return !broken
                && getInactiveMillis()
                < 120_000L;
    }

    /**
     * Returns whether the detection has an exact location.
     */
    public boolean hasLocation() {

        return location != null
                && location.getWorld() != null;
    }

    /**
     * Returns whether a player is associated.
     */
    public boolean hasPlayer() {

        return player != null
                && !player.isBlank()
                && !player.equalsIgnoreCase(
                        "Unknown"
                );
    }

    /**
     * Creates a safe copy of this detection.
     *
     * Useful for history storage.
     */
    public Detection copy() {

        Detection copy =
                new Detection(
                        this.location,
                        this.type
                );

        copy.player =
                this.player;

        copy.playerUuid =
                this.playerUuid;

        copy.suspicion =
                this.suspicion;

        copy.events =
                this.events;

        copy.activations =
                this.activations;

        copy.blockEvents =
                this.blockEvents;

        copy.firstActivity =
                this.firstActivity;

        copy.lastActivity =
                this.lastActivity;

        copy.lastAlert =
                this.lastAlert;

        copy.alerted =
                this.alerted;

        copy.investigated =
                this.investigated;

        copy.broken =
                this.broken;

        copy.exploded =
                this.exploded;

        copy.banned =
                this.banned;

        return copy;
    }

    /**
     * Returns a readable representation for debugging.
     */
    @Override
    public String toString() {

        return "Detection{"
                + "id='" + id + '\''
                + ", location=" + location
                + ", player='" + player + '\''
                + ", type='" + type + '\''
                + ", suspicion=" + suspicion
                + ", events=" + events
                + ", activations=" + activations
                + ", lastActivity=" + lastActivity
                + ", broken=" + broken
                + ", exploded=" + exploded
                + ", banned=" + banned
                + '}';
    }
          }
