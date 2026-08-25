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
import org.bukkit.event.inventory.InventoryType;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.Plugin;
import org.bukkit.Sound;
import vn.haohan.backpack.gui.BackpackHolder;
import vn.haohan.backpack.service.BackpackService;

import java.util.Map;

public final class BackpackListener implements Listener {
    private final Plugin plugin;
    private final BackpackService service;

    public BackpackListener(Plugin plugin, BackpackService service) {
        this.plugin = plugin;
        this.service = service;
    }

    @EventHandler public void onUse(PlayerInteractEvent event) {
        if (event.getHand() != EquipmentSlot.HAND || (event.getAction() != Action.RIGHT_CLICK_AIR && event.getAction() != Action.RIGHT_CLICK_BLOCK)) return;
        if (event.getAction() == Action.RIGHT_CLICK_BLOCK && event.getClickedBlock() != null && service.isPlacedBackpack(event.getClickedBlock())) {
            event.setCancelled(true); service.openAt(event.getPlayer(), event.getClickedBlock().getLocation()); return;
        }
        if (!service.isBackpack(event.getItem())) return;

        Player player = event.getPlayer();
        // If chestplate is empty and player right-clicks air or right-clicks without sneaking, equip to chestplate
        if (player.getInventory().getChestplate() == null) {
            if (event.getAction() == Action.RIGHT_CLICK_AIR || !player.isSneaking()) {
                event.setCancelled(true);
                ItemStack equip = event.getItem().clone();
                equip.setAmount(1);
                player.getInventory().setChestplate(equip);
                if (event.getItem().getAmount() > 1) {
                    event.getItem().setAmount(event.getItem().getAmount() - 1);
                } else {
                    player.getInventory().setItem(event.getHand(), null);
                }
                player.playSound(player.getLocation(), Sound.ITEM_ARMOR_EQUIP_LEATHER, 1.0f, 1.0f);
                service.updateWornBackpack(player);
                return;
            }
        }

        if (event.getAction() == Action.RIGHT_CLICK_BLOCK) {
            if (!event.getPlayer().isSneaking()) {
                event.setCancelled(true);
                service.openItem(event.getPlayer(), event.getItem());
                return;
            }
            Block clicked = event.getClickedBlock();
            Block target = clicked == null ? null : clicked.getRelative(event.getBlockFace());
            if (target == null || !target.getType().isAir() || service.hasPlacedBackpackAt(target)) return;
            event.setCancelled(true);
            service.spawnPlacedBackpack(target, event.getItem().clone(), event.getPlayer().getLocation().getYaw());
            event.getItem().setAmount(event.getItem().getAmount() - 1);
            return;
        }
        event.setCancelled(true); service.openItem(event.getPlayer(), event.getItem());
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

        ItemStack cursor = player.getItemOnCursor();
        if ((cursor == null || cursor.getType().isAir()) && event.getCursor() != null && !event.getCursor().getType().isAir()) {
            cursor = event.getCursor();
        }
        ItemStack currentItem = event.getCurrentItem();

        boolean isChestSlot = (event.getRawSlot() == 6)
                           || (event.getSlot() == 38)
                           || (event.getSlot() == 6 && event.getSlotType() == InventoryType.SlotType.ARMOR)
                           || (event.getSlotType() == InventoryType.SlotType.ARMOR && (event.getRawSlot() == 6 || event.getSlot() == 38 || event.getSlot() == 6))
                           || (event.getClickedInventory() != null && event.getClickedInventory().getType() == InventoryType.PLAYER && event.getSlot() == 38);

        // 1. Direct click on chestplate slot with a backpack on cursor (Equip)
        if (isChestSlot && service.isBackpack(cursor)) {
            event.setCancelled(true);
            ItemStack toEquip = cursor.clone();
            toEquip.setAmount(1);
            ItemStack currentChest = player.getInventory().getChestplate();
            player.getInventory().setChestplate(toEquip);

            if (cursor.getAmount() > 1) {
                cursor.setAmount(cursor.getAmount() - 1);
                player.setItemOnCursor(cursor);
                event.getView().setCursor(cursor);
            } else {
                player.setItemOnCursor(currentChest);
                event.getView().setCursor(currentChest);
            }

            player.playSound(player.getLocation(), Sound.ITEM_ARMOR_EQUIP_LEATHER, 1.0f, 1.0f);
            plugin.getServer().getScheduler().runTask(plugin, () -> service.updateWornBackpack(player));
            return;
        }

        // 2. Direct click on chestplate slot to take off worn backpack (Unequip)
        if (isChestSlot && service.isBackpack(currentItem) && (cursor == null || cursor.getType().isAir())) {
            event.setCancelled(true);
            ItemStack chest = currentItem.clone();
            player.getInventory().setChestplate(null);
            player.setItemOnCursor(chest);
            event.getView().setCursor(chest);
            player.playSound(player.getLocation(), Sound.ITEM_ARMOR_EQUIP_LEATHER, 1.0f, 1.0f);
            plugin.getServer().getScheduler().runTask(plugin, () -> service.updateWornBackpack(player));
            return;
        }

        // 3. Shift-click a backpack in player inventory when chestplate is empty
        if (event.isShiftClick() && service.isBackpack(currentItem) && player.getInventory().getChestplate() == null) {
            if (event.getClickedInventory() == player.getInventory() || event.getView().getTopInventory().getHolder() == null || event.getView().getTopInventory().getType() == InventoryType.CRAFTING || event.getView().getTopInventory().getType() == InventoryType.CREATIVE) {
                event.setCancelled(true);
                ItemStack equip = currentItem.clone();
                equip.setAmount(1);
                player.getInventory().setChestplate(equip);
                if (currentItem.getAmount() > 1) {
                    currentItem.setAmount(currentItem.getAmount() - 1);
                } else {
                    event.setCurrentItem(null);
                }
                player.playSound(player.getLocation(), Sound.ITEM_ARMOR_EQUIP_LEATHER, 1.0f, 1.0f);
                plugin.getServer().getScheduler().runTask(plugin, () -> service.updateWornBackpack(player));
                return;
            }
        }

        plugin.getServer().getScheduler().runTask(plugin, () -> service.updateWornBackpack(player));

        if (!(event.getView().getTopInventory().getHolder() instanceof BackpackHolder)) {
            boolean externalContainer = event.getView().getTopInventory().getType() != InventoryType.CRAFTING;
            if (externalContainer && service.blockBackpackInContainers() && (service.isBackpack(event.getCurrentItem()) || service.isBackpack(event.getCursor()))) event.setCancelled(true);
            return;
        }

        int raw = event.getRawSlot();
        Inventory top = event.getView().getTopInventory();
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
        if (!(event.getView().getTopInventory().getHolder() instanceof BackpackHolder)) return;
        if (event.getRawSlots().stream().anyMatch(slot -> slot < event.getView().getTopInventory().getSize() && !isStorage(slot))) event.setCancelled(true);
        if (!event.isCancelled() && (service.isBlocked(event.getOldCursor()) || (service.isBackpack(event.getOldCursor()) && !service.allowBackpacksInsideBackpacks()))) event.setCancelled(true);
    }

    @EventHandler public void onHopperMove(InventoryMoveItemEvent event) {
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
        plugin.getServer().getScheduler().runTaskLater(plugin, () -> service.updateWornBackpack(event.getPlayer()), 5L);
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
