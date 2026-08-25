package vn.haohan.backpack.service;

import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Location;
import org.bukkit.block.Block;
import org.bukkit.block.TileState;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.entity.Entity;
import org.bukkit.entity.ItemDisplay;
import org.bukkit.entity.Interaction;
import org.bukkit.util.Transformation;
import org.joml.Vector3f;
import org.joml.Quaternionf;
import org.bukkit.Color;
import org.bukkit.Sound;
import org.bukkit.Particle;
import org.bukkit.block.data.Levelled;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.LeatherArmorMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.Plugin;
import org.bukkit.util.io.BukkitObjectInputStream;
import org.bukkit.util.io.BukkitObjectOutputStream;
import vn.haohan.backpack.gui.BackpackHolder;
import vn.haohan.backpack.storage.SqliteStore;

import java.io.File;
import java.lang.reflect.Method;
import java.io.IOException;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.*;

public final class BackpackService {
    /** The reserved socket is deliberately outside persisted backpack storage. */
    public static final int[] MODULE_SLOTS = {47};
    /** All usable storage slots in the six-row backpack (54 - module sockets). */
    public static final int[] STORAGE_SLOTS = java.util.stream.IntStream.range(0, 54)
            .filter(slot -> !containsStatic(MODULE_SLOTS, slot)).toArray();
    private final Plugin plugin;
    private final NamespacedKey itemKey;
    private final NamespacedKey backpackIdKey;
    private final NamespacedKey placedKey;
    private final NamespacedKey placedIdKey;
    private final NamespacedKey contentsKey;
    private final NamespacedKey visualKey;
    private final NamespacedKey visualIdKey;
    private final NamespacedKey wornKey;
    private final NamespacedKey colorKey;
    private final Map<UUID, Inventory> open = new HashMap<>();
    private final File dataFolder;
    private final SqliteStore database;

    public BackpackService(Plugin plugin, NamespacedKey itemKey) {
        this.plugin = plugin; this.itemKey = itemKey; this.backpackIdKey = new NamespacedKey(plugin, "backpack_id");
        this.placedKey = new NamespacedKey(plugin, "placed_backpack"); this.placedIdKey = new NamespacedKey(plugin, "placed_backpack_id"); this.contentsKey = new NamespacedKey(plugin, "backpack_contents");
        this.visualKey = new NamespacedKey(plugin, "backpack_visual"); this.visualIdKey = new NamespacedKey(plugin, "backpack_visual_id");
        this.wornKey = new NamespacedKey(plugin, "worn_backpack");
        this.colorKey = new NamespacedKey(plugin, "backpack_color");
        this.dataFolder = new File(plugin.getDataFolder(), "backpacks"); dataFolder.mkdirs();
        SqliteStore store;
        try { store = new SqliteStore(plugin.getDataFolder()); }
        catch (Exception ex) { plugin.getLogger().severe("SQLite kon the khoi tao: " + ex.getMessage()); store = null; }
        this.database = store;
    }

    public void registerItemCoreDefinition() {
        initDyedTexturesAndModels();
        if (plugin.getServer().getPluginManager().getPlugin("HaoHanItemCore") == null) return;
        try {
            var core = vn.haohan.itemcore.api.HaoHanItemCore.get();
            if (!core.getItemService().exists("haohan:backpack")) {
                core.getItemRegistry().register(vn.haohan.itemcore.api.item.ItemDefinition.builder("haohan:backpack")
                        .material(Material.BROWN_DYE).displayName("Backpack").maxStackSize(1)
                        .type(vn.haohan.itemcore.api.item.ItemType.SPECIAL).model("haohan:backpack")
                        .addLore("&7Chuột phải để mở ba lô cá nhân.").addLore("&8Dung lượng: 53 ô + 1 module").build());
            }

            for (org.bukkit.DyeColor dye : org.bukkit.DyeColor.values()) {
                String colorName = dye.name().toLowerCase(java.util.Locale.ROOT);
                String id = "haohan:backpack_" + colorName;
                if (!core.getItemService().exists(id)) {
                    core.getItemRegistry().register(vn.haohan.itemcore.api.item.ItemDefinition.builder(id)
                            .material(Material.BROWN_DYE).displayName("Backpack").maxStackSize(1)
                            .type(vn.haohan.itemcore.api.item.ItemType.SPECIAL).model(id)
                            .addLore("&7Chuột phải để mở ba lô cá nhân.").addLore("&8Dung lượng: 53 ô + 1 module").build());
                }
            }
        } catch (Throwable ex) {
            if (plugin.getConfig().getBoolean("debug", false)) plugin.getLogger().info("ItemCore không khả dụng, dùng item fallback: " + ex.getClass().getSimpleName());
        }
    }

    public void registerDyeRecipes() {
        for (org.bukkit.DyeColor dye : org.bukkit.DyeColor.values()) {
            String colorName = dye.name().toLowerCase(java.util.Locale.ROOT);
            NamespacedKey key = new NamespacedKey(plugin, "dye_backpack_" + colorName);
            try {
                plugin.getServer().removeRecipe(key);
            } catch (Throwable ignored) { }
            try {
                ItemStack dyedBackpack = createBackpackItem();
                setBackpackColor(dyedBackpack, dye.getColor().asRGB());

                Material dyeMat = Material.valueOf(dye.name() + "_DYE");
                org.bukkit.inventory.ShapelessRecipe recipe = new org.bukkit.inventory.ShapelessRecipe(key, dyedBackpack);
                recipe.setGroup("haohan_backpack_dye");
                recipe.setCategory(org.bukkit.inventory.recipe.CraftingBookCategory.EQUIPMENT);
                recipe.addIngredient(Material.BROWN_DYE);
                recipe.addIngredient(dyeMat);
                plugin.getServer().addRecipe(recipe);
            } catch (Throwable ignored) { }
        }
    }

    public void discoverRecipes(Player player) {
        if (player == null || !player.isOnline()) return;
        List<NamespacedKey> keys = new ArrayList<>();
        for (org.bukkit.DyeColor dye : org.bukkit.DyeColor.values()) {
            keys.add(new NamespacedKey(plugin, "dye_backpack_" + dye.name().toLowerCase(java.util.Locale.ROOT)));
        }
        try {
            player.discoverRecipes(keys);
        } catch (Throwable ignored) { }
    }

