package vn.haohan.backpack.service;

import org.bukkit.Bukkit;
import org.bukkit.inventory.Recipe;
import org.bukkit.inventory.CookingRecipe;
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
import org.bukkit.entity.ArmorStand;
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
import vn.haohan.backpack.tier.BackpackTier;

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
    public static final int[] MODULE_SLOTS = { 47, 48, 49, 50, 51 };
    public static final int[] STORAGE_SLOTS = java.util.stream.IntStream.range(0, 54)
            .filter(slot -> java.util.Arrays.stream(MODULE_SLOTS).noneMatch(m -> m == slot)).toArray();

    private static boolean containsStatic(int[] a, int value) {
        if (a == null)
            return false;
        for (int slot : a)
            if (slot == value)
                return true;
        return false;
    }

    private static float getPlayerBodyYaw(Player player) {
        try {
            return player.getBodyYaw();
        } catch (Throwable ignored) {
            return player.getLocation().getYaw();
        }
    }

    private final Plugin plugin;
    private final NamespacedKey itemKey;
    private final NamespacedKey backpackIdKey;
    private final NamespacedKey placedKey;
    private final NamespacedKey placedIdKey;
    private final NamespacedKey contentsKey;
    private final NamespacedKey visualKey;
    private final NamespacedKey visualIdKey;
    private final NamespacedKey wornKey;
    private final NamespacedKey equippedKey;
    private final NamespacedKey colorKey;
    private final NamespacedKey origNameKey;
    private final NamespacedKey tierKey;
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
        this.equippedKey = new NamespacedKey(plugin, "equipped_backpack");
        this.colorKey = new NamespacedKey(plugin, "backpack_color");
        this.origNameKey = new NamespacedKey(plugin, "orig_display_name");
        this.tierKey = new NamespacedKey(plugin, "backpack_tier");
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

    public NamespacedKey equippedKey() {
        return equippedKey;
    }

    public ItemStack getEquippedBackpack(Player player) {
        if (player == null || !player.isOnline())
            return null;
        if (!player.getPersistentDataContainer().has(equippedKey, PersistentDataType.BYTE_ARRAY))
            return null;
        byte[] bytes = player.getPersistentDataContainer().get(equippedKey, PersistentDataType.BYTE_ARRAY);
        if (bytes == null || bytes.length == 0)
            return null;
        try {
            return ItemStack.deserializeBytes(bytes);
        } catch (Throwable ex) {
            return null;
        }
    }

    public void setEquippedBackpack(Player player, ItemStack item) {
        if (player == null || !player.isOnline())
            return;
        if (item == null || item.getType().isAir()) {
            player.getPersistentDataContainer().remove(equippedKey);
        } else {
            byte[] bytes = item.serializeAsBytes();
            player.getPersistentDataContainer().set(equippedKey, PersistentDataType.BYTE_ARRAY, bytes);
        }
    }

    public boolean hasEquippedBackpack(Player player) {
        return getEquippedBackpack(player) != null;
    }

    public ItemStack unequipBackpack(Player player) {
        ItemStack current = getEquippedBackpack(player);
        if (current != null) {
            player.getPersistentDataContainer().remove(equippedKey);
            removeWornBackpack(player);
        }
        return current;
    }

    public ItemStack getWornOrEquippedBackpack(Player player) {
        if (player == null || !player.isOnline())
            return null;
        ItemStack equipped = getEquippedBackpack(player);
        if (equipped != null && isBackpack(equipped))
            return equipped;
        ItemStack chest = player.getInventory().getChestplate();
        if (chest != null && isBackpack(chest))
            return chest;
        return null;
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

    public BackpackTier getBackpackTier(ItemMeta meta) {
        if (meta == null)
            return BackpackTier.LEATHER;
        if (meta.getPersistentDataContainer().has(tierKey, PersistentDataType.STRING)) {
            String id = meta.getPersistentDataContainer().get(tierKey, PersistentDataType.STRING);
            return BackpackTier.fromId(id);
        }

        // Detect from ItemModel if present
        if (meta.hasItemModel()) {
            String model = meta.getItemModel().toString().toLowerCase();
            for (BackpackTier tier : BackpackTier.values()) {
                if (model.contains("backpack_" + tier.getId())) {
                    meta.getPersistentDataContainer().set(tierKey, PersistentDataType.STRING, tier.getId());
                    return tier;
                }
            }
            if (model.contains("backpack")) {
                meta.getPersistentDataContainer().set(tierKey, PersistentDataType.STRING,
                        BackpackTier.NETHERITE.getId());
                return BackpackTier.NETHERITE;
            }
        }

        // Detect from Display Name if present
        if (meta.hasDisplayName()) {
            String name = net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer.plainText()
                    .serialize(meta.displayName()).toLowerCase();
            if (name.contains("netherite")) {
                meta.getPersistentDataContainer().set(tierKey, PersistentDataType.STRING,
                        BackpackTier.NETHERITE.getId());
                return BackpackTier.NETHERITE;
            }
            if (name.contains("kim cương") || name.contains("diamond")) {
                meta.getPersistentDataContainer().set(tierKey, PersistentDataType.STRING, BackpackTier.DIAMOND.getId());
                return BackpackTier.DIAMOND;
            }
            if (name.contains("vàng") || name.contains("gold")) {
                meta.getPersistentDataContainer().set(tierKey, PersistentDataType.STRING, BackpackTier.GOLD.getId());
                return BackpackTier.GOLD;
            }
            if (name.contains("sắt") || name.contains("iron")) {
                meta.getPersistentDataContainer().set(tierKey, PersistentDataType.STRING, BackpackTier.IRON.getId());
                return BackpackTier.IRON;
            }
            if (name.contains("da") || name.contains("leather")) {
                meta.getPersistentDataContainer().set(tierKey, PersistentDataType.STRING, BackpackTier.LEATHER.getId());
                return BackpackTier.LEATHER;
            }
        }

        return BackpackTier.LEATHER;
    }

    public BackpackTier getBackpackTier(ItemStack item) {
        if (item == null || item.getType().isAir())
            return BackpackTier.LEATHER;
        try {
            String itemId = vn.haohan.itemcore.api.HaoHanItemCore.get().getItemService().getId(item);
            if (itemId != null) {
                for (BackpackTier tier : BackpackTier.values()) {
                    if (itemId.contains("_" + tier.getId())) {
                        if (item.hasItemMeta()) {
                            ItemMeta meta = item.getItemMeta();
                            meta.getPersistentDataContainer().set(tierKey, PersistentDataType.STRING, tier.getId());
                            item.setItemMeta(meta);
                        }
                        return tier;
                    }
                }
                if (itemId.equals("haohan:backpack")) {
                    if (item.hasItemMeta()) {
                        ItemMeta meta = item.getItemMeta();
                        meta.getPersistentDataContainer().set(tierKey, PersistentDataType.STRING,
                                BackpackTier.NETHERITE.getId());
                        item.setItemMeta(meta);
                    }
                    return BackpackTier.NETHERITE;
                }
            }
        } catch (Throwable ignored) {
        }
        if (item.hasItemMeta()) {
            return getBackpackTier(item.getItemMeta());
        }
        return BackpackTier.LEATHER;
    }

    public void setBackpackTier(ItemStack item, BackpackTier tier) {
        if (item == null || item.getType().isAir() || tier == null)
            return;
        ItemMeta meta = item.getItemMeta();
        if (meta == null)
            return;
        meta.getPersistentDataContainer().set(tierKey, PersistentDataType.STRING, tier.getId());
        applyBackpackMeta(meta);
        updateBackpackLore(meta, null);
        item.setItemMeta(meta);
    }

    /**
     * Keep the item model and stack limit on the actual ItemStack. ItemCore's
     * definition is not enough for items created by an older definition, and
     * packs such as Hyper Punchy can otherwise fall back to the CHEST model.
     */
    private void applyBackpackMeta(ItemMeta meta) {
        BackpackTier tier = getBackpackTier(meta);
        meta.getPersistentDataContainer().set(tierKey, PersistentDataType.STRING, tier.getId());
        String modelName = "haohan:backpack_" + tier.getId();
        if (meta.getPersistentDataContainer().has(colorKey, PersistentDataType.INTEGER)) {
            int rgb = meta.getPersistentDataContainer().get(colorKey, PersistentDataType.INTEGER);
            modelName = "haohan:backpack_" + tier.getId() + "_" + getClosestDyeColorName(rgb);
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
            String itemId = vn.haohan.itemcore.api.HaoHanItemCore.get().getItemService().getId(item);
            if (itemId != null && (itemId.equals("haohan:backpack") || itemId.startsWith("haohan:backpack_")))
                return true;
        } catch (Throwable ignored) {
        }
        if (item.getItemMeta() == null)
            return false;
        return item.getItemMeta().getPersistentDataContainer().has(itemKey, PersistentDataType.BYTE)
                || item.getItemMeta().getPersistentDataContainer().has(backpackIdKey, PersistentDataType.STRING);
    }

    public void refreshBackpackItem(ItemStack item) {
        if (item == null || item.getType().isAir() || !isBackpack(item))
            return;
        BackpackTier tier = getBackpackTier(item);
        ItemMeta meta = item.getItemMeta();
        if (meta == null)
            return;
        meta.getPersistentDataContainer().set(tierKey, PersistentDataType.STRING, tier.getId());
        updateBackpackLore(meta, null);
        applyBackpackMeta(meta);
        item.setItemMeta(meta);
        if (meta.getPersistentDataContainer().has(contentsKey, PersistentDataType.BYTE_ARRAY)) {
            byte[] bytes = meta.getPersistentDataContainer().get(contentsKey, PersistentDataType.BYTE_ARRAY);
            List<ItemStack> items = deserializeToItems(bytes);
            if (!items.isEmpty()) {
                applyContainerComponent(item, items);
            }
        }
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

    public BackpackTier getTierFromInventory(Inventory inventory) {
        if (inventory == null)
            return BackpackTier.NETHERITE;
        if (inventory.getHolder() instanceof BackpackHolder holder && holder.sourceItem() != null) {
            return getBackpackTier(holder.sourceItem());
        }
        int size = inventory.getSize();
        for (BackpackTier tier : BackpackTier.values()) {
            if (tier.getTotalSlots() == size)
                return tier;
        }
        return BackpackTier.NETHERITE;
    }

    private Inventory open(Player player, UUID storage, String storageId, ItemStack sourceItem, TileState sourceBlock,
            ItemDisplay sourceDisplay) {
        cleanupStaleLocks();
        if (open.containsKey(storage)) {
            player.sendMessage("§cBa lô này đang được mở bởi người khác.");
            return null;
        }
        BackpackTier tier = sourceItem != null ? getBackpackTier(sourceItem) : BackpackTier.NETHERITE;
        boolean hasJuke = (sourceItem != null && hasJukeboxModule(sourceItem)) || (sourceBlock != null && hasJukeboxModule(sourceBlock));
        boolean hasFurnace = (sourceItem != null && hasFurnaceModule(sourceItem)) || (sourceBlock != null && hasFurnaceModule(sourceBlock));
        int[] dynamicStorageSlots = tier.getStorageSlots();
        BackpackHolder holder = new BackpackHolder(storageId, dynamicStorageSlots, sourceItem, sourceBlock,
                sourceDisplay);
        Inventory inventory = plugin.getServer().createInventory(holder, tier.getTotalSlots(), guiTitle(player, tier, hasJuke, hasFurnace));
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
        plugin.getServer().getScheduler().runTask(plugin, () -> {
            if (player.isOnline() && player.getOpenInventory().getTopInventory().equals(inventory)) {
                for (int slot : dynamicStorageSlots) {
                    ItemStack cur = inventory.getItem(slot);
                    if (cur != null && !cur.getType().isAir()) {
                        vn.haohan.backpack.listener.BackpackListener.sendDirectSlotUpdate(player, slot, cur);
                    }
                }
            }
        });
        player.playSound(player.getLocation(), "haohan:backpack.open", 1.0f, 1.0f);
        return inventory;
    }

    public void updateInventoryTitle(Player player, Inventory inventory, boolean hasJukebox) {
        boolean hasFurnace = hasFurnaceModule(inventory);
        updateInventoryTitle(player, inventory, hasJukebox, hasFurnace);
    }

    public void updateInventoryTitle(Player player, Inventory inventory, boolean hasJukebox, boolean hasFurnace) {
        if (player == null || !player.isOnline() || inventory == null)
            return;
        BackpackTier tier = getTierFromInventory(inventory);
        Component newTitle = guiTitle(player, tier, hasJukebox, hasFurnace);

        // 1. Send ClientboundOpenScreenPacket via reflection to update title preserving custom font
        try {
            Object craftPlayer = player;
            java.lang.reflect.Method getHandleMethod = craftPlayer.getClass().getMethod("getHandle");
            Object serverPlayer = getHandleMethod.invoke(craftPlayer);
            java.lang.reflect.Field containerMenuField = serverPlayer.getClass().getField("containerMenu");
            Object containerMenu = containerMenuField.get(serverPlayer);

            java.lang.reflect.Field containerIdField = containerMenu.getClass().getField("containerId");
            int containerId = containerIdField.getInt(containerMenu);

            java.lang.reflect.Method getTypeMethod = containerMenu.getClass().getMethod("getType");
            Object menuType = getTypeMethod.invoke(containerMenu);

            Class<?> paperAdventureClass = Class.forName("io.papermc.paper.adventure.PaperAdventure");
            java.lang.reflect.Method asVanillaMethod = paperAdventureClass.getMethod("asVanilla", net.kyori.adventure.text.Component.class);
            Object nmsComponent = asVanillaMethod.invoke(null, newTitle);

            Class<?> packetClass = Class.forName("net.minecraft.network.protocol.game.ClientboundOpenScreenPacket");
            java.lang.reflect.Constructor<?> targetCtor = null;
            for (java.lang.reflect.Constructor<?> c : packetClass.getConstructors()) {
                if (c.getParameterCount() == 3) {
                    targetCtor = c;
                    break;
                }
            }
            if (targetCtor != null) {
                Object packet = targetCtor.newInstance(containerId, menuType, nmsComponent);
                java.lang.reflect.Field connectionField = serverPlayer.getClass().getField("connection");
                Object connection = connectionField.get(serverPlayer);
                java.lang.reflect.Method sendMethod = null;
                for (java.lang.reflect.Method m : connection.getClass().getMethods()) {
                    if ((m.getName().equals("send") || m.getName().equals("sendPacket")) && m.getParameterCount() == 1) {
                        sendMethod = m;
                        break;
                    }
                }
                if (sendMethod != null) {
                    sendMethod.invoke(connection, packet);
                    java.lang.reflect.Method sendDataMethod = containerMenu.getClass().getMethod("sendAllDataToRemote");
                    sendDataMethod.invoke(containerMenu);
                    return;
                }
            }
        } catch (Throwable ignored) {
        }

        // 2. Fallback: Smooth inventory recreate with custom font Component
        plugin.getServer().getScheduler().runTask(plugin, () -> {
            if (player.isOnline() && player.getOpenInventory().getTopInventory().equals(inventory)) {
                ItemStack cursor = player.getItemOnCursor() != null ? player.getItemOnCursor().clone() : null;
                BackpackHolder holder = inventory.getHolder() instanceof BackpackHolder h ? h : null;
                Inventory newInv = plugin.getServer().createInventory(holder, tier.getTotalSlots(), newTitle);
                newInv.setMaxStackSize(512);
                if (holder != null) {
                    holder.inventory(newInv);
                    if (holder.sourceItem() != null) {
                        open.put(backpackId(holder.sourceItem()), newInv);
                    }
                }
                newInv.setContents(inventory.getContents());
                player.openInventory(newInv);
                if (cursor != null) {
                    player.setItemOnCursor(cursor);
                }
            }
        });
    }

        public Component guiTitle(Player player, BackpackTier tier) {
        return guiTitle(player, tier, false, false);
    }

    public Component guiTitle(Player player, BackpackTier tier, boolean hasJukebox) {
        return guiTitle(player, tier, hasJukebox, false);
    }

    public Component guiTitle(Player player, BackpackTier tier, boolean hasJukebox, boolean hasFurnace) {
        String fallback = plugin.getConfig().getString("title", "&8Ba lô của %player%").replace("%player%",
                player.getName());
        if (!plugin.getConfig().getBoolean("custom-gui.enabled", true))
            return component(fallback);

        String font = plugin.getConfig().getString("custom-gui.font", "haohan:gui");
        String prefix = "\uE100";
        if (plugin.getConfig().contains("custom-gui.prefix")) {
            String cfgPrefix = unescapeUnicode(plugin.getConfig().getString("custom-gui.prefix"));
            if (cfgPrefix != null && !cfgPrefix.isEmpty() && !cfgPrefix.contains("?")) {
                prefix = cfgPrefix;
            }
        }

        int rows = tier != null ? tier.getRows() : 6;
        String glyph;
        if (hasFurnace && hasJukebox) {
            glyph = switch (rows) {
                case 2 -> "\uE133";
                case 3 -> "\uE134";
                case 4 -> "\uE135";
                case 6 -> "\uE137";
                default -> "\uE137";
            };
            if (plugin.getConfig().contains("custom-gui.glyphs-furnace-jukebox." + rows)) {
                String cfgGlyph = unescapeUnicode(plugin.getConfig().getString("custom-gui.glyphs-furnace-jukebox." + rows));
                if (cfgGlyph != null && !cfgGlyph.isEmpty() && !cfgGlyph.contains("?")) {
                    glyph = cfgGlyph;
                }
            }
        } else if (hasFurnace) {
            glyph = switch (rows) {
                case 2 -> "\uE123";
                case 3 -> "\uE124";
                case 4 -> "\uE125";
                case 6 -> "\uE127";
                default -> "\uE127";
            };
            if (plugin.getConfig().contains("custom-gui.glyphs-furnace." + rows)) {
                String cfgGlyph = unescapeUnicode(plugin.getConfig().getString("custom-gui.glyphs-furnace." + rows));
                if (cfgGlyph != null && !cfgGlyph.isEmpty() && !cfgGlyph.contains("?")) {
                    glyph = cfgGlyph;
                }
            }
        } else if (hasJukebox) {
            glyph = switch (rows) {
                case 2 -> "\uE113";
                case 3 -> "\uE114";
                case 4 -> "\uE115";
                case 6 -> "\uE117";
                default -> "\uE111";
            };
            if (plugin.getConfig().contains("custom-gui.glyphs-jukebox." + rows)) {
                String cfgGlyph = unescapeUnicode(plugin.getConfig().getString("custom-gui.glyphs-jukebox." + rows));
                if (cfgGlyph != null && !cfgGlyph.isEmpty() && !cfgGlyph.contains("?")) {
                    glyph = cfgGlyph;
                }
            }
        } else {
            glyph = switch (rows) {
                case 1 -> "\uE102";
                case 2 -> "\uE103";
                case 3 -> "\uE104";
                case 4 -> "\uE105";
                case 5 -> "\uE106";
                case 6 -> "\uE101";
                default -> "\uE101";
            };
            if (plugin.getConfig().contains("custom-gui.glyphs." + rows)) {
                String cfgGlyph = unescapeUnicode(plugin.getConfig().getString("custom-gui.glyphs." + rows));
                if (cfgGlyph != null && !cfgGlyph.isEmpty() && !cfgGlyph.contains("?")) {
                    glyph = cfgGlyph;
                }
            }
        }
        net.kyori.adventure.key.Key fontKey = net.kyori.adventure.key.Key.key(font);
        return Component.text(prefix + glyph)
                .font(fontKey)
                .color(NamedTextColor.WHITE);
    }

    public static String unescapeUnicode(String st) {
        if (st == null) return null;
        StringBuilder sb = new StringBuilder();
        int i = 0;
        while (i < st.length()) {
            char c = st.charAt(i);
            if (c == '\\' && i + 1 < st.length()) {
                char next = st.charAt(i + 1);
                if ((next == 'u' || next == 'U') && i + 5 < st.length()) {
                    try {
                        String hex = st.substring(i + 2, i + 6);
                        int code = Integer.parseInt(hex, 16);
                        sb.append((char) code);
                        i += 6;
                        continue;
                    } catch (NumberFormatException ignored) {
                    }
                }
            }
            sb.append(c);
            i++;
        }
        return sb.toString();
    }

    /**
     * The custom bitmap GUI supplies the background; module sockets are real locked
     * items.
     */
        private void decorate(Inventory inventory) {
        if (inventory == null)
            return;
        // Clean out any legacy empty module placeholders from all slots
        for (int i = 0; i < inventory.getSize(); i++) {
            ItemStack item = inventory.getItem(i);
            if (item != null && isEmptyModuleSocket(item)) {
                inventory.setItem(i, null);
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
        if (meta == null)
            return false;
        if (meta.hasItemModel() && meta.getItemModel().toString().contains("empty_module_slot")) {
            return true;
        }
        if (meta.hasDisplayName()) {
            String name = net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer.plainText()
                    .serialize(meta.displayName());
            if (name.contains("Ô Cắm Module") || name.contains("Empty Module Slot")) {
                return true;
            }
        }
        return false;
    }

    public boolean isModule(ItemStack item) {
        if (item == null || item.getType().isAir() || isEmptyModuleSocket(item))
            return false;
        if (plugin.getServer().getPluginManager().isPluginEnabled("HaoHanItemCore")) {
            try {
                String id = vn.haohan.backpack.hook.ItemCoreHook.getItemId(item);
                if (id != null && (id.startsWith("haohan:upgrade_tier_") || id.equals("haohan:storage_module") || id.equals("haohan:magnet_module") || id.equals("haohan:jukebox_module") || id.startsWith("haohan:furnace_module")))
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

    public int getMaxStackCapacity(ItemStack backpack) {
        if (backpack == null || !isBackpack(backpack))
            return 64;
        UUID id = backpackId(backpack);
        if (id != null && open.containsKey(id)) {
            return getMaxStackCapacity(open.get(id));
        }
        ItemMeta meta = backpack.getItemMeta();
        if (meta == null || !meta.getPersistentDataContainer().has(contentsKey, PersistentDataType.BYTE_ARRAY)) {
            return 64;
        }
        byte[] bytes = meta.getPersistentDataContainer().get(contentsKey, PersistentDataType.BYTE_ARRAY);
        List<ItemStack> items = deserializeToItems(bytes);
        BackpackTier tier = getBackpackTier(meta);
        int highestCap = 64;
        for (int slot : tier.getModuleSlots()) {
            if (slot < items.size()) {
                ItemStack module = items.get(slot);
                if (module != null && isModule(module)) {
                    int cap = getModuleStackCapacity(module);
                    if (cap > highestCap) {
                        highestCap = cap;
                    }
                }
            }
        }
        return highestCap;
    }

    public int getMaxStackCapacity(Inventory inventory) {
        if (inventory == null)
            return 64;
        int highestCap = 64;
        BackpackTier tier = getTierFromInventory(inventory);
        for (int slot : tier.getModuleSlots()) {
            if (slot < inventory.getSize()) {
                ItemStack module = inventory.getItem(slot);
                if (module != null && isModule(module)) {
                    int cap = getModuleStackCapacity(module);
                    if (cap > highestCap) {
                        highestCap = cap;
                    }
                }
            }
        }
        return highestCap;
    }

    public boolean isMagnetModule(ItemStack item) {
        if (item == null || item.getType().isAir() || isEmptyModuleSocket(item))
            return false;
        if (plugin.getServer().getPluginManager().isPluginEnabled("HaoHanItemCore")) {
            try {
                String id = vn.haohan.backpack.hook.ItemCoreHook.getItemId(item);
                if ("haohan:magnet_module".equals(id))
                    return true;
            } catch (Throwable ignored) {
            }
        }
        return false;
    }

    public boolean hasMagnetModule(ItemStack backpack) {
        if (backpack == null || !isBackpack(backpack))
            return false;
        UUID id = backpackId(backpack);
        if (id != null && open.containsKey(id)) {
            return hasMagnetModule(open.get(id));
        }
        ItemMeta meta = backpack.getItemMeta();
        if (meta == null || !meta.getPersistentDataContainer().has(contentsKey, PersistentDataType.BYTE_ARRAY)) {
            return false;
        }
        byte[] bytes = meta.getPersistentDataContainer().get(contentsKey, PersistentDataType.BYTE_ARRAY);
        List<ItemStack> items = deserializeToItems(bytes);
        BackpackTier tier = getBackpackTier(meta);
        for (int slot : tier.getModuleSlots()) {
            if (slot < items.size()) {
                ItemStack mod = items.get(slot);
                if (isMagnetModule(mod)) {
                    return true;
                }
            }
        }
        return false;
    }

    public boolean hasMagnetModule(Inventory inventory) {
        if (inventory == null)
            return false;
        BackpackTier tier = getTierFromInventory(inventory);
        for (int slot : tier.getModuleSlots()) {
            if (slot < inventory.getSize()) {
                ItemStack mod = inventory.getItem(slot);
                if (isMagnetModule(mod)) {
                    return true;
                }
            }
        }
        return false;
    }

    public boolean hasMagnetActive(Player player) {
        if (player == null || !player.isValid() || player.isDead() || player.getGameMode() == org.bukkit.GameMode.SPECTATOR) {
            return false;
        }
        ItemStack worn = getWornOrEquippedBackpack(player);
        if (worn != null && isBackpack(worn) && hasMagnetModule(worn)) {
            return true;
        }
        for (ItemStack item : player.getInventory().getContents()) {
            if (item != null && isBackpack(item) && hasMagnetModule(item)) {
                return true;
            }
        }
        return false;
    }

    public double getMagnetRadius() {
        return plugin.getConfig().getDouble("magnet.radius", 6.0);
    }

    public void tickMagnetModules() {
        if (!plugin.getConfig().getBoolean("magnet.enabled", true))
            return;
        double radius = getMagnetRadius();
        if (radius <= 0)
            return;
        double speed = plugin.getConfig().getDouble("magnet.speed", 0.45);
        boolean particles = plugin.getConfig().getBoolean("magnet.particles", true);

        for (Player player : plugin.getServer().getOnlinePlayers()) {
            if (!hasMagnetActive(player))
                continue;

            Location playerLoc = player.getLocation().add(0, 0.75, 0);
            for (Entity entity : player.getWorld().getNearbyEntities(playerLoc, radius, radius, radius)) {
                if (!(entity instanceof org.bukkit.entity.Item itemEntity))
                    continue;
                if (!itemEntity.isValid() || itemEntity.isDead())
                    continue;
                if (itemEntity.getPickupDelay() > 0)
                    continue;

                Location itemLoc = itemEntity.getLocation();
                double distSq = playerLoc.distanceSquared(itemLoc);
                if (distSq > radius * radius || distSq < 0.05)
                    continue;

                org.bukkit.util.Vector dir = playerLoc.toVector().subtract(itemLoc.toVector());
                double dist = Math.sqrt(distSq);
                if (dist > 0.001) {
                    org.bukkit.util.Vector pull = dir.normalize().multiply(speed);
                    itemEntity.setVelocity(pull);

                    if (particles) {
                        Location iLoc = itemLoc.clone().add(0, 0.2, 0);
                        player.getWorld().spawnParticle(
                                Particle.DUST,
                                iLoc,
                                2, 0.08, 0.08, 0.08, 0.0,
                                new Particle.DustOptions(Color.fromRGB(255, 30, 30), 0.85f)
                        );
                        if (dist > 0.6) {
                            org.bukkit.util.Vector step = dir.clone().normalize().multiply(0.3);
                            player.getWorld().spawnParticle(
                                    Particle.DUST,
                                    iLoc.add(step),
                                    1, 0.02, 0.02, 0.02, 0.0,
                                    new Particle.DustOptions(Color.fromRGB(255, 75, 75), 0.65f)
                            );
                        }
                    }
                }
            }
        }
    }

    public boolean isJukeboxModule(ItemStack item) {
        if (item == null || item.getType().isAir() || isEmptyModuleSocket(item))
            return false;
        if (plugin.getServer().getPluginManager().isPluginEnabled("HaoHanItemCore")) {
            try {
                String id = vn.haohan.backpack.hook.ItemCoreHook.getItemId(item);
                if ("haohan:jukebox_module".equals(id))
                    return true;
            } catch (Throwable ignored) {
            }
        }
        return false;
    }

    public static class DiscInfo {
        private final String soundKey;
        private final Sound soundEnum;
        private final String displayName;
        private final int durationTicks;
        private final String uniqueId;

        public DiscInfo(String soundKey, Sound soundEnum, String displayName, int durationTicks, String uniqueId) {
            this.soundKey = soundKey;
            this.soundEnum = soundEnum;
            this.displayName = displayName;
            this.durationTicks = durationTicks;
            this.uniqueId = uniqueId;
        }

        public String getSoundKey() { return soundKey; }
        public Sound getSoundEnum() { return soundEnum; }
        public String getDisplayName() { return displayName; }
        public int getDurationTicks() { return durationTicks; }
        public String getUniqueId() { return uniqueId; }
    }

        public DiscInfo getDiscInfo(ItemStack item) {
        if (item == null || item.getType().isAir())
            return null;

        // 1. Check ItemCore custom item ID
        if (plugin.getServer().getPluginManager().isPluginEnabled("HaoHanItemCore")) {
            try {
                String id = vn.haohan.backpack.hook.ItemCoreHook.getItemId(item);
                if (id != null) {
                    if (id.contains("i_really_want_to_stay_at_your_house")) {
                        String name = "Rosa Walton - I Really Want to Stay at Your House";
                        if (item.hasItemMeta() && item.getItemMeta().hasDisplayName()) {
                            name = item.getItemMeta().getDisplayName();
                        }
                        return new DiscInfo("haohan:music_disc.i_really_want_to_stay_at_your_house", null, name, 5004, id);
                    } else if (id.startsWith("haohan:")) {
                        String discKey = id.replace("haohan:", "").replace("item/", "").replace("music_disc.", "");
                        String name = Character.toUpperCase(discKey.charAt(0)) + discKey.substring(1).replace("_", " ");
                        if (item.hasItemMeta() && item.getItemMeta().hasDisplayName()) {
                            name = item.getItemMeta().getDisplayName();
                        }
                        return new DiscInfo("haohan:music_disc." + discKey, null, name, 5000, id);
                    }
                }
            } catch (Throwable ignored) {
            }
        }

        // 2. Check 1.21.4 ItemModel component & DisplayName
        if (item.hasItemMeta()) {
            org.bukkit.inventory.meta.ItemMeta meta = item.getItemMeta();
            try {
                if (meta.hasItemModel()) {
                    org.bukkit.NamespacedKey model = meta.getItemModel();
                    if (model != null) {
                        String modelStr = model.toString().toLowerCase();
                        if (modelStr.contains("i_really_want_to_stay_at_your_house")) {
                            String name = "Rosa Walton - I Really Want to Stay at Your House";
                            if (meta.hasDisplayName()) {
                                name = meta.getDisplayName();
                            }
                            return new DiscInfo("haohan:music_disc.i_really_want_to_stay_at_your_house", null, name, 5004, "haohan:i_really_want_to_stay_at_your_house");
                        } else if (model.getNamespace().equals("haohan")) {
                            String discKey = model.getKey().replace("item/", "").replace("music_disc.", "");
                            String name = Character.toUpperCase(discKey.charAt(0)) + discKey.substring(1).replace("_", " ");
                            if (meta.hasDisplayName()) {
                                name = meta.getDisplayName();
                            }
                            return new DiscInfo("haohan:music_disc." + discKey, null, name, 5000, model.toString());
                        }
                    }
                }
            } catch (Throwable ignored) {
            }

            // Check Display Name specifically for known custom tracks
            if (meta.hasDisplayName()) {
                String dName = meta.getDisplayName();
                String plain = dName.replaceAll("§[0-9a-fk-or]", "").toLowerCase();
                if (plain.contains("i really want to stay at your house") || plain.contains("stay at your house")) {
                    return new DiscInfo("haohan:music_disc.i_really_want_to_stay_at_your_house", null, dName, 5004, "haohan:i_really_want_to_stay_at_your_house");
                }
            }

            // Check JukeboxPlayable component
            try {
                if (meta.hasJukeboxPlayable()) {
                    org.bukkit.inventory.meta.components.JukeboxPlayableComponent jb = meta.getJukeboxPlayable();
                    org.bukkit.NamespacedKey songKey = jb.getSongKey();
                    if (songKey != null) {
                        String songName = songKey.getKey();
                        String formatted = Character.toUpperCase(songName.charAt(0)) + songName.substring(1).replace("_", " ");
                        if (meta.hasDisplayName()) {
                            formatted = meta.getDisplayName();
                        }
                        String soundKey = songKey.getNamespace().equals("minecraft")
                                ? "minecraft:music_disc." + songKey.getKey()
                                : songKey.getNamespace() + ":music_disc." + songKey.getKey();
                        return new DiscInfo(soundKey, null, formatted, 5004, songKey.toString());
                    }
                }
            } catch (Throwable ignored) {
            }
        }

        // 3. Vanilla fallback
        Material mat = item.getType();
        if (mat.isRecord() || mat.name().startsWith("MUSIC_DISC_")) {
            Sound sound = getDiscSound(mat);
            String name = formatDiscName(mat);
            if (item.hasItemMeta() && item.getItemMeta().hasDisplayName()) {
                name = item.getItemMeta().getDisplayName();
            }
            int duration = getDiscDurationTicks(mat);
            return new DiscInfo(null, sound, name, duration, mat.name());
        }

        return null;
    }

    public boolean isMusicDisc(ItemStack item) {
        return getDiscInfo(item) != null;
    }

    public boolean hasJukeboxModule(ItemStack backpack) {
        if (backpack == null || !isBackpack(backpack))
            return false;
        UUID id = backpackId(backpack);
        if (id != null && open.containsKey(id)) {
            return hasJukeboxModule(open.get(id));
        }
        ItemMeta meta = backpack.getItemMeta();
        if (meta == null || !meta.getPersistentDataContainer().has(contentsKey, PersistentDataType.BYTE_ARRAY)) {
            return false;
        }
        byte[] bytes = meta.getPersistentDataContainer().get(contentsKey, PersistentDataType.BYTE_ARRAY);
        List<ItemStack> items = deserializeToItems(bytes);
        BackpackTier tier = getBackpackTier(meta);
        for (int slot : tier.getModuleSlots()) {
            if (slot < items.size()) {
                ItemStack mod = items.get(slot);
                if (isJukeboxModule(mod)) {
                    return true;
                }
            }
        }
        return false;
    }

    public boolean hasJukeboxModule(TileState state) {
        if (state == null)
            return false;
        if (!state.getPersistentDataContainer().has(contentsKey, PersistentDataType.BYTE_ARRAY))
            return false;
        byte[] bytes = state.getPersistentDataContainer().get(contentsKey, PersistentDataType.BYTE_ARRAY);
        List<ItemStack> items = deserializeToItems(bytes);
        BackpackTier tier = BackpackTier.NETHERITE;
        for (int slot : tier.getModuleSlots()) {
            if (slot < items.size()) {
                ItemStack mod = items.get(slot);
                if (isJukeboxModule(mod)) {
                    return true;
                }
            }
        }
        return false;
    }

    public boolean hasJukeboxModule(Inventory inventory) {
        if (inventory == null)
            return false;
        BackpackTier tier = getTierFromInventory(inventory);
        for (int slot : tier.getModuleSlots()) {
            if (slot < inventory.getSize()) {
                ItemStack mod = inventory.getItem(slot);
                if (isJukeboxModule(mod)) {
                    return true;
                }
            }
        }
        return false;
    }

    public ItemStack getMusicDisc(ItemStack backpack) {
        if (backpack == null || !isBackpack(backpack))
            return null;
        UUID id = backpackId(backpack);
        if (id != null && open.containsKey(id)) {
            return getMusicDisc(open.get(id));
        }
        ItemMeta meta = backpack.getItemMeta();
        if (meta == null || !meta.getPersistentDataContainer().has(contentsKey, PersistentDataType.BYTE_ARRAY)) {
            return null;
        }
        byte[] bytes = meta.getPersistentDataContainer().get(contentsKey, PersistentDataType.BYTE_ARRAY);
        List<ItemStack> items = deserializeToItems(bytes);
        BackpackTier tier = getBackpackTier(meta);
        int discSlot = tier.getDiscSlot();
        if (discSlot >= 0 && discSlot < items.size()) {
            ItemStack item = items.get(discSlot);
            if (isMusicDisc(item)) {
                return item;
            }
        }
        return null;
    }

    public ItemStack getMusicDisc(Inventory inventory) {
        if (inventory == null)
            return null;
        BackpackTier tier = getTierFromInventory(inventory);
        int discSlot = tier.getDiscSlot();
        if (discSlot >= 0 && discSlot < inventory.getSize()) {
            ItemStack item = inventory.getItem(discSlot);
            if (isMusicDisc(item)) {
                return item;
            }
        }
        return null;
    }

    public static Sound getDiscSound(Material mat) {
        try {
            return Sound.valueOf(mat.name());
        } catch (Throwable ignored) {
            return Sound.MUSIC_DISC_CAT;
        }
    }

    public static int getDiscDurationTicks(Material mat) {
        return switch (mat) {
            case MUSIC_DISC_13 -> 3560;
            case MUSIC_DISC_CAT -> 3700;
            case MUSIC_DISC_BLOCKS -> 6900;
            case MUSIC_DISC_CHIRP -> 3700;
            case MUSIC_DISC_FAR -> 3480;
            case MUSIC_DISC_MALL -> 3940;
            case MUSIC_DISC_MELLOHI -> 1920;
            case MUSIC_DISC_STAL -> 3000;
            case MUSIC_DISC_STRAD -> 3760;
            case MUSIC_DISC_WARD -> 5020;
            case MUSIC_DISC_11 -> 1420;
            case MUSIC_DISC_WAIT -> 4760;
            case MUSIC_DISC_OTHERSIDE -> 3900;
            case MUSIC_DISC_5 -> 3560;
            case MUSIC_DISC_PIGSTEP -> 2960;
            case MUSIC_DISC_RELIC -> 4360;
            case MUSIC_DISC_CREATOR -> 3520;
            case MUSIC_DISC_CREATOR_MUSIC_BOX -> 1460;
            case MUSIC_DISC_PRECIPICE -> 5980;
            default -> 3600;
        };
    }

    public static String formatDiscName(Material mat) {
        String name = mat.name().replace("MUSIC_DISC_", "").toLowerCase(Locale.ROOT);
        name = Character.toUpperCase(name.charAt(0)) + name.substring(1).replace("_", " ");
        return name;
    }

    private static class JukeboxPlayState {
        String currentDiscId;
        String soundKey;
        Sound soundEnum;
        int ticksLeft;

        JukeboxPlayState(String currentDiscId, String soundKey, Sound soundEnum, int ticksLeft) {
            this.currentDiscId = currentDiscId;
            this.soundKey = soundKey;
            this.soundEnum = soundEnum;
            this.ticksLeft = ticksLeft;
        }
    }

    private final Map<UUID, JukeboxPlayState> activeJukeboxes = new java.util.concurrent.ConcurrentHashMap<>();

    public void stopJukeboxMusic(Player player) {
        if (player == null || !player.isOnline())
            return;
        JukeboxPlayState state = activeJukeboxes.remove(player.getUniqueId());
        stopJukeboxSound(player, state);
    }

    public void stopJukeboxSound(Player player, JukeboxPlayState state) {
        if (player == null || !player.isOnline())
            return;
        try {
            for (Player nearby : player.getWorld().getPlayers()) {
                if (nearby.getLocation().distanceSquared(player.getLocation()) <= 64.0 * 64.0) {
                    if (state != null) {
                        if (state.soundKey != null) {
                            nearby.stopSound(state.soundKey, org.bukkit.SoundCategory.RECORDS);
                            nearby.stopSound(state.soundKey);
                            if (state.soundKey.contains(":")) {
                                nearby.stopSound(state.soundKey.split(":")[1]);
                                nearby.stopSound(state.soundKey.split(":")[1], org.bukkit.SoundCategory.RECORDS);
                            }
                        }
                        if (state.soundEnum != null) {
                            nearby.stopSound(state.soundEnum, org.bukkit.SoundCategory.RECORDS);
                            nearby.stopSound(state.soundEnum);
                        }
                    } else {
                        nearby.stopSound(org.bukkit.SoundCategory.RECORDS);
                    }
                }
            }
        } catch (Throwable ignored) {
        }
    }public void stopAllJukeboxMusic() {
        for (UUID pid : activeJukeboxes.keySet()) {
            Player p = plugin.getServer().getPlayer(pid);
            if (p != null) {
                stopJukeboxMusic(p);
            }
        }
        activeJukeboxes.clear();
    }

    public void tickJukeboxModules() {
        if (!plugin.getConfig().getBoolean("jukebox.enabled", true)) {
            if (!activeJukeboxes.isEmpty()) {
                stopAllJukeboxMusic();
            }
            return;
        }

        float volume = (float) plugin.getConfig().getDouble("jukebox.volume", 4.0);
        boolean particles = plugin.getConfig().getBoolean("jukebox.particles", true);

        for (Player player : plugin.getServer().getOnlinePlayers()) {
            UUID pid = player.getUniqueId();
            if (!player.isValid() || player.isDead() || player.getGameMode() == org.bukkit.GameMode.SPECTATOR) {
                if (activeJukeboxes.remove(pid) != null) {
                    stopJukeboxMusic(player);
                }
                continue;
            }

            ItemStack activeBackpack = null;
            ItemStack worn = getWornOrEquippedBackpack(player);
            if (worn != null && isBackpack(worn) && hasJukeboxModule(worn)) {
                activeBackpack = worn;
            } else {
                for (ItemStack item : player.getInventory().getContents()) {
                    if (item != null && isBackpack(item) && hasJukeboxModule(item)) {
                        activeBackpack = item;
                        break;
                    }
                }
            }

            if (activeBackpack != null) {
                ItemStack disc = getMusicDisc(activeBackpack);
                DiscInfo info = disc != null ? getDiscInfo(disc) : null;
                if (info != null) {
                    JukeboxPlayState state = activeJukeboxes.get(pid);

                    if (state == null || !java.util.Objects.equals(state.currentDiscId, info.getUniqueId()) || state.ticksLeft <= 0) {
                        stopJukeboxMusic(player);
                        try {
                            if (info.getSoundKey() != null) {
                                player.getWorld().playSound(player.getLocation(), info.getSoundKey(), org.bukkit.SoundCategory.RECORDS, volume, 1.0f);
                            } else if (info.getSoundEnum() != null) {
                                player.getWorld().playSound(player, info.getSoundEnum(), org.bukkit.SoundCategory.RECORDS, volume, 1.0f);
                            }
                        } catch (Throwable ignored) {
                        }
                        activeJukeboxes.put(pid, new JukeboxPlayState(info.getUniqueId(), info.getSoundKey(), info.getSoundEnum(), info.getDurationTicks()));
                        player.sendActionBar(Component.text("§6🎵 Ba lô đang phát: §e" + info.getDisplayName()));
                    } else {
                        state.ticksLeft -= 10;
                    }

                    if (particles && (player.getTicksLived() % 10 == 0)) {
                        Location noteLoc = player.getLocation().add(0, 2.1, 0);
                        double noteColor = (player.getTicksLived() % 24) / 24.0;
                        player.getWorld().spawnParticle(
                                Particle.NOTE,
                                noteLoc,
                                1, 0.35, 0.15, 0.35, noteColor
                        );
                    }
                } else {
                    if (activeJukeboxes.remove(pid) != null) {
                        stopJukeboxMusic(player);
                    }
                }
            } else {
                if (activeJukeboxes.remove(pid) != null) {
                    stopJukeboxMusic(player);
                }
            }
        }
    }

    public void applyCustomStackSize(ItemStack item, int maxCap) {
        if (item == null || item.getType().isAir())
            return;
        ItemMeta meta = item.getItemMeta();
        if (meta == null)
            return;

        if (maxCap > 64) {
            try {
                meta.setMaxStackSize(99);
            } catch (Throwable ignored) {
            }
        } else {
            if (meta.hasMaxStackSize())
                meta.setMaxStackSize(null);
        }

        if (item.getAmount() > 64) {
            String baseName;
            if (meta.getPersistentDataContainer().has(origNameKey, PersistentDataType.STRING)) {
                baseName = meta.getPersistentDataContainer().get(origNameKey, PersistentDataType.STRING);
            } else {
                if (meta.hasDisplayName()) {
                    baseName = meta.getDisplayName();
                } else {
                    baseName = formatItemStackName(item);
                }
                meta.getPersistentDataContainer().set(origNameKey, PersistentDataType.STRING, baseName);
            }
            meta.setDisplayName(baseName + " §e(x" + item.getAmount() + ")");
        } else {
            if (meta.getPersistentDataContainer().has(origNameKey, PersistentDataType.STRING)) {
                String orig = meta.getPersistentDataContainer().get(origNameKey, PersistentDataType.STRING);
                meta.getPersistentDataContainer().remove(origNameKey);
                if (orig != null && !orig.equals(formatItemStackName(item))) {
                    meta.setDisplayName(orig);
                } else {
                    meta.setDisplayName(null);
                }
            } else if (meta.hasDisplayName() && meta.getDisplayName().matches(".* §e\\(x\\d+\\)$")) {
                String clean = meta.getDisplayName().replaceAll(" §e\\(x\\d+\\)$", "");
                if (clean.equals(formatItemStackName(item))) {
                    meta.setDisplayName(null);
                } else {
                    meta.setDisplayName(clean);
                }
            }
        }

        item.setItemMeta(meta);
    }

    public void cleanCustomStackSize(ItemStack item) {
        if (item == null || item.getType().isAir())
            return;
        if (item.hasItemMeta()) {
            ItemMeta meta = item.getItemMeta();
            if (meta != null) {
                if (meta.hasMaxStackSize())
                    meta.setMaxStackSize(null);
                if (meta.getPersistentDataContainer().has(origNameKey, PersistentDataType.STRING)) {
                    String orig = meta.getPersistentDataContainer().get(origNameKey, PersistentDataType.STRING);
                    meta.getPersistentDataContainer().remove(origNameKey);
                    if (orig != null && !orig.equals(formatItemStackName(item))) {
                        meta.setDisplayName(orig);
                    } else {
                        meta.setDisplayName(null);
                    }
                } else if (meta.hasDisplayName() && meta.getDisplayName().matches(".* §e\\(x\\d+\\)$")) {
                    String clean = meta.getDisplayName().replaceAll(" §e\\(x\\d+\\)$", "");
                    if (clean.equals(formatItemStackName(item))) {
                        meta.setDisplayName(null);
                    } else {
                        meta.setDisplayName(clean);
                    }
                }
                item.setItemMeta(meta);
            }
        }
    }

    public boolean isSimilarIgnoringCustomStack(ItemStack a, ItemStack b) {
        if (a == null || b == null)
            return false;
        if (a.getType() != b.getType())
            return false;
        ItemStack aClone = a.clone();
        cleanCustomStackSize(aClone);
        ItemStack bClone = b.clone();
        cleanCustomStackSize(bClone);
        return aClone.isSimilar(bClone);
    }

    public void applyCustomStackLimits(Inventory inventory) {
        if (inventory == null)
            return;
        int maxCap = getMaxStackCapacity(inventory);
        inventory.setMaxStackSize(maxCap);
        BackpackTier tier = getTierFromInventory(inventory);
        for (int slot : tier.getStorageSlots()) {
            if (slot < inventory.getSize()) {
                ItemStack item = inventory.getItem(slot);
                if (item != null && !item.getType().isAir()) {
                    applyCustomStackSize(item, maxCap);
                }
            }
        }
    }

    public boolean isModuleSlot(int slot) {
        return containsStatic(MODULE_SLOTS, slot);
    }

    public boolean isModuleSlot(Inventory inventory, int slot) {
        if (inventory == null)
            return isModuleSlot(slot);
        BackpackTier tier = getTierFromInventory(inventory);
        return tier.isModuleSlot(slot);
    }

    private boolean contains(int[] a, int value) {
        for (int i : a)
            if (i == value)
                return true;
        return false;
    }

    public void close(Player player, Inventory inventory) {
        if (!(inventory.getHolder() instanceof BackpackHolder holder))
            return;
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
            if (owner != null) {
                Player player = plugin.getServer().getPlayer(owner);
                if (player != null && player.isOnline() && hasEquippedBackpack(player)) {
                    ItemStack equipped = getEquippedBackpack(player);
                    if (equipped != null && isBackpack(equipped)) {
                        UUID eqId = backpackId(equipped);
                        if (eqId != null && eqId.equals(storage)) {
                            setEquippedBackpack(player, holder.sourceItem());
                        }
                    }
                }
            }
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
        if (!file.exists())
            return;
        List<?> items = YamlConfiguration.loadConfiguration(file).getList("items", List.of());
        loadItems(items, inventory);
    }

    private void loadItems(List<?> items, Inventory inventory) {
        inventory.setMaxStackSize(512);
        int invSize = inventory.getSize();

        for (int i = 0; i < invSize && i < items.size(); i++) {
            Object obj = items.get(i);
            ItemStack stack = obj instanceof ItemStack s ? s : null;
            if (stack != null && !stack.getType().isAir() && !isEmptyModuleSocket(stack)) {
                vn.haohan.backpack.hook.NmsStackHelper.setDirectSlot(inventory, i, stack);
            } else {
                vn.haohan.backpack.hook.NmsStackHelper.setDirectSlot(inventory, i, null);
            }
        }

        int cap = getMaxStackCapacity(inventory);
        inventory.setMaxStackSize(cap);
    }

    private void putInFirstStorageSlot(ItemStack stack, Inventory inventory) {
        BackpackTier tier = getTierFromInventory(inventory);
        for (int slot : tier.getStorageSlots()) {
            if (slot < inventory.getSize()) {
                if (inventory.getItem(slot) == null || inventory.getItem(slot).getType().isAir()) {
                    inventory.setItem(slot, stack);
                    return;
                }
            }
        }
    }

    private void save(UUID owner, UUID uuid, Inventory inventory) {
        YamlConfiguration yaml = new YamlConfiguration();
        List<ItemStack> items = new ArrayList<>();
        int invSize = inventory.getSize();
        BackpackTier tier = getTierFromInventory(inventory);

        for (int physical = 0; physical < invSize; physical++) {
            ItemStack item = inventory.getItem(physical);
            if (tier.isModuleSlot(physical) && isEmptyModuleSocket(item)) {
                items.add(null);
            } else if (item != null && !item.getType().isAir()) {
                ItemStack safe = item.clone();
                if (safe.getAmount() > 99)
                    safe.setAmount(99);
                items.add(safe);
            } else {
                items.add(null);
            }
        }
        yaml.set("items", items);
        int[] dynamicStorageSlots = tier.getStorageSlots();
        if (database != null)
            database.save(uuid, owner, inventory, dynamicStorageSlots);
        // Retain the legacy YAML as a migration/back-up format.
        try {
            yaml.save(file(uuid));
        } catch (IOException ex) {
            plugin.getLogger().warning("Không lưu được ba lô " + uuid + ": " + ex.getMessage());
        }
    }

    private File file(UUID uuid) {
        return new File(dataFolder, uuid + ".yml");
    }

    public boolean isPlacedBackpack(Block block) {
        return block.getState() instanceof TileState state
                && state.getPersistentDataContainer().has(placedKey, PersistentDataType.BYTE);
    }

    public void markPlacedBackpack(Block block) {
        if (!(block.getState() instanceof TileState state))
            return;
        state.getPersistentDataContainer().set(placedKey, PersistentDataType.BYTE, (byte) 1);
        state.update(true, false);
    }

    public void markPlacedBackpack(Block block, ItemStack item) {
        if (!(block.getState() instanceof TileState state))
            return;
        state.getPersistentDataContainer().set(placedKey, PersistentDataType.BYTE, (byte) 1);
        UUID id = backpackId(item);
        if (id != null)
            state.getPersistentDataContainer().set(placedIdKey, PersistentDataType.STRING, id.toString());
        copyContainer(item, state);
        state.update(true, false);
    }

    /**
     * Spawn the backpack display and its exact matching interaction entity hitbox
     * directly.
     */
    public void spawnPlacedBackpack(Block block, ItemStack item, float yaw) {
        if (hasPlacedBackpackAt(block))
            return;
        Location location = block.getLocation();
        UUID id = backpackId(item);
        if (id == null)
            id = UUID.randomUUID();
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
            if (block.getType().isAir() && !hasPlacedBackpackAt(block))
                spawnPlacedBackpack(block, item, yaw);
        });
    }

    /**
     * Returns whether a backpack visual already occupies this block position.
     * The search box is tightly scoped to the block so vertically or horizontally
     * adjacent backpacks do not conflict.
     */
    public boolean hasPlacedBackpackAt(Block block) {
        if (block == null || block.getWorld() == null)
            return false;
        Location center = block.getLocation().add(0.5, 0.3, 0.5);
        return block.getWorld().getNearbyEntities(center, 0.4, 0.25, 0.4).stream()
                .anyMatch(this::isBackpackVisual);
    }

    public boolean isBackpackVisual(Entity entity) {
        if (entity == null || entity.isDead())
            return false;
        boolean isVisual = entity.getPersistentDataContainer().has(visualKey, PersistentDataType.BYTE);
        if (isVisual && entity instanceof Interaction interaction) {
            sanitizeInteraction(interaction);
        }
        return isVisual;
    }

    /**
     * Auto-correct interaction entity position and dimensions if they are offset or
     * oversized.
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

    public Location backpackVisualLocation(Entity entity) {
        return entity.getLocation().getBlock().getLocation();
    }

    public ItemDisplay backpackVisualDisplay(Entity entity) {
        if (entity instanceof ItemDisplay display)
            return display;
        String visualId = entity.getPersistentDataContainer().get(visualIdKey, PersistentDataType.STRING);
        if (visualId != null) {
            for (Entity nearby : entity.getNearbyEntities(0.8, 0.8, 0.8)) {
                if (nearby instanceof ItemDisplay display && isBackpackVisual(display)) {
                    String nearbyVisualId = display.getPersistentDataContainer().get(visualIdKey,
                            PersistentDataType.STRING);
                    if (visualId.equals(nearbyVisualId)) {
                        return display;
                    }
                }
            }
        }
        Block block = entity.getLocation().getBlock();
        for (Entity nearby : entity.getWorld().getNearbyEntities(block.getLocation().add(0.5, 0.3, 0.5), 0.4, 0.4,
                0.4)) {
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
        if (entity == null)
            return;
        String visualId = entity.getPersistentDataContainer().get(visualIdKey, PersistentDataType.STRING);
        if (visualId != null) {
            for (Entity nearby : entity.getNearbyEntities(0.8, 0.8, 0.8)) {
                if (!isBackpackVisual(nearby))
                    continue;
                String nearbyVisualId = nearby.getPersistentDataContainer().get(visualIdKey, PersistentDataType.STRING);
                if (visualId.equals(nearbyVisualId)) {
                    nearby.remove();
                }
            }
        } else {
            Block block = entity.getLocation().getBlock();
            for (Entity nearby : entity.getWorld().getNearbyEntities(block.getLocation().add(0.5, 0.3, 0.5), 0.4, 0.4,
                    0.4)) {
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
        if (item == null)
            item = createBackpackItem();
        item = item.clone();
        item.setAmount(1);

        UUID storage = visualStorageId(entity);
        if (storage == null)
            storage = backpackId(item);

        // Fallback for legacy placed backpacks without container NBT:
        if (!hasContainerContents(item) && storage != null) {
            List<ItemStack> contents = removeContents(storage);
            if (!contents.isEmpty()) {
                Inventory temp = plugin.getServer().createInventory(null, 54);
                for (ItemStack content : contents) {
                    if (content != null && !content.getType().isAir())
                        temp.addItem(content);
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
        try {
            return value == null ? null : UUID.fromString(value);
        } catch (IllegalArgumentException ignored) {
            return null;
        }
    }

    public UUID placedBackpackId(Block block) {
        if (!(block.getState() instanceof TileState state))
            return null;
        String value = state.getPersistentDataContainer().get(placedIdKey, PersistentDataType.STRING);
        try {
            return value == null ? null : UUID.fromString(value);
        } catch (IllegalArgumentException ex) {
            return null;
        }
    }

    public ItemStack createBackpackItem(UUID id) {
        ItemStack item = createBackpackItem();
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.getPersistentDataContainer().set(backpackIdKey, PersistentDataType.STRING, id.toString());
            item.setItemMeta(meta);
        }
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
            if (contents != null)
                to.getPersistentDataContainer().set(contentsKey, PersistentDataType.BYTE_ARRAY, contents);
        }
    }

    private void copyContainer(TileState from, ItemStack to) {
        ItemMeta meta = to.getItemMeta();
        if (meta != null) {
            byte[] contents = from.getPersistentDataContainer().get(contentsKey, PersistentDataType.BYTE_ARRAY);
            if (contents != null) {
                meta.getPersistentDataContainer().set(contentsKey, PersistentDataType.BYTE_ARRAY, contents);
                to.setItemMeta(meta);
            }
        }
    }

    private void loadContainer(ItemStack item, Inventory inventory) {
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            loadSerialized(meta.getPersistentDataContainer().get(contentsKey, PersistentDataType.BYTE_ARRAY),
                    inventory);
            updateBackpackLore(meta, inventory);
            item.setItemMeta(meta);
        }
    }

    private boolean hasContainerContents(ItemStack item) {
        ItemMeta meta = item.getItemMeta();
        return meta != null && meta.getPersistentDataContainer().has(contentsKey, PersistentDataType.BYTE_ARRAY);
    }

    private void loadContainer(TileState state, Inventory inventory) {
        loadSerialized(state.getPersistentDataContainer().get(contentsKey, PersistentDataType.BYTE_ARRAY), inventory);
    }

    private void loadSerialized(byte[] bytes, Inventory inventory) {
        if (bytes == null)
            return;
        List<ItemStack> items = deserializeToItems(bytes);
        if (!items.isEmpty())
            loadItems(items, inventory);
    }

    private void saveContainer(ItemStack item, Inventory inventory) {
        ItemMeta meta = item.getItemMeta();
        if (meta == null)
            return;
        meta.getPersistentDataContainer().set(contentsKey, PersistentDataType.BYTE_ARRAY,
                serializeInventory(inventory));
        updateBackpackLore(meta, inventory);
        item.setItemMeta(meta);
        applyContainerComponent(item, inventory);
    }

    public void applyContainerComponent(ItemStack item, Inventory inventory) {
        if (inventory == null)
            return;
        int size = inventory.getSize();
        List<ItemStack> items = new ArrayList<>(size);
        for (int i = 0; i < size; i++) {
            items.add(inventory.getItem(i));
        }
        applyContainerComponent(item, items);
    }

    public void applyContainerComponent(ItemStack item, List<ItemStack> items) {
        if (item == null || item.getType().isAir() || items == null)
            return;
        try {
            Class<?> craftItemStackClass = Class
                    .forName(plugin.getServer().getClass().getPackage().getName() + ".inventory.CraftItemStack");
            Method asNMSCopy = craftItemStackClass.getMethod("asNMSCopy", ItemStack.class);
            Method asBukkitCopy = craftItemStackClass.getMethod("asBukkitCopy",
                    Class.forName("net.minecraft.world.item.ItemStack"));

            Object nmsStack = asNMSCopy.invoke(null, item);
            if (nmsStack == null)
                return;

            Class<?> dataComponentsClass = Class.forName("net.minecraft.core.component.DataComponents");
            Object containerComponentKey = dataComponentsClass.getField("CONTAINER").get(null);

            Class<?> itemContainerContentsClass = Class
                    .forName("net.minecraft.world.item.component.ItemContainerContents");
            Method fromItemsMethod = itemContainerContentsClass.getMethod("fromItems", List.class);

            List<Object> nmsItems = new ArrayList<>(items.size());
            for (int i = 0; i < items.size(); i++) {
                ItemStack slotItem = items.get(i);
                boolean isModuleSocket = (i == items.size() - 1) && isEmptyModuleSocket(slotItem);
                if (slotItem == null || slotItem.getType().isAir() || isModuleSocket) {
                    Object emptyNms = asNMSCopy.invoke(null, new ItemStack(Material.AIR));
                    nmsItems.add(emptyNms);
                } else {
                    ItemStack clone = slotItem.clone();
                    cleanCustomStackSize(clone);
                    int visualAmount = Math.min(clone.getAmount(), 99);
                    clone.setAmount(visualAmount);
                    Object slotNms = asNMSCopy.invoke(null, clone);
                    nmsItems.add(slotNms);
                }
            }

            Object containerContents = fromItemsMethod.invoke(null, nmsItems);
            Method setMethod = nmsStack.getClass().getMethod("set",
                    Class.forName("net.minecraft.core.component.DataComponentType"), Object.class);
            setMethod.invoke(nmsStack, containerComponentKey, containerContents);

            ItemStack result = (ItemStack) asBukkitCopy.invoke(null, nmsStack);
            if (result != null && result.hasItemMeta()) {
                item.setItemMeta(result.getItemMeta());
            }
        } catch (Throwable ignored) {
            // NMS fallback
        }
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
        if (item == null || item.getType().isAir())
            return;
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
        if (item == null || !item.hasItemMeta())
            return null;
        return item.getItemMeta().getPersistentDataContainer().get(colorKey, PersistentDataType.INTEGER);
    }

    public void applyDyedColorComponent(ItemStack item, int rgb) {
        if (item == null || item.getType().isAir())
            return;
        try {
            Class<?> craftItemStackClass = Class
                    .forName(plugin.getServer().getClass().getPackage().getName() + ".inventory.CraftItemStack");
            Method asNMSCopy = craftItemStackClass.getMethod("asNMSCopy", ItemStack.class);
            Method asBukkitCopy = craftItemStackClass.getMethod("asBukkitCopy",
                    Class.forName("net.minecraft.world.item.ItemStack"));

            Object nmsStack = asNMSCopy.invoke(null, item);
            if (nmsStack == null)
                return;

            Class<?> dyedItemColorClass = Class.forName("net.minecraft.world.item.component.DyedItemColor");
            Object dyedColorObj;
            try {
                dyedColorObj = dyedItemColorClass.getConstructor(int.class, boolean.class).newInstance(rgb, false);
            } catch (Throwable t) {
                dyedColorObj = dyedItemColorClass.getConstructor(int.class).newInstance(rgb);
            }

            Class<?> dataComponentsClass = Class.forName("net.minecraft.core.component.DataComponents");
            Object dyedColorComponentKey = dataComponentsClass.getField("DYED_COLOR").get(null);

            Method setMethod = nmsStack.getClass().getMethod("set",
                    Class.forName("net.minecraft.core.component.DataComponentType"), Object.class);
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
        if (item == null || item.getType().isAir())
            return;
        try {
            Class<?> craftItemStackClass = Class
                    .forName(plugin.getServer().getClass().getPackage().getName() + ".inventory.CraftItemStack");
            Method asNMSCopy = craftItemStackClass.getMethod("asNMSCopy", ItemStack.class);
            Method asBukkitCopy = craftItemStackClass.getMethod("asBukkitCopy",
                    Class.forName("net.minecraft.world.item.ItemStack"));

            Object nmsStack = asNMSCopy.invoke(null, item);
            if (nmsStack == null)
                return;

            Class<?> dataComponentsClass = Class.forName("net.minecraft.core.component.DataComponents");
            Object dyedColorComponentKey = dataComponentsClass.getField("DYED_COLOR").get(null);

            Method removeMethod = nmsStack.getClass().getMethod("remove",
                    Class.forName("net.minecraft.core.component.DataComponentType"));
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
        if (item == null || item.getType().isAir())
            return false;
        ItemMeta meta = item.getItemMeta();
        if (meta == null)
            return false;
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
        if (block.getType() != Material.WATER_CAULDRON)
            return false;
        if (!(block.getBlockData() instanceof Levelled levelled))
            return false;

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
        block.getWorld().spawnParticle(Particle.SPLASH, block.getLocation().add(0.5, 0.75, 0.5), 18, 0.2, 0.1, 0.2,
                0.1);
        return true;
    }

    public void checkItemInCauldron(org.bukkit.entity.Item itemEntity) {
        if (itemEntity == null || !itemEntity.isValid() || itemEntity.isDead())
            return;
        ItemStack stack = itemEntity.getItemStack();
        if (!isBackpack(stack))
            return;
        if (getBackpackColor(stack) == null)
            return;

        Block block = itemEntity.getLocation().getBlock();
        if (block.getType() != Material.WATER_CAULDRON) {
            block = itemEntity.getLocation().clone().add(0, -0.1, 0).getBlock();
            if (block.getType() != Material.WATER_CAULDRON)
                return;
        }

        if (!consumeCauldronLevel(block))
            return;

        ItemStack uncolored = stack.clone();
        clearBackpackColor(uncolored);
        itemEntity.setItemStack(uncolored);
    }

    public void checkCauldrons() {
        for (org.bukkit.World world : plugin.getServer().getWorlds()) {
            for (org.bukkit.entity.Item itemEntity : world.getEntitiesByClass(org.bukkit.entity.Item.class)) {
                if (!itemEntity.isValid() || itemEntity.isDead())
                    continue;
                ItemStack stack = itemEntity.getItemStack();
                if (isBackpack(stack) && getBackpackColor(stack) != null) {
                    checkItemInCauldron(itemEntity);
                }
            }
        }
    }

    public void updateBackpackLore(ItemMeta meta, Inventory inventory) {
        if (meta == null)
            return;

        BackpackTier tier = getBackpackTier(meta);
        int occupiedSlots = 0;
        List<String> itemLines = new ArrayList<>();

        int cap = 64;
        boolean hasMagnet = false;
        boolean hasJukebox = false;
        ItemStack playingDisc = null;
        if (inventory != null) {
            cap = getMaxStackCapacity(inventory);
            hasMagnet = hasMagnetModule(inventory);
            hasJukebox = hasJukeboxModule(inventory);
            if (hasJukebox) {
                playingDisc = getMusicDisc(inventory);
            }
            for (int slot : tier.getStorageSlots()) {
                if (slot < inventory.getSize()) {
                    ItemStack stack = inventory.getItem(slot);
                    if (stack != null && !stack.getType().isAir()) {
                        occupiedSlots++;
                        if (itemLines.size() < 7) {
                            String name = formatItemStackName(stack);
                            itemLines.add(" §8• §f" + name + " §7x" + stack.getAmount());
                        }
                    }
                }
            }
        } else if (meta.getPersistentDataContainer().has(contentsKey, PersistentDataType.BYTE_ARRAY)) {
            byte[] bytes = meta.getPersistentDataContainer().get(contentsKey, PersistentDataType.BYTE_ARRAY);
            List<ItemStack> items = deserializeToItems(bytes);
            for (int i = 0; i < items.size(); i++) {
                ItemStack stack = items.get(i);
                if (stack != null && !stack.getType().isAir()) {
                    if (tier.isModuleSlot(i) && isModule(stack)) {
                        int moduleCap = getModuleStackCapacity(stack);
                        if (moduleCap > cap)
                            cap = moduleCap;
                        if (isMagnetModule(stack))
                            hasMagnet = true;
                        if (isJukeboxModule(stack))
                            hasJukebox = true;
                    } else if (!tier.isModuleSlot(i)) {
                        if (playingDisc == null && isMusicDisc(stack)) {
                            playingDisc = stack;
                        }
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
        lore.add("§7Cấp bậc: " + tier.getDisplayName());
        if (meta.getPersistentDataContainer().has(colorKey, PersistentDataType.INTEGER)) {
            int rgb = meta.getPersistentDataContainer().get(colorKey, PersistentDataType.INTEGER);
            lore.add("§7Đã nhuộm: " + getFriendlyDyeName(rgb));
        }
        if (tier.getModuleSlotsCount() > 0) {
            lore.add("§7Sức chứa: §e" + tier.getStorageSlotsCount() + " slot + " + tier.getModuleSlotsCount()
                    + " module");
        } else {
            lore.add("§7Sức chứa: §e" + tier.getStorageSlotsCount() + " slot");
        }
        if (cap > 64) {
            lore.add("§7Giới hạn Stack: §e" + cap);
        }
        if (hasMagnet) {
            lore.add("§7Tính năng: §cModule Nam Châm §8(Hút đồ)");
        }
        if (hasJukebox) {
            if (playingDisc != null) {
                DiscInfo info = getDiscInfo(playingDisc);
                String discTitle = info != null ? info.getDisplayName() : formatDiscName(playingDisc.getType());
                lore.add("§7Tính năng: §6Module Máy Hát §8(Đang phát: §e" + discTitle + "§8)");
            } else {
                lore.add("§7Tính năng: §6Module Máy Hát §8(Cần 1 đĩa nhạc)");
            }
        }
        lore.add("");
        lore.add("§7─── §fChứa bên trong §8(§e" + occupiedSlots + "§7/§e" + tier.getStorageSlotsCount()
                + "§7 slot) §7───");

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
        if (bytes == null || bytes.length == 0)
            return List.of();
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
        if (stack.hasItemMeta()) {
            ItemMeta meta = stack.getItemMeta();
            if (meta.getPersistentDataContainer().has(origNameKey, PersistentDataType.STRING)) {
                return meta.getPersistentDataContainer().get(origNameKey, PersistentDataType.STRING);
            }
            if (meta.hasDisplayName()) {
                return meta.getDisplayName().replaceAll(" §e\\(x\\d+\\)$", "").replaceAll(" \\(x\\d+\\)$", "");
            }
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
        state.getPersistentDataContainer().set(contentsKey, PersistentDataType.BYTE_ARRAY,
                serializeInventory(inventory));
        state.update(true, false);
    }

    public byte[] serializeItems(List<ItemStack> items) {
        try (ByteArrayOutputStream bytes = new ByteArrayOutputStream();
                DataOutputStream dataOut = new DataOutputStream(bytes)) {
            dataOut.writeInt(0x48484250); // Magic 'HHBP'
            dataOut.writeInt(2); // Version 2
            int size = items.size();
            dataOut.writeInt(size);

            try (BukkitObjectOutputStream out = new BukkitObjectOutputStream(dataOut)) {
                for (int physical = 0; physical < size; physical++) {
                    ItemStack item = items.get(physical);
                    boolean isModuleSocket = (physical == size - 1) && isEmptyModuleSocket(item);
                    if (item == null || item.getType().isAir() || isModuleSocket) {
                        dataOut.writeBoolean(false);
                    } else {
                        dataOut.writeBoolean(true);
                        int realAmount = item.getAmount();
                        dataOut.writeInt(realAmount);

                        ItemStack toSerialize = item.clone();
                        toSerialize.setAmount(1);
                        cleanCustomStackSize(toSerialize);
                        out.writeObject(toSerialize);
                    }
                }
                out.flush();
            }
            return bytes.toByteArray();
        } catch (IOException ex) {
            throw new IllegalStateException("Không serialize được items", ex);
        }
    }

    private byte[] serializeInventory(Inventory inventory) {
        try (ByteArrayOutputStream bytes = new ByteArrayOutputStream();
                DataOutputStream dataOut = new DataOutputStream(bytes)) {
            dataOut.writeInt(0x48484250); // Magic 'HHBP'
            dataOut.writeInt(2); // Version 2
            int size = inventory.getSize();
            dataOut.writeInt(size);

            try (BukkitObjectOutputStream out = new BukkitObjectOutputStream(dataOut)) {
                for (int physical = 0; physical < size; physical++) {
                    ItemStack item = inventory.getItem(physical);
                    boolean isModuleSocket = (physical == size - 1) && isEmptyModuleSocket(item);
                    if (item == null || item.getType().isAir() || isModuleSocket) {
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
        if (inventory == null || inventory.getLocation() == null)
            return null;
        if (!(inventory.getLocation().getBlock().getState() instanceof TileState state))
            return null;
        return isPlacedBackpack(inventory.getLocation().getBlock()) ? state : null;
    }

    public boolean isPlacedBackpackInventory(Inventory inventory) {
        return placedState(inventory) != null;
    }

    public ItemStack addToPlacedBackpack(Inventory inventory, ItemStack item) {
        TileState state = placedState(inventory);
        if (state == null || item == null)
            return item;
        Inventory temp = plugin.getServer().createInventory(null, 54);
        loadContainer(state, temp);
        Map<Integer, ItemStack> leftovers = temp.addItem(item.clone());
        saveContainer(state, temp);
        return leftovers.values().stream().findFirst().orElse(null);
    }

    public ItemStack removeFromPlacedBackpack(Inventory inventory, ItemStack requested) {
        TileState state = placedState(inventory);
        if (state == null || requested == null)
            return null;
        Inventory temp = plugin.getServer().createInventory(null, 54);

        ItemStack wanted = requested.clone();
        int before = wanted.getAmount();
        int remaining = temp.removeItem(wanted).values().stream().mapToInt(ItemStack::getAmount).sum();
        int removed = before - remaining;
        if (removed <= 0)
            return null;
        saveContainer(state, temp);
        wanted.setAmount(removed);
        return wanted;
    }

    public List<ItemStack> removePlacedContents(Location location) {
        UUID storage = UUID.nameUUIDFromBytes(storageKey(location).getBytes(StandardCharsets.UTF_8));
        List<ItemStack> contents = new ArrayList<>();
        File file = file(storage);
        if (database != null)
            contents.addAll(database.load(storage));
        if (contents.isEmpty() && file.exists()) {
            List<?> items = YamlConfiguration.loadConfiguration(file).getList("items", List.of());
            for (Object item : items)
                if (item instanceof ItemStack stack && !stack.getType().isAir())
                    contents.add(stack);
            file.delete();
        }
        if (database != null)
            database.delete(storage);
        return contents;
    }

    public List<ItemStack> removePlacedContents(Block block) {
        UUID id = placedBackpackId(block);
        return id == null ? removePlacedContents(block.getLocation()) : removeContents(id);
    }

    public ItemStack createPlacedBackpackItem(Block block) {
        UUID id = placedBackpackId(block);
        if (!(block.getState() instanceof TileState state))
            return id == null ? createBackpackItem() : createBackpackItem(id);
        ItemStack item = id == null ? createBackpackItem() : createBackpackItem(id);
        copyContainer(state, item);
        return item;
    }

    private List<ItemStack> removeContents(UUID storage) {
        List<ItemStack> contents = new ArrayList<>();

        databaseDelete(storage);

        File file = file(storage);
        if (file.exists())
            file.delete();

        return contents;
    }

    private void databaseDelete(UUID storage) {
        if (database != null)
            database.delete(storage);
    }

    private String storageKey(Location location) {
        return location.getWorld().getUID() + ":" + location.getBlockX() + ":" + location.getBlockY() + ":"
                + location.getBlockZ();
    }

    private UUID storageId(String value) {
        try {
            return UUID.fromString(value);
        } catch (IllegalArgumentException ex) {
            return UUID.nameUUIDFromBytes(value.getBytes(StandardCharsets.UTF_8));
        }
    }

    private String color(String text) {
        return ChatColor.translateAlternateColorCodes('&', text);
    }

    private Component component(String text) {
        return LegacyComponentSerializer.legacyAmpersand().deserialize(text);
    }

    public NamespacedKey wornKey() {
        return wornKey;
    }

        private final Map<UUID, ItemDisplay> wornDisplays = new java.util.concurrent.ConcurrentHashMap<>();
    private final Map<UUID, Float> playerBodyYawTracker = new java.util.concurrent.ConcurrentHashMap<>();
    private final Map<UUID, Location> playerLastLocationTracker = new java.util.concurrent.ConcurrentHashMap<>();

    public void tickWornBackpacks() {
        if (!plugin.getConfig().getBoolean("worn-backpack.enabled", true)) {
            if (!wornDisplays.isEmpty()) {
                wornDisplays.values().forEach(Entity::remove);
                wornDisplays.clear();
                playerBodyYawTracker.clear();
                playerLastLocationTracker.clear();
            }
            return;
        }

        double backOffset = plugin.getConfig().getDouble("worn-backpack.offset.back", 0.40);
        double heightOffset = plugin.getConfig().getDouble("worn-backpack.offset.height", 0.73);
        double sneakHeight = plugin.getConfig().getDouble("worn-backpack.offset.sneak-height", 0.70);

        for (Player player : plugin.getServer().getOnlinePlayers()) {
            UUID pid = player.getUniqueId();
            if (!player.isValid() || player.isDead()) {
                ItemDisplay d = wornDisplays.remove(pid);
                if (d != null)
                    d.remove();
                playerBodyYawTracker.remove(pid);
                playerLastLocationTracker.remove(pid);
                continue;
            }

            ItemStack worn = getWornOrEquippedBackpack(player);
            if (worn != null && isBackpack(worn)) {
                ItemDisplay display = wornDisplays.get(pid);
                if (display == null || !display.isValid() || display.getVehicle() != player) {
                    if (display != null)
                        display.remove();
                    display = player.getWorld().spawn(player.getLocation(), ItemDisplay.class, ent -> {
                        ent.setItemStack(worn.clone());
                        ent.setItemDisplayTransform(ItemDisplay.ItemDisplayTransform.FIXED);
                        ent.setPersistent(false);
                        ent.setTeleportDuration(1);
                        ent.setInterpolationDuration(1);
                        ent.setInterpolationDelay(0);
                        ent.getPersistentDataContainer().set(wornKey, PersistentDataType.BYTE, (byte) 1);
                    });
                    player.addPassenger(display);
                    wornDisplays.put(pid, display);
                } else {
                    display.setItemStack(worn.clone());
                }

                                Location currentLoc = player.getLocation();
                Location lastLoc = playerLastLocationTracker.put(pid, currentLoc);
                boolean sameWorld = lastLoc != null && currentLoc.getWorld().equals(lastLoc.getWorld());
                double distSq = sameWorld ? currentLoc.distanceSquared(lastLoc) : 999.0;
                boolean teleported = !sameWorld || distSq > 64.0;
                boolean isMoving = sameWorld && distSq > 0.0001;

                float headYaw = currentLoc.getYaw();
                float headPitch = currentLoc.getPitch();
                org.bukkit.entity.Pose pose = player.getPose();

                boolean isCrawling = (pose == org.bukkit.entity.Pose.SWIMMING && !player.isInWater());
                boolean isSwimmingWater = (player.isSwimming() || (pose == org.bukkit.entity.Pose.SWIMMING && player.isInWater()));
                boolean isGliding = (player.isGliding() || pose == org.bukkit.entity.Pose.FALL_FLYING || pose == org.bukkit.entity.Pose.SPIN_ATTACK);
                boolean isSitting = player.isInsideVehicle();

                Float trackedYaw = playerBodyYawTracker.get(pid);
                if (trackedYaw == null || teleported) {
                    trackedYaw = headYaw;
                }

                // Continuous motion detection: Whenever player moves (A/D strafe, W/S walk, sprint, jump, crawl, swim, fly, sneak),
                // the torso directly locks to headYaw with 0ms delay.
                if (isMoving || player.isSprinting() || isGliding || isSwimmingWater || isCrawling || player.isSneaking()) {
                    trackedYaw = headYaw;
                } else if (isSitting && player.getVehicle() != null) {
                    trackedYaw = player.getVehicle().getLocation().getYaw();
                } else {
                    // Standing still: Allow looking around within a 45-degree cone before the back naturally turns
                    float diff = ((headYaw - trackedYaw + 540.0f) % 360.0f) - 180.0f;
                    if (diff < -45.0f) {
                        trackedYaw = headYaw + 45.0f;
                    } else if (diff > 45.0f) {
                        trackedYaw = headYaw - 45.0f;
                    }
                }
                playerBodyYawTracker.put(pid, trackedYaw);

                display.setRotation(trackedYaw + 180.0f, 0.0f);

                org.joml.Vector3f translation;
                org.joml.Quaternionf rotation = new org.joml.Quaternionf();

                if (isGliding || isSwimmingWater) {
                    // Pitch tilts with view direction during diving or climbing
                    rotation.rotateX((float) Math.toRadians(-headPitch - 90.0f));
                    org.joml.Vector3f localTorsoOffset = new org.joml.Vector3f(0.0f, -0.20f, -0.15f);
                    translation = rotation.transform(localTorsoOffset, new org.joml.Vector3f());
                } else if (isCrawling) {
                    // Flat horizontal orientation along the spine when crawling
                    rotation.rotateX((float) Math.toRadians(-90.0f));
                    org.joml.Vector3f localTorsoOffset = new org.joml.Vector3f(0.0f, -0.20f, -0.15f);
                    translation = rotation.transform(localTorsoOffset, new org.joml.Vector3f());
                    translation.y -= 1.05f; // Lower to sit snugly on crawling back
                } else if (player.isSneaking() || pose == org.bukkit.entity.Pose.SNEAKING) {
                    float height = (float) (sneakHeight - 1.48);
                    rotation.rotateX((float) Math.toRadians(-28.6f));
                    translation = new org.joml.Vector3f(0.0f, height, (float) backOffset + 0.15f);
                } else if (isSitting) {
                    float height = (float) (heightOffset - 1.75);
                    translation = new org.joml.Vector3f(0.0f, height, (float) backOffset);
                } else {
                    float height = (float) (heightOffset - 1.45);
                    translation = new org.joml.Vector3f(0.0f, height - 0.15f, (float) backOffset);
                }

                org.joml.Vector3f scale = new org.joml.Vector3f(1.0f, 1.0f, 1.0f);

                org.bukkit.util.Transformation trans = new org.bukkit.util.Transformation(
                        translation,
                        rotation,
                        scale,
                        new org.joml.Quaternionf());
                display.setTransformation(trans);
            } else {
                ItemDisplay d = wornDisplays.remove(pid);
                if (d != null)
                    d.remove();
                playerBodyYawTracker.remove(pid);
                playerLastLocationTracker.remove(pid);
            }
        }
    }

    public void updateWornBackpack(Player player) {
        if (player == null || !player.isOnline())
            return;
        ItemStack worn = getWornOrEquippedBackpack(player);
        UUID pid = player.getUniqueId();
        if (worn == null || !isBackpack(worn)) {
            ItemDisplay d = wornDisplays.remove(pid);
            if (d != null)
                d.remove();
            removeWornBackpack(player);
        }
    }

    public boolean isWornBackpack(Entity entity) {
        return entity != null && entity.isValid()
                && entity.getPersistentDataContainer().has(wornKey, PersistentDataType.BYTE);
    }

    public void removeWornBackpack(Player player) {
        if (player == null)
            return;
        playerBodyYawTracker.remove(player.getUniqueId());
        playerLastLocationTracker.remove(player.getUniqueId());
        ItemDisplay d = wornDisplays.remove(player.getUniqueId());
        if (d != null) {
            d.remove();
        }
        for (Entity passenger : new ArrayList<>(player.getPassengers())) {
            if (isWornBackpack(passenger)) {
                player.removePassenger(passenger);
                passenger.remove();
            }
        }
        if (player.getWorld() != null) {
            for (Entity nearby : player.getWorld().getNearbyEntities(player.getLocation(), 3.0, 3.0, 3.0)) {
                if ((nearby instanceof ArmorStand || nearby instanceof ItemDisplay) && isWornBackpack(nearby)) {
                    nearby.remove();
                }
            }
        }
    }

    public void removeAllWornBackpacks() {
        wornDisplays.values().forEach(Entity::remove);
        wornDisplays.clear();
        playerBodyYawTracker.clear();
                playerLastLocationTracker.clear();
    }

    // ==================== Furnace Module Logic ====================

    public boolean isFurnaceModule(ItemStack item) {
        if (item == null || item.getType().isAir() || isEmptyModuleSocket(item))
            return false;
        if (plugin.getServer().getPluginManager().isPluginEnabled("HaoHanItemCore")) {
            try {
                String id = vn.haohan.backpack.hook.ItemCoreHook.getItemId(item);
                if (id != null && (id.startsWith("haohan:furnace_module") || id.startsWith("furnace_module")))
                    return true;
            } catch (Throwable ignored) {
            }
        }
        if (item.hasItemMeta()) {
            ItemMeta meta = item.getItemMeta();
            if (meta != null && meta.hasItemModel()) {
                String model = meta.getItemModel().asString();
                if (model.contains("furnace_module"))
                    return true;
            }
            if (meta != null && meta.hasDisplayName()) {
                String name = net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer.plainText().serialize(meta.displayName());
                if (name.contains("Furnace Module") || name.contains("Module Lò Nung"))
                    return true;
            }
        }
        return false;
    }

    public int getFurnaceModuleTier(ItemStack item) {
        if (!isFurnaceModule(item))
            return -1;
        if (plugin.getServer().getPluginManager().isPluginEnabled("HaoHanItemCore")) {
            try {
                String id = vn.haohan.backpack.hook.ItemCoreHook.getItemId(item);
                if (id != null) {
                    if (id.endsWith("tier_4") || id.endsWith("tier4")) return 4;
                    if (id.endsWith("tier_3") || id.endsWith("tier3")) return 3;
                    if (id.endsWith("tier_2") || id.endsWith("tier2")) return 2;
                    if (id.endsWith("tier_1") || id.endsWith("tier1")) return 1;
                    if (id.endsWith("tier_0") || id.endsWith("tier0")) return 0;
                }
            } catch (Throwable ignored) {
            }
        }
        if (item.hasItemMeta()) {
            ItemMeta meta = item.getItemMeta();
            if (meta != null && meta.hasItemModel()) {
                String model = meta.getItemModel().asString();
                for (int t = 4; t >= 0; t--) {
                    if (model.contains("tier_" + t) || model.contains("tier" + t))
                        return t;
                }
            }
        }
        return 0;
    }

    public int getHighestFurnaceTier(Inventory inventory) {
        if (inventory == null)
            return -1;
        BackpackTier tier = getTierFromInventory(inventory);
        int highest = -1;
        for (int slot : tier.getModuleSlots()) {
            if (slot < inventory.getSize()) {
                ItemStack mod = inventory.getItem(slot);
                if (mod != null && isFurnaceModule(mod)) {
                    int t = getFurnaceModuleTier(mod);
                    if (t > highest)
                        highest = t;
                }
            }
        }
        return highest;
    }

    public boolean hasFurnaceModule(TileState state) {
        if (state == null)
            return false;
        if (!state.getPersistentDataContainer().has(contentsKey, PersistentDataType.BYTE_ARRAY))
            return false;
        byte[] bytes = state.getPersistentDataContainer().get(contentsKey, PersistentDataType.BYTE_ARRAY);
        List<ItemStack> items = deserializeToItems(bytes);
        BackpackTier tier = BackpackTier.NETHERITE;
        for (int slot : tier.getModuleSlots()) {
            if (slot < items.size()) {
                ItemStack mod = items.get(slot);
                if (mod != null && isFurnaceModule(mod)) {
                    return true;
                }
            }
        }
        return false;
    }

    public boolean hasFurnaceModule(Inventory inventory) {
        return getHighestFurnaceTier(inventory) >= 0;
    }

    public int getHighestFurnaceTier(ItemStack backpack) {
        if (backpack == null || !isBackpack(backpack))
            return -1;
        UUID id = backpackId(backpack);
        if (id != null && open.containsKey(id)) {
            return getHighestFurnaceTier(open.get(id));
        }
        ItemMeta meta = backpack.getItemMeta();
        if (meta == null || !meta.getPersistentDataContainer().has(contentsKey, PersistentDataType.BYTE_ARRAY)) {
            return -1;
        }
        byte[] bytes = meta.getPersistentDataContainer().get(contentsKey, PersistentDataType.BYTE_ARRAY);
        List<ItemStack> items = deserializeToItems(bytes);
        BackpackTier tier = getBackpackTier(meta);
        int highest = -1;
        for (int slot : tier.getModuleSlots()) {
            if (slot < items.size()) {
                ItemStack mod = items.get(slot);
                if (mod != null && isFurnaceModule(mod)) {
                    int t = getFurnaceModuleTier(mod);
                    if (t > highest)
                        highest = t;
                }
            }
        }
        return highest;
    }

    public boolean hasFurnaceModule(ItemStack backpack) {
        return getHighestFurnaceTier(backpack) >= 0;
    }

    public int getFuelBurnTicks(ItemStack fuel) {
        if (fuel == null || fuel.getType().isAir())
            return 0;
        Material mat = fuel.getType();
        if (mat == Material.LAVA_BUCKET) return 20000;
        if (mat == Material.COAL || mat == Material.CHARCOAL) return 1600;
        if (mat == Material.COAL_BLOCK) return 16000;
        if (mat == Material.BLAZE_ROD) return 2400;
        if (mat == Material.DRIED_KELP_BLOCK) return 4000;
        if (mat == Material.BAMBOO_BLOCK) return 1080;
        if (mat.name().endsWith("_LOG") || mat.name().endsWith("_WOOD") || mat.name().endsWith("_PLANKS") || mat.name().endsWith("_STEM") || mat.name().endsWith("_HYPHAE")) return 300;
        if (mat.name().startsWith("WOODEN_") || mat.name().endsWith("_SLAB") || mat.name().endsWith("_STAIRS") || mat.name().endsWith("_PRESSURE_PLATE") || mat.name().endsWith("_BUTTON")) return 150;
        if (mat == Material.STICK) return 100;
        if (mat == Material.BAMBOO) return 50;
        try {
            if (mat.isFuel()) return 200;
        } catch (Throwable ignored) {}
        return 0;
    }

    private static final ItemStack NO_SMELT_RESULT = new ItemStack(Material.AIR);
    private final Map<Material, ItemStack> smeltCache = new java.util.concurrent.ConcurrentHashMap<>();

    public ItemStack getSmeltResult(ItemStack input) {
        if (input == null || input.getType().isAir())
            return null;
        Material mat = input.getType();
        ItemStack cached = smeltCache.get(mat);
        if (cached != null) {
            return cached.getType().isAir() ? null : cached.clone();
        }

        ItemStack result = findSmeltingResult(mat);
        smeltCache.put(mat, result != null ? result.clone() : NO_SMELT_RESULT);
        return result != null ? result.clone() : null;
    }

    private ItemStack findSmeltingResult(Material mat) {
        Material outputMat = switch (mat) {
            case RAW_IRON, IRON_ORE, DEEPSLATE_IRON_ORE -> Material.IRON_INGOT;
            case RAW_GOLD, GOLD_ORE, DEEPSLATE_GOLD_ORE, NETHER_GOLD_ORE -> Material.GOLD_INGOT;
            case RAW_COPPER, COPPER_ORE, DEEPSLATE_COPPER_ORE -> Material.COPPER_INGOT;
            case COAL_ORE, DEEPSLATE_COAL_ORE -> Material.COAL;
            case EMERALD_ORE, DEEPSLATE_EMERALD_ORE -> Material.EMERALD;
            case LAPIS_ORE, DEEPSLATE_LAPIS_ORE -> Material.LAPIS_LAZULI;
            case DIAMOND_ORE, DEEPSLATE_DIAMOND_ORE -> Material.DIAMOND;
            case REDSTONE_ORE, DEEPSLATE_REDSTONE_ORE -> Material.REDSTONE;
            case NETHER_QUARTZ_ORE -> Material.QUARTZ;
            case ANCIENT_DEBRIS -> Material.NETHERITE_SCRAP;
            case BEEF -> Material.COOKED_BEEF;
            case PORKCHOP -> Material.COOKED_PORKCHOP;
            case CHICKEN -> Material.COOKED_CHICKEN;
            case MUTTON -> Material.COOKED_MUTTON;
            case RABBIT -> Material.COOKED_RABBIT;
            case COD -> Material.COOKED_COD;
            case SALMON -> Material.COOKED_SALMON;
            case POTATO -> Material.BAKED_POTATO;
            case KELP -> Material.DRIED_KELP;
            case SAND, RED_SAND -> Material.GLASS;
            case COBBLESTONE -> Material.STONE;
            case STONE -> Material.SMOOTH_STONE;
            case SANDSTONE -> Material.SMOOTH_SANDSTONE;
            case RED_SANDSTONE -> Material.SMOOTH_RED_SANDSTONE;
            case BASALT -> Material.SMOOTH_BASALT;
            case QUARTZ_BLOCK -> Material.SMOOTH_QUARTZ;
            case CLAY_BALL -> Material.BRICK;
            case CLAY -> Material.TERRACOTTA;
            case NETHERRACK -> Material.NETHER_BRICK;
            case CACTUS -> Material.GREEN_DYE;
            case SEA_PICKLE -> Material.LIME_DYE;
            case CHORUS_FRUIT -> Material.POPPED_CHORUS_FRUIT;
            case WET_SPONGE -> Material.SPONGE;
            default -> null;
        };
        if (outputMat != null) {
            return new ItemStack(outputMat);
        }
        if (mat.name().endsWith("_LOG") || mat.name().endsWith("_WOOD") || mat.name().endsWith("_STEM") || mat.name().endsWith("_HYPHAE")) {
            return new ItemStack(Material.CHARCOAL);
        }

        try {
            ItemStack single = new ItemStack(mat);
            for (java.util.Iterator<Recipe> it = Bukkit.recipeIterator(); it.hasNext(); ) {
                Recipe r = it.next();
                if (r instanceof CookingRecipe<?> cooking) {
                    if (cooking.getInputChoice().test(single)) {
                        return cooking.getResult().clone();
                    }
                }
            }
        } catch (Throwable ignored) {}

        return null;
    }

    public static class FurnaceState {
        public int burnTicksLeft;
        public int maxBurnTicks;
        public int cookTicks;
        public FurnaceState(int burnTicksLeft, int maxBurnTicks, int cookTicks) {
            this.burnTicksLeft = burnTicksLeft;
            this.maxBurnTicks = maxBurnTicks;
            this.cookTicks = cookTicks;
        }
    }

    private final Map<UUID, FurnaceState> activeFurnaces = new java.util.concurrent.ConcurrentHashMap<>();

    public void tickFurnaceModules() {
        if (!plugin.getConfig().getBoolean("furnace.enabled", true))
            return;
        boolean particles = plugin.getConfig().getBoolean("furnace.particles", true);
        boolean sound = plugin.getConfig().getBoolean("furnace.sound", true);

        // Process open backpack inventories
        for (Map.Entry<UUID, Inventory> entry : open.entrySet()) {
            UUID id = entry.getKey();
            Inventory inv = entry.getValue();
            if (inv == null)
                continue;
            int furnaceTier = getHighestFurnaceTier(inv);
            if (furnaceTier < 0)
                continue;
            Player viewer = null;
            if (!inv.getViewers().isEmpty() && inv.getViewers().get(0) instanceof Player p) {
                viewer = p;
            }
            processFurnace(id, inv, viewer, furnaceTier, particles, sound);
        }

        // Process worn/carried backpacks for online players if not open
        for (Player player : plugin.getServer().getOnlinePlayers()) {
            if (!player.isValid() || player.isDead())
                continue;
            ItemStack worn = getWornOrEquippedBackpack(player);
            if (worn != null && isBackpack(worn)) {
                UUID id = backpackId(worn);
                if (id != null && !open.containsKey(id)) {
                    int furnaceTier = getHighestFurnaceTier(worn);
                    if (furnaceTier >= 0) {
                        processFurnaceItemStack(id, worn, player, furnaceTier, particles, sound);
                    }
                }
            }
            for (ItemStack item : player.getInventory().getContents()) {
                if (item != null && isBackpack(item) && !item.equals(worn)) {
                    UUID id = backpackId(item);
                    if (id != null && !open.containsKey(id)) {
                        int furnaceTier = getHighestFurnaceTier(item);
                        if (furnaceTier >= 0) {
                            processFurnaceItemStack(id, item, player, furnaceTier, particles, sound);
                        }
                    }
                }
            }
        }
    }

    private void processFurnaceItemStack(UUID id, ItemStack backpack, Player player, int tierLevel, boolean particles, boolean sound) {
        ItemMeta meta = backpack.getItemMeta();
        if (meta == null || !meta.getPersistentDataContainer().has(contentsKey, PersistentDataType.BYTE_ARRAY))
            return;
        byte[] bytes = meta.getPersistentDataContainer().get(contentsKey, PersistentDataType.BYTE_ARRAY);
        List<ItemStack> items = deserializeToItems(bytes);
        BackpackTier tier = getBackpackTier(meta);
        int inputSlot = tier.getFurnaceInputSlot();
        int fuelSlot = tier.getFurnaceFuelSlot();
        if (inputSlot < 0 || fuelSlot < 0 || inputSlot >= items.size() || fuelSlot >= items.size())
            return;

        ItemStack input = items.get(inputSlot);
        ItemStack fuel = items.get(fuelSlot);
        ItemStack smeltResult = (input != null && !input.getType().isAir()) ? getSmeltResult(input) : null;

        FurnaceState state = activeFurnaces.computeIfAbsent(id, k -> new FurnaceState(0, 0, 0));

        int requiredCookTicks = switch (tierLevel) {
            case 4 -> 10;
            case 3 -> 25;
            case 2 -> 50;
            case 1 -> 100;
            default -> 200;
        };

        boolean modified = false;

        if (smeltResult != null) {
            if (state.burnTicksLeft <= 0) {
                int fuelBurn = getFuelBurnTicks(fuel);
                if (fuelBurn > 0) {
                    if (tierLevel == 4) fuelBurn *= 2;
                    state.burnTicksLeft = fuelBurn;
                    state.maxBurnTicks = fuelBurn;

                    if (fuel.getType() == Material.LAVA_BUCKET) {
                        items.set(fuelSlot, new ItemStack(Material.BUCKET));
                    } else {
                        if (fuel.getAmount() > 1) {
                            fuel.setAmount(fuel.getAmount() - 1);
                            items.set(fuelSlot, fuel);
                        } else {
                            items.set(fuelSlot, null);
                        }
                    }
                    modified = true;

                }
            }

            if (state.burnTicksLeft > 0) {
                state.burnTicksLeft -= 5;
                state.cookTicks += 5;

                if (particles && player != null && (player.getTicksLived() % 10 == 0)) {
                    Location loc = player.getLocation().add(0, 1.2, 0);
                    player.getWorld().spawnParticle(Particle.SMOKE, loc, 2, 0.2, 0.2, 0.2, 0.01);
                    player.getWorld().spawnParticle(Particle.FLAME, loc, 1, 0.1, 0.1, 0.1, 0.01);
                }
                // Gentle ambient furnace crackle like a normal furnace only when the GUI is open
                if (sound && player != null && player.isOnline() && (state.cookTicks % 60 == 0)) {
                    player.playSound(player.getLocation(), Sound.BLOCK_FURNACE_FIRE_CRACKLE, 0.25f, 1.0f);
                }

                if (state.cookTicks >= requiredCookTicks) {
                    state.cookTicks = 0;
                    if (input.getAmount() > 1) {
                        input.setAmount(input.getAmount() - 1);
                        items.set(inputSlot, input);
                    } else {
                        items.set(inputSlot, null);
                    }

                    ItemStack resultToStore = smeltResult.clone();
                    int maxCap = getMaxStackCapacity(backpack);
                    boolean stored = autoStoreSmeltedItemList(items, resultToStore, maxCap, tier);
                    if (!stored && player != null) {
                        player.getWorld().dropItemNaturally(player.getLocation(), resultToStore);
                    }
                    modified = true;

                }
            }
        } else {
            if (state.burnTicksLeft > 0) {
                state.burnTicksLeft -= 5;
            }
            state.cookTicks = 0;
        }

        if (modified) {
            byte[] updatedBytes = serializeItems(items);
            meta.getPersistentDataContainer().set(contentsKey, PersistentDataType.BYTE_ARRAY, updatedBytes);
            backpack.setItemMeta(meta);
        }
    }

    private void processFurnace(UUID id, Inventory inv, Player player, int tierLevel, boolean particles, boolean sound) {
        BackpackTier tier = getTierFromInventory(inv);
        int inputSlot = tier.getFurnaceInputSlot();
        int fuelSlot = tier.getFurnaceFuelSlot();
        if (inputSlot < 0 || fuelSlot < 0 || inputSlot >= inv.getSize() || fuelSlot >= inv.getSize())
            return;

        ItemStack input = inv.getItem(inputSlot);
        ItemStack fuel = inv.getItem(fuelSlot);
        ItemStack smeltResult = (input != null && !input.getType().isAir()) ? getSmeltResult(input) : null;

        FurnaceState state = activeFurnaces.computeIfAbsent(id, k -> new FurnaceState(0, 0, 0));

        int requiredCookTicks = switch (tierLevel) {
            case 4 -> 10;
            case 3 -> 25;
            case 2 -> 50;
            case 1 -> 100;
            default -> 200;
        };

        if (smeltResult != null) {
            if (state.burnTicksLeft <= 0) {
                int fuelBurn = getFuelBurnTicks(fuel);
                if (fuelBurn > 0) {
                    if (tierLevel == 4) fuelBurn *= 2;
                    state.burnTicksLeft = fuelBurn;
                    state.maxBurnTicks = fuelBurn;

                    if (fuel.getType() == Material.LAVA_BUCKET) {
                        vn.haohan.backpack.hook.NmsStackHelper.setDirectSlot(inv, fuelSlot, new ItemStack(Material.BUCKET));
                    } else {
                        if (fuel.getAmount() > 1) {
                            fuel.setAmount(fuel.getAmount() - 1);
                            vn.haohan.backpack.hook.NmsStackHelper.setDirectSlot(inv, fuelSlot, fuel);
                        } else {
                            vn.haohan.backpack.hook.NmsStackHelper.setDirectSlot(inv, fuelSlot, null);
                        }
                    }
                    if (player != null && player.isOnline() && player.getOpenInventory().getTopInventory().equals(inv)) {
                        vn.haohan.backpack.listener.BackpackListener.sendDirectSlotUpdate(player, fuelSlot, inv.getItem(fuelSlot));
                    }

                }
            }

            if (state.burnTicksLeft > 0) {
                state.burnTicksLeft -= 5;
                state.cookTicks += 5;

                if (particles && player != null && (player.getTicksLived() % 10 == 0)) {
                    Location loc = player.getLocation().add(0, 1.2, 0);
                    player.getWorld().spawnParticle(Particle.SMOKE, loc, 2, 0.2, 0.2, 0.2, 0.01);
                    player.getWorld().spawnParticle(Particle.FLAME, loc, 1, 0.1, 0.1, 0.1, 0.01);
                }
                // Gentle ambient furnace crackle like a normal furnace only when the GUI is open
                if (sound && player != null && player.isOnline() && (state.cookTicks % 60 == 0)) {
                    player.playSound(player.getLocation(), Sound.BLOCK_FURNACE_FIRE_CRACKLE, 0.25f, 1.0f);
                }

                if (state.cookTicks >= requiredCookTicks) {
                    state.cookTicks = 0;
                    if (input.getAmount() > 1) {
                        input.setAmount(input.getAmount() - 1);
                        vn.haohan.backpack.hook.NmsStackHelper.setDirectSlot(inv, inputSlot, input);
                    } else {
                        vn.haohan.backpack.hook.NmsStackHelper.setDirectSlot(inv, inputSlot, null);
                    }
                    if (player != null && player.isOnline() && player.getOpenInventory().getTopInventory().equals(inv)) {
                        vn.haohan.backpack.listener.BackpackListener.sendDirectSlotUpdate(player, inputSlot, inv.getItem(inputSlot));
                    }

                    ItemStack resultToStore = smeltResult.clone();
                    int maxCap = getMaxStackCapacity(inv);
                    boolean stored = autoStoreSmeltedItem(inv, resultToStore, maxCap, tier);
                    if (!stored && player != null) {
                        player.getWorld().dropItemNaturally(player.getLocation(), resultToStore);
                    }

                }
            }
        } else {
            if (state.burnTicksLeft > 0) {
                state.burnTicksLeft -= 5;
            }
            state.cookTicks = 0;
        }

        // Send real-time tooltip progress update packet to viewers
        if (player != null && player.isOnline() && player.getOpenInventory().getTopInventory().equals(inv)) {
            ItemStack inputCur = inv.getItem(inputSlot);
            ItemStack fuelCur = inv.getItem(fuelSlot);
            ItemStack dispInput = createSmeltDisplayItem(inputCur, smeltResult, state.cookTicks, requiredCookTicks, state.burnTicksLeft > 0, tierLevel);
            ItemStack dispFuel = createFuelDisplayItem(fuelCur, state.burnTicksLeft, state.maxBurnTicks, tierLevel);
            vn.haohan.backpack.listener.BackpackListener.sendDirectSlotUpdate(player, inputSlot, dispInput != null ? dispInput : inputCur);
            vn.haohan.backpack.listener.BackpackListener.sendDirectSlotUpdate(player, fuelSlot, dispFuel != null ? dispFuel : fuelCur);
        }
    }

    public static String formatProgressBar(int current, int max, int totalBars, String fillChar, String emptyChar) {
        if (max <= 0) return "§7[----------] §e0%";
        float percent = Math.min(1.0f, Math.max(0.0f, (float) current / max));
        int filledBars = Math.round(percent * totalBars);
        StringBuilder sb = new StringBuilder("§6[§a");
        for (int i = 0; i < filledBars; i++) sb.append(fillChar);
        sb.append("§8");
        for (int i = filledBars; i < totalBars; i++) sb.append(emptyChar);
        sb.append("§6] §e").append(Math.round(percent * 100)).append("%");
        return sb.toString();
    }

    public ItemStack createSmeltDisplayItem(ItemStack rawItem, ItemStack resultItem, int cookTicks, int requiredTicks, boolean isBurning, int tierLevel) {
        if (rawItem == null || rawItem.getType().isAir()) return null;
        ItemStack display = rawItem.clone();
        ItemMeta meta = display.getItemMeta();
        if (meta == null) return display;

        List<net.kyori.adventure.text.Component> lore = meta.hasLore() ? new ArrayList<>(meta.lore()) : new ArrayList<>();
        lore.add(Component.text("§8§m------------------------"));
        if (resultItem != null) {
            String resultName = resultItem.getItemMeta() != null && resultItem.getItemMeta().hasDisplayName() ?
                    net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer.plainText().serialize(resultItem.getItemMeta().displayName()) :
                    resultItem.getType().name().replace('_', ' ').toLowerCase(java.util.Locale.ROOT);
            if (isBurning) {
                float cookSec = Math.max(0.0f, (float) cookTicks / 20.0f);
                float totalSec = (float) requiredTicks / 20.0f;
                lore.add(Component.text("§e⚡ Tiến độ nung: " + formatProgressBar(cookTicks, requiredTicks, 10, "■", "□")));
                lore.add(Component.text(String.format(java.util.Locale.ROOT, "§7⏱ Thời gian: §f%.1fs §7/ §e%.1fs", cookSec, totalSec)));
                lore.add(Component.text("§7🔥 Đang nung ra: §a" + resultName));
            } else {
                lore.add(Component.text("§c⌛ Đang chờ nhiên liệu đốt..."));
                lore.add(Component.text("§7Thành phẩm: §a" + resultName));
            }
        } else {
            lore.add(Component.text("§c❌ Vật phẩm này không thể nung"));
        }
        lore.add(Component.text("§8§m------------------------"));
        meta.lore(lore);
        display.setItemMeta(meta);
        return display;
    }

    public ItemStack createFuelDisplayItem(ItemStack rawFuel, int burnTicksLeft, int maxBurnTicks, int tierLevel) {
        if (rawFuel == null || rawFuel.getType().isAir()) return null;
        ItemStack display = rawFuel.clone();
        ItemMeta meta = display.getItemMeta();
        if (meta == null) return display;

        List<net.kyori.adventure.text.Component> lore = meta.hasLore() ? new ArrayList<>(meta.lore()) : new ArrayList<>();
        lore.add(Component.text("§8§m------------------------"));
        if (burnTicksLeft > 0) {
            float burnSec = (float) burnTicksLeft / 20.0f;
            float maxSec = (float) maxBurnTicks / 20.0f;
            lore.add(Component.text("§6🔥 Lửa đang cháy: " + formatProgressBar(burnTicksLeft, maxBurnTicks, 10, "■", "□")));
            lore.add(Component.text(String.format(java.util.Locale.ROOT, "§7⏱ Thời gian còn: §e%.1fs §7/ §f%.1fs", burnSec, maxSec)));
            if (tierLevel == 4) {
                lore.add(Component.text("§d✨ Hiệu suất Netherite: §a-50% nhiên liệu"));
            }
        } else {
            int singleBurn = getFuelBurnTicks(rawFuel);
            if (singleBurn > 0) {
                if (tierLevel == 4) singleBurn *= 2;
                lore.add(Component.text(String.format(java.util.Locale.ROOT, "§7⏱ Thời gian cháy 1 viên: §e%.1fs", (float) singleBurn / 20.0f)));
                if (tierLevel == 4) {
                    lore.add(Component.text("§d✨ Hiệu suất Netherite: §a-50% nhiên liệu"));
                }
            } else {
                lore.add(Component.text("§c❌ Không phải nhiên liệu đốt hợp lệ"));
            }
        }
        lore.add(Component.text("§8§m------------------------"));
        meta.lore(lore);
        display.setItemMeta(meta);
        return display;
    }

    public boolean autoStoreSmeltedItem(Inventory inv, ItemStack item, int maxCap, BackpackTier tier) {
        if (item == null || item.getType().isAir())
            return true;
        int[] dynamicSlots = tier.getStorageSlots();

        for (int slot : dynamicSlots) {
            if (tier.isModuleSlot(slot) || tier.isDiscSlot(slot) || tier.isFurnaceSlot(slot))
                continue;
            if (slot >= inv.getSize())
                continue;
            ItemStack existing = inv.getItem(slot);
            if (existing != null && isSimilarIgnoringCustomStack(existing, item) && existing.getAmount() < maxCap) {
                int space = maxCap - existing.getAmount();
                int move = Math.min(space, item.getAmount());
                ItemStack updated = existing.clone();
                updated.setAmount(existing.getAmount() + move);
                applyCustomStackSize(updated, maxCap);
                vn.haohan.backpack.hook.NmsStackHelper.setDirectSlot(inv, slot, updated);
                if (!inv.getViewers().isEmpty() && inv.getViewers().get(0) instanceof Player viewer) {
                    vn.haohan.backpack.listener.BackpackListener.sendDirectSlotUpdate(viewer, slot, updated);
                }
                item.setAmount(item.getAmount() - move);
                if (item.getAmount() <= 0)
                    return true;
            }
        }

        for (int slot : dynamicSlots) {
            if (tier.isModuleSlot(slot) || tier.isDiscSlot(slot) || tier.isFurnaceSlot(slot))
                continue;
            if (slot >= inv.getSize())
                continue;
            ItemStack existing = inv.getItem(slot);
            if (existing == null || existing.getType().isAir()) {
                ItemStack placed = item.clone();
                applyCustomStackSize(placed, maxCap);
                vn.haohan.backpack.hook.NmsStackHelper.setDirectSlot(inv, slot, placed);
                if (!inv.getViewers().isEmpty() && inv.getViewers().get(0) instanceof Player viewer) {
                    vn.haohan.backpack.listener.BackpackListener.sendDirectSlotUpdate(viewer, slot, placed);
                }
                return true;
            }
        }
        return false;
    }

    public boolean autoStoreSmeltedItemList(List<ItemStack> items, ItemStack item, int maxCap, BackpackTier tier) {
        if (item == null || item.getType().isAir())
            return true;
        int[] dynamicSlots = tier.getStorageSlots();

        for (int slot : dynamicSlots) {
            if (tier.isModuleSlot(slot) || tier.isDiscSlot(slot) || tier.isFurnaceSlot(slot))
                continue;
            if (slot >= items.size())
                continue;
            ItemStack existing = items.get(slot);
            if (existing != null && isSimilarIgnoringCustomStack(existing, item) && existing.getAmount() < maxCap) {
                int space = maxCap - existing.getAmount();
                int move = Math.min(space, item.getAmount());
                ItemStack updated = existing.clone();
                updated.setAmount(existing.getAmount() + move);
                applyCustomStackSize(updated, maxCap);
                items.set(slot, updated);
                item.setAmount(item.getAmount() - move);
                if (item.getAmount() <= 0)
                    return true;
            }
        }

        for (int slot : dynamicSlots) {
            if (tier.isModuleSlot(slot) || tier.isDiscSlot(slot) || tier.isFurnaceSlot(slot))
                continue;
            if (slot >= items.size())
                continue;
            ItemStack existing = items.get(slot);
            if (existing == null || existing.getType().isAir()) {
                ItemStack placed = item.clone();
                applyCustomStackSize(placed, maxCap);
                items.set(slot, placed);
                return true;
            }
        }
        return false;
    }

}
