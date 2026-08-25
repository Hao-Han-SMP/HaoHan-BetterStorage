package vn.haohan.backpack.listener;

import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryMoveItemEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerTeleportEvent;
import org.bukkit.event.player.PlayerChangedWorldEvent;
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
import org.bukkit.inventory.meta.LeatherArmorMeta;
import org.bukkit.event.inventory.InventoryAction;
import org.bukkit.event.inventory.ClickType;
import vn.haohan.backpack.gui.BackpackHolder;
import vn.haohan.backpack.service.BackpackService;

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
            if (backpackCount > 0) {
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

        if (backpack == null || !hasDye) return;

        ItemStack result = inv.getResult();
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
        if (event.getAction() == Action.RIGHT_CLICK_BLOCK && event.getClickedBlock() != null && service.isPlacedBackpack(event.getClickedBlock())) {
            event.setCancelled(true); service.openAt(event.getPlayer(), event.getClickedBlock().getLocation()); return;
        }
        if (!service.isBackpack(event.getItem())) return;

        Player player = event.getPlayer();

        // 1. Sneak + Right click BLOCK -> PLACE BACKPACK ON GROUND
        if (event.getAction() == Action.RIGHT_CLICK_BLOCK && player.isSneaking()) {
            Block clicked = event.getClickedBlock();
            Block target = clicked == null ? null : clicked.getRelative(event.getBlockFace());
            if (target == null || !target.getType().isAir() || service.hasPlacedBackpackAt(target)) return;
            event.setCancelled(true);
            service.spawnPlacedBackpack(target, event.getItem().clone(), event.getPlayer().getLocation().getYaw());
            if (player.getGameMode() != GameMode.CREATIVE) {
                event.getItem().setAmount(event.getItem().getAmount() - 1);
            }
            return;
        }

        // 2. All other right clicks (air with/without sneak, or block without sneak) -> OPEN BACKPACK GUI
        event.setCancelled(true);
        service.openItem(player, event.getItem());
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

        boolean isChestSlot = isChestplateSlot(event);

        // 1. Direct click on chestplate slot with a backpack on cursor (Equip)
        if (isChestSlot && service.isBackpack(cursor)) {
            event.setCancelled(true);
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
        if (event.isShiftClick() && service.isBackpack(currentItem) && player.getInventory().getChestplate() == null) {
            if (event.getClickedInventory() == player.getInventory() || event.getView().getTopInventory().getHolder() == null || event.getView().getTopInventory().getType() == InventoryType.CRAFTING || event.getView().getTopInventory().getType() == InventoryType.CREATIVE) {
                event.setCancelled(true);
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
            return;
        }

        int raw = event.getRawSlot();
        boolean isTop = raw >= 0 && raw < top.getSize();

        // Prevent clicking/interacting with non-storage decorative slots (e.g. module slot 47)
        if (isTop && !isStorage(raw)) {
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

        // Handle Shift-Clicking (Quick Move between Backpack & Inventory)
        if (event.isShiftClick()) {
            event.setCancelled(true);
            if (current == null || current.getType().isAir()) return;

            if (isTop) {
                // Shift-click FROM Backpack TO Player Inventory (filling from last slot 35 down to 0)
                if (!isStorage(raw)) return;
                Map<Integer, ItemStack> leftovers = addItemToPlayerInventoryReverse(player.getInventory(), current.clone());
                if (leftovers.isEmpty()) {
                    top.setItem(raw, null);
                } else {
                    top.setItem(raw, leftovers.values().iterator().next());
                }
                player.playSound(player.getLocation(), Sound.ENTITY_ITEM_PICKUP, 0.5f, 1.0f);
            } else {
                // Shift-click FROM Player Inventory TO Backpack
                if (service.isBackpack(current) && !service.allowBackpacksInsideBackpacks()) return;

                ItemStack toMove = current.clone();
                for (int slot : BackpackService.STORAGE_SLOTS) {
                    ItemStack existing = top.getItem(slot);
                    if (existing == null || existing.getType().isAir()) {
                        top.setItem(slot, toMove);
                        event.setCurrentItem(null);
                        player.playSound(player.getLocation(), Sound.ENTITY_ITEM_PICKUP, 0.5f, 1.0f);
                        return;
                    } else if (existing.isSimilar(toMove) && existing.getAmount() < existing.getMaxStackSize()) {
                        int space = existing.getMaxStackSize() - existing.getAmount();
                        int move = Math.min(space, toMove.getAmount());
                        existing.setAmount(existing.getAmount() + move);
                        top.setItem(slot, existing);
                        toMove.setAmount(toMove.getAmount() - move);
                        if (toMove.getAmount() <= 0) {
                            event.setCurrentItem(null);
                            player.playSound(player.getLocation(), Sound.ENTITY_ITEM_PICKUP, 0.5f, 1.0f);
                            return;
                        }
                    }
                }
                if (toMove.getAmount() != current.getAmount()) {
                    event.setCurrentItem(toMove);
                    player.playSound(player.getLocation(), Sound.ENTITY_ITEM_PICKUP, 0.5f, 1.0f);
                }
            }
            return;
        }

        if (service.allowBackpacksInsideBackpacks() && service.isBackpack(current) && !isTop) {
            event.setCancelled(true);
            service.openItem(player, current);
        }
    }

    @EventHandler public void onDrag(InventoryDragEvent event) {
        if (event.getWhoClicked() instanceof Player player) {
            plugin.getServer().getScheduler().runTask(plugin, () -> service.updateWornBackpack(player));
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

        if (!isTopBackpack) return;

        if (event.getRawSlots().stream().anyMatch(slot -> slot < event.getView().getTopInventory().getSize() && !isStorage(slot))) {
            event.setCancelled(true);
        }
        if (!event.isCancelled() && (service.isBlocked(event.getOldCursor()) || (service.isBackpack(event.getOldCursor()) && !service.allowBackpacksInsideBackpacks()))) {
            event.setCancelled(true);
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
        } else if (sourceBackpack && !destinationBackpack) {
            ItemStack moved = service.removeFromPlacedBackpack(event.getSource(), event.getItem());
            if (moved != null) {
                Map<Integer, ItemStack> leftovers = event.getDestination().addItem(moved);
                for (ItemStack leftover : leftovers.values()) service.addToPlacedBackpack(event.getSource(), leftover);
            }
        }
    }

    @EventHandler public void onClose(InventoryCloseEvent event) {
        if (event.getPlayer() instanceof Player player) {
            service.updateWornBackpack(player);
            if (event.getInventory().getHolder() instanceof BackpackHolder) service.close(player, event.getInventory());
        }
    }
    @EventHandler public void onQuit(PlayerQuitEvent event) {
        Inventory top = event.getPlayer().getOpenInventory().getTopInventory();
        if (top.getHolder() instanceof BackpackHolder) service.close(event.getPlayer(), top);
        service.removeWornBackpack(event.getPlayer());
    }
    @EventHandler public void onJoin(PlayerJoinEvent event) {
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
    private boolean isStorage(int slot) { for (int value : BackpackService.STORAGE_SLOTS) if (value == slot) return true; return false; }

    @EventHandler public void onPickup(EntityPickupItemEvent event) {
        if (!(event.getEntity() instanceof Player player) || !service.isBackpack(event.getItem().getItemStack())) return;
        if (!service.canReceiveBackpacks(player, event.getItem().getItemStack().getAmount())) event.setCancelled(true);
    }

    @EventHandler public void onDeath(PlayerDeathEvent event) {
        service.removeWornBackpack(event.getEntity());
        if (!service.keepBackpacksAfterDeath()) return;
        var backpacks = event.getDrops().stream().filter(service::isBackpack).toList();
        event.getDrops().removeIf(service::isBackpack);
        event.getItemsToKeep().addAll(backpacks);
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
            if (existing != null && !existing.getType().isAir() && existing.isSimilar(item) && existing.getAmount() < existing.getMaxStackSize()) {
                int space = existing.getMaxStackSize() - existing.getAmount();
                int move = Math.min(space, item.getAmount());
                existing.setAmount(existing.getAmount() + move);
                inv.setItem(i, existing);
                item.setAmount(item.getAmount() - move);
                if (item.getAmount() <= 0) break;
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
}
