package id.itzbrezz.redstonedetector;

import org.bukkit.Location;

import java.util.UUID;

/**
 * Represents a detected redstone lag source.
 *
 * This class is shared by:
 * - RedstoneDetector
 * - LagCommand
 * - LagGui
 */
public final class Detection {

    final String id;
    Location location;
    String type;
    long events;
    long lastActivity;
    int suspicion;
    String player;
    UUID playerUuid;

    /**
     * Creates a new detection.
     *
     * @param location exact detection location
     * @param type detection type
     */
    public Detection(Location location, String type) {

        this.id = UUID.randomUUID()
                .toString()
                .replace("-", "")
                .substring(0, 8);

        this.location = location == null
                ? null
                : location.clone();

        this.type = type == null
                ? "REDSTONE ACTIVITY"
                : type;

        this.events = 0L;
        this.lastActivity = System.currentTimeMillis();
        this.suspicion = 0;
        this.player = null;
        this.playerUuid = null;
    }

    /**
     * Calculates the suspicion score.
     */
    public void calculateScore() {

        int score = 25;

        score += (int) Math.min(
                55L,
                events / 10L
        );

        long inactive =
                System.currentTimeMillis() - lastActivity;

        if (inactive < 5_000L) {
            score += 10;
        }

        if (inactive < 1_000L) {
            score += 10;
        }

        suspicion = Math.max(
                0,
                Math.min(
                        100,
                        score
                )
        );
    }

    /**
     * Creates a safe copy of this detection.
     */
    public Detection copy() {

        Detection copy =
                new Detection(location, type);

        copy.events = events;
        copy.lastActivity = lastActivity;
        copy.suspicion = suspicion;
        copy.player = player;
        copy.playerUuid = playerUuid;

        return copy;
    }

    /**
     * Returns the detection ID.
     */
    public String getId() {
        return id;
    }

    /**
     * Returns the exact detection location.
     */
    public Location getLocation() {

        return location == null
                ? null
                : location.clone();
    }

    /**
     * Updates the detection location.
     */
    public void setLocation(Location location) {

        this.location = location == null
                ? null
                : location.clone();
    }

    /**
     * Returns the detection type.
     */
    public String getType() {
        return type;
    }

    /**
     * Updates the detection type.
     */
    public void setType(String type) {

        this.type = type == null
                ? "REDSTONE ACTIVITY"
                : type;
    }

    /**
     * Returns the number of detected events.
     */
    public long getEvents() {
        return events;
    }

    /**
     * Sets the event count.
     */
    public void setEvents(long events) {

        this.events = Math.max(
                0L,
                events
        );
    }

    /**
     * Adds one detected event.
     */
    public void incrementEvents() {

        events++;
        touch();
    }

    /**
     * Returns the last activity timestamp.
     */
    public long getLastActivity() {
        return lastActivity;
    }

    /**
     * Sets the last activity timestamp.
     */
    public void setLastActivity(long lastActivity) {

        this.lastActivity = lastActivity;
    }

    /**
     * Marks the detection as recently active.
     */
    public void touch() {

        this.lastActivity =
                System.currentTimeMillis();
    }

    /**
     * Returns suspicion percentage.
     */
    public int getSuspicion() {
        return suspicion;
    }

    /**
     * Sets suspicion percentage.
     */
    public void setSuspicion(int suspicion) {

        this.suspicion = Math.max(
                0,
                Math.min(
                        100,
                        suspicion
                )
        );
    }

    /**
     * Returns associated player name.
     */
    public String getPlayer() {
        return player;
    }

    /**
     * Sets associated player name.
     */
    public void setPlayer(String player) {

        this.player = player;
    }

    /**
     * Returns associated player UUID.
     */
    public UUID getPlayerUuid() {
        return playerUuid;
    }

    /**
     * Sets associated player UUID.
     */
    public void setPlayerUuid(UUID playerUuid) {

        this.playerUuid = playerUuid;
    }

    /**
     * Returns whether a player is associated.
     */
    public boolean hasPlayer() {

        return player != null
                && !player.isBlank();
    }

    /**
     * Returns whether a player UUID is available.
     */
    public boolean hasPlayerUuid() {

        return playerUuid != null;
    }

    /**
     * Returns milliseconds since last activity.
     */
    public long getInactiveMillis() {

        return Math.max(
                0L,
                System.currentTimeMillis()
                        - lastActivity
        );
    }

    /**
     * Returns whether the detection is still active.
     */
    public boolean isActive() {

        return getInactiveMillis()
                < 120_000L;
    }

    /**
     * Returns whether the detection has a valid location.
     */
    public boolean hasLocation() {

        return location != null
                && location.getWorld() != null;
    }

    @Override
    public String toString() {

        return "Detection{"
                + "id='"
                + id
                + '\''
                + ", location="
                + location
                + ", type='"
                + type
                + '\''
                + ", events="
                + events
                + ", lastActivity="
                + lastActivity
                + ", suspicion="
                + suspicion
                + ", player='"
                + player
                + '\''
                + ", playerUuid="
                + playerUuid
                + '}';
    }
    }