    public void initDyedTexturesAndModels() {
        java.awt.image.BufferedImage baseImage = null;
        try (var in = plugin.getResource("backpack.png")) {
            if (in != null) {
                baseImage = javax.imageio.ImageIO.read(in);
            }
        } catch (Throwable ignored) { }

        if (baseImage == null) {
            File localImg = new File(plugin.getDataFolder(), "backpack.png");
            if (localImg.exists()) {
                try { baseImage = javax.imageio.ImageIO.read(localImg); } catch (Throwable ignored) { }
            }
        }

        if (baseImage == null) {
            File rpImg = new File("F:/.HaoHanProject/HaoHan-Resourcepack/assets/haohan/textures/block/backpack.png");
            if (rpImg.exists()) {
                try { baseImage = javax.imageio.ImageIO.read(rpImg); } catch (Throwable ignored) { }
            }
        }

        if (baseImage == null) return;

        // Register in ItemCore if available
        if (plugin.getServer().getPluginManager().getPlugin("HaoHanItemCore") != null) {
            try {
                var core = vn.haohan.itemcore.api.HaoHanItemCore.get();
                var registry = core.getIconTextureRegistry();
                if (registry.get("haohan:backpack").isEmpty()) {
                    registry.register("haohan:backpack", new vn.haohan.itemcore.api.texture.IconTexture("haohan:backpack", baseImage));
                }
                for (org.bukkit.DyeColor dye : org.bukkit.DyeColor.values()) {
                    String colorName = dye.name().toLowerCase(java.util.Locale.ROOT);
                    String id = "haohan:backpack_" + colorName;
                    if (registry.get(id).isEmpty()) {
                        java.awt.image.BufferedImage shifted = shiftHueImage(baseImage, dye.getColor().asRGB());
                        if (shifted != null) {
                            registry.register(id, new vn.haohan.itemcore.api.texture.IconTexture(id, shifted));
                        }
                    }
                }
            } catch (Throwable ignored) { }
        }

        // Export to Resourcepack workspace directory if present
        try {
            File rpDir = new File("F:/.HaoHanProject/HaoHan-Resourcepack");
            if (rpDir.exists() && rpDir.isDirectory()) {
                File texturesDir = new File(rpDir, "assets/haohan/textures/block");
                File modelsDir = new File(rpDir, "assets/haohan/models/item");
                File itemsDir = new File(rpDir, "assets/haohan/items");
                File blockModelsDir = new File(rpDir, "assets/haohan/models/block");
                texturesDir.mkdirs(); modelsDir.mkdirs(); itemsDir.mkdirs(); blockModelsDir.mkdirs();

                File baseModelFile = new File(modelsDir, "backpack.json");
                String baseModelContent = baseModelFile.exists() ? java.nio.file.Files.readString(baseModelFile.toPath()) : null;

                File normalMap = new File(texturesDir, "backpack_n.png");
                File specularMap = new File(texturesDir, "backpack_s.png");

                for (org.bukkit.DyeColor dye : org.bukkit.DyeColor.values()) {
                    String colorName = dye.name().toLowerCase(java.util.Locale.ROOT);
                    File texFile = new File(texturesDir, "backpack_" + colorName + ".png");
                    if (!texFile.exists()) {
                        java.awt.image.BufferedImage shifted = shiftHueImage(baseImage, dye.getColor().asRGB());
                        if (shifted != null) {
                            javax.imageio.ImageIO.write(shifted, "PNG", texFile);
                        }
                    }

                    if (normalMap.exists()) {
                        File normOut = new File(texturesDir, "backpack_" + colorName + "_n.png");
                        if (!normOut.exists()) java.nio.file.Files.copy(normalMap.toPath(), normOut.toPath());
                    }
                    if (specularMap.exists()) {
                        File specOut = new File(texturesDir, "backpack_" + colorName + "_s.png");
                        if (!specOut.exists()) java.nio.file.Files.copy(specularMap.toPath(), specOut.toPath());
                    }

                    File modelFile = new File(modelsDir, "backpack_" + colorName + ".json");
                    if (!modelFile.exists() && baseModelContent != null) {
                        String json = baseModelContent.replace("\"haohan:block/backpack\"", "\"haohan:block/backpack_" + colorName + "\"");
                        java.nio.file.Files.writeString(modelFile.toPath(), json);
                    }

                    File itemFile = new File(itemsDir, "backpack_" + colorName + ".json");
                    if (!itemFile.exists()) {
                        String json = "{\n  \"model\": {\n    \"type\": \"minecraft:model\",\n    \"model\": \"haohan:item/backpack_" + colorName + "\"\n  }\n}";
                        java.nio.file.Files.writeString(itemFile.toPath(), json);
                    }

                    File blockModelFile = new File(blockModelsDir, "backpack_" + colorName + ".json");
                    if (!blockModelFile.exists()) {
                        String json = "{\n  \"parent\": \"haohan:item/backpack_" + colorName + "\"\n}";
                        java.nio.file.Files.writeString(blockModelFile.toPath(), json);
                    }
                }
            }
        } catch (Throwable ignored) { }
    }

    public ItemStack createBackpackItem() {
        try {
            if (plugin.getServer().getPluginManager().getPlugin("HaoHanItemCore") != null) {
                var service = vn.haohan.itemcore.api.HaoHanItemCore.get().getItemService();
                if (service.exists("haohan:backpack")) {
                    ItemStack item = service.create("haohan:backpack");
                    item.setType(Material.BROWN_DYE);
                    ItemMeta meta = item.getItemMeta();
                    if (meta != null) {
                        applyBackpackMeta(meta);
                        meta.getPersistentDataContainer().set(backpackIdKey, PersistentDataType.STRING, UUID.randomUUID().toString());
                        item.setItemMeta(meta);
                    }
                    return item;
                }
            }
        } catch (Throwable ignored) { }
        ItemStack item = new ItemStack(Material.BROWN_DYE); ItemMeta meta = item.getItemMeta();
        meta.displayName(component(plugin.getConfig().getString("backpack-item-name", "Backpack")));
        meta.lore(List.of(component("&7Chuột phải để mở ba lô cá nhân."), component("&8Dung lượng: 53 ô + 1 module")));
        applyBackpackMeta(meta);
        meta.getPersistentDataContainer().set(itemKey, PersistentDataType.BYTE, (byte) 1);
        meta.getPersistentDataContainer().set(backpackIdKey, PersistentDataType.STRING, UUID.randomUUID().toString());
        item.setItemMeta(meta); return item;
    }

    /**
     * Keep the item model and stack limit on the actual ItemStack. ItemCore's
     * definition is not enough for items created by an older definition, and
     * packs such as Hyper Punchy can otherwise fall back to the CHEST model.
     */
    private void applyBackpackMeta(ItemMeta meta) {
        String modelName = "haohan:backpack";
        if (meta.getPersistentDataContainer().has(colorKey, PersistentDataType.INTEGER)) {
            int rgb = meta.getPersistentDataContainer().get(colorKey, PersistentDataType.INTEGER);
            modelName = "haohan:backpack_" + getClosestDyeColorName(rgb);
        } else {
            modelName = plugin.getConfig().getString("backpack-item-model", "haohan:backpack");
        }
        meta.setItemModel(NamespacedKey.fromString(modelName));
        meta.setMaxStackSize(1);
        meta.setUnbreakable(true);
        meta.addItemFlags(
                ItemFlag.HIDE_ATTRIBUTES,
                ItemFlag.HIDE_ADDITIONAL_TOOLTIP,
                ItemFlag.HIDE_UNBREAKABLE,
                ItemFlag.HIDE_ARMOR_TRIM,
                ItemFlag.HIDE_STORED_ENCHANTS,
                ItemFlag.HIDE_ENCHANTS,
                ItemFlag.HIDE_DYE
        );
    }

    public boolean isBackpack(ItemStack item) {
        if (item == null || item.getType().isAir()) return false;
        try { if (vn.haohan.itemcore.api.HaoHanItemCore.get().getItemService().isItem(item, "haohan:backpack")) return true; } catch (Throwable ignored) { }
        if (item.getItemMeta() == null) return false;
        return item.getItemMeta().getPersistentDataContainer().has(itemKey, PersistentDataType.BYTE)
                || item.getItemMeta().getPersistentDataContainer().has(backpackIdKey, PersistentDataType.STRING);
    }

    public UUID backpackId(ItemStack item) {
        if (!isBackpack(item) || item.getItemMeta() == null) return null;
        String value = item.getItemMeta().getPersistentDataContainer().get(backpackIdKey, PersistentDataType.STRING);
        if (value == null) return null;
        try { return UUID.fromString(value); } catch (IllegalArgumentException ignored) { return null; }
    }

    public boolean isBlocked(ItemStack item) {
        if (item == null) return false;
        for (String material : plugin.getConfig().getStringList("blocked-materials"))
            if (item.getType().name().equalsIgnoreCase(material)) return true;
        return false;
    }

