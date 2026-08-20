package com.webbservermc.griefheal;

import io.papermc.paper.threadedregions.scheduler.ScheduledTask;
import net.kyori.adventure.key.Key;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.BlockState;
import org.bukkit.block.Container;
import org.bukkit.block.data.BlockData;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.sql.*;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.TimeUnit;

public class GriefHeal extends JavaPlugin {
    private static GriefHeal instance;
    private Connection connection;
    private HealListener healListener;

    private final Map<Location, ScheduledTask> pendingTasks = new ConcurrentHashMap<>();
    private final Map<Location, List<BlockSnapshot>> areaBuffers = new ConcurrentHashMap<>();
    private final ConcurrentLinkedQueue<BlockSnapshot> dbWriteQueue = new ConcurrentLinkedQueue<>();
    private final ConcurrentLinkedQueue<BlockSnapshot> dbDeleteQueue = new ConcurrentLinkedQueue<>();
    private final Object dbLock = new Object();

    @Override
    public void onEnable() {
        instance = this;
        saveDefaultConfig();
        setupDatabase();

        healListener = new HealListener();
        healListener.reloadSettings(getConfig());
        getServer().getPluginManager().registerEvents(healListener, this);

        if (getCommand("griefheal") != null) {
            getCommand("griefheal").setExecutor(new CommandHandler());
        }

        startAsyncDatabaseWorkers();

        // Stagger crash recovery by 5 seconds to ensure all worlds/dimensions are fully loaded
        Bukkit.getAsyncScheduler().runDelayed(this, task -> recoverFromCrash(), 5, TimeUnit.SECONDS);
    }

    @Override
    public void onDisable() {
        // Cancel scheduled tasks
        for (ScheduledTask task : pendingTasks.values()) {
            task.cancel();
        }
        pendingTasks.clear();

        // Flush remaining queue synchronously before shutting down
        flushDatabaseQueuesSynchronously();

        try {
            synchronized (dbLock) {
                if (connection != null && !connection.isClosed()) {
                    connection.close();
                    getLogger().info("SQLite connection closed safely.");
                }
            }
        } catch (SQLException e) {
            getLogger().severe("Error closing SQLite connection: " + e.getMessage());
        }
    }

    public void reloadPluginConfig() {
        reloadConfig();
        if (healListener != null) {
            healListener.reloadSettings(getConfig());
        }
    }

    private void setupDatabase() {
        try {
            if (!getDataFolder().exists()) getDataFolder().mkdirs();
            connection = DriverManager.getConnection("jdbc:sqlite:" + getDataFolder() + "/griefheal.db");

            try (Statement s = connection.createStatement()) {
                s.execute("PRAGMA journal_mode = WAL;");
                s.execute("PRAGMA synchronous = NORMAL;");
                s.execute("CREATE TABLE IF NOT EXISTS queue (" +
                        "id INTEGER PRIMARY KEY AUTOINCREMENT, " +
                        "world TEXT, " +
                        "x INT, y INT, z INT, " +
                        "block_data TEXT, " +
                        "container_data BLOB)");
                s.execute("CREATE INDEX IF NOT EXISTS idx_coords ON queue(world, x, y, z)");
            }
        } catch (SQLException e) {
            getLogger().severe("Failed to initialize SQLite database: " + e.getMessage());
        }
    }

    private void startAsyncDatabaseWorkers() {
        Bukkit.getAsyncScheduler().runAtFixedRate(this, task -> {
            flushDatabaseQueuesSynchronously();
        }, 1, 1, TimeUnit.SECONDS);
    }

    private void flushDatabaseQueuesSynchronously() {
        synchronized (dbLock) {
            try {
                if (connection == null || connection.isClosed()) return;

                // Handle Insertions
                if (!dbWriteQueue.isEmpty()) {
                    String insertSql = "INSERT INTO queue (world, x, y, z, block_data, container_data) VALUES (?, ?, ?, ?, ?, ?)";
                    try (PreparedStatement pstmt = connection.prepareStatement(insertSql)) {
                        connection.setAutoCommit(false);
                        BlockSnapshot record;
                        int count = 0;
                        while ((record = dbWriteQueue.poll()) != null && count < 5000) {
                            pstmt.setString(1, record.worldKey);
                            pstmt.setInt(2, record.x);
                            pstmt.setInt(3, record.y);
                            pstmt.setInt(4, record.z);
                            pstmt.setString(5, record.blockDataString);
                            pstmt.setBytes(6, record.containerBytes);
                            pstmt.addBatch();
                            count++;
                        }
                        pstmt.executeBatch();
                        connection.commit();
                    }
                }

                // Handle Deletions
                if (!dbDeleteQueue.isEmpty()) {
                    String deleteSql = "DELETE FROM queue WHERE world = ? AND x = ? AND y = ? AND z = ?";
                    try (PreparedStatement pstmt = connection.prepareStatement(deleteSql)) {
                        connection.setAutoCommit(false);
                        BlockSnapshot record;
                        int count = 0;
                        while ((record = dbDeleteQueue.poll()) != null && count < 5000) {
                            pstmt.setString(1, record.worldKey);
                            pstmt.setInt(2, record.x);
                            pstmt.setInt(3, record.y);
                            pstmt.setInt(4, record.z);
                            pstmt.addBatch();
                            count++;
                        }
                        pstmt.executeBatch();
                        connection.commit();
                    }
                }
            } catch (SQLException e) {
                getLogger().severe("Database batch write exception: " + e.getMessage());
            } finally {
                try { connection.setAutoCommit(true); } catch (SQLException ignored) {}
            }
        }
    }

