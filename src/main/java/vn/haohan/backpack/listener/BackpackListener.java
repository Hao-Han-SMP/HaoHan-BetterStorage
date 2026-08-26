package vn.haohan.backpack.listener;

import org.bukkit.entity.Player;
import org.bukkit.entity.ItemDisplay;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryMoveItemEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerSwapHandItemsEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerTeleportEvent;
import org.bukkit.event.player.PlayerChangedWorldEvent;
import org.bukkit.event.player.PlayerRespawnEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.entity.EntityPickupItemEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.block.Block;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.Inventory;
import org.bukkit.GameMode;
import org.bukkit.event.entity.EntityDismountEvent;
import org.bukkit.event.inventory.InventoryCreativeEvent;
import org.bukkit.event.inventory.InventoryType;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.Plugin;
import org.bukkit.Sound;
import org.bukkit.Color;
import org.bukkit.DyeColor;
import org.bukkit.Material;
import org.bukkit.event.inventory.PrepareItemCraftEvent;
import org.bukkit.inventory.CraftingInventory;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.LeatherArmorMeta;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.event.inventory.InventoryAction;
import org.bukkit.event.inventory.ClickType;
import net.kyori.adventure.text.Component;
import vn.haohan.backpack.gui.BackpackHolder;
import vn.haohan.backpack.service.BackpackService;

import java.lang.reflect.Field;
import java.lang.reflect.Method;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.bukkit.event.EventPriority;
import org.bukkit.event.inventory.CraftItemEvent;

public final class BackpackListener implements Listener {
    private final Plugin plugin;
    private final BackpackService service;

    public BackpackListener(Plugin plugin, BackpackService service) {
        this.plugin = plugin;
        this.service = service;
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onPrepareCraft(PrepareItemCraftEvent event) {
        CraftingInventory inv = event.getInventory();
        ItemStack[] matrix = inv.getMatrix();
        ItemStack backpack = null;
        int backpackCount = 0;
        List<DyeColor> dyes = new ArrayList<>();
        int nonAirCount = 0;

        for (ItemStack item : matrix) {
            if (item == null || item.getType().isAir()) continue;
            nonAirCount++;
            if (service.isBackpack(item)) {
                backpackCount++;
                backpack = item;
            } else {
                DyeColor dyeColor = getDyeColor(item.getType());
                if (dyeColor != null) {
                    dyes.add(dyeColor);
                }
            }
        }

        if (backpackCount != 1 || dyes.isEmpty() || nonAirCount != (1 + dyes.size())) {
            ItemStack res = inv.getResult();
            if (res != null && service.isBackpack(res)) {
                inv.setResult(null);
            }
            return;
        }

        Color newColor = blendDyeColors(dyes);
        Integer currentRgb = service.getBackpackColor(backpack);
        String newDyeName = BackpackService.getClosestDyeColorName(newColor.asRGB());

        if (currentRgb != null) {
            String currentDyeName = BackpackService.getClosestDyeColorName(currentRgb);
            if (currentDyeName.equalsIgnoreCase(newDyeName) || currentRgb.intValue() == newColor.asRGB()) {
                inv.setResult(null);
                return;
            }
        }

        ItemStack result = backpack.clone();
        result.setAmount(1);

        service.setBackpackColor(result, newColor.asRGB());
        inv.setResult(result);
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onCraftItem(CraftItemEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) return;
        CraftingInventory inv = event.getInventory();
        ItemStack[] matrix = inv.getMatrix();
        ItemStack backpack = null;
        boolean hasDye = false;

        for (ItemStack item : matrix) {
            if (item == null || item.getType().isAir()) continue;
            if (service.isBackpack(item)) {
                backpack = item;
            } else if (getDyeColor(item.getType()) != null) {
                hasDye = true;
            }
        }

        ItemStack result = inv.getResult();

        if (backpack == null || !hasDye) {
            if (result != null && service.isBackpack(result)) {
                event.setCancelled(true);
            }
            return;
        }

        if (result == null || result.getType().isAir() || !service.isBackpack(result)) {
            event.setCancelled(true);
            return;
        }

        Integer currentRgb = service.getBackpackColor(backpack);
        Integer resultRgb = service.getBackpackColor(result);
        if (currentRgb != null && resultRgb != null && currentRgb.equals(resultRgb)) {
            event.setCancelled(true);
            return;
        }

        // Snapshot matrix before craft
        ItemStack[] matrixSnapshot = new ItemStack[matrix.length];
        for (int i = 0; i < matrix.length; i++) {
            matrixSnapshot[i] = matrix[i] != null ? matrix[i].clone() : null;
        }

        // Post-craft sync task to ensure matrix items are decremented properly
        plugin.getServer().getScheduler().runTask(plugin, () -> {
            ItemStack[] currentMatrix = inv.getMatrix();
            boolean changed = false;

            for (int i = 0; i < matrixSnapshot.length; i++) {
                ItemStack before = matrixSnapshot[i];
                if (before == null || before.getType().isAir()) continue;

                int expectedAmount = before.getAmount() - 1;
                ItemStack cur = (i < currentMatrix.length) ? currentMatrix[i] : null;

                if (expectedAmount <= 0) {
                    if (cur != null && !cur.getType().isAir()) {
                        currentMatrix[i] = null;
                        changed = true;
                    }
                } else {
                    if (cur == null || cur.getType().isAir() || cur.getAmount() != expectedAmount) {
                        ItemStack fixed = before.clone();
                        fixed.setAmount(expectedAmount);
                        currentMatrix[i] = fixed;
                        changed = true;
                    }
                }
            }

            if (changed) {
                inv.setMatrix(currentMatrix);
                player.updateInventory();
            }
        });

        // Play equip sound on successful dye craft
        player.playSound(player.getLocation(), Sound.ITEM_ARMOR_EQUIP_LEATHER, 1.0f, 1.0f);
    }

    private DyeColor getDyeColor(Material material) {
        String name = material.name();
        if (name.endsWith("_DYE")) {
            String colorName = name.substring(0, name.length() - 4);
            try {
                return DyeColor.valueOf(colorName);
            } catch (IllegalArgumentException ignored) {
                return null;
            }
        }
        return null;
    }

    private Color blendDyeColors(List<DyeColor> dyes) {
        if (dyes.isEmpty()) return Color.fromRGB(176, 46, 38);
        if (dyes.size() == 1) return dyes.get(0).getColor();

        int rSum = 0, gSum = 0, bSum = 0;
        int maxColorSum = 0;
        int count = 0;

        for (DyeColor dye : dyes) {
            Color c = dye.getColor();
            rSum += c.getRed();
            gSum += c.getGreen();
            bSum += c.getBlue();
            maxColorSum += Math.max(c.getRed(), Math.max(c.getGreen(), c.getBlue()));
            count++;
        }

        int rAvg = rSum / count;
        int gAvg = gSum / count;
        int bAvg = bSum / count;

        float maxAvg = (float) maxColorSum / (float) count;
        float currentMax = (float) Math.max(rAvg, Math.max(gAvg, bAvg));

        if (currentMax > 0) {
            rAvg = (int) ((float) rAvg * maxAvg / currentMax);
            gAvg = (int) ((float) gAvg * maxAvg / currentMax);
            bAvg = (int) ((float) bAvg * maxAvg / currentMax);
        }

        return Color.fromRGB(
                Math.min(255, Math.max(0, rAvg)),
                Math.min(255, Math.max(0, gAvg)),
                Math.min(255, Math.max(0, bAvg))
        );
    }

    @EventHandler public void onDismount(EntityDismountEvent event) {
        if (service.isWornBackpack(event.getEntity())) {
            event.setCancelled(true);
        }
    }

    private boolean isChestplateSlot(InventoryClickEvent event) {
        if (event.getSlotType() != InventoryType.SlotType.ARMOR) {
            return false;
        }
        if (event.getClickedInventory() != null && event.getClickedInventory().getType() == InventoryType.PLAYER) {
            return event.getSlot() == 38;
        }
        if (event.getView().getType() == InventoryType.CRAFTING) {
            return event.getRawSlot() == 6;
        }
        return event.getSlot() == 38;
    }

    @EventHandler public void onCreativeClick(InventoryCreativeEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) return;
        if (isChestplateSlot(event)) {
            plugin.getServer().getScheduler().runTask(plugin, () -> {
                service.updateWornBackpack(player);
                player.updateInventory();
            });
        }
    }

