package vn.haohan.backpack.command;

import io.papermc.paper.command.brigadier.BasicCommand;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import vn.haohan.backpack.service.BackpackService;

import java.util.Collection;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

/** Commands and suggestions for /backpack (aliases: /hhbp, /bp, /balo). */
public final class BackpackCommand implements BasicCommand {
    private static final String PREFIX = "§8[§bHaoHanBackpack§8] ";
    private static final List<CommandInfo> COMMANDS = List.of(
            new CommandInfo("give", "haohanbackpack.give", "/hhbp give <người chơi> <số lượng>"),
            new CommandInfo("list", null, "/hhbp list"),
            new CommandInfo("info", "haohanbackpack.admin", "/hhbp info <người chơi>"),
            new CommandInfo("delete", "haohanbackpack.admin", "/hhbp delete <người chơi>"),
            new CommandInfo("reload", "haohanbackpack.admin", "/hhbp reload")
    );
    private final BackpackService service;

    public BackpackCommand(BackpackService service) { this.service = service; }

    @Override public void execute(CommandSourceStack source, String[] args) {
        CommandSender sender = source.getSender();
        if (args.length == 0 || args[0].equalsIgnoreCase("help")) { sendHelp(sender); return; }
        CommandInfo command = findCommand(args[0]);
        if (command == null) { sender.sendMessage(PREFIX + "§cLệnh không hợp lệ. Dùng §f/hhbp help§c để xem hướng dẫn."); return; }
        if (!hasPermission(sender, command.permission())) { sender.sendMessage(PREFIX + "§cBạn không có quyền."); return; }
        switch (command.name()) {
            case "give" -> give(sender, args);
            case "list" -> list(sender, args);
            case "info" -> info(sender, args);
            case "delete" -> delete(sender, args);
            case "reload" -> reload(sender, args);
        }
    }

    private void give(CommandSender sender, String[] args) {
        if (!requireArgs(sender, args, 3, usage("give"))) return;
        Player target = Bukkit.getPlayerExact(args[1]);
        if (target == null) { sender.sendMessage(PREFIX + "§cKhông tìm thấy người chơi đang online: " + args[1]); return; }
        int amount;
        try { amount = Integer.parseInt(args[2]); }
        catch (NumberFormatException ex) { sender.sendMessage(PREFIX + "§cSố lượng phải là số nguyên dương."); return; }
        if (amount <= 0) { sender.sendMessage(PREFIX + "§cSố lượng phải lớn hơn 0."); return; }
        if (!service.canReceiveBackpacks(target, amount)) { sender.sendMessage(PREFIX + "§cNgười chơi đã đạt giới hạn backpack trong túi đồ."); return; }
        int remaining = amount;
        while (remaining > 0) {
            ItemStack backpack = service.createBackpackItem();
            for (ItemStack leftover : target.getInventory().addItem(backpack).values()) target.getWorld().dropItemNaturally(target.getLocation(), leftover);
            // Every backpack has its own storage UUID, so never combine them
            // into one stack even if an old item definition was stackable.
            remaining--;
        }
        sender.sendMessage(PREFIX + "§aĐã give " + amount + " ba lô cho " + target.getName() + ".");
        target.sendMessage(PREFIX + "§aBạn nhận được " + amount + " ba lô thám hiểm.");
    }

    private void list(CommandSender sender, String[] args) {
        if (!requireArgs(sender, args, 1, usage("list"))) return;
        if (!(sender instanceof Player player)) { sender.sendMessage(PREFIX + "§cLệnh này chỉ dành cho người chơi."); return; }
        List<UUID> ids = service.listBackpacks(player.getUniqueId());
        sender.sendMessage(PREFIX + "§eBackpack của bạn: §f" + (ids.isEmpty() ? "chưa có dữ liệu đã lưu" : ids));
    }

