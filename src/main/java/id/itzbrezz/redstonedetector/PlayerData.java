package id.itzbrezz.redstonedetector;

import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;

import java.util.UUID;

public final class PlayerData {

    private final UUID uuid;
    private String name;

    private long firstSeen;
    private long lastOnline;
    private long playTime;

    public PlayerData(UUID uuid) {
        this.uuid = uuid;

        OfflinePlayer player = Bukkit.getOfflinePlayer(uuid);

        this.name = player.getName() != null
                ? player.getName()
                : "Unknown";

        this.firstSeen = player.getFirstPlayed();
        this.lastOnline = player.getLastPlayed();
    }

    public UUID getUuid() {
        return uuid;
    }

    public String getName() {
        return name;
    }

    public long getFirstSeen() {
        return firstSeen;
    }

    public long getLastOnline() {
        return lastOnline;
    }

    public long getPlayTime() {
        return playTime;
    }

    public void setName(String name) {
        if (name != null && !name.isBlank()) {
            this.name = name;
        }
    }

    public void setFirstSeen(long firstSeen) {
        this.firstSeen = Math.max(0L, firstSeen);
    }

    public void setLastOnline(long lastOnline) {
        this.lastOnline = Math.max(0L, lastOnline);
    }

    public void setPlayTime(long playTime) {
        this.playTime = Math.max(0L, playTime);
    }

    public boolean hasPlayedBefore() {
        return firstSeen > 0L;
    }

    public boolean isOnline() {
        return Bukkit.getPlayer(uuid) != null;
    }

    public String getLastOnlineText() {

        if (isOnline()) {
            return "Online";
        }

        if (lastOnline <= 0L) {
            return "Tidak diketahui";
        }

        return formatTimestamp(lastOnline);
    }

    public String getPlayTimeText() {
        return formatDuration(playTime);
    }

    private String formatTimestamp(long timestamp) {

        long difference =
                Math.max(
                        0L,
                        System.currentTimeMillis() - timestamp
                );

        long seconds = difference / 1000L;
        long minutes = seconds / 60L;
        long hours = minutes / 60L;
        long days = hours / 24L;

        if (days > 0) {
            return days + " hari lalu";
        }

        if (hours > 0) {
            return hours + " jam lalu";
        }

        if (minutes > 0) {
            return minutes + " menit lalu";
        }

        return "Baru saja";
    }

    private String formatDuration(long milliseconds) {

        if (milliseconds <= 0L) {
            return "0 menit";
        }

        long totalMinutes =
                milliseconds / 60_000L;

        long days =
                totalMinutes / (60L * 24L);

        long hours =
                (totalMinutes % (60L * 24L)) / 60L;

        long minutes =
                totalMinutes % 60L;

        StringBuilder result =
                new StringBuilder();

        if (days > 0) {
            result.append(days)
                    .append(" hari ");
        }

        if (hours > 0) {
            result.append(hours)
                    .append(" jam ");
        }

        if (minutes > 0) {
            result.append(minutes)
                    .append(" menit");
        }

        if (result.length() == 0) {
            return "Kurang dari 1 menit";
        }

        return result.toString().trim();
    }
          }