    @EventHandler public void onUse(PlayerInteractEvent event) {
        if (event.getHand() != EquipmentSlot.HAND || (event.getAction() != Action.RIGHT_CLICK_AIR && event.getAction() != Action.RIGHT_CLICK_BLOCK)) return;
        Player player = event.getPlayer();

        if (event.getAction() == Action.RIGHT_CLICK_BLOCK && event.getClickedBlock() != null && service.isPlacedBackpack(event.getClickedBlock())) {
            event.setCancelled(true); service.openAt(event.getPlayer(), event.getClickedBlock().getLocation()); return;
        }

        ItemStack held = event.getItem();

        // 1. Shift + Right Click with EMPTY HAND -> Open Worn or Equipped Backpack
        if (held == null || held.getType().isAir()) {
            if (player.isSneaking()) {
                ItemStack backpack = service.getWornOrEquippedBackpack(player);
                if (backpack != null && service.isBackpack(backpack)) {
                    event.setCancelled(true);
                    service.openItem(player, backpack);
                }
            }
            return;
        }

        if (!service.isBackpack(held)) return;

        // 2. Right click WATER_CAULDRON -> BLEACH / CLEAR BACKPACK COLOR
        if (event.getAction() == Action.RIGHT_CLICK_BLOCK && event.getClickedBlock() != null && event.getClickedBlock().getType() == Material.WATER_CAULDRON) {
            if (service.getBackpackColor(held) != null) {
                event.setCancelled(true);
                if (service.consumeCauldronLevel(event.getClickedBlock())) {
                    service.clearBackpackColor(held);
                    player.updateInventory();
                }
                return;
            }
        }

        // 3. Sneak + Right Click AIR -> QUICK EQUIP BACKPACK TO VIRTUAL ACCESSORY SLOT
        if (player.isSneaking() && event.getAction() == Action.RIGHT_CLICK_AIR) {
            event.setCancelled(true);

            ItemStack currentEquipped = service.getEquippedBackpack(player);
            if (currentEquipped != null && service.isBackpack(currentEquipped)) {
                player.sendMessage(Component.text("§c❌ Bạn đã đang đeo một chiếc ba lô sau lưng rồi!"));
                player.sendMessage(Component.text("§7Hãy gõ lệnh §f/bp unequip §7để tháo ba lô hiện tại trước khi đeo cái mới."));
                player.playSound(player.getLocation(), Sound.ENTITY_VILLAGER_NO, 1.0f, 1.0f);
                return;
            }

            ItemStack toEquip = held.clone();
            toEquip.setAmount(1);

            // Equip to virtual accessory backpack slot (Chestplate armor stays untouched!)
            service.setEquippedBackpack(player, toEquip);
            if (player.getGameMode() != GameMode.CREATIVE) {
                if (held.getAmount() > 1) {
                    held.setAmount(held.getAmount() - 1);
                } else {
                    if (event.getHand() == EquipmentSlot.HAND) {
                        player.getInventory().setItemInMainHand(null);
                    } else {
                        player.getInventory().setItemInOffHand(null);
                    }
                }
            }
            player.sendMessage(Component.text("§a✔ Đã trang bị ba lô sau lưng! Bạn có thể vừa mặc giáp vừa đeo ba lô."));
            player.sendMessage(Component.text("§7(Nhấn §fShift + F §7hoặc gõ §f/bp §7để mở, gõ §f/bp unequip §7để tháo)"));

            player.playSound(player.getLocation(), Sound.ITEM_ARMOR_EQUIP_LEATHER, 1.0f, 1.0f);
            plugin.getServer().getScheduler().runTask(plugin, () -> {
                service.updateWornBackpack(player);
                player.updateInventory();
            });
            return;
        }

        // 4. Sneak + Right click BLOCK -> PLACE BACKPACK ON GROUND
        if (event.getAction() == Action.RIGHT_CLICK_BLOCK && player.isSneaking()) {
            Block clicked = event.getClickedBlock();
            Block target = clicked == null ? null : clicked.getRelative(event.getBlockFace());
            if (target == null || !target.getType().isAir() || service.hasPlacedBackpackAt(target)) return;
            event.setCancelled(true);
            service.spawnPlacedBackpack(target, held.clone(), event.getPlayer().getLocation().getYaw());
            if (player.getGameMode() != GameMode.CREATIVE) {
                held.setAmount(held.getAmount() - 1);
            }
            return;
        }

        // 5. All other right clicks -> OPEN BACKPACK GUI
        event.setCancelled(true);
        service.openItem(player, held);
    }

    @EventHandler
    public void onSwapHand(PlayerSwapHandItemsEvent event) {
        Player player = event.getPlayer();
        if (player.isSneaking()) {
            ItemStack backpack = service.getWornOrEquippedBackpack(player);
            if (backpack != null && service.isBackpack(backpack)) {
                event.setCancelled(true);
                service.openItem(player, backpack);
            }
        }
    }

    @EventHandler public void onPlace(BlockPlaceEvent event) {
        if (service.isBackpack(event.getItemInHand())) {
            if (!event.getPlayer().isSneaking()) {
                event.setCancelled(true);
                return;
            }
            var block = event.getBlockPlaced();
            if (service.hasPlacedBackpackAt(block)) {
                event.setCancelled(true);
                return;
            }
            event.setCancelled(true);
            var item = event.getItemInHand().clone();
            service.spawnPlacedBackpackNextTick(block, item, event.getPlayer().getLocation().getYaw());

            ItemStack hand = event.getPlayer().getInventory().getItem(event.getHand());
            if (hand != null && service.isBackpack(hand)) {
                hand.setAmount(hand.getAmount() - 1);
                event.getPlayer().getInventory().setItem(event.getHand(), hand.getAmount() <= 0 ? null : hand);
            }
        }
    }

    @EventHandler public void onVisualUse(PlayerInteractEntityEvent event) {
        if (!service.isBackpackVisual(event.getRightClicked())) return;
        event.setCancelled(true);
        if (event.getPlayer().isSneaking()) {
            pickupVisual(event.getPlayer(), event.getRightClicked());
            return;
        }
        service.openAt(event.getPlayer(), event.getRightClicked());
    }

    private void pickupVisual(Player player, org.bukkit.entity.Entity visual) {
        ItemStack item = service.backpackVisualItem(visual);
        if (item == null) return;
        if (!service.canReceiveBackpacks(player, 1)) {
            player.sendMessage("§cBạn không thể mang thêm ba lô.");
            return;
        }

        item = service.breakBackpackVisual(visual);
        for (ItemStack leftover : player.getInventory().addItem(item).values())
            player.getWorld().dropItemNaturally(player.getLocation(), leftover);
    }

    @EventHandler public void onVisualBreak(EntityDamageByEntityEvent event) {
        if (!service.isBackpackVisual(event.getEntity())) return;
        event.setCancelled(true);
        if (!(event.getDamager() instanceof Player player)) return;
        dropBrokenVisual(player, event.getEntity());
    }

    private void dropBrokenVisual(Player player, org.bukkit.entity.Entity visual) {
        var item = service.breakBackpackVisual(visual);
        visual.getWorld().dropItemNaturally(visual.getLocation(), item);
    }

    @EventHandler public void onBreak(BlockBreakEvent event) {
        if (!service.isPlacedBackpack(event.getBlock())) return;
        event.setDropItems(false);
        event.getBlock().getWorld().dropItemNaturally(event.getBlock().getLocation(), service.createPlacedBackpackItem(event.getBlock()));
        for (var item : service.removePlacedContents(event.getBlock()))
            event.getBlock().getWorld().dropItemNaturally(event.getBlock().getLocation(), item);
    }

