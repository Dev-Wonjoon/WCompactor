package net.wcompactor.command

import com.willfp.eco.core.items.Items
import net.wcompactor.WCompactor
import org.bukkit.Bukkit
import org.bukkit.command.Command
import org.bukkit.command.CommandExecutor
import org.bukkit.command.CommandSender
import org.bukkit.command.TabCompleter
import org.bukkit.entity.Player

class WCompactorCommand(
    private val plugin: WCompactor
) : CommandExecutor, TabCompleter {
    override fun onCommand(
        sender: CommandSender,
        command: Command,
        label: String,
        args: Array<out String>
    ): Boolean {
        if(!sender.hasPermission("wcompactor.admin")) {
            sender.sendMessage("You do not have permission.")
            return true
        }

        when(args.getOrNull(0)?.lowercase()) {
            "give" -> give(sender, args)
            "reload" -> reload(sender)
            "debugitem" -> debugItem(sender)
            "compact" -> compact(sender)
            else -> help(sender, label)
        }

        return true
    }

    private fun give(sender: CommandSender, args: Array<out String>) {
        val targetName = args.getOrNull(1)
        val tierId = args.getOrNull(2) ?: "basic"
        val amount = args.getOrNull(3)?.toIntOrNull()?.coerceIn(1, 64) ?: 1

        if(targetName == null) {
            sender.sendMessage("Usage: /wcompactor give <player> <tier> [amount]")
            return
        }

        val target = Bukkit.getPlayerExact(targetName)
        if(target == null) {
            sender.sendMessage("Player not found: $targetName")
            return
        }

        if(!plugin.compactorConfig.tiers.containsKey(tierId)) {
            sender.sendMessage("Unknown tier: $tierId")
            sender.sendMessage("Available tiers: ${plugin.compactorConfig.tiers.keys.joinToString(", ")}")
            return
        }

        repeat(amount) {
            val item = plugin.compactorItemService.createCompactor(tierId) ?: return@repeat
            val leftovers = target.inventory.addItem(item)
            leftovers.values.forEach { leftover ->
                target.world.dropItemNaturally(target.location, leftover)
            }
        }

        sender.sendMessage("Gave $amount $tierId compactor(s) to ${target.name}.")
    }

    private fun reload(sender: CommandSender) {
        plugin.reloadWCompactor()
        sender.sendMessage("WCompactor reloaded.")
        sender.sendMessage("Loaded ${plugin.compactorConfig.tiers.size} tiers and ${plugin.compactorConfig.recipes.size} recipes.")
    }

    private fun debugItem(sender: CommandSender) {
        if(sender !is Player) {
            sender.sendMessage("Only players can use debugitem.")
            return
        }

        val item = sender.inventory.itemInMainHand
        if(item.type.isAir) {
            sender.sendMessage("Hold an item first.")
            return
        }

        sender.sendMessage("Type: ${item.type}")
        sender.sendMessage("Amount: ${item.amount}")

        try {
            sender.sendMessage("Lookup: ${Items.toLookupString(item)}")
            sender.sendMessage("Custom item: ${Items.isCustomItem(item)}")
        } catch(e: Exception) {
            sender.sendMessage("eco lookup failed: ${e.message}")
        }

        val state = plugin.compactorItemService.readState(item)
        if(state == null) {
            sender.sendMessage("Compactor: no")
        } else {
            sender.sendMessage("Compactor: yes")
            sender.sendMessage("ID: ${state.compactorId}")
            sender.sendMessage("Tier: ${state.tierId}")
            sender.sendMessage("Selected: ${state.selectedRecipes.joinToString(",") { it ?: "_" }}")
            sender.sendMessage("Version: ${state.version}")
        }
    }

    private fun compact(sender: CommandSender) {
        if(sender !is Player) {
            sender.sendMessage("Only players can use compact.")
            return
        }

        plugin.inventoryCompactor.compact(sender)
        sender.sendMessage("Compaction scan completed.")
    }

    private fun help(sender: CommandSender, label: String) {
        sender.sendMessage("/$label give <player> <tier> [amount]")
        sender.sendMessage("/$label reload")
        sender.sendMessage("/$label debugitem")
        sender.sendMessage("/$label compact")
    }

    override fun onTabComplete(
        sender: CommandSender,
        command: Command,
        label: String,
        args: Array<out String>
    ): List<String> {
        if(!sender.hasPermission("wcompactor.admin")) {
            return emptyList()
        }

        return when (args.size) {
            1 -> listOf("give", "reload", "debugitem", "compact")
                .filter { it.startsWith(args[0], ignoreCase = true) }
            2 -> if(args[0].equals("give", ignoreCase = true)) {
                Bukkit.getOnlinePlayers()
                    .map { it.name }
                    .filter { it.startsWith(args[1], ignoreCase = true) }
                } else {
                    emptyList()
                }
            3 -> if (args[0].equals("give", ignoreCase = true)) {
                plugin.compactorConfig.tiers.keys
                    .filter { it.startsWith(args[2], ignoreCase = true) }
            } else {
                emptyList()
            }

            else -> emptyList()
        }
    }
}