    public boolean limitReached(Player player) {
        return !canReceiveBackpacks(player, 1);
    }

    public boolean canReceiveBackpacks(Player player, int amount) {
        if (!plugin.getConfig().getBoolean("backpack-limit.enabled", false)) return true;
        int limit = Math.max(0, plugin.getConfig().getInt("backpack-limit.default", 1));
        int count = 0;
        for (ItemStack item : player.getInventory().getContents()) if (isBackpack(item)) count += item.getAmount();
        return count + amount <= limit;
    }

    public boolean keepBackpacksAfterDeath() { return plugin.getConfig().getBoolean("keep-backpacks-after-death", true); }
    public boolean blockBackpackInContainers() { return plugin.getConfig().getBoolean("block-backpack-in-containers", true); }
    public boolean allowBackpacksInsideBackpacks() { return plugin.getConfig().getBoolean("allow-backpacks-inside-backpacks", false); }
    public boolean hopperEnabled() { return plugin.getConfig().getBoolean("hopper.enabled", true); }
    public boolean backpackCollisionEnabled() { return plugin.getConfig().getBoolean("backpack-collision.enabled", true); }

    public SqliteStore database() { return database; }

    public List<UUID> listBackpacks(UUID owner) { return database == null ? List.of() : database.listByOwner(owner); }

    public void closeDatabase() {
        if (database != null) try { database.close(); } catch (Exception ex) { plugin.getLogger().warning("Không đóng được SQLite: " + ex.getMessage()); }
    }

    public void reload() { plugin.reloadConfig(); }

    public Inventory open(Player player) {
        ItemStack chest = player.getInventory().getChestplate();
        if (isBackpack(chest)) {
            return openItem(player, chest);
        }
        return open(player, player.getUniqueId(), player.getUniqueId().toString());
    }

    public Inventory openItem(Player player, ItemStack item) {
        UUID id = backpackId(item);
        if (id == null) {
            ItemMeta meta = item.getItemMeta();
            if (meta == null) return open(player);
            id = UUID.randomUUID();
            meta.getPersistentDataContainer().set(backpackIdKey, PersistentDataType.STRING, id.toString());
            item.setItemMeta(meta);
        }
        return open(player, id, id.toString(), item, null, null);
    }

    public Inventory openAt(Player player, Location location) {
        String key = storageKey(location);
        Block block = location.getBlock();
        if (block.getState() instanceof TileState state) {
            String id = state.getPersistentDataContainer().get(placedIdKey, PersistentDataType.STRING);
            if (id != null) try { return open(player, UUID.fromString(id), id, null, state, null); } catch (IllegalArgumentException ignored) { }
        }
        return open(player, UUID.nameUUIDFromBytes(key.getBytes(StandardCharsets.UTF_8)), key, null, block.getState() instanceof TileState state ? state : null, null);
    }

    public Inventory openAt(Player player, Entity visual) {
        ItemDisplay display = backpackVisualDisplay(visual);
        ItemStack item = display != null ? display.getItemStack() : null;
        UUID id = item != null ? backpackId(item) : visualStorageId(visual);
        if (id == null) {
            String value = visual.getPersistentDataContainer().get(placedIdKey, PersistentDataType.STRING);
            if (value != null) try { id = UUID.fromString(value); } catch (IllegalArgumentException ignored) { }
        }
        if (id == null) id = UUID.randomUUID();
        if (item == null) item = createBackpackItem(id);

        return open(player, id, id.toString(), item, null, display);
    }

    private Inventory open(Player player, UUID storage, String storageId) {
        return open(player, storage, storageId, null, null, null);
    }

    private Inventory open(Player player, UUID storage, String storageId, ItemStack sourceItem, TileState sourceBlock, ItemDisplay sourceDisplay) {
        cleanupStaleLocks();
        if (open.containsKey(storage)) {
            player.sendMessage("§cBa lô này đang được mở bởi người khác.");
            return null;
        }
        BackpackHolder holder = new BackpackHolder(storageId, STORAGE_SLOTS, sourceItem, sourceBlock, sourceDisplay);
        Inventory inventory = plugin.getServer().createInventory(holder, 54, guiTitle(player));
        holder.inventory(inventory);
        if (sourceItem != null) {
            loadContainer(sourceItem, inventory);
            // One-time migration for items created by the previous SQLite-backed version.
            if (!hasContainerContents(sourceItem) && database != null && database.exists(storage)) load(storage, inventory);
        } else if (sourceBlock != null) loadContainer(sourceBlock, inventory); else load(storage, inventory);
        decorate(inventory);
        open.put(storage, inventory); player.openInventory(inventory);
        player.playSound(player.getLocation(), "haohan:backpack.open", 1.0f, 1.0f);
        return inventory;
    }

    private Component guiTitle(Player player) {
        String fallback = plugin.getConfig().getString("title", "&8Ba lô của %player%").replace("%player%", player.getName());
        if (!plugin.getConfig().getBoolean("custom-gui.enabled", true)) return component(fallback);

        String font = plugin.getConfig().getString("custom-gui.font", "haohan:gui");
        String prefix = plugin.getConfig().getString("custom-gui.prefix", "\uE100");
        String glyph = plugin.getConfig().getString("custom-gui.glyph", "\uE101");
        net.kyori.adventure.key.Key fontKey = net.kyori.adventure.key.Key.key(font);
        return Component.text(prefix + glyph)
                .font(fontKey)
                .color(NamedTextColor.WHITE);
    }

    /** The custom bitmap GUI supplies the background; module sockets are real locked items. */
    private void decorate(Inventory inventory) {
        ItemStack socket = new ItemStack(Material.WHITE_STAINED_GLASS_PANE);
        ItemMeta meta = socket.getItemMeta();
        if (meta != null) {
            meta.displayName(component("&eEmpty Module Slot"));
            meta.lore(List.of(
                    component("&7This is an empty module socket."),
                    component("&7Place a module here to activate"),
                    component("&7special backpack abilities.")));
            socket.setItemMeta(meta);
        }
        for (int slot : MODULE_SLOTS) inventory.setItem(slot, socket.clone());
    }
    private boolean contains(int[] a, int value) { for (int i : a) if (i == value) return true; return false; }
    private static boolean containsStatic(int[] a, int value) { for (int slot : a) if (slot == value) return true; return false; }

    public void close(Player player, Inventory inventory) {
        if (!(inventory.getHolder() instanceof BackpackHolder holder)) return;
        UUID storage = storageId(holder.storageId());
        // InventoryCloseEvent and PlayerQuitEvent can both be fired for the
        // same view. Only the current lock owner may release it; an old close
        // event must never remove a newer player's lock.
        if (open.get(storage) != inventory) return;
        saveOpenInventory(player == null ? null : player.getUniqueId(), storage, holder, inventory);
        open.remove(storage, inventory);
        player.playSound(player.getLocation(), "haohan:backpack.close", 1.0f, 1.0f);
    }
    private void saveOpenInventory(UUID owner, UUID storage, BackpackHolder holder, Inventory inventory) {
        if (holder.sourceItem() != null) {
            saveContainer(holder.sourceItem(), inventory);
            if (holder.sourceDisplay() != null && holder.sourceDisplay().isValid()) {
                holder.sourceDisplay().setItemStack(holder.sourceItem());
            }
            save(owner, storage, inventory);
        } else if (holder.sourceBlock() != null) {
            saveContainer(holder.sourceBlock(), inventory);
        } else {
            save(owner, storage, inventory);
        }
    }

