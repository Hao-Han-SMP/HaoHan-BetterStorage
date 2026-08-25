package vn.haohan.backpack.storage;

import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.util.io.BukkitObjectInputStream;
import org.bukkit.util.io.BukkitObjectOutputStream;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.sql.*;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/** Small synchronous SQLite store; all calls are made on the server thread. */
public final class SqliteStore implements AutoCloseable {
    private final Connection connection;

    public SqliteStore(java.io.File dataFolder) throws SQLException {
        dataFolder.mkdirs();
        connection = DriverManager.getConnection("jdbc:sqlite:" + new java.io.File(dataFolder, "backpacks.db"));
        try (Statement statement = connection.createStatement()) {
            statement.executeUpdate("PRAGMA journal_mode=WAL");
            statement.executeUpdate("CREATE TABLE IF NOT EXISTS backpacks (id TEXT PRIMARY KEY, owner TEXT, contents BLOB, updated_at INTEGER NOT NULL)");
        }
    }

    public List<ItemStack> load(UUID id) {
        try (PreparedStatement ps = connection.prepareStatement("SELECT contents FROM backpacks WHERE id = ?")) {
            ps.setString(1, id.toString());
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) return deserialize(rs.getBytes(1));
            }
        } catch (SQLException | IOException ex) { throw new IllegalStateException("Could not load backpack " + id, ex); }
        return List.of();
    }

    public boolean exists(UUID id) {
        try (PreparedStatement ps = connection.prepareStatement("SELECT 1 FROM backpacks WHERE id = ?")) {
            ps.setString(1, id.toString());
            try (ResultSet rs = ps.executeQuery()) { return rs.next(); }
        } catch (SQLException ex) { throw new IllegalStateException("Could not check backpack " + id, ex); }
    }

    public void save(UUID id, UUID owner, Inventory inventory, int[] slots) {
        List<ItemStack> items = new ArrayList<>();
        for (int slot : slots) items.add(inventory.getItem(slot));
        try (PreparedStatement ps = connection.prepareStatement("INSERT INTO backpacks(id, owner, contents, updated_at) VALUES(?,?,?,?) ON CONFLICT(id) DO UPDATE SET contents=excluded.contents, owner=excluded.owner, updated_at=excluded.updated_at")) {
            ps.setString(1, id.toString());
            ps.setString(2, owner == null ? null : owner.toString());
            ps.setBytes(3, serialize(items));
            ps.setLong(4, System.currentTimeMillis());
            ps.executeUpdate();
        } catch (SQLException | IOException ex) { throw new IllegalStateException("Could not save backpack " + id, ex); }
    }

    public void delete(UUID id) {
        try (PreparedStatement ps = connection.prepareStatement("DELETE FROM backpacks WHERE id = ?")) {
            ps.setString(1, id.toString()); ps.executeUpdate();
        } catch (SQLException ex) { throw new IllegalStateException("Could not delete backpack " + id, ex); }
    }

    public List<UUID> listByOwner(UUID owner) {
        List<UUID> ids = new ArrayList<>();
        try (PreparedStatement ps = connection.prepareStatement("SELECT id FROM backpacks WHERE owner = ? ORDER BY updated_at DESC")) {
            ps.setString(1, owner.toString());
            try (ResultSet rs = ps.executeQuery()) { while (rs.next()) ids.add(UUID.fromString(rs.getString(1))); }
        } catch (SQLException ex) { throw new IllegalStateException("Could not list backpacks", ex); }
        return ids;
    }

    private byte[] serialize(List<ItemStack> items) throws IOException {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (BukkitObjectOutputStream out = new BukkitObjectOutputStream(bytes)) { out.writeObject(items); }
        return bytes.toByteArray();
    }

    @SuppressWarnings("unchecked")
    private List<ItemStack> deserialize(byte[] bytes) throws IOException {
        if (bytes == null) return List.of();
        try (BukkitObjectInputStream in = new BukkitObjectInputStream(new ByteArrayInputStream(bytes))) {
            Object value = in.readObject();
            return value instanceof List<?> list ? (List<ItemStack>) list : List.of();
        } catch (ClassNotFoundException ex) { throw new IOException(ex); }
    }

    @Override public void close() throws SQLException { connection.close(); }
}
