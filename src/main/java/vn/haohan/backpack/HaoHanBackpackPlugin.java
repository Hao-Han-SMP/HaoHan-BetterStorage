package vn.haohan.backpack;

import org.bukkit.Bukkit;
import org.bukkit.NamespacedKey;
import org.bukkit.plugin.java.JavaPlugin;
import vn.haohan.backpack.command.BackpackCommand;
import vn.haohan.backpack.listener.BackpackListener;
import vn.haohan.backpack.service.BackpackService;

public final class HaoHanBackpackPlugin extends JavaPlugin {
    private BackpackService backpackService;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        NamespacedKey itemKey = new NamespacedKey(this, "backpack_item");
        backpackService = new BackpackService(this, itemKey);
        backpackService.registerItemCoreDefinition();
        backpackService.registerDyeRecipes();

        BackpackCommand command = new BackpackCommand(backpackService);
        registerCommand("backpack", java.util.List.of("hhbp", "bp", "balo"), command);
        getServer().getPluginManager().registerEvents(new BackpackListener(this, backpackService), this);

        getServer().getScheduler().runTaskTimer(this, () -> getServer().getOnlinePlayers().forEach(backpackService::updateWornBackpack), 20L, 20L);
        getServer().getOnlinePlayers().forEach(backpackService::discoverRecipes);

        getLogger().info("HaoHanBackpack enabled (54 storage slots)");
    }

    @Override
    public void onDisable() {
        if (backpackService != null) {
            getServer().getOnlinePlayers().forEach(backpackService::removeWornBackpack);
            backpackService.saveAllOpenBackpacks();
            backpackService.closeDatabase();
        }
    }
}