    /**
     * A quit/kick or another plugin can leave an inventory in the lock map
     * without delivering a usable close event. An inventory with no viewers
     * is no longer open, so release and persist that lock before the next
     * player tries to open it.
     */
    private void cleanupStaleLocks() {
        Iterator<Map.Entry<UUID, Inventory>> iterator = open.entrySet().iterator();
        while (iterator.hasNext()) {
            Map.Entry<UUID, Inventory> entry = iterator.next();
            Inventory inventory = entry.getValue();
            if (!inventory.getViewers().isEmpty()) continue;
            if (inventory.getHolder() instanceof BackpackHolder holder) {
                saveOpenInventory(null, entry.getKey(), holder, inventory);
            }
            iterator.remove();
        }
    }

    public void saveAllOpenBackpacks() {
        open.forEach((id, inventory) -> {
            if (inventory.getHolder() instanceof BackpackHolder holder) saveOpenInventory(null, id, holder, inventory);
        });
        open.clear();
    }
    private void load(UUID uuid, Inventory inventory) {
        List<ItemStack> databaseItems = database == null ? List.of() : database.load(uuid);
        if (database != null && database.exists(uuid)) {
            loadItems(databaseItems, inventory);
            return;
        }
        File file = file(uuid); if (!file.exists()) return;
        List<?> items = YamlConfiguration.loadConfiguration(file).getList("items", List.of());
        loadItems(items, inventory);
    }
    private void loadItems(List<?> items, Inventory inventory) {
        // Older versions persisted all 54 physical slots. Keep those backpacks
        // readable after reserving slot 47 for the module socket.
        if (items.size() >= 54) {
            for (int physical = 0; physical < 54; physical++) {
                if (!(items.get(physical) instanceof ItemStack stack)) continue;
                if (containsStatic(MODULE_SLOTS, physical)) {
                    putInFirstStorageSlot(stack, inventory);
                } else {
                    inventory.setItem(physical, stack);
                }
            }
            return;
        }
        for (int i = 0; i < STORAGE_SLOTS.length && i < items.size(); i++)
            if (items.get(i) instanceof ItemStack stack) inventory.setItem(STORAGE_SLOTS[i], stack);
    }
    private void putInFirstStorageSlot(ItemStack stack, Inventory inventory) {
        for (int slot : STORAGE_SLOTS) {
            if (inventory.getItem(slot) == null || inventory.getItem(slot).getType().isAir()) {
                inventory.setItem(slot, stack);
                return;
            }
        }
    }
    private void save(UUID owner, UUID uuid, Inventory inventory) {
        YamlConfiguration yaml = new YamlConfiguration(); List<ItemStack> items = new ArrayList<>();
        for (int slot : STORAGE_SLOTS) items.add(inventory.getItem(slot)); yaml.set("items", items);
        if (database != null) database.save(uuid, owner, inventory, STORAGE_SLOTS);
        // Retain the legacy YAML as a migration/back-up format.
        try { yaml.save(file(uuid)); } catch (IOException ex) { plugin.getLogger().warning("Không lưu được ba lô " + uuid + ": " + ex.getMessage()); }
    }
    private File file(UUID uuid) { return new File(dataFolder, uuid + ".yml"); }
    public boolean isPlacedBackpack(Block block) {
        return block.getState() instanceof TileState state && state.getPersistentDataContainer().has(placedKey, PersistentDataType.BYTE);
    }
    public void markPlacedBackpack(Block block) {
        if (!(block.getState() instanceof TileState state)) return;
        state.getPersistentDataContainer().set(placedKey, PersistentDataType.BYTE, (byte) 1); state.update(true, false);
    }

    public void markPlacedBackpack(Block block, ItemStack item) {
        if (!(block.getState() instanceof TileState state)) return;
        state.getPersistentDataContainer().set(placedKey, PersistentDataType.BYTE, (byte) 1);
        UUID id = backpackId(item);
        if (id != null) state.getPersistentDataContainer().set(placedIdKey, PersistentDataType.STRING, id.toString());
        copyContainer(item, state);
        state.update(true, false);
    }

    /** Spawn the backpack display and its exact matching interaction entity hitbox directly. */
    public void spawnPlacedBackpack(Block block, ItemStack item, float yaw) {
        if (hasPlacedBackpackAt(block)) return;
        Location location = block.getLocation();
        UUID id = backpackId(item);
        if (id == null) id = UUID.randomUUID();
        final UUID placedId = id;
        block.setType(Material.AIR, false);
        UUID visualId = UUID.randomUUID();

        // 1. Visual ItemDisplay (slightly lifted to keep bottom from clipping)
        Location displayLocation = location.clone().add(0.5, 0.20, 0.5);
        displayLocation.setYaw(yaw);
        displayLocation.setPitch(0.0f);
        ItemDisplay display = block.getWorld().spawn(displayLocation, ItemDisplay.class);
        display.setItemStack(item.clone());
        display.setItemDisplayTransform(ItemDisplay.ItemDisplayTransform.GROUND);
        display.setRotation(yaw, 0.0f);
        display.setPersistent(true);
        display.getPersistentDataContainer().set(visualKey, PersistentDataType.BYTE, (byte) 1);
        display.getPersistentDataContainer().set(visualIdKey, PersistentDataType.STRING, visualId.toString());
        display.getPersistentDataContainer().set(placedIdKey, PersistentDataType.STRING, id.toString());

        // 2. Interaction Hitbox Entity (0.6x0.6x0.6 starting from floor y=0.0)
        Location interactionLocation = location.clone().add(0.5, 0.0, 0.5);
        interactionLocation.setYaw(yaw);
        interactionLocation.setPitch(0.0f);
        Interaction interaction = block.getWorld().spawn(interactionLocation, Interaction.class);
        interaction.setInteractionWidth(0.6f);
        interaction.setInteractionHeight(0.6f);
        interaction.setResponsive(true);
        interaction.setPersistent(true);
        interaction.getPersistentDataContainer().set(visualKey, PersistentDataType.BYTE, (byte) 1);
        interaction.getPersistentDataContainer().set(visualIdKey, PersistentDataType.STRING, visualId.toString());
        interaction.getPersistentDataContainer().set(placedIdKey, PersistentDataType.STRING, id.toString());
    }

    /**
     * Spawn after a cancelled BlockPlaceEvent has finished restoring the old
     * block state. This avoids the restoration overwriting the display.
     */
    public void spawnPlacedBackpackNextTick(Block block, ItemStack item, float yaw) {
        plugin.getServer().getScheduler().runTask(plugin, () -> {
            if (block.getType().isAir() && !hasPlacedBackpackAt(block)) spawnPlacedBackpack(block, item, yaw);
        });
    }

    /**
     * Returns whether a backpack visual already occupies this block position.
     * The search box is tightly scoped to the block so vertically or horizontally adjacent backpacks do not conflict.
     */
    public boolean hasPlacedBackpackAt(Block block) {
        if (block == null || block.getWorld() == null) return false;
        Location center = block.getLocation().add(0.5, 0.3, 0.5);
        return block.getWorld().getNearbyEntities(center, 0.4, 0.25, 0.4).stream()
                .anyMatch(this::isBackpackVisual);
    }

    public boolean isBackpackVisual(Entity entity) {
        if (entity == null || entity.isDead()) return false;
        boolean isVisual = entity.getPersistentDataContainer().has(visualKey, PersistentDataType.BYTE);
        if (isVisual && entity instanceof Interaction interaction) {
            sanitizeInteraction(interaction);
        }
        return isVisual;
    }