    private void recoverFromCrash() {
        List<BlockSnapshot> recovered = new ArrayList<>();
        synchronized (dbLock) {
            try (Statement s = connection.createStatement(); ResultSet rs = s.executeQuery("SELECT * FROM queue")) {
                while (rs.next()) {
                    String worldKey = rs.getString("world");
                    int x = rs.getInt("x");
                    int y = rs.getInt("y");
                    int z = rs.getInt("z");
                    String dataString = rs.getString("block_data");
                    byte[] containerBytes = rs.getBytes("container_data");

                    recovered.add(new BlockSnapshot(worldKey, x, y, z, dataString, containerBytes));
                }
            } catch (SQLException e) {
                getLogger().severe("Failed to query crash recovery queue: " + e.getMessage());
            }
        }

        if (!recovered.isEmpty()) {
            getLogger().info("Auto-recovery: Restoring " + recovered.size() + " blocks across dimensions.");
            
            // Group by world for recovery
            Map<String, List<BlockSnapshot>> worldGroups = new HashMap<>();
            for (BlockSnapshot snapshot : recovered) {
                worldGroups.computeIfAbsent(snapshot.worldKey, k -> new ArrayList<>()).add(snapshot);
            }

            for (Map.Entry<String, List<BlockSnapshot>> entry : worldGroups.entrySet()) {
                World world = Bukkit.getWorld(NamespacedKey.fromString(entry.getKey()));
                if (world == null) continue;

                List<BlockSnapshot> group = entry.getValue();
                Location center = new Location(world, group.get(0).x, group.get(0).y, group.get(0).z);
                restoreArea(center, group);
            }
        }
    }

    public void handleGrief(Location center, List<Block> blocks) {
        if (blocks.isEmpty()) return;

        List<BlockSnapshot> snapshots = new ArrayList<>();
        for (Block b : blocks) {
            byte[] containerBytes = null;
            if (b.getState() instanceof Container container) {
                containerBytes = serializeInventory(container.getInventory().getContents());
                container.getInventory().clear(); // Zero-dupe enforcement
            }
            snapshots.add(new BlockSnapshot(
                    b.getWorld().key().asString(),
                    b.getX(), b.getY(), b.getZ(),
                    b.getBlockData().getAsString(),
                    containerBytes
            ));
        }

        dbWriteQueue.addAll(snapshots);

        double radiusSq = Math.pow(getConfig().getDouble("spatial-debounce-radius", 10.0), 2);
        Location groupCenter = pendingTasks.keySet().stream()
                .filter(loc -> loc.getWorld().equals(center.getWorld()) && loc.distanceSquared(center) <= radiusSq)
                .findFirst().orElse(center);

        areaBuffers.computeIfAbsent(groupCenter, k -> Collections.synchronizedList(new ArrayList<>())).addAll(snapshots);

        ScheduledTask existingTask = pendingTasks.remove(groupCenter);
        if (existingTask != null) existingTask.cancel();

        long delaySeconds = getConfig().getLong("restoration-delay", 10);
        ScheduledTask timer = Bukkit.getRegionScheduler().runDelayed(this, groupCenter, task -> {
            List<BlockSnapshot> buffer = areaBuffers.remove(groupCenter);
            pendingTasks.remove(groupCenter);
            if (buffer != null && !buffer.isEmpty()) {
                restoreArea(groupCenter, buffer);
            }
        }, delaySeconds * 20L);

        pendingTasks.put(groupCenter, timer);
    }

