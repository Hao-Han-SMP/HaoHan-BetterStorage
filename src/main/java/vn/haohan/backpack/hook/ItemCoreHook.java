package vn.haohan.backpack.hook;

import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;
import vn.haohan.backpack.tier.BackpackTier;
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

        // 1. Register Storage Module
        if (!core.getItemService().exists("haohan:storage_module")) {
            core.getItemRegistry().register(ItemDefinition.builder("haohan:storage_module")
                    .material(Material.PAPER).displayName("§eStorage Module").maxStackSize(64)
                    .type(ItemType.MACHINE_COMPONENT).model("haohan:storage_module")
                    .addLore("§7Module lưu trữ dùng để chế tạo hoặc")
                    .addLore("§7nâng cấp ba lô.").build());
        }

        // 2. Register 5 Tier Stack Upgrades (Tier 0 -> Tier 4)
        record StackUpgradeTier(String id, String name, String lore, Material cornerMat, String prevId) {}
        List<StackUpgradeTier> stackTiers = List.of(
                new StackUpgradeTier("haohan:upgrade_tier_0", "§6Nâng cấp Ba Lô (Tier 0 - Đồng)", "§7Tăng giới hạn stack trong ba lô lên: §e128", Material.COPPER_INGOT, "haohan:storage_module"),
                new StackUpgradeTier("haohan:upgrade_tier_1", "§fNâng cấp Ba Lô (Tier 1 - Sắt)", "§7Tăng giới hạn stack trong ba lô lên: §e192", Material.IRON_INGOT, "haohan:upgrade_tier_0"),
                new StackUpgradeTier("haohan:upgrade_tier_2", "§aNâng cấp Ba Lô (Tier 2 - Emerald)", "§7Tăng giới hạn stack trong ba lô lên: §e320", Material.EMERALD, "haohan:upgrade_tier_1"),
                new StackUpgradeTier("haohan:upgrade_tier_3", "§bNâng cấp Ba Lô (Tier 3 - Kim cương)", "§7Tăng giới hạn stack trong ba lô lên: §e448", Material.DIAMOND, "haohan:upgrade_tier_2"),
                new StackUpgradeTier("haohan:upgrade_tier_4", "§5Nâng cấp Ba Lô (Tier 4 - Netherite)", "§7Tăng giới hạn stack trong ba lô lên: §e512", Material.NETHERITE_INGOT, "haohan:upgrade_tier_3")
        );

        for (StackUpgradeTier tier : stackTiers) {
            if (!core.getItemService().exists(tier.id)) {
                core.getItemRegistry().register(ItemDefinition.builder(tier.id)
                        .material(Material.PAPER).displayName(tier.name).maxStackSize(16)
                        .type(ItemType.MACHINE_COMPONENT).model(tier.id)
                        .addLore(tier.lore)
                        .addLore("§8Đặt vào ô Module trong ba lô để kích hoạt.")
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

        // 2b. Register Magnet Module
        if (!core.getItemService().exists("haohan:magnet_module")) {
            core.getItemRegistry().register(ItemDefinition.builder("haohan:magnet_module")
                    .material(Material.PAPER).displayName("§cMagnet Module").maxStackSize(16)
                    .type(ItemType.MACHINE_COMPONENT).model("haohan:magnet_module")
                    .addLore("§7Tự động hút các vật phẩm rơi")
                    .addLore("§7xung quanh lại gần người chơi.")
                    .addLore("§8Đặt vào ô Module trong ba lô để kích hoạt.")
                    .build());
        }

        String magnetRecipeKey = "haohan:magnet_module_craft";
        if (!core.getRecipeRegistry().exists(magnetRecipeKey)) {
            Map<Character, Ingredient> ingMap = new HashMap<>();
            ingMap.put('I', new Ingredient.MaterialIngredient(Material.IRON_INGOT));
            ingMap.put('R', new Ingredient.MaterialIngredient(Material.REDSTONE));
            ingMap.put('L', new Ingredient.MaterialIngredient(Material.LAPIS_LAZULI));
            ingMap.put('M', new Ingredient.ItemIngredient("haohan:storage_module"));

            core.getRecipeRegistry().register(new ShapedRecipeDefinition(
                    magnetRecipeKey,
                    List.of(
                            "R L",
                            "IMI",
                            " I "
                    ),
                    ingMap,
                    new ItemResult("haohan:magnet_module", 1)
            ));
        }

        // 2c. Register Jukebox Module
        if (!core.getItemService().exists("haohan:jukebox_module")) {
            core.getItemRegistry().register(ItemDefinition.builder("haohan:jukebox_module")
                    .material(Material.PAPER).displayName("§6Jukebox Module").maxStackSize(16)
                    .type(ItemType.MACHINE_COMPONENT).model("haohan:jukebox_module")
                    .addLore("§7Biến ba lô thành máy phát nhạc di động.")
                    .addLore("§7Đặt đĩa nhạc vào ô ba lô để phát nhạc theo bạn,")
                    .addLore("§7những người chơi xung quanh cũng có thể nghe.")
                    .addLore("§8Đặt vào ô Module trong ba lô để kích hoạt.")
                    .build());
        }

        String jukeboxRecipeKey = "haohan:jukebox_module_craft";
        if (!core.getRecipeRegistry().exists(jukeboxRecipeKey)) {
            Map<Character, Ingredient> ingMap = new HashMap<>();
            ingMap.put('J', new Ingredient.MaterialIngredient(Material.JUKEBOX));
            ingMap.put('N', new Ingredient.MaterialIngredient(Material.NOTE_BLOCK));
            ingMap.put('R', new Ingredient.MaterialIngredient(Material.REDSTONE));
            ingMap.put('M', new Ingredient.ItemIngredient("haohan:storage_module"));

            core.getRecipeRegistry().register(new ShapedRecipeDefinition(
                    jukeboxRecipeKey,
                    List.of(
                            "RNR",
                            "JMJ",
                            "RNR"
                    ),
                    ingMap,
                    new ItemResult("haohan:jukebox_module", 1)
            ));
        }

                // 2d. Register 5 Tier Furnace Modules (Tier 0 -> Tier 4)
        record FurnaceModuleDef(String id, String name, String speedLore, Material cornerMat, String prevId, Material centerMat) {}
        List<FurnaceModuleDef> furnaceDefs = List.of(
                new FurnaceModuleDef("haohan:furnace_module_tier_0", "§6Module Lò Nung (Tier 0 - Cơ bản)", "§7Tốc độ nung: §e1x (10s/quặng)", Material.COBBLESTONE, "haohan:storage_module", Material.FURNACE),
                new FurnaceModuleDef("haohan:furnace_module_tier_1", "§fModule Lò Nung (Tier 1 - Sắt)", "§7Tốc độ nung: §e2x (5s/quặng)", Material.IRON_INGOT, "haohan:furnace_module_tier_0", Material.BLAST_FURNACE),
                new FurnaceModuleDef("haohan:furnace_module_tier_2", "§eModule Lò Nung (Tier 2 - Vàng)", "§7Tốc độ nung: §e4x (2.5s/quặng)", Material.GOLD_INGOT, "haohan:furnace_module_tier_1", Material.BLAST_FURNACE),
                new FurnaceModuleDef("haohan:furnace_module_tier_3", "§bModule Lò Nung (Tier 3 - Kim Cương)", "§7Tốc độ nung: §e8x (1.25s/quặng)", Material.DIAMOND, "haohan:furnace_module_tier_2", Material.BLAST_FURNACE),
                new FurnaceModuleDef("haohan:furnace_module_tier_4", "§5Module Lò Nung (Tier 4 - Netherite)", "§7Tốc độ: §d16x (Nung tức thì) §7+ §a-50% nhiên liệu", Material.NETHERITE_INGOT, "haohan:furnace_module_tier_3", Material.BLAST_FURNACE)
        );

        for (FurnaceModuleDef def : furnaceDefs) {
            if (!core.getItemService().exists(def.id)) {
                core.getItemRegistry().register(ItemDefinition.builder(def.id)
                        .material(Material.PAPER).displayName(def.name).maxStackSize(16)
                        .type(ItemType.MACHINE_COMPONENT).model(def.id)
                        .addLore("§7Tự động nung quặng & đồ ăn trong ba lô.")
                        .addLore(def.speedLore)
                        .addLore("§7Ô trên: Quặng nung - Ô dưới: Nhiên liệu đốt")
                        .addLore("§8Đặt vào ô Module trong ba lô để kích hoạt.")
                        .build());
            }

            String rKey = def.id + "_craft";
            if (!core.getRecipeRegistry().exists(rKey)) {
                Map<Character, Ingredient> ingMap = new HashMap<>();
                ingMap.put('M', new Ingredient.MaterialIngredient(def.cornerMat));
                ingMap.put('U', new Ingredient.ItemIngredient(def.prevId));
                ingMap.put('R', new Ingredient.MaterialIngredient(Material.REDSTONE));
                ingMap.put('F', new Ingredient.MaterialIngredient(def.centerMat));

                core.getRecipeRegistry().register(new ShapedRecipeDefinition(
                        rKey,
                        List.of(
                                "MRM",
                                "FUF",
                                "MRM"
                        ),
                        ingMap,
                        new ItemResult(def.id, 1)
                ));
            }
        }

        // 3. Register All 5 Backpack Tiers & Colored Variants
        for (BackpackTier tier : BackpackTier.values()) {
            String baseId = "haohan:backpack_" + tier.getId();
            String defaultModel = "haohan:backpack_" + tier.getId();

            if (!core.getItemService().exists(baseId)) {
                core.getItemRegistry().register(ItemDefinition.builder(baseId)
                        .material(Material.BROWN_DYE).displayName(tier.getDisplayName()).maxStackSize(1)
                        .type(ItemType.SPECIAL).model(defaultModel)
                        .addLore("§7Cấp bậc: " + tier.getDisplayName())
                        .addLore("§7Sức chứa: §e" + tier.getStorageSlotsCount() + " ô + " + tier.getModuleSlotsCount() + " module")
                        .addLore("")
                        .addLore("§7Chuột phải để mở ba lô cá nhân.")
                        .build());
            }

            // Colored variants for each tier
            for (org.bukkit.DyeColor dye : org.bukkit.DyeColor.values()) {
                String colorName = dye.name().toLowerCase(java.util.Locale.ROOT);
                String coloredId = "haohan:backpack_" + tier.getId() + "_" + colorName;
                String coloredModel = "haohan:backpack_" + tier.getId() + "_" + colorName;

                if (!core.getItemService().exists(coloredId)) {
                    core.getItemRegistry().register(ItemDefinition.builder(coloredId)
                            .material(Material.BROWN_DYE).displayName(tier.getDisplayName()).maxStackSize(1)
                            .type(ItemType.SPECIAL).model(coloredModel)
                            .addLore("§7Cấp bậc: " + tier.getDisplayName())
                            .addLore("§7Đã nhuộm: " + getFriendlyColorName(colorName))
                            .addLore("§7Sức chứa: §e" + tier.getStorageSlotsCount() + " ô + " + tier.getModuleSlotsCount() + " module")
                            .addLore("")
                            .addLore("§7Chuột phải để mở ba lô cá nhân.")
                            .build());
                }
            }
        }

        // 4. Recipes for Backpack Tiers
        // Leather Backpack Craft
        if (!core.getRecipeRegistry().exists("haohan:backpack_leather_craft")) {
            Map<Character, Ingredient> ingMap = new HashMap<>();
            ingMap.put('I', new Ingredient.MaterialIngredient(Material.IRON_INGOT));
            ingMap.put('M', new Ingredient.ItemIngredient("haohan:storage_module"));
            ingMap.put('S', new Ingredient.MaterialIngredient(Material.STRING));
            ingMap.put('C', new Ingredient.MaterialIngredient(Material.CHEST));
            ingMap.put('L', new Ingredient.MaterialIngredient(Material.LEATHER));

            core.getRecipeRegistry().register(new ShapedRecipeDefinition(
                    "haohan:backpack_leather_craft",
                    List.of(
                            "IMI",
                            "SCS",
                            "LLL"
                    ),
                    ingMap,
                    new ItemResult("haohan:backpack_leather", 1)
            ));
        }

        // Tier upgrades (Leather -> Iron -> Gold -> Diamond -> Netherite)
        for (BackpackTier tier : BackpackTier.values()) {
            if (tier.getPrevTierId() != null) {
                String recipeKey = "haohan:backpack_" + tier.getId() + "_upgrade";
                if (!core.getRecipeRegistry().exists(recipeKey)) {
                    Map<Character, Ingredient> ingMap = new HashMap<>();
                    ingMap.put('M', new Ingredient.MaterialIngredient(tier.getUpgradeMaterial()));
                    ingMap.put('B', new Ingredient.ItemIngredient(tier.getPrevTierId()));

                    core.getRecipeRegistry().register(new ShapedRecipeDefinition(
                            recipeKey,
                            List.of(
                                    "MMM",
                                    "MBM",
                                    "MMM"
                            ),
                            ingMap,
                            new ItemResult("haohan:backpack_" + tier.getId(), 1)
                    ));
                }
            }
        }
    }

    public static String getFriendlyColorName(String colorName) {
        return switch (colorName) {
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
            default -> "§f" + colorName;
        };
    }

    public static ItemStack createItem(String id) {
        var service = HaoHanItemCore.get().getItemService();
        if (service.exists(id)) {
            return service.create(id);
        }
        if (id.equals("haohan:backpack") && service.exists("haohan:backpack_leather")) {
            return service.create("haohan:backpack_leather");
        }
        return null;
    }

    public static String getItemId(ItemStack item) {
        if (item == null || item.getType().isAir()) return null;
        var service = HaoHanItemCore.get().getItemService();
        return service.getId(item);
    }
}
