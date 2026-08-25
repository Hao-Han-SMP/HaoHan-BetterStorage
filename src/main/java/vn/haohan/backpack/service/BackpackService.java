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
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.lang.reflect.Method;
import java.io.IOException;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.*;

public final class BackpackService {
    /**
     * The reserved sockets (5 module slots) are deliberately outside persisted
     * backpack storage.
     */
    public static final int[] MODULE_SLOTS = { 47, 48, 49, 50, 51 };
    /**
     * All usable storage slots in the six-row backpack (54 - module sockets = 49
     * storage slots).
     */
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
        this.plugin = plugin;
        this.itemKey = itemKey;
        this.backpackIdKey = new NamespacedKey(plugin, "backpack_id");
        this.placedKey = new NamespacedKey(plugin, "placed_backpack");
        this.placedIdKey = new NamespacedKey(plugin, "placed_backpack_id");
        this.contentsKey = new NamespacedKey(plugin, "backpack_contents");
        this.visualKey = new NamespacedKey(plugin, "backpack_visual");
        this.visualIdKey = new NamespacedKey(plugin, "backpack_visual_id");
        this.wornKey = new NamespacedKey(plugin, "worn_backpack");
        this.colorKey = new NamespacedKey(plugin, "backpack_color");
        this.dataFolder = new File(plugin.getDataFolder(), "backpacks");
        dataFolder.mkdirs();
        SqliteStore store;
        try {
            store = new SqliteStore(plugin.getDataFolder());
        } catch (Exception ex) {
            plugin.getLogger().severe("SQLite kon the khoi tao: " + ex.getMessage());
            store = null;
        }
        this.database = store;
    }

    public void registerItemCoreDefinition() {
        if (!plugin.getServer().getPluginManager().isPluginEnabled("HaoHanItemCore"))
            return;
        try {
            vn.haohan.backpack.hook.ItemCoreHook.register();
        } catch (Throwable ex) {
            if (plugin.getConfig().getBoolean("debug", false))
                plugin.getLogger().info("ItemCore hook lỗi: " + ex.getClass().getSimpleName());
        }
    }

    public void registerDyeRecipes() {
        ItemStack templateBackpack = createTemplateBackpack();
        for (org.bukkit.DyeColor dye : org.bukkit.DyeColor.values()) {
            String colorName = dye.name().toLowerCase(java.util.Locale.ROOT);
            NamespacedKey key = new NamespacedKey(plugin, "dye_backpack_" + colorName);
            try {
                plugin.getServer().removeRecipe(key);
            } catch (Throwable ignored) {
            }
            try {
                ItemStack dyedBackpack = createTemplateBackpack();
                setBackpackColor(dyedBackpack, dye.getColor().asRGB());

                Material dyeMat = Material.valueOf(dye.name() + "_DYE");
                org.bukkit.inventory.ShapelessRecipe recipe = new org.bukkit.inventory.ShapelessRecipe(key,
                        dyedBackpack);
                recipe.setGroup("haohan_backpack_dye");
                recipe.setCategory(org.bukkit.inventory.recipe.CraftingBookCategory.EQUIPMENT);
                recipe.addIngredient(new org.bukkit.inventory.RecipeChoice.ExactChoice(templateBackpack));
                recipe.addIngredient(dyeMat);
                plugin.getServer().addRecipe(recipe);
            } catch (Throwable ignored) {
            }
        }
    }

    public ItemStack createTemplateBackpack() {
        ItemStack item = new ItemStack(Material.BROWN_DYE);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.displayName(component(plugin.getConfig().getString("backpack-item-name", "Backpack")));
            meta.lore(List.of(component("&7Chuột phải để mở ba lô cá nhân."),
                    component("&8Dung lượng: 53 ô + 1 module")));
            applyBackpackMeta(meta);
            meta.getPersistentDataContainer().set(itemKey, PersistentDataType.BYTE, (byte) 1);
            item.setItemMeta(meta);
        }
        return item;
    }

    public void discoverRecipes(Player player) {
        if (player == null || !player.isOnline())
            return;
        List<NamespacedKey> keys = new ArrayList<>();
        for (org.bukkit.DyeColor dye : org.bukkit.DyeColor.values()) {
            keys.add(new NamespacedKey(plugin, "dye_backpack_" + dye.name().toLowerCase(java.util.Locale.ROOT)));
        }
        try {
            player.discoverRecipes(keys);
        } catch (Throwable ignored) {
        }
    }

    public ItemStack createBackpackItem() {
        try {
            if (plugin.getServer().getPluginManager().isPluginEnabled("HaoHanItemCore")) {
                ItemStack item = vn.haohan.backpack.hook.ItemCoreHook.createItem("haohan:backpack");
                if (item != null) {
                    item.setType(Material.BROWN_DYE);
                    ItemMeta meta = item.getItemMeta();
                    if (meta != null) {
                        applyBackpackMeta(meta);
                        meta.getPersistentDataContainer().set(backpackIdKey, PersistentDataType.STRING,
                                UUID.randomUUID().toString());
                        item.setItemMeta(meta);
                    }
                    return item;
                }
            }
        } catch (Throwable ignored) {
        }
        ItemStack item = new ItemStack(Material.BROWN_DYE);
        ItemMeta meta = item.getItemMeta();
        meta.displayName(component(plugin.getConfig().getString("backpack-item-name", "Backpack")));
        meta.lore(List.of(component("&7Chuột phải để mở ba lô cá nhân."), component("&8Dung lượng: 53 ô + 1 module")));
        applyBackpackMeta(meta);
        meta.getPersistentDataContainer().set(itemKey, PersistentDataType.BYTE, (byte) 1);
        meta.getPersistentDataContainer().set(backpackIdKey, PersistentDataType.STRING, UUID.randomUUID().toString());
        item.setItemMeta(meta);
        return item;
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
                ItemFlag.HIDE_DYE);
    }

    public boolean isBackpack(ItemStack item) {
        if (item == null || item.getType().isAir())
            return false;
        try {
            if (vn.haohan.itemcore.api.HaoHanItemCore.get().getItemService().isItem(item, "haohan:backpack"))
                return true;
        } catch (Throwable ignored) {
        }
        if (item.getItemMeta() == null)
            return false;
        return item.getItemMeta().getPersistentDataContainer().has(itemKey, PersistentDataType.BYTE)
                || item.getItemMeta().getPersistentDataContainer().has(backpackIdKey, PersistentDataType.STRING);
    }

    public UUID backpackId(ItemStack item) {
        if (!isBackpack(item) || item.getItemMeta() == null)
            return null;
        String value = item.getItemMeta().getPersistentDataContainer().get(backpackIdKey, PersistentDataType.STRING);
        if (value == null)
            return null;
        try {
            return UUID.fromString(value);
        } catch (IllegalArgumentException ignored) {
            return null;
        }
    }

    public boolean isBlocked(ItemStack item) {
        if (item == null)
            return false;
        for (String material : plugin.getConfig().getStringList("blocked-materials"))
            if (item.getType().name().equalsIgnoreCase(material))
                return true;
        return false;
    }

    public boolean limitReached(Player player) {
        return !canReceiveBackpacks(player, 1);
    }

    public boolean canReceiveBackpacks(Player player, int amount) {
        if (!plugin.getConfig().getBoolean("backpack-limit.enabled", false))
            return true;
        int limit = Math.max(0, plugin.getConfig().getInt("backpack-limit.default", 1));
        int count = 0;
        for (ItemStack item : player.getInventory().getContents())
            if (isBackpack(item))
                count += item.getAmount();
        return count + amount <= limit;
    }

    public boolean keepBackpacksAfterDeath() {
        return plugin.getConfig().getBoolean("keep-backpacks-after-death", true);
    }

    public boolean blockBackpackInContainers() {
        return plugin.getConfig().getBoolean("block-backpack-in-containers", true);
    }

    public boolean allowBackpacksInsideBackpacks() {
        return plugin.getConfig().getBoolean("allow-backpacks-inside-backpacks", false);
    }

    public boolean hopperEnabled() {
        return plugin.getConfig().getBoolean("hopper.enabled", true);
    }

    public boolean backpackCollisionEnabled() {
        return plugin.getConfig().getBoolean("backpack-collision.enabled", true);
    }

    public SqliteStore database() {
        return database;
    }

    public List<UUID> listBackpacks(UUID owner) {
        return database == null ? List.of() : database.listByOwner(owner);
    }

    public void closeDatabase() {
        if (database != null)
            try {
                database.close();
            } catch (Exception ex) {
                plugin.getLogger().warning("Không đóng được SQLite: " + ex.getMessage());
            }
    }

    public void reload() {
        plugin.reloadConfig();
    }

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
            if (meta == null)
                return open(player);
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
            if (id != null)
                try {
                    return open(player, UUID.fromString(id), id, null, state, null);
                } catch (IllegalArgumentException ignored) {
                }
        }
        return open(player, UUID.nameUUIDFromBytes(key.getBytes(StandardCharsets.UTF_8)), key, null,
                block.getState() instanceof TileState state ? state : null, null);
    }

    public Inventory openAt(Player player, Entity visual) {
        ItemDisplay display = backpackVisualDisplay(visual);
        ItemStack item = display != null ? display.getItemStack() : null;
        UUID id = item != null ? backpackId(item) : visualStorageId(visual);
        if (id == null) {
            String value = visual.getPersistentDataContainer().get(placedIdKey, PersistentDataType.STRING);
            if (value != null)
                try {
                    id = UUID.fromString(value);
                } catch (IllegalArgumentException ignored) {
                }
        }
        if (id == null)
            id = UUID.randomUUID();
        if (item == null)
            item = createBackpackItem(id);

        return open(player, id, id.toString(), item, null, display);
    }

    private Inventory open(Player player, UUID storage, String storageId) {
        return open(player, storage, storageId, null, null, null);
    }

    private Inventory open(Player player, UUID storage, String storageId, ItemStack sourceItem, TileState sourceBlock,
            ItemDisplay sourceDisplay) {
        cleanupStaleLocks();
        if (open.containsKey(storage)) {
            player.sendMessage("§cBa lô này đang được mở bởi người khác.");
            return null;
        }
        BackpackHolder holder = new BackpackHolder(storageId, STORAGE_SLOTS, sourceItem, sourceBlock, sourceDisplay);
        Inventory inventory = plugin.getServer().createInventory(holder, 54, guiTitle(player));
        inventory.setMaxStackSize(512);
        holder.inventory(inventory);
        if (sourceItem != null) {
            loadContainer(sourceItem, inventory);
            // One-time migration for items created by the previous SQLite-backed version.
            if (!hasContainerContents(sourceItem) && database != null && database.exists(storage))
                load(storage, inventory);
        } else if (sourceBlock != null)
            loadContainer(sourceBlock, inventory);
        else
            load(storage, inventory);
        decorate(inventory);
        open.put(storage, inventory);
        player.openInventory(inventory);
        player.playSound(player.getLocation(), "haohan:backpack.open", 1.0f, 1.0f);
        return inventory;
    }

    private Component guiTitle(Player player) {
        String fallback = plugin.getConfig().getString("title", "&8Ba lô của %player%").replace("%player%",
                player.getName());
        if (!plugin.getConfig().getBoolean("custom-gui.enabled", true))
            return component(fallback);

        String font = plugin.getConfig().getString("custom-gui.font", "haohan:gui");
        String prefix = plugin.getConfig().getString("custom-gui.prefix", "\uE100");
        String glyph = plugin.getConfig().getString("custom-gui.glyph", "\uE101");
        net.kyori.adventure.key.Key fontKey = net.kyori.adventure.key.Key.key(font);
        return Component.text(prefix + glyph)
                .font(fontKey)
                .color(NamedTextColor.WHITE);
    }

    /**
     * The custom bitmap GUI supplies the background; module sockets are real locked
     * items.
     */
    /**
     * The custom bitmap GUI supplies the background; module sockets are real locked
     * items.
     */
    private void decorate(Inventory inventory) {
        ItemStack socket = createModuleSocketItem();
        for (int slot : MODULE_SLOTS) {
            ItemStack current = inventory.getItem(slot);
            if (current == null || current.getType().isAir()) {
                inventory.setItem(slot, socket.clone());
            }
        }
        applyCustomStackLimits(inventory);
    }

    public ItemStack createModuleSocketItem() {
        ItemStack socket = new ItemStack(Material.PAPER);
        ItemMeta meta = socket.getItemMeta();
        if (meta != null) {
            meta.displayName(component("&eÔ Cắm Module"));
            meta.lore(List.of(
                    component("&7Đây là ô cắm module trống."),
                    component("&7Đặt &fUpgrade Module &7vào đây"),
                    component("&7để tăng giới hạn stack của ba lô.")));
            meta.setItemModel(NamespacedKey.fromString("haohan:empty_module_slot"));
            socket.setItemMeta(meta);
        }
        return socket;
    }

    public boolean isEmptyModuleSocket(ItemStack item) {
        if (item == null || item.getType().isAir())
            return false;
        ItemMeta meta = item.getItemMeta();
        if (meta == null || !meta.hasDisplayName())
            return false;
        String name = net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer.plainText()
                .serialize(meta.displayName());
        return name.contains("Ô Cắm Module") || name.contains("Empty Module Slot");
    }

    public boolean isModule(ItemStack item) {
        if (item == null || item.getType().isAir() || isEmptyModuleSocket(item))
            return false;
        if (plugin.getServer().getPluginManager().isPluginEnabled("HaoHanItemCore")) {
            try {
                String id = vn.haohan.backpack.hook.ItemCoreHook.getItemId(item);
                if (id != null && (id.startsWith("haohan:upgrade_tier_") || id.equals("haohan:storage_module")))
                    return true;
            } catch (Throwable ignored) {
            }
        }
        return false;
    }

    public int getModuleStackCapacity(ItemStack moduleItem) {
        if (moduleItem == null || moduleItem.getType().isAir() || !isModule(moduleItem))
            return 64;
        String id = null;
        if (plugin.getServer().getPluginManager().isPluginEnabled("HaoHanItemCore")) {
            try {
                id = vn.haohan.backpack.hook.ItemCoreHook.getItemId(moduleItem);
            } catch (Throwable ignored) {
            }
        }
        if (id == null)
            return 64;

        return switch (id) {
            case "haohan:upgrade_tier_0" -> 128;
            case "haohan:upgrade_tier_1" -> 192;
            case "haohan:upgrade_tier_2" -> 320;
            case "haohan:upgrade_tier_3" -> 448;
            case "haohan:upgrade_tier_4" -> 512;
            default -> 64;
        };
    }

    public int getMaxStackCapacity(Inventory inventory) {
        if (inventory == null)
            return 64;
        int highestCap = 64;
        for (int slot : MODULE_SLOTS) {
            ItemStack module = inventory.getItem(slot);
            if (module != null && isModule(module)) {
                int cap = getModuleStackCapacity(module);
                if (cap > highestCap) {
                    highestCap = cap; // Prioritize highest tier without stacking
                }
            }
        }
        return highestCap;
    }

    public void applyCustomStackSize(ItemStack item, int maxCap) {
        if (item == null || item.getType().isAir()) return;
        ItemMeta meta = item.getItemMeta();
        if (meta == null) return;
        if (maxCap > 64) {
            try {
                meta.setMaxStackSize(99);
            } catch (Throwable ignored) {}
        } else {
            if (meta.hasMaxStackSize()) meta.setMaxStackSize(null);
        }
        item.setItemMeta(meta);
    }

    public void cleanCustomStackSize(ItemStack item) {
        if (item == null || item.getType().isAir()) return;
        if (item.hasItemMeta()) {
            ItemMeta meta = item.getItemMeta();
            if (meta != null) {
                if (meta.hasMaxStackSize()) {
                    meta.setMaxStackSize(null);
                    item.setItemMeta(meta);
                }
            }
        }
    }

    public boolean isSimilarIgnoringCustomStack(ItemStack a, ItemStack b) {
        if (a == null || b == null) return false;
        if (a.getType() != b.getType()) return false;
        ItemStack aClone = a.clone();
        cleanCustomStackSize(aClone);
        ItemStack bClone = b.clone();
        cleanCustomStackSize(bClone);
        return aClone.isSimilar(bClone);
    }

    public void applyCustomStackLimits(Inventory inventory) {
        if (inventory == null) return;
        int maxCap = getMaxStackCapacity(inventory);
        inventory.setMaxStackSize(maxCap);
        for (int slot : STORAGE_SLOTS) {
            ItemStack item = inventory.getItem(slot);
            if (item != null && !item.getType().isAir()) {
                applyCustomStackSize(item, maxCap);
            }
        }
    }

    public boolean isModuleSlot(int slot) {
        return containsStatic(MODULE_SLOTS, slot);
    }

    private boolean contains(int[] a, int value) { for (int i : a) if (i == value) return true; return false; }

    private static boolean containsStatic(int[] a, int value) {
        for (int slot : a)
            if (slot == value)
                return true;
        return false;
    }

    public void close(Player player, Inventory inventory) {
        if (!(inventory.getHolder() instanceof BackpackHolder holder)) return;
        UUID storage = storageId(holder.storageId());
        // InventoryCloseEvent and PlayerQuitEvent can both be fired for the
        // same view. Only the current lock owner may release it; an old close
        // event must never remove a newer player's lock.
        if (open.get(storage) != inventory)
            return;
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
     * without delivering a usable close event. An
     * inventory with no viewers
     * is no longer open, so release and persist that lock before the next
     * player tries to open it.
     */
    private void cleanupStaleLocks() {
        Iterator<Map.Entry<UUID, Inventory>> iterator = open.entrySet().iterator();
        while (iterator.hasNext()) {
            Map.Entry<UUID, Inventory> entry = iterator.next();
            Inventory inventory = entry.getValue();
            if (!inventory.getViewers().isEmpty())
                continue;
            if (inventory.getHolder() instanceof BackpackHolder holder) {
                saveOpenInventory(null, entry.getKey(), holder, inventory);
            }

        }
    }

    public void saveAllOpenBackpacks() {
        open.forEach((id, inventory) -> {
            if (inventory.getHolder() instanceof BackpackHolder holder)
                saveOpenInventory(null, id, holder, inventory);
        });
        open.clear();
    }

    private void load(UUID uuid, Inventory inventory) {
        inventory.setMaxStackSize(512);
        List<ItemStack> databaseItems = database == null ? List.of() : database.load(uuid);
        if (database != null && database.exists(uuid)) {
            loadItems(databaseItems, inventory);
            return;
        }
        File file = file(uuid);
        if (!file.exists()) return;
        List<?> items = YamlConfiguration.loadConfiguration(file).getList("items", List.of());
        loadItems(items, inventory);
    }

    private void loadItems(List<?> items, Inventory inventory) {
        inventory.setMaxStackSize(512);
        if (items.size() >= 54) {
            for (int physical = 0; physical < 54; physical++) {
                Object obj = items.get(physical);
                ItemStack stack = obj instanceof ItemStack s ? s : null;
                if (containsStatic(MODULE_SLOTS, physical)) {
                    if (stack != null && !stack.getType().isAir() && isModule(stack)) {
                        inventory.setItem(physical, stack);
                    } else {
                        inventory.setItem(physical, createModuleSocketItem());
                    }
                } else {
                    inventory.setItem(physical, stack);
                }
            }
            int cap = getMaxStackCapacity(inventory);
            inventory.setMaxStackSize(cap);
            return;
        }
        for (int i = 0; i < STORAGE_SLOTS.length && i < items.size(); i++)
            if (items.get(i) instanceof ItemStack stack) inventory.setItem(STORAGE_SLOTS[i], stack);
        for (int slot : MODULE_SLOTS) {
            if (inventory.getItem(slot) == null || inventory.getItem(slot).getType().isAir()) {
                inventory.setItem(slot, createModuleSocketItem());
            }
        }
        int cap = getMaxStackCapacity(inventory);
        inventory.setMaxStackSize(cap);

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
        YamlConfiguration yaml = new YamlConfiguration();
        List<ItemStack> items = new ArrayList<>();
        for (int physical = 0; physical < 54; physical++) {
            ItemStack item = inventory.getItem(physical);
            if (containsStatic(MODULE_SLOTS, physical) && isEmptyModuleSocket(item)) {
                items.add(null);
            } else if (item != null && !item.getType().isAir()) {
                ItemStack safe = item.clone();
                if (safe.getAmount() > 99) safe.setAmount(99);
                items.add(safe);
            } else {
                items.add(null);
            }
        }
        yaml.set("items", items);
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
        List<ItemStack> items = deserializeToItems(bytes);
        if (!items.isEmpty()) loadItems(items, inventory);
    }

    private void saveContainer(ItemStack item, Inventory inventory) {
        ItemMeta meta = item.getItemMeta();
        if (meta == null) return;
        meta.getPersistentDataContainer().set(contentsKey, PersistentDataType.BYTE_ARRAY, serializeInventory(inventory));
        updateBackpackLore(meta, inventory);
        item.setItemMeta(meta);
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

        int cap = 64;
        if (inventory != null) {
            cap = getMaxStackCapacity(inventory);
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
            List<ItemStack> items = deserializeToItems(bytes);
            for (int i = 0; i < items.size(); i++) {
                ItemStack stack = items.get(i);
                if (stack != null && !stack.getType().isAir()) {
                    if (containsStatic(MODULE_SLOTS, i) && isModule(stack)) {
                        int moduleCap = getModuleStackCapacity(stack);
                        if (moduleCap > cap) cap = moduleCap;
                    } else if (!containsStatic(MODULE_SLOTS, i)) {
                        occupiedSlots++;
                        if (itemLines.size() < 7) {
                            String name = formatItemStackName(stack);
                            itemLines.add(" §8• §f" + name + " §7x" + stack.getAmount());
                        }
                    }
                }
            }
        }

        List<String> lore = new ArrayList<>();
        if (meta.getPersistentDataContainer().has(colorKey, PersistentDataType.INTEGER)) {
            int rgb = meta.getPersistentDataContainer().get(colorKey, PersistentDataType.INTEGER);
            lore.add("§7Màu sắc: " + getFriendlyDyeName(rgb));
        }
        lore.add("§7Sức chứa: §e53 slot + 1 module");
        if (cap > 64) {
            lore.add("§7Giới hạn Stack: §e" + cap);
        }
        lore.add("");
        lore.add("§7─── §fChứa bên trong §8(§e" + occupiedSlots + "§7/§e53§7 slot) §7───");

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

    public List<ItemStack> deserializeToItems(byte[] bytes) {
        if (bytes == null || bytes.length == 0) return List.of();
        try {
            if (bytes.length >= 8) {
                ByteArrayInputStream bais = new ByteArrayInputStream(bytes);
                DataInputStream dataIn = new DataInputStream(bais);
                int magic = dataIn.readInt();
                if (magic == 0x48484250) {
                    int version = dataIn.readInt();
                    if (version == 2) {
                        int size = dataIn.readInt();
                        List<ItemStack> result = new ArrayList<>(size);
                        try (BukkitObjectInputStream in = new BukkitObjectInputStream(dataIn)) {
                            for (int i = 0; i < size; i++) {
                                boolean hasItem = dataIn.readBoolean();
                                if (hasItem) {
                                    int realAmount = dataIn.readInt();
                                    Object obj = in.readObject();
                                    if (obj instanceof ItemStack stack && !stack.getType().isAir()) {
                                        stack.setAmount(realAmount);
                                        cleanCustomStackSize(stack);
                                        result.add(stack);
                                    } else {
                                        result.add(null);
                                    }
                                } else {
                                    result.add(null);
                                }
                            }
                        }
                        return result;
                    }
                }
            }

            try (BukkitObjectInputStream in = new BukkitObjectInputStream(new ByteArrayInputStream(bytes))) {
                Object value = in.readObject();
                if (value instanceof List<?> items) {
                    List<ItemStack> result = new ArrayList<>();
                    for (Object obj : items) {
                        if (obj instanceof ItemStack stack) {
                            cleanCustomStackSize(stack);
                            result.add(stack);
                        } else {
                            result.add(null);
                        }
                    }
                    return result;
                }
            }
        } catch (Throwable ex) {
            plugin.getLogger().warning("Không đọc được serialized contents: " + ex.getMessage());
        }
        return List.of();
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
        try (ByteArrayOutputStream bytes = new ByteArrayOutputStream();
             DataOutputStream dataOut = new DataOutputStream(bytes)) {
            dataOut.writeInt(0x48484250); // Magic 'HHBP'
            dataOut.writeInt(2); // Version 2
            dataOut.writeInt(54);

            try (BukkitObjectOutputStream out = new BukkitObjectOutputStream(dataOut)) {
                for (int physical = 0; physical < 54; physical++) {
                    ItemStack item = inventory.getItem(physical);
                    if (item == null || item.getType().isAir() || (containsStatic(MODULE_SLOTS, physical) && isEmptyModuleSocket(item))) {
                        dataOut.writeBoolean(false);
                    } else {
                        dataOut.writeBoolean(true);
                        int realAmount = item.getAmount();
                        dataOut.writeInt(realAmount);

                        ItemStack toSerialize = item.clone();
                        toSerialize.setAmount(1); // Set to 1 so CraftMagicNumbers [1;99] codec never fails
                        cleanCustomStackSize(toSerialize);
                        out.writeObject(toSerialize);
                    }
                }
                out.flush();
            }
            return bytes.toByteArray();
        } catch (IOException ex) {
            throw new IllegalStateException("Không serialize được backpack", ex);
        }
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
