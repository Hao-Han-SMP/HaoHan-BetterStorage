package vn.haohan.backpack.tier;

import org.bukkit.Material;
import java.util.Arrays;
import java.util.stream.IntStream;

public enum BackpackTier {
    LEATHER("leather", "§6Ba Lô Da", 1, 9, new int[]{}, Material.LEATHER, null),
    IRON("iron", "§fBa Lô Sắt", 2, 18, new int[]{ 16, 17 }, Material.IRON_INGOT, "haohan:backpack_leather"),
    GOLD("gold", "§eBa Lô Vàng", 3, 27, new int[]{ 24, 25, 26 }, Material.GOLD_INGOT, "haohan:backpack_iron"),
    DIAMOND("diamond", "§bBa Lô Kim Cương", 4, 36, new int[]{ 29, 30, 31, 32, 33 }, Material.DIAMOND, "haohan:backpack_gold"),
    NETHERITE("netherite", "§5Ba Lô Netherite", 6, 54, new int[]{ 47, 48, 49, 50, 51 }, Material.NETHERITE_INGOT, "haohan:backpack_diamond");

    private final String id;
    private final String displayName;
    private final int rows;
    private final int totalSlots;
    private final int[] moduleSlots;
    private final int[] storageSlots;
    private final Material upgradeMaterial;
    private final String prevTierId;

    BackpackTier(String id, String displayName, int rows, int totalSlots, int[] moduleSlots, Material upgradeMaterial, String prevTierId) {
        this.id = id;
        this.displayName = displayName;
        this.rows = rows;
        this.totalSlots = totalSlots;
        this.moduleSlots = moduleSlots;
        this.storageSlots = IntStream.range(0, totalSlots)
                .filter(slot -> Arrays.stream(moduleSlots).noneMatch(m -> m == slot))
                .toArray();
        this.upgradeMaterial = upgradeMaterial;
        this.prevTierId = prevTierId;
    }

    public String getId() { return id; }
    public String getDisplayName() { return displayName; }
    public int getRows() { return rows; }
    public int getTotalSlots() { return totalSlots; }
    public int getModuleSlotsCount() { return moduleSlots.length; }
    public int[] getModuleSlots() { return moduleSlots; }
    public int[] getStorageSlots() { return storageSlots; }
    public Material getUpgradeMaterial() { return upgradeMaterial; }
    public String getPrevTierId() { return prevTierId; }

    public int getStorageSlotsCount() {
        return storageSlots.length;
    }

    public boolean isModuleSlot(int slot) {
        for (int m : moduleSlots) if (m == slot) return true;
        return false;
    }

    public int getDiscSlot() {
        return switch (this) {
            case IRON -> 15;
            case GOLD -> 23;
            case DIAMOND -> 28;
            case NETHERITE -> 46;
            default -> -1;
        };
    }

    public boolean isDiscSlot(int slot) {
        return slot == getDiscSlot();
    }

    public int getFurnaceInputSlot() {
        return switch (this) {
            case IRON -> 0;
            case GOLD -> 9;
            case DIAMOND -> 18;
            case NETHERITE -> 36;
            default -> -1;
        };
    }

    public int getFurnaceFuelSlot() {
        return switch (this) {
            case IRON -> 9;
            case GOLD -> 18;
            case DIAMOND -> 27;
            case NETHERITE -> 45;
            default -> -1;
        };
    }

    public boolean isFurnaceInputSlot(int slot) {
        return slot == getFurnaceInputSlot();
    }

    public boolean isFurnaceFuelSlot(int slot) {
        return slot == getFurnaceFuelSlot();
    }

    public boolean isFurnaceSlot(int slot) {
        return isFurnaceInputSlot(slot) || isFurnaceFuelSlot(slot);
    }

    public static BackpackTier fromId(String id) {
        if (id == null) return LEATHER;
        for (BackpackTier tier : values()) {
            if (tier.id.equalsIgnoreCase(id) || id.contains(tier.id)) return tier;
        }
        return LEATHER;
    }
}
