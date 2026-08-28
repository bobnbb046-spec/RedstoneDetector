package id.itzbrezz.redstonedetector;

import org.bukkit.Location;

import java.util.UUID;

/**
 * Shared detection model used by RedstoneDetector, LagCommand and LagGui.
 */
public final class Detection {

    final String id;
    Location location;
    String type;
    long events;
    long lastActivity;
    int suspicion;
    String player;

    public Detection(Location location, String type) {
        this.id = UUID.randomUUID()
                .toString()
                .replace("-", "")
                .substring(0, 8);

        this.location = location == null ? null : location.clone();
        this.type = type == null ? "REDSTONE ACTIVITY" : type;
        this.lastActivity = System.currentTimeMillis();
    }

    public void calculateScore() {
        int score = 25;

        score += (int) Math.min(55L, events / 10L);

        long age = System.currentTimeMillis() - lastActivity;

        if (age < 5_000L) {
            score += 10;
        }

        if (age < 1_000L) {
            score += 10;
        }

        suspicion = Math.max(0, Math.min(100, score));
    }

    public Detection copy() {
        Detection copy = new Detection(location, type);
        copy.events = events;
        copy.lastActivity = lastActivity;
        copy.suspicion = suspicion;
        copy.player = player;
        return copy;
    }

    public String getId() {
        return id;
    }

    public Location getLocation() {
        return location == null ? null : location.clone();
    }

    public String getType() {
        return type;
    }

    public long getEvents() {
        return events;
    }

    public long getLastActivity() {
        return lastActivity;
    }

    public int getSuspicion() {
        return suspicion;
    }

    public String getPlayer() {
        return player;
    }
}