    private void info(CommandSender sender, String[] args) {
        UUID id = resolvePlayerId(sender, args, "info");
        if (id == null) return;
        boolean exists = service.database() != null && service.database().exists(id);
        sender.sendMessage(PREFIX + "§eBackpack ID: §f" + id + " §7(" + (exists ? "đã tồn tại" : "chưa có dữ liệu") + "§7)");
    }

    private void delete(CommandSender sender, String[] args) {
        UUID id = resolvePlayerId(sender, args, "delete");
        if (id == null) return;
        if (service.database() == null) { sender.sendMessage(PREFIX + "§cDatabase chưa sẵn sàng, không thể xoá."); return; }
        try { service.database().delete(id); sender.sendMessage(PREFIX + "§aĐã xoá dữ liệu backpack: " + id); }
        catch (IllegalStateException ex) { sender.sendMessage(PREFIX + "§cKhông thể xoá dữ liệu backpack."); }
    }

    private void reload(CommandSender sender, String[] args) {
        if (!requireArgs(sender, args, 1, usage("reload"))) return;
        service.reload(); sender.sendMessage(PREFIX + "§aĐã reload cấu hình HaoHanBackpack.");
    }

    private UUID resolvePlayerId(CommandSender sender, String[] args, String command) {
        if (!requireArgs(sender, args, 2, usage(command))) return null;
        try { return UUID.fromString(args[1]); } catch (IllegalArgumentException ignored) { }
        Player player = Bukkit.getPlayerExact(args[1]);
        if (player != null) return player.getUniqueId();
        sender.sendMessage(PREFIX + "§cKhông tìm thấy người chơi đang online: " + args[1]);
        return null;
    }

    @Override public Collection<String> suggest(CommandSourceStack source, String[] args) {
        CommandSender sender = source.getSender();
        if (args.length <= 1) {
            String prefix = args.length == 0 ? "" : args[0].toLowerCase(Locale.ROOT);
            return COMMANDS.stream().filter(command -> hasPermission(sender, command.permission())).map(CommandInfo::name)
                    .filter(name -> name.startsWith(prefix)).toList();
        }
        CommandInfo command = findCommand(args[0]);
        if (command == null || !hasPermission(sender, command.permission())) return List.of();
        if (command.name().equals("give") && args.length == 2) return matchingPlayers(args[1]);
        if (command.name().equals("give") && args.length == 3) return matching(args[2], List.of("1", "5", "10"));
        if ((command.name().equals("info") || command.name().equals("delete")) && args.length == 2) return matchingPlayers(args[1]);
        return List.of();
    }

    private Collection<String> matchingPlayers(String prefix) {
        return Bukkit.getOnlinePlayers().stream().map(Player::getName)
                .filter(name -> name.regionMatches(true, 0, prefix, 0, prefix.length())).toList();
    }
    private Collection<String> matching(String prefix, Collection<String> values) { return values.stream().filter(value -> value.startsWith(prefix)).toList(); }
    private void sendHelp(CommandSender sender) {
        sender.sendMessage(PREFIX + "§eCách dùng:");
        COMMANDS.stream().filter(command -> hasPermission(sender, command.permission())).forEach(command -> sender.sendMessage("§7- §f" + command.usage()));
    }
    private boolean requireArgs(CommandSender sender, String[] args, int count, String usage) {
        if (args.length == count) return true;
        sender.sendMessage(PREFIX + "§cDùng: §f" + usage); return false;
    }
    private String usage(String command) { return COMMANDS.stream().filter(info -> info.name().equals(command)).map(CommandInfo::usage).findFirst().orElse("/hhbp help"); }
    private CommandInfo findCommand(String name) { return COMMANDS.stream().filter(command -> command.name().equalsIgnoreCase(name)).findFirst().orElse(null); }
    private boolean hasPermission(CommandSender sender, String permission) { return permission == null || sender.hasPermission(permission); }
    private record CommandInfo(String name, String permission, String usage) { }
}