    public void restoreArea(Location center, List<BlockSnapshot> snapshots) {
        if (snapshots == null || snapshots.isEmpty()) return;

        List<BlockSnapshot> sorted = snapshots.stream()
                .sorted(Comparator.comparing((BlockSnapshot s) -> isFragile(s.blockDataString))
                        .thenComparing(s -> s.distanceSquared(center), Comparator.reverseOrder()))
                .toList();

        boolean playSounds = getConfig().getBoolean("effects.sounds-enabled", true);
        boolean showParticles = getConfig().getBoolean("effects.particles-enabled", true);

        if (playSounds && center.getWorld() != null) {
            center.getWorld().playSound(center, Sound.BLOCK_BEACON_ACTIVATE, 8.0f, 0.6f);
        }

        int bpt = Math.max(1, getConfig().getInt("blocks-per-tick", 5));

        Bukkit.getRegionScheduler().runAtFixedRate(this, center, new java.util.function.Consumer<ScheduledTask>() {
            int index = 0;
            int soundCooldown = 80;

            @Override
            public void accept(ScheduledTask task) {
                if (playSounds && center.getWorld() != null) {
                    if (soundCooldown <= 0) {
                        center.getWorld().playSound(center, Sound.BLOCK_BEACON_AMBIENT, 8.0f, 0.6f);
                        soundCooldown = 80;
                    }
                    soundCooldown--;
                }

                for (int i = 0; i < bpt; i++) {
                    if (index >= sorted.size()) {
                        task.cancel();
                        dbDeleteQueue.addAll(sorted);
                        return;
                    }

                    BlockSnapshot snap = sorted.get(index);
                    World world = Bukkit.getWorld(NamespacedKey.fromString(snap.worldKey));
                    if (world != null) {
                        Block block = world.getBlockAt(snap.x, snap.y, snap.z);
                        BlockData data = Bukkit.createBlockData(snap.blockDataString);
                        block.setBlockData(data, false);

                        if (snap.containerBytes != null && block.getState() instanceof Container container) {
                            ItemStack[] contents = deserializeInventory(snap.containerBytes);
                            container.getInventory().setContents(contents);
                            container.update(true, false);
                        }

                        if (showParticles) {
                            world.spawnParticle(Particle.REVERSE_PORTAL,
                                    snap.x + 0.5, snap.y + 0.5, snap.z + 0.5, 5, 0.1, 0.1, 0.1, 0.02);
                        }
                    }
                    index++;
                }
            }
        }, 1L, 1L);
    }

    public void processAll() {
        for (Map.Entry<Location, ScheduledTask> entry : new HashMap<>(pendingTasks).entrySet()) {
            entry.getValue().cancel();
            List<BlockSnapshot> buffer = areaBuffers.remove(entry.getKey());
            if (buffer != null) {
                restoreArea(entry.getKey(), buffer);
            }
        }
        pendingTasks.clear();
        areaBuffers.clear();
    }

    private boolean isFragile(String blockDataString) {
        String n = blockDataString.toUpperCase(Locale.ROOT);
        return n.contains("REDSTONE") || n.contains("TORCH") || n.contains("RAIL") || 
               n.contains("FLOWER") || n.contains("SAPLING") || n.contains("DOOR") || 
               n.contains("BUTTON") || n.contains("LEVER");
    }

    private byte[] serializeInventory(ItemStack[] contents) {
        try (ByteArrayOutputStream baos = new ByteArrayOutputStream();
             DataOutputStream out = new DataOutputStream(baos)) {
            out.writeInt(contents.length);
            for (ItemStack item : contents) {
                if (item == null || item.getType().isAir()) {
                    out.writeInt(0);
                } else {
                    byte[] bytes = item.serializeAsBytes();
                    out.writeInt(bytes.length);
                    out.write(bytes);
                }
            }
            return baos.toByteArray();
        } catch (IOException e) {
            getLogger().severe("Failed to serialize inventory: " + e.getMessage());
            return null;
        }
    }

    private ItemStack[] deserializeInventory(byte[] data) {
        try (ByteArrayInputStream bais = new ByteArrayInputStream(data);
             DataInputStream in = new DataInputStream(bais)) {
            int length = in.readInt();
            ItemStack[] items = new ItemStack[length];
            for (int i = 0; i < length; i++) {
                int byteLength = in.readInt();
                if (byteLength > 0) {
                    byte[] itemBytes = new byte[byteLength];
                    in.readFully(itemBytes);
                    items[i] = ItemStack.deserializeBytes(itemBytes);
                } else {
                    items[i] = null;
                }
            }
            return items;
        } catch (IOException e) {
            getLogger().severe("Failed to deserialize inventory: " + e.getMessage());
            return new ItemStack[0];
        }
    }

    public static GriefHeal getInstance() { return instance; }

    public static class BlockSnapshot {
        final String worldKey;
        final int x, y, z;
        final String blockDataString;
        final byte[] containerBytes;

        BlockSnapshot(String worldKey, int x, int y, int z, String blockDataString, byte[] containerBytes) {
            this.worldKey = worldKey;
            this.x = x;
            this.y = y;
            this.z = z;
            this.blockDataString = blockDataString;
            this.containerBytes = containerBytes;
        }

        double distanceSquared(Location center) {
            double dx = this.x - center.getX();
            double dy = this.y - center.getY();
            double dz = this.z - center.getZ();
            return (dx * dx) + (dy * dy) + (dz * dz);
        }
    }
    }
