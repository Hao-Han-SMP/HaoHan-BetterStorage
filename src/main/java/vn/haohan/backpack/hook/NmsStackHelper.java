package vn.haohan.backpack.hook;

import org.bukkit.Material;

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
}