    /**
     * Auto-correct interaction entity position and dimensions if they are offset or oversized.
     */
    private void sanitizeInteraction(Interaction interaction) {
        if (Math.abs(interaction.getInteractionWidth() - 0.6f) > 0.01f) {
            interaction.setInteractionWidth(0.6f);
        }
        if (Math.abs(interaction.getInteractionHeight() - 0.6f) > 0.01f) {
            interaction.setInteractionHeight(0.6f);
        }
        Location blockLoc = interaction.getLocation().getBlock().getLocation();
        Location targetLoc = blockLoc.add(0.5, 0.0, 0.5);
        targetLoc.setYaw(interaction.getLocation().getYaw());
        targetLoc.setPitch(0.0f);
        if (interaction.getLocation().distanceSquared(targetLoc) > 0.01) {
            interaction.teleport(targetLoc);
        }
    }

    public Location backpackVisualLocation(Entity entity) { return entity.getLocation().getBlock().getLocation(); }

    public ItemDisplay backpackVisualDisplay(Entity entity) {
        if (entity instanceof ItemDisplay display) return display;
        String visualId = entity.getPersistentDataContainer().get(visualIdKey, PersistentDataType.STRING);
        if (visualId != null) {
            for (Entity nearby : entity.getNearbyEntities(0.8, 0.8, 0.8)) {
                if (nearby instanceof ItemDisplay display && isBackpackVisual(display)) {
                    String nearbyVisualId = display.getPersistentDataContainer().get(visualIdKey, PersistentDataType.STRING);
                    if (visualId.equals(nearbyVisualId)) {
                        return display;
                    }
                }
            }
        }
        Block block = entity.getLocation().getBlock();
        for (Entity nearby : entity.getWorld().getNearbyEntities(block.getLocation().add(0.5, 0.3, 0.5), 0.4, 0.4, 0.4)) {
            if (nearby instanceof ItemDisplay display && isBackpackVisual(display)) {
                return display;
            }
        }
        return null;
    }

    public ItemStack backpackVisualItem(Entity entity) {
        ItemDisplay display = backpackVisualDisplay(entity);
        return display != null ? display.getItemStack() : null;
    }

    public void removeBackpackVisual(Entity entity) {
        if (entity == null) return;
        String visualId = entity.getPersistentDataContainer().get(visualIdKey, PersistentDataType.STRING);
        if (visualId != null) {
            for (Entity nearby : entity.getNearbyEntities(0.8, 0.8, 0.8)) {
                if (!isBackpackVisual(nearby)) continue;
                String nearbyVisualId = nearby.getPersistentDataContainer().get(visualIdKey, PersistentDataType.STRING);
                if (visualId.equals(nearbyVisualId)) {
                    nearby.remove();
                }
            }
        } else {
            Block block = entity.getLocation().getBlock();
            for (Entity nearby : entity.getWorld().getNearbyEntities(block.getLocation().add(0.5, 0.3, 0.5), 0.4, 0.4, 0.4)) {
                if (isBackpackVisual(nearby) && nearby != entity) {
                    if (nearby.getClass() != entity.getClass()) {
                        nearby.remove();
                    }
                }
            }
        }
        entity.remove();
    }

    public ItemStack breakBackpackVisual(Entity entity) {
        ItemDisplay display = backpackVisualDisplay(entity);
        ItemStack item = display != null ? display.getItemStack() : null;
        if (item == null) item = createBackpackItem();
        item = item.clone();
        item.setAmount(1);

        UUID storage = visualStorageId(entity);
        if (storage == null) storage = backpackId(item);

        // Fallback for legacy placed backpacks without container NBT:
        if (!hasContainerContents(item) && storage != null) {
            List<ItemStack> contents = removeContents(storage);
            if (!contents.isEmpty()) {
                Inventory temp = plugin.getServer().createInventory(null, 54);
                for (ItemStack content : contents) {
                    if (content != null && !content.getType().isAir()) temp.addItem(content);
                }
                saveContainer(item, temp);
            }
        } else if (storage != null) {
            databaseDelete(storage);
        }

        removeBackpackVisual(entity);
        return item;
    }

    private UUID visualStorageId(Entity entity) {
        String value = entity.getPersistentDataContainer().get(placedIdKey, PersistentDataType.STRING);
        try { return value == null ? null : UUID.fromString(value); }
        catch (IllegalArgumentException ignored) { return null; }
    }

    public UUID placedBackpackId(Block block) {
        if (!(block.getState() instanceof TileState state)) return null;
        String value = state.getPersistentDataContainer().get(placedIdKey, PersistentDataType.STRING);
        try { return value == null ? null : UUID.fromString(value); } catch (IllegalArgumentException ex) { return null; }
    }

    public ItemStack createBackpackItem(UUID id) {
        ItemStack item = createBackpackItem();
        ItemMeta meta = item.getItemMeta();
        if (meta != null) { meta.getPersistentDataContainer().set(backpackIdKey, PersistentDataType.STRING, id.toString()); item.setItemMeta(meta); }
        return item;
    }

    public ItemStack createBackpackItemWithContents(TileState state) {
        ItemStack item = createBackpackItem();
        copyContainer(state, item);
        return item;
    }

    private void copyContainer(ItemStack from, TileState to) {
        ItemMeta meta = from.getItemMeta();
        if (meta != null) {
            byte[] contents = meta.getPersistentDataContainer().get(contentsKey, PersistentDataType.BYTE_ARRAY);
            if (contents != null) to.getPersistentDataContainer().set(contentsKey, PersistentDataType.BYTE_ARRAY, contents);
        }
    }

    private void copyContainer(TileState from, ItemStack to) {
        ItemMeta meta = to.getItemMeta();
        if (meta != null) {
            byte[] contents = from.getPersistentDataContainer().get(contentsKey, PersistentDataType.BYTE_ARRAY);
            if (contents != null) { meta.getPersistentDataContainer().set(contentsKey, PersistentDataType.BYTE_ARRAY, contents); to.setItemMeta(meta); }
        }
    }

