package vn.haohan.backpack.hook;

import org.bukkit.Material;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public final class NmsStackHelper {
    private static boolean initialized = false;
    private static final Map<Material, Integer> VANILLA_MAX_STACKS = new ConcurrentHashMap<>();

    private NmsStackHelper() {}

    public static int getVanillaMaxStackSize(Material material) {
        if (material == null || material.isAir()) return 0;
        return VANILLA_MAX_STACKS.getOrDefault(material, material.getMaxStackSize() > 0 ? material.getMaxStackSize() : 64);
    }

    public static void initialize() {
        if (initialized) return;
        initialized = true;

        for (Material mat : Material.values()) {
            if (mat.isItem() && !mat.isAir()) {
                int max = mat.getMaxStackSize();
                VANILLA_MAX_STACKS.put(mat, max > 0 ? max : 64);
            }
        }
    }

    public static void setDirectSlot(Inventory inventory, int slot, ItemStack item) {
        if (inventory == null || slot < 0 || slot >= inventory.getSize()) return;
        try {
            Class<?> craftInventoryClass = Class.forName("org.bukkit.craftbukkit.inventory.CraftInventory");
            Class<?> craftItemStackClass = Class.forName("org.bukkit.craftbukkit.inventory.CraftItemStack");
            Object craftInv = craftInventoryClass.cast(inventory);
            Method getInventoryMethod = craftInventoryClass.getMethod("getInventory");
            Object container = getInventoryMethod.invoke(craftInv);

            Method asNMSCopy = craftItemStackClass.getMethod("asNMSCopy", ItemStack.class);
            Object nmsItem = asNMSCopy.invoke(null, item == null ? new ItemStack(Material.AIR) : item);

            Field itemsField = null;
            Class<?> clazz = container.getClass();
            while (clazz != null && clazz != Object.class) {
                try {
                    itemsField = clazz.getDeclaredField("items");
                    itemsField.setAccessible(true);
                    break;
                } catch (NoSuchFieldException e) {
                    clazz = clazz.getSuperclass();
                }
            }

            if (itemsField != null) {
                List<?> itemsList = (List<?>) itemsField.get(container);
                Method setMethod = itemsList.getClass().getMethod("set", int.class, Object.class);
                setMethod.invoke(itemsList, slot, nmsItem);

                Method setChanged = container.getClass().getMethod("setChanged");
                setChanged.invoke(container);
            } else {
                inventory.setItem(slot, item);
            }
        } catch (Throwable t) {
            inventory.setItem(slot, item);
        }
    }
}

