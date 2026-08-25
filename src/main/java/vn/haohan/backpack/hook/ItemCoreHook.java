package vn.haohan.backpack.hook;

import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.inventory.ItemStack;
import vn.haohan.itemcore.api.HaoHanItemCore;
import vn.haohan.itemcore.api.item.ItemDefinition;
import vn.haohan.itemcore.api.item.ItemType;
import vn.haohan.itemcore.api.recipe.Ingredient;
import vn.haohan.itemcore.api.recipe.ItemResult;
import vn.haohan.itemcore.api.recipe.ShapedRecipeDefinition;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

public final class ItemCoreHook {
    private ItemCoreHook() {}

    public static void register() {
        var core = HaoHanItemCore.get();

        if (!core.getItemService().exists("haohan:backpack")) {
            core.getItemRegistry().register(ItemDefinition.builder("haohan:backpack")
                    .material(Material.BROWN_DYE).displayName("Backpack").maxStackSize(1)
                    .type(ItemType.SPECIAL).model("haohan:backpack")
                    .addLore("&7Chuột phải để mở ba lô cá nhân.").addLore("&8Dung lượng: 53 ô + 1 module").build());
        }

        if (!core.getItemService().exists("haohan:storage_module")) {
            core.getItemRegistry().register(ItemDefinition.builder("haohan:storage_module")
                    .material(Material.PAPER).displayName("§eStorage Module").maxStackSize(64)
                    .type(ItemType.MACHINE_COMPONENT).model("haohan:storage_module")
                    .addLore("&7Module lưu trữ dùng để chế tạo hoặc")
                    .addLore("&7nâng cấp ba lô.").build());
        }

        // 5 Tier Upgrades: Tier 0 (Copper), Tier 1 (Iron), Tier 2 (Emerald), Tier 3 (Diamond), Tier 4 (Netherite)
        record UpgradeTier(String id, String name, String lore, Material cornerMat, String prevId) {}
        List<UpgradeTier> tiers = List.of(
                new UpgradeTier("haohan:upgrade_tier_0", "§6Nâng cấp Ba Lô (Tier 0 - Đồng)", "&7Tăng giới hạn stack trong ba lô lên: &e128", Material.COPPER_INGOT, "haohan:storage_module"),
                new UpgradeTier("haohan:upgrade_tier_1", "§fNâng cấp Ba Lô (Tier 1 - Sắt)", "&7Tăng giới hạn stack trong ba lô lên: &e192", Material.IRON_INGOT, "haohan:upgrade_tier_0"),
                new UpgradeTier("haohan:upgrade_tier_2", "§aNâng cấp Ba Lô (Tier 2 - Emerald)", "&7Tăng giới hạn stack trong ba lô lên: &e320", Material.EMERALD, "haohan:upgrade_tier_1"),
                new UpgradeTier("haohan:upgrade_tier_3", "§bNâng cấp Ba Lô (Tier 3 - Kim cương)", "&7Tăng giới hạn stack trong ba lô lên: &e448", Material.DIAMOND, "haohan:upgrade_tier_2"),
                new UpgradeTier("haohan:upgrade_tier_4", "§5Nâng cấp Ba Lô (Tier 4 - Netherite)", "&7Tăng giới hạn stack trong ba lô lên: &e512", Material.NETHERITE_INGOT, "haohan:upgrade_tier_3")
        );

        for (UpgradeTier tier : tiers) {
            if (!core.getItemService().exists(tier.id)) {
                core.getItemRegistry().register(ItemDefinition.builder(tier.id)
                        .material(Material.PAPER).displayName(tier.name).maxStackSize(16)
                        .type(ItemType.MACHINE_COMPONENT).model(tier.id)
                        .addLore(tier.lore)
                        .addLore("&8Đặt vào ô Module trong ba lô để kích hoạt.")
                        .build());
            }

            String recipeKey = tier.id + "_craft";
            if (!core.getRecipeRegistry().exists(recipeKey)) {
                Map<Character, Ingredient> ingMap = new HashMap<>();
                ingMap.put('M', new Ingredient.MaterialIngredient(tier.cornerMat));
                ingMap.put('U', new Ingredient.ItemIngredient(tier.prevId));
                ingMap.put('R', new Ingredient.MaterialIngredient(Material.REDSTONE));

                core.getRecipeRegistry().register(new ShapedRecipeDefinition(
                        recipeKey,
                        List.of(
                                "MRM",
                                "RUR",
                                "MRM"
                        ),
                        ingMap,
                        new ItemResult(tier.id, 1)
                ));
            }
        }

        if (!core.getRecipeRegistry().exists("haohan:backpack_craft")) {
            Map<Character, Ingredient> ingMap = new HashMap<>();
            ingMap.put('I', new Ingredient.MaterialIngredient(Material.IRON_INGOT));
            ingMap.put('M', new Ingredient.ItemIngredient("haohan:storage_module"));
            ingMap.put('S', new Ingredient.MaterialIngredient(Material.STRING));
            ingMap.put('C', new Ingredient.MaterialIngredient(Material.CHEST));
            ingMap.put('L', new Ingredient.MaterialIngredient(Material.LEATHER));

            core.getRecipeRegistry().register(new ShapedRecipeDefinition(
                    "haohan:backpack_craft",
                    List.of(
                            "IMI",
                            "SCS",
                            "LLL"
                    ),
                    ingMap,
                    new ItemResult("haohan:backpack", 1)
            ));
        }

        for (org.bukkit.DyeColor dye : org.bukkit.DyeColor.values()) {
            String colorName = dye.name().toLowerCase(java.util.Locale.ROOT);
            String id = "haohan:backpack_" + colorName;
            if (!core.getItemService().exists(id)) {
                core.getItemRegistry().register(ItemDefinition.builder(id)
                        .material(Material.BROWN_DYE).displayName("Backpack").maxStackSize(1)
                        .type(ItemType.SPECIAL).model(id)
                        .addLore("&7Chuột phải để mở ba lô cá nhân.").addLore("&8Dung lượng: 53 ô + 1 module").build());
            }
        }
    }

    public static ItemStack createItem(String id) {
        var service = HaoHanItemCore.get().getItemService();
        if (service.exists(id)) {
            return service.create(id);
        }
        return null;
    }

    public static String getItemId(ItemStack item) {
        if (item == null || item.getType().isAir()) return null;
        var service = HaoHanItemCore.get().getItemService();
        return service.getId(item);
    }
}