    private void loadContainer(ItemStack item, Inventory inventory) {
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            loadSerialized(meta.getPersistentDataContainer().get(contentsKey, PersistentDataType.BYTE_ARRAY), inventory);
            updateBackpackLore(meta, inventory);
            item.setItemMeta(meta);
        }
    }

    private boolean hasContainerContents(ItemStack item) {
        ItemMeta meta = item.getItemMeta();
        return meta != null && meta.getPersistentDataContainer().has(contentsKey, PersistentDataType.BYTE_ARRAY);
    }

    private void loadContainer(TileState state, Inventory inventory) { loadSerialized(state.getPersistentDataContainer().get(contentsKey, PersistentDataType.BYTE_ARRAY), inventory); }

    private void loadSerialized(byte[] bytes, Inventory inventory) {
        if (bytes == null) return;
        try (BukkitObjectInputStream in = new BukkitObjectInputStream(new ByteArrayInputStream(bytes))) {
            Object value = in.readObject();
            if (value instanceof List<?> items) loadItems(items, inventory);
        } catch (IOException | ClassNotFoundException ex) { plugin.getLogger().warning("Không đọc được contents backpack: " + ex.getMessage()); }
    }

    private void saveContainer(ItemStack item, Inventory inventory) {
        ItemMeta meta = item.getItemMeta();
        if (meta == null) return;
        meta.getPersistentDataContainer().set(contentsKey, PersistentDataType.BYTE_ARRAY, serializeInventory(inventory));
        updateBackpackLore(meta, inventory);
        item.setItemMeta(meta);
        applyContainerComponent(item, inventory);
    }

    public void applyContainerComponent(ItemStack item, Inventory inventory) {
        if (item == null || item.getType().isAir() || inventory == null) return;
        try {
            Class<?> craftItemStackClass = Class.forName(plugin.getServer().getClass().getPackage().getName() + ".inventory.CraftItemStack");
            Method asNMSCopy = craftItemStackClass.getMethod("asNMSCopy", ItemStack.class);
            Method asBukkitCopy = craftItemStackClass.getMethod("asBukkitCopy", Class.forName("net.minecraft.world.item.ItemStack"));

            Object nmsStack = asNMSCopy.invoke(null, item);
            if (nmsStack == null) return;

            List<Object> nmsItems = new ArrayList<>();
            for (int slot : STORAGE_SLOTS) {
                ItemStack stack = inventory.getItem(slot);
                if (stack != null && !stack.getType().isAir()) {
                    Object nmsSubItem = asNMSCopy.invoke(null, stack);
                    if (nmsSubItem != null) nmsItems.add(nmsSubItem);
                }
            }

            Class<?> itemContainerContentsClass = Class.forName("net.minecraft.world.item.component.ItemContainerContents");
            Method fromItemsMethod = itemContainerContentsClass.getMethod("fromItems", List.class);
            Object containerContents = fromItemsMethod.invoke(null, nmsItems);

            Class<?> dataComponentsClass = Class.forName("net.minecraft.core.component.DataComponents");
            Object containerComponentKey = dataComponentsClass.getField("CONTAINER").get(null);

            Method setMethod = nmsStack.getClass().getMethod("set", Class.forName("net.minecraft.core.component.DataComponentType"), Object.class);
            setMethod.invoke(nmsStack, containerComponentKey, containerContents);

            ItemStack result = (ItemStack) asBukkitCopy.invoke(null, nmsStack);
            if (result != null && result.hasItemMeta()) {
                item.setItemMeta(result.getItemMeta());
            }
        } catch (Throwable ignored) {
            // NMS reflection failsafe
        }
    }

    /**
     * Shifts the hue and tints a BufferedImage based on a target RGB color.
     * This creates dyed variants dynamically from a single base texture without redrawing.
     */
    public static java.awt.image.BufferedImage shiftHueImage(java.awt.image.BufferedImage source, int targetRgb) {
        if (source == null) return null;
        int width = source.getWidth();
        int height = source.getHeight();
        java.awt.image.BufferedImage result = new java.awt.image.BufferedImage(width, height, java.awt.image.BufferedImage.TYPE_INT_ARGB);

        float[] targetHsb = java.awt.Color.RGBtoHSB((targetRgb >> 16) & 0xFF, (targetRgb >> 8) & 0xFF, targetRgb & 0xFF, null);
        float targetHue = targetHsb[0];
        float targetSat = targetHsb[1];
        float targetBri = targetHsb[2];

        float[] pixelHsb = new float[3];

        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                int argb = source.getRGB(x, y);
                int alpha = (argb >>> 24) & 0xFF;
                if (alpha == 0) {
                    result.setRGB(x, y, argb);
                    continue;
                }

                int r = (argb >> 16) & 0xFF;
                int g = (argb >> 8) & 0xFF;
                int b = argb & 0xFF;

                java.awt.Color.RGBtoHSB(r, g, b, pixelHsb);

                // Set new hue to target dye hue
                float newHue = targetHue;
                // Blend saturation while respecting pixel texture details
                float newSat = Math.min(1.0f, Math.max(0.10f, pixelHsb[1] * (targetSat > 0.05f ? (0.6f + 0.4f * targetSat) : targetSat)));
                // Maintain luminance shading and scale with dye brightness
                float newBri = Math.min(1.0f, Math.max(0.0f, pixelHsb[2] * (0.35f + 0.65f * targetBri)));

                int newRgb = java.awt.Color.HSBtoRGB(newHue, newSat, newBri);
                int newArgb = (alpha << 24) | (newRgb & 0x00FFFFFF);
                result.setRGB(x, y, newArgb);
            }
        }
        return result;
    }

    public static String getClosestDyeColorName(int rgb) {
        int r = (rgb >> 16) & 0xFF;
        int g = (rgb >> 8) & 0xFF;
        int b = rgb & 0xFF;
        String bestName = "brown";
        double minDistance = Double.MAX_VALUE;
        for (org.bukkit.DyeColor dye : org.bukkit.DyeColor.values()) {
            Color c = dye.getColor();
            double dist = Math.pow(r - c.getRed(), 2) + Math.pow(g - c.getGreen(), 2) + Math.pow(b - c.getBlue(), 2);
            if (dist < minDistance) {
                minDistance = dist;
                bestName = dye.name().toLowerCase(java.util.Locale.ROOT);
            }
        }
        return bestName;
    }

    public static String getFriendlyDyeName(int rgb) {
        String dyeName = getClosestDyeColorName(rgb);
        return switch (dyeName) {
            case "white" -> "§fTrắng";
            case "orange" -> "§6Cam";
            case "magenta" -> "§dĐỏ sẫm";
            case "light_blue" -> "§bXanh nước biển nhạt";
            case "yellow" -> "§eVàng";
            case "lime" -> "§aXanh lá mạ";
            case "pink" -> "§dHồng";
            case "gray" -> "§8Xám";
            case "light_gray" -> "§7Xám nhạt";
            case "cyan" -> "§3Xanh lục lam";
            case "purple" -> "§5Tím";
            case "blue" -> "§9Xanh nước biển";
            case "brown" -> "§6Nâu";
            case "green" -> "§2Xanh lá cây";
            case "red" -> "§cĐỏ";
            case "black" -> "§8Đen";
            default -> "§f" + dyeName;
        };
    }

    public void setBackpackColor(ItemStack item, int rgb) {
        if (item == null || item.getType().isAir()) return;
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.getPersistentDataContainer().set(colorKey, PersistentDataType.INTEGER, rgb);

            String colorName = getClosestDyeColorName(rgb);
            String dyedModel = "haohan:backpack_" + colorName;
            String dyedId = "haohan:backpack_dyed_" + Integer.toHexString(rgb);

            if (plugin.getServer().getPluginManager().getPlugin("HaoHanItemCore") != null) {
                try {
                    var core = vn.haohan.itemcore.api.HaoHanItemCore.get();
                    var registry = core.getIconTextureRegistry();
                    var baseOpt = registry.get("haohan:backpack");
                    if (baseOpt.isPresent()) {
                        if (registry.get(dyedModel).isEmpty() || registry.get(dyedId).isEmpty()) {
                            java.awt.image.BufferedImage shifted = shiftHueImage(baseOpt.get().getImage(), rgb);
                            if (shifted != null) {
                                registry.register(dyedModel, new vn.haohan.itemcore.api.texture.IconTexture(dyedModel, shifted));
                                registry.register(dyedId, new vn.haohan.itemcore.api.texture.IconTexture(dyedId, shifted));
                            }
                        }
                    }
                } catch (Throwable ignored) { }
            }

            if (meta instanceof LeatherArmorMeta leatherMeta) {
                leatherMeta.setColor(Color.fromRGB(rgb));
            }
            applyBackpackMeta(meta);
            updateBackpackLore(meta, null);
            item.setItemMeta(meta);
        }
        applyDyedColorComponent(item, rgb);
    }

    public Integer getBackpackColor(ItemStack item) {
        if (item == null || !item.hasItemMeta()) return null;
        return item.getItemMeta().getPersistentDataContainer().get(colorKey, PersistentDataType.INTEGER);
    }

    public void applyDyedColorComponent(ItemStack item, int rgb) {
        if (item == null || item.getType().isAir()) return;
        try {
            Class<?> craftItemStackClass = Class.forName(plugin.getServer().getClass().getPackage().getName() + ".inventory.CraftItemStack");
            Method asNMSCopy = craftItemStackClass.getMethod("asNMSCopy", ItemStack.class);
            Method asBukkitCopy = craftItemStackClass.getMethod("asBukkitCopy", Class.forName("net.minecraft.world.item.ItemStack"));

            Object nmsStack = asNMSCopy.invoke(null, item);
            if (nmsStack == null) return;

            Class<?> dyedItemColorClass = Class.forName("net.minecraft.world.item.component.DyedItemColor");
            Object dyedColorObj;
            try {
                dyedColorObj = dyedItemColorClass.getConstructor(int.class, boolean.class).newInstance(rgb, false);
            } catch (Throwable t) {
                dyedColorObj = dyedItemColorClass.getConstructor(int.class).newInstance(rgb);
            }

            Class<?> dataComponentsClass = Class.forName("net.minecraft.core.component.DataComponents");
            Object dyedColorComponentKey = dataComponentsClass.getField("DYED_COLOR").get(null);

            Method setMethod = nmsStack.getClass().getMethod("set", Class.forName("net.minecraft.core.component.DataComponentType"), Object.class);
            setMethod.invoke(nmsStack, dyedColorComponentKey, dyedColorObj);

            ItemStack result = (ItemStack) asBukkitCopy.invoke(null, nmsStack);
            if (result != null && result.hasItemMeta()) {
                item.setItemMeta(result.getItemMeta());
            }
        } catch (Throwable ignored) {
            // NMS reflection failsafe
        }
    }

    public void removeDyedColorComponent(ItemStack item) {
        if (item == null || item.getType().isAir()) return;
        try {
            Class<?> craftItemStackClass = Class.forName(plugin.getServer().getClass().getPackage().getName() + ".inventory.CraftItemStack");
            Method asNMSCopy = craftItemStackClass.getMethod("asNMSCopy", ItemStack.class);
            Method asBukkitCopy = craftItemStackClass.getMethod("asBukkitCopy", Class.forName("net.minecraft.world.item.ItemStack"));

            Object nmsStack = asNMSCopy.invoke(null, item);
            if (nmsStack == null) return;

            Class<?> dataComponentsClass = Class.forName("net.minecraft.core.component.DataComponents");
            Object dyedColorComponentKey = dataComponentsClass.getField("DYED_COLOR").get(null);

            Method removeMethod = nmsStack.getClass().getMethod("remove", Class.forName("net.minecraft.core.component.DataComponentType"));
            removeMethod.invoke(nmsStack, dyedColorComponentKey);

            ItemStack result = (ItemStack) asBukkitCopy.invoke(null, nmsStack);
            if (result != null && result.hasItemMeta()) {
                item.setItemMeta(result.getItemMeta());
            }
        } catch (Throwable ignored) {
            // NMS reflection failsafe
        }
    }

    public boolean clearBackpackColor(ItemStack item) {
        if (item == null || item.getType().isAir()) return false;
        ItemMeta meta = item.getItemMeta();
        if (meta == null) return false;
        if (!meta.getPersistentDataContainer().has(colorKey, PersistentDataType.INTEGER)) {
            return false;
        }
        meta.getPersistentDataContainer().remove(colorKey);
        if (meta instanceof LeatherArmorMeta leatherMeta) {
            leatherMeta.setColor(null);
        }
        applyBackpackMeta(meta);
        updateBackpackLore(meta, null);
        item.setItemMeta(meta);
        removeDyedColorComponent(item);
        return true;
    }

    public boolean consumeCauldronLevel(Block block) {
        if (block.getType() != Material.WATER_CAULDRON) return false;
        if (!(block.getBlockData() instanceof Levelled levelled)) return false;

        int currentLevel = levelled.getLevel();
        if (currentLevel > 1) {
            levelled.setLevel(currentLevel - 1);
            block.setBlockData(levelled);
        } else {
            block.setType(Material.CAULDRON);
        }

        Location loc = block.getLocation().add(0.5, 0.5, 0.5);
        block.getWorld().playSound(loc, Sound.ITEM_BUCKET_EMPTY, 1.0f, 1.2f);
        block.getWorld().playSound(loc, Sound.ENTITY_GENERIC_SPLASH, 0.8f, 1.4f);
        block.getWorld().spawnParticle(Particle.SPLASH, block.getLocation().add(0.5, 0.75, 0.5), 18, 0.2, 0.1, 0.2, 0.1);
        return true;
    }

    public void checkItemInCauldron(org.bukkit.entity.Item itemEntity) {
        if (itemEntity == null || !itemEntity.isValid() || itemEntity.isDead()) return;
        ItemStack stack = itemEntity.getItemStack();
        if (!isBackpack(stack)) return;
        if (getBackpackColor(stack) == null) return;

        Block block = itemEntity.getLocation().getBlock();
        if (block.getType() != Material.WATER_CAULDRON) {
            block = itemEntity.getLocation().clone().add(0, -0.1, 0).getBlock();
            if (block.getType() != Material.WATER_CAULDRON) return;
        }

        if (!consumeCauldronLevel(block)) return;

        ItemStack uncolored = stack.clone();
        clearBackpackColor(uncolored);
        itemEntity.setItemStack(uncolored);
    }

    public void checkCauldrons() {
        for (org.bukkit.World world : plugin.getServer().getWorlds()) {
            for (org.bukkit.entity.Item itemEntity : world.getEntitiesByClass(org.bukkit.entity.Item.class)) {
                if (!itemEntity.isValid() || itemEntity.isDead()) continue;
                ItemStack stack = itemEntity.getItemStack();
                if (isBackpack(stack) && getBackpackColor(stack) != null) {
                    checkItemInCauldron(itemEntity);
                }
            }
        }
    }

    public void updateBackpackLore(ItemMeta meta, Inventory inventory) {
        if (meta == null) return;

        int occupiedSlots = 0;
        List<String> itemLines = new ArrayList<>();

        if (inventory != null) {
            for (int slot : STORAGE_SLOTS) {
                ItemStack stack = inventory.getItem(slot);
                if (stack != null && !stack.getType().isAir()) {
                    occupiedSlots++;
                    if (itemLines.size() < 7) {
                        String name = formatItemStackName(stack);
                        itemLines.add(" §8• §f" + name + " §7x" + stack.getAmount());
                    }
                }
            }
        } else if (meta.getPersistentDataContainer().has(contentsKey, PersistentDataType.BYTE_ARRAY)) {
            byte[] bytes = meta.getPersistentDataContainer().get(contentsKey, PersistentDataType.BYTE_ARRAY);
            if (bytes != null) {
                try (BukkitObjectInputStream in = new BukkitObjectInputStream(new ByteArrayInputStream(bytes))) {
                    Object value = in.readObject();
                    if (value instanceof List<?> items) {
                        for (Object obj : items) {
                            if (obj instanceof ItemStack stack && !stack.getType().isAir()) {
                                occupiedSlots++;
                                if (itemLines.size() < 7) {
                                    String name = formatItemStackName(stack);
                                    itemLines.add(" §8• §f" + name + " §7x" + stack.getAmount());
                                }
                            }
                        }
                    }
                } catch (Throwable ignored) { }
            }
        }

        List<String> lore = new ArrayList<>();
        if (meta.getPersistentDataContainer().has(colorKey, PersistentDataType.INTEGER)) {
            int rgb = meta.getPersistentDataContainer().get(colorKey, PersistentDataType.INTEGER);
            lore.add("§7Màu sắc: " + getFriendlyDyeName(rgb));
        }
        lore.add("§7Sức chứa: §e54 slot");
        lore.add("");
        lore.add("§7─── §fChứa bên trong §8(§e" + occupiedSlots + "§7/§e54§7 slot) §7───");

        if (itemLines.isEmpty()) {
            lore.add(" §8• §7(Trống)");
        } else {
            lore.addAll(itemLines);
            if (occupiedSlots > 7) {
                lore.add(" §8• §7...và " + (occupiedSlots - 7) + " ô đồ khác");
            }
        }

        meta.setLore(lore);
    }

    private String formatItemStackName(ItemStack stack) {
        if (stack.hasItemMeta() && stack.getItemMeta().hasDisplayName()) {
            return stack.getItemMeta().getDisplayName();
        }
        String name = stack.getType().name().toLowerCase().replace('_', ' ');
        String[] words = name.split(" ");
        StringBuilder sb = new StringBuilder();
        for (String word : words) {
            if (!word.isEmpty()) {
                sb.append(Character.toUpperCase(word.charAt(0))).append(word.substring(1)).append(" ");
            }
        }
        return sb.toString().trim();
    }

    private void saveContainer(TileState state, Inventory inventory) {
        state.getPersistentDataContainer().set(contentsKey, PersistentDataType.BYTE_ARRAY, serializeInventory(inventory)); state.update(true, false);
    }

    private byte[] serializeInventory(Inventory inventory) {
        List<ItemStack> items = new ArrayList<>(); for (int slot : STORAGE_SLOTS) items.add(inventory.getItem(slot));
        try { ByteArrayOutputStream bytes = new ByteArrayOutputStream(); try (BukkitObjectOutputStream out = new BukkitObjectOutputStream(bytes)) { out.writeObject(items); } return bytes.toByteArray(); }
        catch (IOException ex) { throw new IllegalStateException("Không serialize được backpack", ex); }
    }

    private TileState placedState(Inventory inventory) {
        if (inventory == null || inventory.getLocation() == null) return null;
        if (!(inventory.getLocation().getBlock().getState() instanceof TileState state)) return null;
        return isPlacedBackpack(inventory.getLocation().getBlock()) ? state : null;
    }

    public boolean isPlacedBackpackInventory(Inventory inventory) { return placedState(inventory) != null; }

    public ItemStack addToPlacedBackpack(Inventory inventory, ItemStack item) {
        TileState state = placedState(inventory); if (state == null || item == null) return item;
        Inventory temp = plugin.getServer().createInventory(null, 54);
        loadContainer(state, temp);
        Map<Integer, ItemStack> leftovers = temp.addItem(item.clone());
        saveContainer(state, temp);
        return leftovers.values().stream().findFirst().orElse(null);
    }

    public ItemStack removeFromPlacedBackpack(Inventory inventory, ItemStack requested) {
        TileState state = placedState(inventory); if (state == null || requested == null) return null;
        Inventory temp = plugin.getServer().createInventory(null, 54);
        loadContainer(state, temp);
        ItemStack wanted = requested.clone();
        int before = wanted.getAmount();
        int remaining = temp.removeItem(wanted).values().stream().mapToInt(ItemStack::getAmount).sum();
        int removed = before - remaining;
        if (removed <= 0) return null;
        saveContainer(state, temp);
        wanted.setAmount(removed);
        return wanted;
    }
    public List<ItemStack> removePlacedContents(Location location) {
        UUID storage = UUID.nameUUIDFromBytes(storageKey(location).getBytes(StandardCharsets.UTF_8));
        List<ItemStack> contents = new ArrayList<>(); File file = file(storage);
        if (database != null) contents.addAll(database.load(storage));
        if (contents.isEmpty() && file.exists()) {
            List<?> items = YamlConfiguration.loadConfiguration(file).getList("items", List.of());
            for (Object item : items) if (item instanceof ItemStack stack && !stack.getType().isAir()) contents.add(stack);
            file.delete();
        }
        if (database != null) database.delete(storage);
        return contents;
    }

    public List<ItemStack> removePlacedContents(Block block) {
        UUID id = placedBackpackId(block);
        return id == null ? removePlacedContents(block.getLocation()) : removeContents(id);
    }

    public ItemStack createPlacedBackpackItem(Block block) {
        UUID id = placedBackpackId(block);
        if (!(block.getState() instanceof TileState state)) return id == null ? createBackpackItem() : createBackpackItem(id);
        ItemStack item = id == null ? createBackpackItem() : createBackpackItem(id);
        copyContainer(state, item);
        return item;
    }

    private List<ItemStack> removeContents(UUID storage) {
        List<ItemStack> contents = new ArrayList<>();
        if (database != null) contents.addAll(database.load(storage));
        databaseDelete(storage);
        File file = file(storage); if (file.exists()) file.delete();
        return contents;
    }

    private void databaseDelete(UUID storage) { if (database != null) database.delete(storage); }
    private String storageKey(Location location) {
        return location.getWorld().getUID() + ":" + location.getBlockX() + ":" + location.getBlockY() + ":" + location.getBlockZ();
    }
    private UUID storageId(String value) { try { return UUID.fromString(value); } catch (IllegalArgumentException ex) { return UUID.nameUUIDFromBytes(value.getBytes(StandardCharsets.UTF_8)); } }
    private String color(String text) { return ChatColor.translateAlternateColorCodes('&', text); }
    private Component component(String text) { return LegacyComponentSerializer.legacyAmpersand().deserialize(text); }

    public void updateWornBackpack(Player player) {
        if (player == null || !player.isOnline()) return;
        ItemStack chest = player.getInventory().getChestplate();
        if (isBackpack(chest)) {
            ItemDisplay display = getWornBackpackDisplay(player);
            if (display == null || !display.isValid()) {
                removeWornBackpack(player);
                display = player.getWorld().spawn(player.getLocation(), ItemDisplay.class, ent -> {
                    ent.setItemStack(chest.clone());
                    ent.setItemDisplayTransform(ItemDisplay.ItemDisplayTransform.HEAD);
                    ent.setPersistent(false);
                    ent.getPersistentDataContainer().set(wornKey, PersistentDataType.BYTE, (byte) 1);

                    Transformation transformation = new Transformation(
                            new Vector3f(0.0f, -0.35f, -0.25f),
                            new Quaternionf().rotateY((float) Math.PI),
                            new Vector3f(0.6f, 0.6f, 0.6f),
                            new Quaternionf()
                    );
                    ent.setTransformation(transformation);
                });
                player.addPassenger(display);
            } else {
                display.setItemStack(chest.clone());
            }
        } else {
            removeWornBackpack(player);
        }
    }

    public ItemDisplay getWornBackpackDisplay(Player player) {
        if (player == null) return null;
        for (Entity passenger : player.getPassengers()) {
            if (passenger instanceof ItemDisplay display && isWornBackpack(display)) {
                return display;
            }
        }
        return null;
    }

    public boolean isWornBackpack(Entity entity) {
        return entity != null && entity.isValid() && entity.getPersistentDataContainer().has(wornKey, PersistentDataType.BYTE);
    }

    public void removeWornBackpack(Player player) {
        if (player == null) return;
        for (Entity passenger : new ArrayList<>(player.getPassengers())) {
            if (isWornBackpack(passenger)) {
                player.removePassenger(passenger);
                passenger.remove();
            }
        }
        if (player.getWorld() != null) {
            for (Entity nearby : player.getWorld().getNearbyEntities(player.getLocation(), 2.0, 2.0, 2.0)) {
                if (nearby instanceof ItemDisplay && isWornBackpack(nearby) && nearby.getPassengers().isEmpty() && !player.getPassengers().contains(nearby)) {
                    nearby.remove();
                }
            }
        }
    }
}
