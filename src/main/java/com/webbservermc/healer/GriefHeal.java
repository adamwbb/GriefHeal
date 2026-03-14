package com.webbservermc.griefheal;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.block.BlockState;
import org.bukkit.block.data.BlockData;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitRunnable;
import java.sql.*;
import java.util.*;

public class GriefHeal extends JavaPlugin {
    private static GriefHeal instance;
    private Connection connection;
    private final Map<Location, BukkitRunnable> pendingTasks = new HashMap<>();
    private final Map<Location, List<BlockState>> areaBuffers = new HashMap<>();
    private final Object dbLock = new Object();

    @Override
    public void onEnable() {
        instance = this;
        saveDefaultConfig();
        setupDatabase();
        
        new BukkitRunnable() {
            @Override
            public void run() { recoverFromCrash(); }
        }.runTaskLater(this, 100L);

        getCommand("griefheal").setExecutor(new CommandHandler());
        getServer().getPluginManager().registerEvents(new HealListener(), this);
    }

    @Override
    public void onDisable() {
        getServer().getScheduler().cancelTasks(this);
        try {
            if (connection != null && !connection.isClosed()) {
                connection.close();
                getLogger().info("SQLite connection closed safely.");
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    private void setupDatabase() {
        try {
            if (!getDataFolder().exists()) getDataFolder().mkdirs();
            connection = DriverManager.getConnection("jdbc:sqlite:" + getDataFolder() + "/griefheal.db");
            try (Statement s = connection.createStatement()) {
                s.execute("CREATE TABLE IF NOT EXISTS queue (id INTEGER PRIMARY KEY AUTOINCREMENT, world TEXT, x INT, y INT, z INT, block_data TEXT)");
                s.execute("CREATE INDEX IF NOT EXISTS idx_coords ON queue(world, x, y, z)");
            }
        } catch (SQLException e) { e.printStackTrace(); }
    }

    private void recoverFromCrash() {
        List<BlockState> recovered = new ArrayList<>();
        try (Statement s = connection.createStatement(); ResultSet rs = s.executeQuery("SELECT * FROM queue")) {
            while (rs.next()) {
                String worldName = rs.getString("world");
                if (Bukkit.getWorld(worldName) == null) continue;
                Location loc = new Location(Bukkit.getWorld(worldName), rs.getInt("x"), rs.getInt("y"), rs.getInt("z"));
                BlockData data = Bukkit.createBlockData(rs.getString("block_data"));
                BlockState state = loc.getBlock().getState();
                state.setBlockData(data);
                recovered.add(state);
            }
            if (!recovered.isEmpty()) {
                getLogger().info("Auto-recovery: Restoring " + recovered.size() + " blocks from database.");
                restoreArea(recovered.get(0).getLocation(), recovered);
                s.execute("DELETE FROM queue");
            }
        } catch (SQLException e) { e.printStackTrace(); }
    }

    public void handleGrief(Location center, List<BlockState> states) {
        saveToDatabaseAsync(states);
        double radiusSq = Math.pow(getConfig().getDouble("spatial-debounce-radius"), 2);
        Location groupCenter = pendingTasks.keySet().stream()
                .filter(loc -> loc.getWorld().equals(center.getWorld()) && loc.distanceSquared(center) <= radiusSq)
                .findFirst().orElse(center);

        areaBuffers.computeIfAbsent(groupCenter, k -> new ArrayList<>()).addAll(states);
        if (pendingTasks.containsKey(groupCenter)) pendingTasks.get(groupCenter).cancel();

        BukkitRunnable timer = new BukkitRunnable() {
            @Override
            public void run() {
                restoreArea(groupCenter, areaBuffers.get(groupCenter));
                pendingTasks.remove(groupCenter);
                areaBuffers.remove(groupCenter);
            }
        };
        timer.runTaskLater(this, getConfig().getLong("restoration-delay") * 20L);
        pendingTasks.put(groupCenter, timer);
    }

    private void saveToDatabaseAsync(List<BlockState> states) {
        List<BlockRecord> records = states.stream()
            .map(s -> new BlockRecord(s.getWorld().getName(), s.getX(), s.getY(), s.getZ(), s.getBlockData().getAsString()))
            .toList();

        Bukkit.getScheduler().runTaskAsynchronously(this, () -> {
            synchronized (dbLock) {
                try (PreparedStatement pstmt = connection.prepareStatement("INSERT INTO queue (world, x, y, z, block_data) VALUES (?, ?, ?, ?, ?)")) {
                    connection.setAutoCommit(false);
                    for (BlockRecord r : records) {
                        pstmt.setString(1, r.world);
                        pstmt.setInt(2, r.x);
                        pstmt.setInt(3, r.y);
                        pstmt.setInt(4, r.z);
                        pstmt.setString(5, r.data);
                        pstmt.addBatch();
                    }
                    pstmt.executeBatch();
                    connection.commit();
                } catch (SQLException e) { 
                    e.printStackTrace(); 
                } finally {
                    try { connection.setAutoCommit(true); } catch (SQLException ex) { ex.printStackTrace(); }
                }
            }
        });
    }

    private void removeFromDatabaseAsync(List<BlockState> states) {
        List<BlockRecord> records = states.stream()
            .map(s -> new BlockRecord(s.getWorld().getName(), s.getX(), s.getY(), s.getZ(), null))
            .toList();

        Bukkit.getScheduler().runTaskAsynchronously(this, () -> {
            synchronized (dbLock) {
                try (PreparedStatement pstmt = connection.prepareStatement("DELETE FROM queue WHERE world = ? AND x = ? AND y = ? AND z = ?")) {
                    connection.setAutoCommit(false);
                    for (BlockRecord r : records) {
                        pstmt.setString(1, r.world);
                        pstmt.setInt(2, r.x);
                        pstmt.setInt(3, r.y);
                        pstmt.setInt(4, r.z);
                        pstmt.addBatch();
                    }
                    pstmt.executeBatch();
                    connection.commit();
                } catch (SQLException e) { 
                    e.printStackTrace(); 
                } finally {
                    try { connection.setAutoCommit(true); } catch (SQLException ex) { ex.printStackTrace(); }
                }
            }
        });
    }

    public void restoreArea(Location center, List<BlockState> states) {
        if (states == null || states.isEmpty()) return;

        List<BlockState> sorted = states.stream()
                .sorted(Comparator.comparing((BlockState s) -> isFragile(s.getType()))
                .thenComparing(s -> s.getLocation().distanceSquared(center), Comparator.reverseOrder()))
                .toList();

        boolean playSounds = getConfig().getBoolean("effects.sounds-enabled");
        boolean showParticles = getConfig().getBoolean("effects.particles-enabled");

        if (playSounds) {
            center.getWorld().playSound(center, Sound.BLOCK_BEACON_ACTIVATE, 8.0f, 0.6f);
        }

        new BukkitRunnable() {
            int index = 0;
            int soundCooldown = 80; 
            final int bpt = getConfig().getInt("blocks-per-tick");
            final int soundInterval = 80; 

            @Override
            public void run() {
                if (playSounds) {
                    if (soundCooldown <= 0) {
                        center.getWorld().playSound(center, Sound.BLOCK_BEACON_AMBIENT, 8.0f, 0.6f);
                        soundCooldown = soundInterval;
                    }
                    soundCooldown--;
                }

                for (int i = 0; i < bpt; i++) {
                    if (index >= sorted.size()) { 
                        this.cancel(); 
                        removeFromDatabaseAsync(sorted);
                        return; 
                    }
                    BlockState state = sorted.get(index);
                    state.update(true, false);
                    
                    if (showParticles) {
                        state.getWorld().spawnParticle(Particle.REVERSE_PORTAL, 
                            state.getLocation().add(0.5, 0.5, 0.5), 5, 0.1, 0.1, 0.1, 0.02);
                    }
                    index++;
                }
            }
        }.runTaskTimer(this, 0L, 1L);
    }

    public void processAll() {
        for (Location loc : new HashSet<>(pendingTasks.keySet())) {
            pendingTasks.get(loc).cancel();
            restoreArea(loc, areaBuffers.get(loc));
        }
        pendingTasks.clear();
        areaBuffers.clear();
    }

    private boolean isFragile(Material m) {
        String n = m.name();
        return n.contains("REDSTONE") || n.contains("TORCH") || n.contains("RAIL") || n.contains("FLOWER") || n.contains("SAPLING");
    }

    public static GriefHeal getInstance() { return instance; }

    private static class BlockRecord {
        String world; int x, y, z; String data;
        BlockRecord(String world, int x, int y, int z, String data) {
            this.world = world; this.x = x; this.y = y; this.z = z; this.data = data;
        }
    }
}