package net.wcompactor.item

import org.bukkit.NamespacedKey
import org.bukkit.plugin.java.JavaPlugin

class CompactorKeys(plugin: JavaPlugin) {
    val compactorId = NamespacedKey(plugin, "compactor_id")
    val tier = NamespacedKey(plugin, "tier")
    val selectedRecipes = NamespacedKey(plugin, "selected_recipes")
    val version = NamespacedKey(plugin, "version")
}