package vn.haohan.backpack.gui;

import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.block.TileState;
import org.bukkit.entity.ItemDisplay;

public final class BackpackHolder implements InventoryHolder {
    private final String storageId;
    private final int[] storageSlots;
    private final ItemStack sourceItem;
    private final TileState sourceBlock;
    private final ItemDisplay sourceDisplay;
    private Inventory inventory;

    public BackpackHolder(String storageId, int[] storageSlots) { this(storageId, storageSlots, null, null, null); }
    public BackpackHolder(String storageId, int[] storageSlots, ItemStack sourceItem, TileState sourceBlock) {
        this(storageId, storageSlots, sourceItem, sourceBlock, null);
    }
    public BackpackHolder(String storageId, int[] storageSlots, ItemStack sourceItem, TileState sourceBlock, ItemDisplay sourceDisplay) {
        this.storageId = storageId; this.storageSlots = storageSlots; this.sourceItem = sourceItem; this.sourceBlock = sourceBlock; this.sourceDisplay = sourceDisplay;
    }
    public String storageId() { return storageId; }
    public int[] storageSlots() { return storageSlots; }
    public ItemStack sourceItem() { return sourceItem; }
    public TileState sourceBlock() { return sourceBlock; }
    public ItemDisplay sourceDisplay() { return sourceDisplay; }
    public void inventory(Inventory inventory) { this.inventory = inventory; }
    @Override public Inventory getInventory() { return inventory; }
}