    @EventHandler public void onClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) return;

        // In Creative Mode: let the creative client handle slot/cursor management.
        // If the player changes their chestplate slot in creative mode, simply update the worn visual.
        if (event instanceof InventoryCreativeEvent) {
            if (isChestplateSlot(event)) {
                plugin.getServer().getScheduler().runTask(plugin, () -> {
                    service.updateWornBackpack(player);
                    player.updateInventory();
                });
            }
            return;
        }

        ItemStack cursor = player.getItemOnCursor();
        if ((cursor == null || cursor.getType().isAir()) && event.getCursor() != null && !event.getCursor().getType().isAir()) {
            cursor = event.getCursor();
        }
        ItemStack currentItem = event.getCurrentItem();

        // 0. ABSOLUTE PROTECTION: Never allow moving or taking empty module socket placeholders
        if (service.isEmptyModuleSocket(cursor)) {
            player.setItemOnCursor(null);
            event.getView().setCursor(null);
            event.setCancelled(true);
            return;
        }
        if (service.isEmptyModuleSocket(currentItem)) {
            event.setCancelled(true);
            Inventory topInv = event.getView().getTopInventory();
            int rSlot = event.getRawSlot();
            if (topInv.getHolder() instanceof BackpackHolder && rSlot >= 0 && rSlot == topInv.getSize() - 1) {
                // If clicked on legitimate module slot, let module click handler below process it
            } else {
                // Otherwise purge the rogue placeholder immediately
                if (event.getClickedInventory() != null) {
                    event.setCurrentItem(null);
                }
                player.updateInventory();
                return;
            }
        }
        if (event.getHotbarButton() >= 0) {
            ItemStack hotbarItem = player.getInventory().getItem(event.getHotbarButton());
            if (service.isEmptyModuleSocket(hotbarItem)) {
                player.getInventory().setItem(event.getHotbarButton(), null);
                event.setCancelled(true);
                return;
            }
        }

        boolean isChestSlot = isChestplateSlot(event);

        // 1. Direct click on chestplate slot with a backpack on cursor (Equip)
        if (isChestSlot && service.isBackpack(cursor)) {
            event.setCancelled(true);
            if (service.hasEquippedBackpack(player)) {
                player.sendMessage(Component.text("§c❌ Bạn đã đang đeo một chiếc ba lô sau lưng rồi!"));
                player.sendMessage(Component.text("§7Hãy gõ lệnh §f/bp unequip §7để tháo ba lô hiện tại trước khi đeo cái mới."));
                player.playSound(player.getLocation(), Sound.ENTITY_VILLAGER_NO, 1.0f, 1.0f);
                return;
            }
            ItemStack toEquip = cursor.clone();
            toEquip.setAmount(1);
            ItemStack currentChest = player.getInventory().getChestplate();
            player.getInventory().setChestplate(toEquip);

            if (player.getGameMode() == GameMode.CREATIVE) {
                player.playSound(player.getLocation(), Sound.ITEM_ARMOR_EQUIP_LEATHER, 1.0f, 1.0f);
                plugin.getServer().getScheduler().runTask(plugin, () -> {
                    service.updateWornBackpack(player);
                    player.updateInventory();
                });
                return;
            }

            if (cursor.getAmount() > 1) {
                cursor.setAmount(cursor.getAmount() - 1);
                player.setItemOnCursor(cursor);
                event.getView().setCursor(cursor);
            } else {
                player.setItemOnCursor(currentChest);
                event.getView().setCursor(currentChest);
            }

            player.playSound(player.getLocation(), Sound.ITEM_ARMOR_EQUIP_LEATHER, 1.0f, 1.0f);
            plugin.getServer().getScheduler().runTask(plugin, () -> {
                service.updateWornBackpack(player);
                player.updateInventory();
            });
            return;
        }

        // 2. Direct click on chestplate slot to take off worn backpack (Unequip)
        if (isChestSlot && service.isBackpack(currentItem) && (cursor == null || cursor.getType().isAir())) {
            event.setCancelled(true);
            ItemStack chest = currentItem.clone();
            player.getInventory().setChestplate(null);
            if (player.getGameMode() != GameMode.CREATIVE) {
                player.setItemOnCursor(chest);
                event.getView().setCursor(chest);
            }
            player.playSound(player.getLocation(), Sound.ITEM_ARMOR_EQUIP_LEATHER, 1.0f, 1.0f);
            plugin.getServer().getScheduler().runTask(plugin, () -> {
                service.updateWornBackpack(player);
                player.updateInventory();
            });
            return;
        }

        // 3. Shift-click a backpack in player inventory when chestplate is empty
        if (event.isShiftClick() && service.isBackpack(currentItem) && (player.getInventory().getChestplate() == null || player.getInventory().getChestplate().getType().isAir())) {
            if (event.getClickedInventory() == player.getInventory() || event.getView().getTopInventory().getHolder() == null || event.getView().getTopInventory().getType() == InventoryType.CRAFTING || event.getView().getTopInventory().getType() == InventoryType.CREATIVE) {
                event.setCancelled(true);
                if (service.hasEquippedBackpack(player)) {
                    player.sendMessage(Component.text("§c❌ Bạn đã đang đeo một chiếc ba lô sau lưng rồi!"));
                    player.sendMessage(Component.text("§7Hãy gõ lệnh §f/bp unequip §7để tháo ba lô hiện tại trước khi đeo cái mới."));
                    player.playSound(player.getLocation(), Sound.ENTITY_VILLAGER_NO, 1.0f, 1.0f);
                    return;
                }
                ItemStack equip = currentItem.clone();
                equip.setAmount(1);
                player.getInventory().setChestplate(equip);
                if (player.getGameMode() != GameMode.CREATIVE) {
                    if (currentItem.getAmount() > 1) {
                        currentItem.setAmount(currentItem.getAmount() - 1);
                    } else {
                        event.setCurrentItem(null);
                    }
                }
                player.playSound(player.getLocation(), Sound.ITEM_ARMOR_EQUIP_LEATHER, 1.0f, 1.0f);
                plugin.getServer().getScheduler().runTask(plugin, () -> {
                    service.updateWornBackpack(player);
                    player.updateInventory();
                });
                return;
            }
        }

        // 4. Hotbar number key swap or offhand swap into chestplate slot with a backpack
        if (isChestSlot) {
            if (event.getClick() == ClickType.NUMBER_KEY && event.getHotbarButton() >= 0) {
                ItemStack hotbarItem = player.getInventory().getItem(event.getHotbarButton());
                if (service.isBackpack(hotbarItem)) {
                    if (service.hasEquippedBackpack(player)) {
                        event.setCancelled(true);
                        player.sendMessage(Component.text("§c❌ Bạn đã đang đeo một chiếc ba lô sau lưng rồi!"));
                        player.sendMessage(Component.text("§7Hãy gõ lệnh §f/bp unequip §7để tháo ba lô hiện tại trước khi đeo cái mới."));
                        player.playSound(player.getLocation(), Sound.ENTITY_VILLAGER_NO, 1.0f, 1.0f);
                        return;
                    }
                }
            } else if (event.getClick() == ClickType.SWAP_OFFHAND) {
                ItemStack offhand = player.getInventory().getItemInOffHand();
                if (service.isBackpack(offhand)) {
                    if (service.hasEquippedBackpack(player)) {
                        event.setCancelled(true);
                        player.sendMessage(Component.text("§c❌ Bạn đã đang đeo một chiếc ba lô sau lưng rồi!"));
                        player.sendMessage(Component.text("§7Hãy gõ lệnh §f/bp unequip §7để tháo ba lô hiện tại trước khi đeo cái mới."));
                        player.playSound(player.getLocation(), Sound.ENTITY_VILLAGER_NO, 1.0f, 1.0f);
                        return;
                    }
                }
            }
        }

        Inventory top = event.getView().getTopInventory();
        boolean isTopShulker = top.getType() == InventoryType.SHULKER_BOX;

        // Block placing/storing backpacks inside Shulker Boxes
        if (isTopShulker && service.blockBackpackInContainers()) {
            boolean isTopClick = event.getClickedInventory() == top;

            // Direct click or drop backpack into shulker box
            if (isTopClick && service.isBackpack(cursor)) {
                event.setCancelled(true);
                return;
            }

            // Shift-click backpack from inventory into shulker box
            if (event.isShiftClick() && service.isBackpack(currentItem)) {
                event.setCancelled(true);
                return;
            }

            // Hotbar number key swap (1-9) into shulker box slot
            if (isTopClick && event.getHotbarButton() >= 0) {
                ItemStack hotbarItem = player.getInventory().getItem(event.getHotbarButton());
                if (service.isBackpack(hotbarItem)) {
                    event.setCancelled(true);
                    return;
                }
            }

            // Offhand swap key (F) into shulker box slot
            if (isTopClick && event.getClick() == ClickType.SWAP_OFFHAND) {
                if (service.isBackpack(player.getInventory().getItemInOffHand())) {
                    event.setCancelled(true);
                    return;
                }
            }

            // Double click collect to cursor
            if (event.getAction() == InventoryAction.COLLECT_TO_CURSOR && service.isBackpack(cursor)) {
                event.setCancelled(true);
                return;
            }
        }

        if (!(top.getHolder() instanceof BackpackHolder)) {
            handleNonBackpackClick(event, player, cursor, currentItem);
            return;
        }

        int raw = event.getRawSlot();
        boolean isTop = raw >= 0 && raw < top.getSize();

        // Handle Module Socket
        if (isTop && service.isModuleSlot(top, raw)) {
            event.setCancelled(true);
            ItemStack currentModule = top.getItem(raw);
            boolean isCurModule = service.isModule(currentModule);
            vn.haohan.backpack.tier.BackpackTier tier = service.getTierFromInventory(top);

            if (service.isModule(cursor)) {
                service.stopJukeboxMusic(player);
                // Place or swap module
                if (!isCurModule) {
                    ItemStack toPlace = cursor.clone();
                    toPlace.setAmount(1);
                    service.cleanCustomStackSize(toPlace);
                    top.setItem(raw, toPlace);

                    if (cursor.getAmount() > 1) {
                        cursor.setAmount(cursor.getAmount() - 1);
                        player.setItemOnCursor(cursor);
                    } else {
                        player.setItemOnCursor(null);
                    }
                    service.applyCustomStackLimits(top);
                    player.playSound(player.getLocation(), Sound.ITEM_ARMOR_EQUIP_NETHERITE, 0.7f, 1.2f);
                    if (service.isMagnetModule(toPlace)) {
                        player.sendMessage(Component.text("§a✔ Đã kích hoạt Magnet Module! Phạm vi hút: §e" + service.getMagnetRadius() + " ô"));
                    } else if (service.isJukeboxModule(toPlace)) {
                        player.sendMessage(Component.text("§a✔ Đã kích hoạt Jukebox Module! Đặt 1 đĩa nhạc vào ô máy hát caro để phát nhạc."));
                    } else {
                        player.sendMessage(Component.text("§a✔ Đã kích hoạt Upgrade Module! Giới hạn stack cao nhất: §e" + service.getMaxStackCapacity(top)));
                    }
                } else {
                    // Swap module
                    ItemStack oldModule = currentModule.clone();
                    service.cleanCustomStackSize(oldModule);
                    ItemStack toPlace = cursor.clone();
                    toPlace.setAmount(1);
                    service.cleanCustomStackSize(toPlace);
                    top.setItem(raw, toPlace);
                    player.setItemOnCursor(oldModule);
                    service.applyCustomStackLimits(top);
                    player.playSound(player.getLocation(), Sound.ITEM_ARMOR_EQUIP_NETHERITE, 0.7f, 1.2f);
                    if (service.isMagnetModule(toPlace)) {
                        player.sendMessage(Component.text("§a✔ Đã kích hoạt Magnet Module! Phạm vi hút: §e" + service.getMagnetRadius() + " ô"));
                    } else if (service.isJukeboxModule(toPlace)) {
                        player.sendMessage(Component.text("§a✔ Đã kích hoạt Jukebox Module! Đặt 1 đĩa nhạc vào ô máy hát caro để phát nhạc."));
                    } else {
                        player.sendMessage(Component.text("§a✔ Đã đổi Upgrade Module! Giới hạn stack cao nhất: §e" + service.getMaxStackCapacity(top)));
                    }
                }
                boolean hasJuke = service.hasJukeboxModule(top);
                service.updateInventoryTitle(player, top, hasJuke);
            } else if ((cursor == null || cursor.getType().isAir()) && isCurModule) {
                // Take out module
                ItemStack taken = currentModule.clone();
                service.cleanCustomStackSize(taken);
                top.setItem(raw, null);
                player.setItemOnCursor(taken);

                int newCap = service.getMaxStackCapacity(top);
                int[] storageSlots = top.getHolder() instanceof BackpackHolder h ? h.storageSlots() : service.getTierFromInventory(top).getStorageSlots();
                for (int slot : storageSlots) {
                    ItemStack item = top.getItem(slot);
                    if (item != null && !item.getType().isAir()) {
                        if (item.getAmount() > newCap) {
                            int excess = item.getAmount() - newCap;
                            item.setAmount(newCap);
                            service.applyCustomStackSize(item, newCap);
                            top.setItem(slot, item);

                            ItemStack eject = item.clone();
                            eject.setAmount(excess);
                            service.cleanCustomStackSize(eject);
                            Map<Integer, ItemStack> left = player.getInventory().addItem(eject);
                            for (ItemStack leftover : left.values()) {
                                player.getWorld().dropItemNaturally(player.getLocation(), leftover);
                            }
                        } else {
                            service.applyCustomStackSize(item, newCap);
                        }
                    }
                }
                player.playSound(player.getLocation(), Sound.ITEM_ARMOR_EQUIP_GENERIC, 0.7f, 0.9f);
                if (service.isMagnetModule(taken)) {
                    player.sendMessage(Component.text("§c✖ Đã gỡ Magnet Module."));
                } else if (service.isJukeboxModule(taken)) {
                    service.stopJukeboxMusic(player);
                    player.sendMessage(Component.text("§c✖ Đã gỡ Jukebox Module."));
                } else if (service.isFurnaceModule(taken)) {
                    player.sendMessage(Component.text("§c✖ Đã gỡ Furnace Module."));
                } else {
                    player.sendMessage(Component.text("§c✖ Đã gỡ Upgrade Module. Giới hạn stack hiện tại: §e" + newCap));
                }
                boolean hasJuke = service.hasJukeboxModule(top);
                boolean hasFurnace = service.hasFurnaceModule(top);
                service.updateInventoryTitle(player, top, hasJuke, hasFurnace);
            } else if (cursor != null && !cursor.getType().isAir()) {
                player.sendMessage(Component.text("§c✖ Chỉ có thể đặt Module vào ô này!"));
            }
            player.updateInventory();
            return;
        }

        // Prevent clicking/interacting with other non-storage decorative slots
        if (isTop && !isStorage(top, raw)) {
            event.setCancelled(true);
            return;
        }

        ItemStack current = event.getCurrentItem();

        if (service.isBlocked(cursor) || service.isBlocked(current)) {
            event.setCancelled(true);
            return;
        }

        if (service.isBackpack(cursor) && !service.allowBackpacksInsideBackpacks()) {
            event.setCancelled(true);
            return;
        }

        if (event.getHotbarButton() >= 0) {
            ItemStack hotbarItem = player.getInventory().getItem(event.getHotbarButton());
            if (service.isBackpack(hotbarItem) && !service.allowBackpacksInsideBackpacks()) {
                event.setCancelled(true);
                return;
            }
        }

        int maxCap = service.getMaxStackCapacity(top);
        top.setMaxStackSize(maxCap);

        // Block Offhand swap key (F) on backpack slots to prevent overflow into offhand
        if (isTop && event.getClick() == ClickType.SWAP_OFFHAND) {
            event.setCancelled(true);
            return;
        }

        // Custom Deep-Stack Storage Slot Click Handlers
        if (isTop && isStorage(top, raw)) {
            vn.haohan.backpack.tier.BackpackTier clickTier = service.getTierFromInventory(top);
            if (clickTier.isDiscSlot(raw)) {
                service.stopJukeboxMusic(player);
            }
            ItemStack existing = top.getItem(raw);
            boolean cursorHasItem = cursor != null && !cursor.getType().isAir();
            boolean slotHasItem = existing != null && !existing.getType().isAir();

            // Number key hotbar swap (1-9)
            if (event.getClick() == ClickType.NUMBER_KEY) {
                event.setCancelled(true);
                int hotbarSlot = event.getHotbarButton();
                if (hotbarSlot < 0 || hotbarSlot > 8) return;
                ItemStack hotbarItem = player.getInventory().getItem(hotbarSlot);
                boolean hotbarHas = hotbarItem != null && !hotbarItem.getType().isAir();

                if (hotbarHas && service.isBackpack(hotbarItem) && !service.allowBackpacksInsideBackpacks()) return;

                if (!hotbarHas && slotHasItem) {
                    int vanillaMax = vn.haohan.backpack.hook.NmsStackHelper.getVanillaMaxStackSize(existing.getType());
                    if (vanillaMax <= 0) vanillaMax = 64;
                    int take = Math.min(existing.getAmount(), vanillaMax);
                    ItemStack toHotbar = existing.clone();
                    toHotbar.setAmount(take);
                    service.cleanCustomStackSize(toHotbar);
                    player.getInventory().setItem(hotbarSlot, toHotbar);

                    if (existing.getAmount() - take <= 0) {
                        vn.haohan.backpack.hook.NmsStackHelper.setDirectSlot(top, raw, null);
                    } else {
                        existing.setAmount(existing.getAmount() - take);
                        service.applyCustomStackSize(existing, maxCap);
                        vn.haohan.backpack.hook.NmsStackHelper.setDirectSlot(top, raw, existing);
                    }
                    player.playSound(player.getLocation(), Sound.ENTITY_ITEM_PICKUP, 0.5f, 1.0f);
                    syncBackpackSlot(player, top, raw);
                    return;
                } else if (hotbarHas && !slotHasItem) {
                    ItemStack toStorage = hotbarItem.clone();
                    service.applyCustomStackSize(toStorage, maxCap);
                    vn.haohan.backpack.hook.NmsStackHelper.setDirectSlot(top, raw, toStorage);
                    player.getInventory().setItem(hotbarSlot, null);
                    player.playSound(player.getLocation(), Sound.ENTITY_ITEM_PICKUP, 0.5f, 1.0f);
                    syncBackpackSlot(player, top, raw);
                    return;
                } else if (hotbarHas && slotHasItem) {
                    if (service.isSimilarIgnoringCustomStack(existing, hotbarItem)) {
                        int space = maxCap - existing.getAmount();
                        if (space > 0) {
                            int move = Math.min(space, hotbarItem.getAmount());
                            existing.setAmount(existing.getAmount() + move);
                            service.applyCustomStackSize(existing, maxCap);
                            vn.haohan.backpack.hook.NmsStackHelper.setDirectSlot(top, raw, existing);
                            if (hotbarItem.getAmount() - move <= 0) {
                                player.getInventory().setItem(hotbarSlot, null);
                            } else {
                                hotbarItem.setAmount(hotbarItem.getAmount() - move);
                                player.getInventory().setItem(hotbarSlot, hotbarItem);
                            }
                            player.playSound(player.getLocation(), Sound.ENTITY_ITEM_PICKUP, 0.5f, 1.0f);
                            syncBackpackSlot(player, top, raw);
                            return;
                        }
                    } else {
                        int existingVanillaMax = vn.haohan.backpack.hook.NmsStackHelper.getVanillaMaxStackSize(existing.getType());
                        if (existingVanillaMax <= 0) existingVanillaMax = 64;
                        if (existing.getAmount() <= existingVanillaMax) {
                            ItemStack newHotbar = existing.clone();
                            service.cleanCustomStackSize(newHotbar);
                            ItemStack newStorage = hotbarItem.clone();
                            service.applyCustomStackSize(newStorage, maxCap);
                            player.getInventory().setItem(hotbarSlot, newHotbar);
                            vn.haohan.backpack.hook.NmsStackHelper.setDirectSlot(top, raw, newStorage);
                            player.playSound(player.getLocation(), Sound.ENTITY_ITEM_PICKUP, 0.5f, 1.0f);
                            syncBackpackSlot(player, top, raw);
                            return;
                        }
                    }
                }
                return;
            }

            // Drop key (Q or Ctrl+Q)
            if (event.getClick() == ClickType.DROP || event.getClick() == ClickType.CONTROL_DROP) {
                event.setCancelled(true);
                if (slotHasItem) {
                    int vanillaMax = vn.haohan.backpack.hook.NmsStackHelper.getVanillaMaxStackSize(existing.getType());
                    if (vanillaMax <= 0) vanillaMax = 64;
                    int dropAmount = event.getClick() == ClickType.DROP ? 1 : Math.min(existing.getAmount(), vanillaMax);
                    ItemStack dropped = existing.clone();
                    dropped.setAmount(dropAmount);
                    service.cleanCustomStackSize(dropped);

                    if (existing.getAmount() - dropAmount <= 0) {
                        vn.haohan.backpack.hook.NmsStackHelper.setDirectSlot(top, raw, null);
                    } else {
                        existing.setAmount(existing.getAmount() - dropAmount);
                        service.applyCustomStackSize(existing, maxCap);
                        vn.haohan.backpack.hook.NmsStackHelper.setDirectSlot(top, raw, existing);
                    }
                    org.bukkit.entity.Item entity = player.getWorld().dropItemNaturally(player.getEyeLocation(), dropped);
                    entity.setVelocity(player.getLocation().getDirection().multiply(0.3));
                    syncBackpackSlot(player, top, raw);
                }
                return;
            }

            // Double Click / Collect to Cursor
            if (event.getAction() == InventoryAction.COLLECT_TO_CURSOR || event.getClick() == ClickType.DOUBLE_CLICK) {
                event.setCancelled(true);
                if (cursorHasItem) {
                    int vanillaMax = vn.haohan.backpack.hook.NmsStackHelper.getVanillaMaxStackSize(cursor.getType());
                    if (vanillaMax <= 0) vanillaMax = 64;
                    if (cursor.getAmount() < vanillaMax) {
                        int needed = vanillaMax - cursor.getAmount();
                        int[] dynamicSlots = top.getHolder() instanceof BackpackHolder h ? h.storageSlots() : service.getTierFromInventory(top).getStorageSlots();
                        for (int slot : dynamicSlots) {
                            ItemStack item = top.getItem(slot);
                            if (item != null && !item.getType().isAir() && service.isSimilarIgnoringCustomStack(item, cursor)) {
                                int take = Math.min(needed, item.getAmount());
                                cursor.setAmount(cursor.getAmount() + take);
                                needed -= take;
                                if (item.getAmount() - take <= 0) {
                                    vn.haohan.backpack.hook.NmsStackHelper.setDirectSlot(top, slot, null);
                                } else {
                                    item.setAmount(item.getAmount() - take);
                                    service.applyCustomStackSize(item, maxCap);
                                    vn.haohan.backpack.hook.NmsStackHelper.setDirectSlot(top, slot, item);
                                }
                                syncBackpackSlot(player, top, slot);
                                if (needed <= 0) break;
                            }
                        }
                        service.cleanCustomStackSize(cursor);
                        player.setItemOnCursor(cursor);
                        player.playSound(player.getLocation(), Sound.ENTITY_ITEM_PICKUP, 0.5f, 1.0f);
                        player.updateInventory();
                    }
                }
                return;
            }

            if (!cursorHasItem && slotHasItem) {
                // Clicking on item with empty cursor (Pick up up to 64 per click)
                int vanillaMax = vn.haohan.backpack.hook.NmsStackHelper.getVanillaMaxStackSize(existing.getType());
                if (vanillaMax <= 0) vanillaMax = 64;

                if (event.getClick() == ClickType.LEFT) {
                    event.setCancelled(true);
                    if (existing.getAmount() > vanillaMax) {
                        ItemStack pick = existing.clone();
                        pick.setAmount(vanillaMax);
                        service.cleanCustomStackSize(pick);
                        existing.setAmount(existing.getAmount() - vanillaMax);
                        service.applyCustomStackSize(existing, maxCap);
                        vn.haohan.backpack.hook.NmsStackHelper.setDirectSlot(top, raw, existing);
                        player.setItemOnCursor(pick);
                    } else {
                        ItemStack pick = existing.clone();
                        service.cleanCustomStackSize(pick);
                        vn.haohan.backpack.hook.NmsStackHelper.setDirectSlot(top, raw, null);
                        player.setItemOnCursor(pick);
                    }
                    player.playSound(player.getLocation(), Sound.ENTITY_ITEM_PICKUP, 0.5f, 1.0f);
                    syncBackpackSlot(player, top, raw);
                    return;
                } else if (event.getClick() == ClickType.RIGHT) {
                    event.setCancelled(true);
                    int half = Math.min(vanillaMax, (existing.getAmount() + 1) / 2);
                    ItemStack pick = existing.clone();
                    pick.setAmount(half);
                    service.cleanCustomStackSize(pick);
                    if (existing.getAmount() - half > 0) {
                        existing.setAmount(existing.getAmount() - half);
                        service.applyCustomStackSize(existing, maxCap);
                        vn.haohan.backpack.hook.NmsStackHelper.setDirectSlot(top, raw, existing);
                    } else {
                        vn.haohan.backpack.hook.NmsStackHelper.setDirectSlot(top, raw, null);
                    }
                    player.setItemOnCursor(pick);
                    player.playSound(player.getLocation(), Sound.ENTITY_ITEM_PICKUP, 0.5f, 1.0f);
                    syncBackpackSlot(player, top, raw);
                    return;
                }
            } else if (cursorHasItem && !slotHasItem) {
                // Placing cursor into empty storage slot
                if (event.getClick() == ClickType.LEFT) {
                    event.setCancelled(true);
                    ItemStack toPlace = cursor.clone();
                    service.applyCustomStackSize(toPlace, maxCap);
                    vn.haohan.backpack.hook.NmsStackHelper.setDirectSlot(top, raw, toPlace);
                    player.setItemOnCursor(null);
                    player.playSound(player.getLocation(), Sound.ENTITY_ITEM_PICKUP, 0.5f, 1.0f);
                    syncBackpackSlot(player, top, raw);
                    return;
                } else if (event.getClick() == ClickType.RIGHT) {
                    event.setCancelled(true);
                    ItemStack place = cursor.clone();
                    place.setAmount(1);
                    service.applyCustomStackSize(place, maxCap);
                    vn.haohan.backpack.hook.NmsStackHelper.setDirectSlot(top, raw, place);
                    if (cursor.getAmount() > 1) {
                        cursor.setAmount(cursor.getAmount() - 1);
                        player.setItemOnCursor(cursor);
                    } else {
                        player.setItemOnCursor(null);
                    }
                    player.playSound(player.getLocation(), Sound.ENTITY_ITEM_PICKUP, 0.5f, 1.0f);
                    syncBackpackSlot(player, top, raw);
                    return;
                }
            } else if (cursorHasItem && slotHasItem) {
                // Stacking or swapping
                if (service.isSimilarIgnoringCustomStack(existing, cursor)) {
                    if (event.getClick() == ClickType.LEFT) {
                        event.setCancelled(true);
                        int space = maxCap - existing.getAmount();
                        if (space > 0) {
                            int toMove = Math.min(space, cursor.getAmount());
                            existing.setAmount(existing.getAmount() + toMove);
                            service.applyCustomStackSize(existing, maxCap);
                            vn.haohan.backpack.hook.NmsStackHelper.setDirectSlot(top, raw, existing);
                            if (cursor.getAmount() > toMove) {
                                cursor.setAmount(cursor.getAmount() - toMove);
                                player.setItemOnCursor(cursor);
                            } else {
                                player.setItemOnCursor(null);
                            }
                            player.playSound(player.getLocation(), Sound.ENTITY_ITEM_PICKUP, 0.5f, 1.0f);
                            syncBackpackSlot(player, top, raw);
                            return;
                        }
                    } else if (event.getClick() == ClickType.RIGHT) {
                        event.setCancelled(true);
                        if (existing.getAmount() < maxCap && cursor.getAmount() >= 1) {
                            existing.setAmount(existing.getAmount() + 1);
                            service.applyCustomStackSize(existing, maxCap);
                            vn.haohan.backpack.hook.NmsStackHelper.setDirectSlot(top, raw, existing);
                            if (cursor.getAmount() > 1) {
                                cursor.setAmount(cursor.getAmount() - 1);
                                player.setItemOnCursor(cursor);
                            } else {
                                player.setItemOnCursor(null);
                            }
                            player.playSound(player.getLocation(), Sound.ENTITY_ITEM_PICKUP, 0.5f, 1.0f);
                            syncBackpackSlot(player, top, raw);
                            return;
                        }
                    }
                } else if (event.getClick() == ClickType.LEFT && cursor.getAmount() <= 64 && existing.getAmount() <= 64) {
                    // Swap different items
                    event.setCancelled(true);
                    ItemStack temp = existing.clone();
                    service.cleanCustomStackSize(temp);
                    ItemStack toPlace = cursor.clone();
                    service.applyCustomStackSize(toPlace, maxCap);
                    vn.haohan.backpack.hook.NmsStackHelper.setDirectSlot(top, raw, toPlace);
                    player.setItemOnCursor(temp);
                    player.playSound(player.getLocation(), Sound.ENTITY_ITEM_PICKUP, 0.5f, 1.0f);
                    syncBackpackSlot(player, top, raw);
                    return;
                }
            }
        }

        // Handle Shift-Clicking (Quick Move between Backpack & Inventory)
        if (event.isShiftClick()) {
            event.setCancelled(true);
            if (current == null || current.getType().isAir()) return;

            int[] dynamicSlots = top.getHolder() instanceof BackpackHolder h ? h.storageSlots() : service.getTierFromInventory(top).getStorageSlots();

            if (isTop) {
                // Shift-click FROM Backpack TO Player Inventory: take 1 stack (up to 64) per shift-click
                if (!isStorage(top, raw)) return;
                vn.haohan.backpack.tier.BackpackTier shiftTier = service.getTierFromInventory(top);
                if (shiftTier.isDiscSlot(raw)) {
                    service.stopJukeboxMusic(player);
                }
                int vanillaMax = vn.haohan.backpack.hook.NmsStackHelper.getVanillaMaxStackSize(current.getType());
                if (vanillaMax <= 0) vanillaMax = 64;

                int amountToTake = Math.min(current.getAmount(), vanillaMax);
                ItemStack chunk = current.clone();
                chunk.setAmount(amountToTake);
                service.cleanCustomStackSize(chunk);

                PlayerInventory playerInv = player.getInventory();
                Map<Integer, ItemStack> leftovers = addItemToPlayerInventoryReverse(playerInv, chunk);
                int transferred = amountToTake - leftovers.values().stream().mapToInt(ItemStack::getAmount).sum();

                if (transferred > 0) {
                    if (current.getAmount() - transferred <= 0) {
                        vn.haohan.backpack.hook.NmsStackHelper.setDirectSlot(top, raw, null);
                    } else {
                        current.setAmount(current.getAmount() - transferred);
                        service.applyCustomStackSize(current, maxCap);
                        vn.haohan.backpack.hook.NmsStackHelper.setDirectSlot(top, raw, current);
                    }
                    player.playSound(player.getLocation(), Sound.ENTITY_ITEM_PICKUP, 0.5f, 1.0f);
                    syncBackpackSlot(player, top, raw);
                }
                return;
            } else {
                // Shift-click FROM Player Inventory TO Backpack (packing up to maxCap)
                if (service.isBackpack(current) && !service.allowBackpacksInsideBackpacks()) return;

                // Quick Equip Module into available Module Socket
                if (service.isModule(current)) {
                    vn.haohan.backpack.tier.BackpackTier tier = service.getTierFromInventory(top);
                    int[] moduleSlots = tier.getModuleSlots();
                    int targetModuleSlot = -1;
                    for (int modSlot : moduleSlots) {
                        ItemStack existingMod = top.getItem(modSlot);
                        if (existingMod == null || existingMod.getType().isAir() || service.isEmptyModuleSocket(existingMod)) {
                            targetModuleSlot = modSlot;
                            break;
                        }
                    }

                    if (targetModuleSlot != -1) {
                        ItemStack oneMod = current.clone();
                        oneMod.setAmount(1);
                        service.cleanCustomStackSize(oneMod);
                        top.setItem(targetModuleSlot, oneMod);
                        syncBackpackSlot(player, top, targetModuleSlot);

                        if (current.getAmount() > 1) {
                            current.setAmount(current.getAmount() - 1);
                            event.setCurrentItem(current);
                        } else {
                            event.setCurrentItem(null);
                        }

                        player.playSound(player.getLocation(), Sound.ITEM_ARMOR_EQUIP_NETHERITE, 0.7f, 1.2f);
                        if (service.isMagnetModule(oneMod)) {
                            player.sendMessage(Component.text("§a✔ Đã gắn Magnet Module. Ba lô sẽ tự động hút vật phẩm!"));
                        } else if (service.isJukeboxModule(oneMod)) {
                            player.sendMessage(Component.text("§a✔ Đã gắn Jukebox Module. Đặt đĩa nhạc vào ô máy hát caro để phát nhạc!"));
                        } else if (service.isFurnaceModule(oneMod)) {
                            int fTier = service.getFurnaceModuleTier(oneMod);
                            player.sendMessage(Component.text("§a✔ Đã gắn Furnace Module (Tier " + fTier + "). Ô trên: Quặng - Ô dưới: Nhiên liệu!"));
                        } else {
                            int newCap = service.getMaxStackCapacity(top);
                            player.sendMessage(Component.text("§a✔ Đã gắn Upgrade Module. Giới hạn stack hiện tại: §e" + newCap));
                        }

                        boolean hasJuke = service.hasJukeboxModule(top);
                        boolean hasFurnace = service.hasFurnaceModule(top);
                        service.updateInventoryTitle(player, top, hasJuke, hasFurnace);
                        player.updateInventory();
                        return;
                    }
                }

                // Quick Insert Music Disc into Jukebox Caro Slot if Jukebox module is equipped
                if (service.hasJukeboxModule(top) && service.isMusicDisc(current)) {
                    vn.haohan.backpack.tier.BackpackTier tier = service.getTierFromInventory(top);
                    int discSlot = tier.getDiscSlot();
                    if (discSlot >= 0 && discSlot < top.getSize()) {
                        ItemStack existingDisc = top.getItem(discSlot);
                        if (existingDisc == null || existingDisc.getType().isAir()) {
                            ItemStack oneDisc = current.clone();
                            oneDisc.setAmount(1);
                            service.cleanCustomStackSize(oneDisc);
                            service.applyCustomStackSize(oneDisc, maxCap);
                            vn.haohan.backpack.hook.NmsStackHelper.setDirectSlot(top, discSlot, oneDisc);
                            syncBackpackSlot(player, top, discSlot);

                            if (current.getAmount() > 1) {
                                current.setAmount(current.getAmount() - 1);
                                event.setCurrentItem(current);
                            } else {
                                event.setCurrentItem(null);
                            }

                            player.playSound(player.getLocation(), Sound.ENTITY_ITEM_PICKUP, 0.5f, 1.0f);
                            player.updateInventory();
                            return;
                        }
                    }
                }

                // Quick Insert Fuel / Smeltable into Furnace Slots if Furnace module is equipped
                if (service.hasFurnaceModule(top)) {
                    vn.haohan.backpack.tier.BackpackTier tier = service.getTierFromInventory(top);
                    if (service.getFuelBurnTicks(current) > 0) {
                        int fuelSlot = tier.getFurnaceFuelSlot();
                        if (fuelSlot >= 0 && fuelSlot < top.getSize()) {
                            ItemStack existingFuel = top.getItem(fuelSlot);
                            if (existingFuel == null || existingFuel.getType().isAir()) {
                                ItemStack toPlace = current.clone();
                                service.applyCustomStackSize(toPlace, maxCap);
                                vn.haohan.backpack.hook.NmsStackHelper.setDirectSlot(top, fuelSlot, toPlace);
                                syncBackpackSlot(player, top, fuelSlot);
                                event.setCurrentItem(null);
                                player.playSound(player.getLocation(), Sound.ENTITY_ITEM_PICKUP, 0.5f, 1.0f);
                                player.updateInventory();
                                return;
                            }
                        }
                    } else if (service.getSmeltResult(current) != null) {
                        int inputSlot = tier.getFurnaceInputSlot();
                        if (inputSlot >= 0 && inputSlot < top.getSize()) {
                            ItemStack existingInput = top.getItem(inputSlot);
                            if (existingInput == null || existingInput.getType().isAir()) {
                                ItemStack toPlace = current.clone();
                                service.applyCustomStackSize(toPlace, maxCap);
                                vn.haohan.backpack.hook.NmsStackHelper.setDirectSlot(top, inputSlot, toPlace);
                                syncBackpackSlot(player, top, inputSlot);
                                event.setCurrentItem(null);
                                player.playSound(player.getLocation(), Sound.ENTITY_ITEM_PICKUP, 0.5f, 1.0f);
                                player.updateInventory();
                                return;
                            }
                        }
                    }
                }

                ItemStack toMove = current.clone();

                // Pass 1: Try stacking into existing similar stacks
                for (int slot : dynamicSlots) {
                    ItemStack existing = top.getItem(slot);
                    if (existing != null && !existing.getType().isAir() && service.isSimilarIgnoringCustomStack(existing, toMove) && existing.getAmount() < maxCap) {
                        int space = maxCap - existing.getAmount();
                        int move = Math.min(space, toMove.getAmount());
                        ItemStack updated = existing.clone();
                        updated.setAmount(existing.getAmount() + move);
                        service.applyCustomStackSize(updated, maxCap);
                        vn.haohan.backpack.hook.NmsStackHelper.setDirectSlot(top, slot, updated);
                        syncBackpackSlot(player, top, slot);
                        toMove.setAmount(toMove.getAmount() - move);
                        if (toMove.getAmount() <= 0) {
                            event.setCurrentItem(null);
                            player.playSound(player.getLocation(), Sound.ENTITY_ITEM_PICKUP, 0.5f, 1.0f);
                            player.updateInventory();
                            return;
                        }
                    }
                }

                // Pass 2: Place remaining into empty slots
                for (int slot : dynamicSlots) {
                    ItemStack existing = top.getItem(slot);
                    if (existing == null || existing.getType().isAir()) {
                        ItemStack placed = toMove.clone();
                        service.applyCustomStackSize(placed, maxCap);
                        vn.haohan.backpack.hook.NmsStackHelper.setDirectSlot(top, slot, placed);
                        syncBackpackSlot(player, top, slot);
                        event.setCurrentItem(null);
                        player.playSound(player.getLocation(), Sound.ENTITY_ITEM_PICKUP, 0.5f, 1.0f);
                        player.updateInventory();
                        return;
                    }
                }

                if (toMove.getAmount() != current.getAmount()) {
                    event.setCurrentItem(toMove);
                    player.playSound(player.getLocation(), Sound.ENTITY_ITEM_PICKUP, 0.5f, 1.0f);
                    player.updateInventory();
                }
            }
            return;
        }

        if (service.allowBackpacksInsideBackpacks() && service.isBackpack(current) && !isTop) {
            event.setCancelled(true);
            service.openItem(player, current);
            return;
        }

        if (!isTop) {
            handleNonBackpackClick(event, player, cursor, current);
        }
    }

    private void handleNonBackpackClick(InventoryClickEvent event, Player player, ItemStack cursor, ItemStack current) {
        if (event.isCancelled()) return;
        boolean cursorHasItem = cursor != null && !cursor.getType().isAir();
        boolean slotHasItem = current != null && !current.getType().isAir();

        if (cursorHasItem && slotHasItem && cursor.isSimilar(current)) {
            int vanillaMax = vn.haohan.backpack.hook.NmsStackHelper.getVanillaMaxStackSize(cursor.getType());
            if (vanillaMax <= 1) return;

            if (event.getClick() == ClickType.LEFT) {
                if (current.getAmount() >= vanillaMax) {
                    event.setCancelled(true);
                    return;
                }
                if (current.getAmount() + cursor.getAmount() > vanillaMax) {
                    event.setCancelled(true);
                    int move = vanillaMax - current.getAmount();
                    current.setAmount(vanillaMax);
                    event.setCurrentItem(current);
                    if (cursor.getAmount() > move) {
                        cursor.setAmount(cursor.getAmount() - move);
                        player.setItemOnCursor(cursor);
                    } else {
                        player.setItemOnCursor(null);
                    }
                    player.playSound(player.getLocation(), Sound.ENTITY_ITEM_PICKUP, 0.5f, 1.0f);
                    player.updateInventory();
                }
            } else if (event.getClick() == ClickType.RIGHT) {
                if (current.getAmount() >= vanillaMax) {
                    event.setCancelled(true);
                }
            }
        } else if (event.getAction() == InventoryAction.COLLECT_TO_CURSOR && cursorHasItem) {
            int vanillaMax = vn.haohan.backpack.hook.NmsStackHelper.getVanillaMaxStackSize(cursor.getType());
            if (cursor.getAmount() >= vanillaMax) {
                event.setCancelled(true);
            }
        }
    }

    @EventHandler public void onDrag(InventoryDragEvent event) {
        if (event.getWhoClicked() instanceof Player player) {
            plugin.getServer().getScheduler().runTask(plugin, () -> service.updateWornBackpack(player));
            if (service.isBackpack(event.getOldCursor()) && service.hasEquippedBackpack(player)) {
                if (event.getRawSlots().contains(6) || (event.getInventory().getType() == InventoryType.PLAYER && event.getInventorySlots().contains(38))) {
                    event.setCancelled(true);
                    player.sendMessage(Component.text("§c❌ Bạn đã đang đeo một chiếc ba lô sau lưng rồi!"));
                    player.sendMessage(Component.text("§7Hãy gõ lệnh §f/bp unequip §7để tháo ba lô hiện tại trước khi đeo cái mới."));
                    player.playSound(player.getLocation(), Sound.ENTITY_VILLAGER_NO, 1.0f, 1.0f);
                    return;
                }
            }
        }

        if (service.isEmptyModuleSocket(event.getOldCursor()) || event.getNewItems().values().stream().anyMatch(service::isEmptyModuleSocket)) {
            event.setCancelled(true);
            return;
        }

        Inventory top = event.getView().getTopInventory();
        boolean isTopBackpack = top.getHolder() instanceof BackpackHolder;
        boolean isTopShulker = top.getType() == InventoryType.SHULKER_BOX;

        // Block dragging backpack into Shulker Boxes
        if (isTopShulker && service.blockBackpackInContainers() && service.isBackpack(event.getOldCursor())) {
            if (event.getRawSlots().stream().anyMatch(slot -> slot < top.getSize())) {
                event.setCancelled(true);
                return;
            }
        }

        if (isTopBackpack) {
            if (event.getRawSlots().stream().anyMatch(slot -> slot < top.getSize() && !isStorage(top, slot))) {
                event.setCancelled(true);
            }
            if (!event.isCancelled() && (service.isBlocked(event.getOldCursor()) || (service.isBackpack(event.getOldCursor()) && !service.allowBackpacksInsideBackpacks()))) {
                event.setCancelled(true);
            }
            if (!event.isCancelled()) {
                plugin.getServer().getScheduler().runTask(plugin, () -> {
                    service.applyCustomStackLimits(top);
                    if (event.getWhoClicked() instanceof Player player) {
                        player.updateInventory();
                    }
                });
            }
            return;
        }

        // Non-backpack drag: ensure no slots exceed vanilla max
        ItemStack oldCursor = event.getOldCursor();
        if (oldCursor != null && !oldCursor.getType().isAir()) {
            int vanillaMax = vn.haohan.backpack.hook.NmsStackHelper.getVanillaMaxStackSize(oldCursor.getType());
            for (ItemStack newItem : event.getNewItems().values()) {
                if (newItem != null && newItem.getAmount() > vanillaMax) {
                    event.setCancelled(true);
                    return;
                }
            }
        }
    }

    @EventHandler public void onHopperMove(InventoryMoveItemEvent event) {
        // Block hopper moving backpack into a Shulker Box
        if (event.getDestination().getType() == InventoryType.SHULKER_BOX && service.blockBackpackInContainers() && service.isBackpack(event.getItem())) {
            event.setCancelled(true);
            return;
        }

        if (!service.hopperEnabled()) return;
        boolean destinationBackpack = service.isPlacedBackpackInventory(event.getDestination());
        boolean sourceBackpack = service.isPlacedBackpackInventory(event.getSource());
        if (!destinationBackpack && !sourceBackpack) return;
        event.setCancelled(true);
        if (destinationBackpack && !sourceBackpack) {
            ItemStack leftover = service.addToPlacedBackpack(event.getDestination(), event.getItem());
            if (leftover == null || leftover.getAmount() < event.getItem().getAmount()) {
                ItemStack moved = event.getItem().clone();
                if (leftover != null) moved.setAmount(event.getItem().getAmount() - leftover.getAmount());
                event.getSource().removeItem(moved);
            }
        } else {
            ItemStack removed = service.removeFromPlacedBackpack(event.getSource(), event.getItem());
            if (removed != null && removed.getAmount() > 0) {
                Map<Integer, ItemStack> leftover = event.getDestination().addItem(removed);
                if (!leftover.isEmpty()) {
                    service.addToPlacedBackpack(event.getSource(), leftover.values().iterator().next());
                }
            }
        }
    }

    @EventHandler public void onClose(InventoryCloseEvent event) {
        if (event.getPlayer() instanceof Player player) {
            service.updateWornBackpack(player);
            if (event.getInventory().getHolder() instanceof BackpackHolder) service.close(player, event.getInventory());
            sanitizePlayerInventory(player);
        }
    }
    @EventHandler public void onQuit(PlayerQuitEvent event) {
        Inventory top = event.getPlayer().getOpenInventory().getTopInventory();
        if (top.getHolder() instanceof BackpackHolder) service.close(event.getPlayer(), top);
        service.stopJukeboxMusic(event.getPlayer());
        service.removeWornBackpack(event.getPlayer());
        sanitizePlayerInventory(event.getPlayer());
    }

    public static void sanitizePlayerInventory(Player player) {
        if (player == null) return;
        PlayerInventory inv = player.getInventory();
        for (int i = 0; i < inv.getSize(); i++) {
            ItemStack item = inv.getItem(i);
            if (item != null && !item.getType().isAir()) {
                if (isPlaceholderItem(item)) {
                    inv.setItem(i, null);
                    continue;
                }
                int vanillaMax = vn.haohan.backpack.hook.NmsStackHelper.getVanillaMaxStackSize(item.getType());
                if (vanillaMax > 0 && item.getAmount() > vanillaMax) {
                    int excess = item.getAmount() - vanillaMax;
                    item.setAmount(vanillaMax);
                    inv.setItem(i, item);

                    ItemStack extra = item.clone();
                    extra.setAmount(excess);
                    Map<Integer, ItemStack> left = inv.addItem(extra);
                    for (ItemStack leftover : left.values()) {
                        player.getWorld().dropItemNaturally(player.getLocation(), leftover);
                    }
                }
            }
        }
        if (isPlaceholderItem(player.getItemOnCursor())) {
            player.setItemOnCursor(null);
        }
    }

    private static boolean isPlaceholderItem(ItemStack item) {
        if (item == null || item.getType().isAir()) return false;
        ItemMeta meta = item.getItemMeta();
        if (meta == null) return false;
        if (meta.hasItemModel() && meta.getItemModel().toString().contains("empty_module_slot")) return true;
        if (meta.hasDisplayName()) {
            String name = net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer.plainText()
                    .serialize(meta.displayName());
            if (name.contains("Ô Cắm Module") || name.contains("Empty Module Slot")) return true;
        }
        return false;
    }

    @EventHandler public void onJoin(PlayerJoinEvent event) {
        for (ItemStack item : event.getPlayer().getInventory().getContents()) {
            if (item != null && service.isBackpack(item)) {
                service.refreshBackpackItem(item);
            }
        }
        sanitizePlayerInventory(event.getPlayer());
        plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
            service.updateWornBackpack(event.getPlayer());
            service.discoverRecipes(event.getPlayer());
        }, 5L);
    }
    @EventHandler public void onTeleport(PlayerTeleportEvent event) {
        service.removeWornBackpack(event.getPlayer());
        plugin.getServer().getScheduler().runTaskLater(plugin, () -> service.updateWornBackpack(event.getPlayer()), 5L);
    }
    @EventHandler public void onChangedWorld(PlayerChangedWorldEvent event) {
        service.removeWornBackpack(event.getPlayer());
        plugin.getServer().getScheduler().runTaskLater(plugin, () -> service.updateWornBackpack(event.getPlayer()), 5L);
    }
    private boolean isStorage(Inventory top, int slot) {
        if (top != null && top.getHolder() instanceof BackpackHolder h) {
            for (int value : h.storageSlots()) if (value == slot) return true;
            return false;
        }
        if (top != null) {
            for (int value : service.getTierFromInventory(top).getStorageSlots()) if (value == slot) return true;
        }
        return false;
    }

    @EventHandler public void onPickup(EntityPickupItemEvent event) {
        if (!(event.getEntity() instanceof Player player)) return;
        ItemStack item = event.getItem().getItemStack();
        if (isPlaceholderItem(item)) {
            event.setCancelled(true);
            event.getItem().remove();
            return;
        }
        if (service.isBackpack(item)) {
            if (!service.canReceiveBackpacks(player, item.getAmount())) {
                event.setCancelled(true);
            } else {
                service.refreshBackpackItem(item);
            }
            return;
        }

        int vanillaMax = vn.haohan.backpack.hook.NmsStackHelper.getVanillaMaxStackSize(item.getType());
        if (vanillaMax > 1 && item.getAmount() > 0) {
            ItemStack toAdd = item.clone();
            for (int i = 0; i < 36; i++) {
                ItemStack slotItem = player.getInventory().getItem(i);
                if (slotItem != null && slotItem.isSimilar(toAdd) && slotItem.getAmount() < vanillaMax) {
                    int space = vanillaMax - slotItem.getAmount();
                    int move = Math.min(space, toAdd.getAmount());
                    slotItem.setAmount(slotItem.getAmount() + move);
                    player.getInventory().setItem(i, slotItem);
                    toAdd.setAmount(toAdd.getAmount() - move);
                    if (toAdd.getAmount() <= 0) break;
                }
            }
            if (toAdd.getAmount() > 0) {
                for (int i = 0; i < 36; i++) {
                    ItemStack slotItem = player.getInventory().getItem(i);
                    if (slotItem == null || slotItem.getType().isAir()) {
                        int move = Math.min(vanillaMax, toAdd.getAmount());
                        ItemStack placed = toAdd.clone();
                        placed.setAmount(move);
                        player.getInventory().setItem(i, placed);
                        toAdd.setAmount(toAdd.getAmount() - move);
                        if (toAdd.getAmount() <= 0) break;
                    }
                }
            }
            event.setCancelled(true);
            int taken = item.getAmount() - toAdd.getAmount();
            if (taken > 0) {
                player.playSound(player.getLocation(), Sound.ENTITY_ITEM_PICKUP, 0.2f, 1.5f);
                if (toAdd.getAmount() <= 0) {
                    event.getItem().remove();
                } else {
                    item.setAmount(toAdd.getAmount());
                    event.getItem().setItemStack(item);
                }
            }
        }
    }

    @EventHandler public void onDeath(PlayerDeathEvent event) {
        service.removeWornBackpack(event.getEntity());
        Player player = event.getEntity();
        ItemStack equipped = service.getEquippedBackpack(player);
        if (equipped != null && service.isBackpack(equipped)) {
            if (!service.keepBackpacksAfterDeath()) {
                event.getDrops().add(equipped);
                service.setEquippedBackpack(player, null);
            }
        }
        if (!service.keepBackpacksAfterDeath()) return;
        var backpacks = event.getDrops().stream().filter(service::isBackpack).toList();
        event.getDrops().removeIf(service::isBackpack);
        event.getItemsToKeep().addAll(backpacks);
    }

    @EventHandler public void onRespawn(PlayerRespawnEvent event) {
        plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
            service.updateWornBackpack(event.getPlayer());
        }, 5L);
    }

    private static Map<Integer, ItemStack> addItemToPlayerInventoryReverse(org.bukkit.inventory.PlayerInventory inv, ItemStack item) {
        if (item == null || item.getType().isAir()) return Map.of();
        ItemStack toAdd = item.clone();

        // 1. Stack into existing matching stacks in Hotbar (slots 8 down to 0)
        toAdd = stackIntoSlotsReverse(inv, toAdd, 8, 0);
        if (toAdd.getAmount() <= 0) return Map.of();

        // 2. Stack into existing matching stacks in Main Inventory (slots 35 down to 9)
        toAdd = stackIntoSlotsReverse(inv, toAdd, 35, 9);
        if (toAdd.getAmount() <= 0) return Map.of();

        // 3. Put into empty slots in Hotbar (slots 8 down to 0)
        toAdd = putIntoEmptySlotsReverse(inv, toAdd, 8, 0);
        if (toAdd.getAmount() <= 0) return Map.of();

        // 4. Put into empty slots in Main Inventory (slots 35 down to 9)
        toAdd = putIntoEmptySlotsReverse(inv, toAdd, 35, 9);
        if (toAdd.getAmount() <= 0) return Map.of();

        return Map.of(0, toAdd);
    }

    private static ItemStack stackIntoSlotsReverse(org.bukkit.inventory.PlayerInventory inv, ItemStack item, int startSlot, int endSlotMin) {
        for (int i = startSlot; i >= endSlotMin; i--) {
            ItemStack existing = inv.getItem(i);
            if (existing != null && !existing.getType().isAir() && existing.isSimilar(item)) {
                int vanillaMax = vn.haohan.backpack.hook.NmsStackHelper.getVanillaMaxStackSize(existing.getType());
                if (vanillaMax <= 0) vanillaMax = 64;
                if (existing.getAmount() < vanillaMax) {
                    int space = vanillaMax - existing.getAmount();
                    int move = Math.min(space, item.getAmount());
                    existing.setAmount(existing.getAmount() + move);
                    inv.setItem(i, existing);
                    item.setAmount(item.getAmount() - move);
                    if (item.getAmount() <= 0) break;
                }
            }
        }
        return item;
    }

    private static ItemStack putIntoEmptySlotsReverse(org.bukkit.inventory.PlayerInventory inv, ItemStack item, int startSlot, int endSlotMin) {
        for (int i = startSlot; i >= endSlotMin; i--) {
            ItemStack existing = inv.getItem(i);
            if (existing == null || existing.getType().isAir()) {
                inv.setItem(i, item.clone());
                item.setAmount(0);
                break;
            }
        }
        return item;
    }

    public static void sendDirectSlotUpdate(Player player, int rawSlot, ItemStack item) {
        if (player == null || !player.isOnline() || rawSlot < 0) return;
        try {
            Class<?> craftPlayerClass = Class.forName("org.bukkit.craftbukkit.entity.CraftPlayer");
            Class<?> craftItemStackClass = Class.forName("org.bukkit.craftbukkit.inventory.CraftItemStack");
            Object craftPlayer = craftPlayerClass.cast(player);
            Method getHandle = craftPlayerClass.getMethod("getHandle");
            Object serverPlayer = getHandle.invoke(craftPlayer);

            Field containerMenuField = serverPlayer.getClass().getField("containerMenu");
            Object containerMenu = containerMenuField.get(serverPlayer);

            Field containerIdField = containerMenu.getClass().getField("containerId");
            int containerId = (int) containerIdField.get(containerMenu);

            Method getStateId = containerMenu.getClass().getMethod("getStateId");
            int stateId = (int) getStateId.invoke(containerMenu);

            Method asNMSCopy = craftItemStackClass.getMethod("asNMSCopy", ItemStack.class);
            Object nmsItem = asNMSCopy.invoke(null, item == null ? new ItemStack(Material.AIR) : item);

            Class<?> setSlotPacketClass = Class.forName("net.minecraft.network.protocol.game.ClientboundContainerSetSlotPacket");
            Object packet = setSlotPacketClass.getConstructor(int.class, int.class, int.class, Class.forName("net.minecraft.world.item.ItemStack"))
                    .newInstance(containerId, stateId, rawSlot, nmsItem);

            Field connectionField = serverPlayer.getClass().getField("connection");
            Object connection = connectionField.get(serverPlayer);
            Method sendMethod = connection.getClass().getMethod("send", Class.forName("net.minecraft.network.protocol.Packet"));
            sendMethod.invoke(connection, packet);
        } catch (Throwable ignored) {
            player.updateInventory();
        }
    }

    private void syncBackpackSlot(Player player, Inventory top, int raw) {
        if (raw >= 0 && top != null && raw < top.getSize()) {
            ItemStack item = top.getItem(raw);
            sendDirectSlotUpdate(player, raw, item);
        }
        player.updateInventory();
        plugin.getServer().getScheduler().runTask(plugin, () -> {
            if (player.isOnline() && player.getOpenInventory().getTopInventory().equals(top)) {
                if (raw >= 0 && top != null && raw < top.getSize()) {
                    ItemStack cur = top.getItem(raw);
                    sendDirectSlotUpdate(player, raw, cur);
                }
                player.updateInventory();
            }
        });
    }
}
