package id.itzbrezz.redstonedetector;

import org.bukkit.Location;

import java.util.UUID;

/**
 * Shared detection model used by RedstoneDetector,
 * LagCommand and LagGui.
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

    public Detection(Location location, String type) {

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

        this.events = 0L;

        this.lastActivity =
                System.currentTimeMillis();

        this.suspicion = 0;

        this.player = null;

        this.playerUuid = null;
    }

    /**
     * Calculates the suspicion score.
     */
    public void calculateScore() {

        int score = 25;

        /*
         * Event activity.
         */
        score +=
                (int) Math.min(
                        55L,
                        events / 10L
                );

        long age =
                System.currentTimeMillis()
                        - lastActivity;

        /*
         * Very recent activity.
         */
        if (age < 5_000L) {
            score += 10;
        }

        if (age < 1_000L) {
            score += 10;
        }

        suspicion =
                Math.max(
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
                new Detection(
                        location,
                        type
                );

        copy.events =
                events;

        copy.lastActivity =
                lastActivity;

        copy.suspicion =
                suspicion;

        copy.player =
                player;

        copy.playerUuid =
                playerUuid;

        return copy;
    }

    /**
     * Returns detection ID.
     */
    public String getId() {
        return id;
    }

    /**
     * Returns exact detection location.
     */
    public Location getLocation() {

        return location == null
                ? null
                : location.clone();
    }

    /**
     * Returns detection type.
     */
    public String getType() {
        return type;
    }

    /**
     * Returns number of redstone events.
     */
    public long getEvents() {
        return events;
    }

    /**
     * Returns the last activity timestamp.
     */
    public long getLastActivity() {
        return lastActivity;
    }

    /**
     * Returns suspicion percentage.
     */
    public int getSuspicion() {
        return suspicion;
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
     *
     * Used by LagGui for player information.
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
     * Returns whether a player is associated
     * with this detection.
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
     * Updates the last activity timestamp.
     */
    public void touch() {

        this.lastActivity =
                System.currentTimeMillis();
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
     * Returns whether this detection is still recent.
     */
    public boolean isActive() {

        return getInactiveMillis()
                < 120_000L;
    }

    /**
     * Returns whether an exact location exists.
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
                + ", suspicion="
                + suspicion
                + ", player='"
                + player
                + '\''
                + ", playerUuid="
                + playerUuid
                + ", lastActivity="
                + lastActivity
                + '}';
    }
    }
