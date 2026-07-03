package net.wcompactor.item

import net.wcompactor.WCompactor
import net.wcompactor.recipe.CompactorTier
import org.bukkit.ChatColor
import org.bukkit.inventory.ItemStack
import org.bukkit.inventory.PlayerInventory
import org.bukkit.persistence.PersistentDataType
import java.util.UUID

class CompactorItemService(
    private val plugin: WCompactor
) {
    private val keys = CompactorKeys(plugin)

    fun createCompactor(tierId: String): ItemStack? {
        val tier = plugin.compactorConfig.tiers[tierId] ?: return null
        val item = ItemStack(tier.fallbackMaterial, 1)
        val state = CompactorState(
            compactorId = UUID.randomUUID().toString(),
            tierId = tier.id,
            selectedRecipes = List(tier.slots) { null }
        )

        applyDisplay(item, tier)
        writeState(item, state)

        return item
    }

    fun isCompactor(item: ItemStack?): Boolean {
        if(item == null || item.type.isAir) {
            return false
        }

        val meta = item.itemMeta ?: return false
        return meta.persistentDataContainer.has(keys.compactorId, PersistentDataType.STRING)
    }

    fun readState(item: ItemStack?): CompactorState? {
        if(item == null || item.type.isAir) {
            return null
        }

        val meta = item.itemMeta ?: return null
        val container = meta.persistentDataContainer

        val compactorId = container.get(keys.compactorId, PersistentDataType.STRING) ?: return null
        val tierId = container.get(keys.tier, PersistentDataType.STRING) ?: return null
        val selectedRaw = container.get(keys.selectedRecipes, PersistentDataType.STRING) ?: ""
        val version = container.get(keys.version, PersistentDataType.INTEGER) ?: 1
        val tier = plugin.compactorConfig.tiers[tierId] ?: return null

        return CompactorState(
            compactorId = compactorId,
            tierId = tierId,
            selectedRecipes = decodeSelectedRecipes(selectedRaw, tier.slots),
            version = version
        )
    }

    fun writeState(item: ItemStack, state: CompactorState) {
        val meta = item.itemMeta ?: return
        val container = meta.persistentDataContainer

        container.set(keys.compactorId, PersistentDataType.STRING, state.compactorId)
        container.set(keys.tier, PersistentDataType.STRING, state.tierId)
        container.set(keys.selectedRecipes, PersistentDataType.STRING, encodeSelectedRecipes(state.selectedRecipes))
        container.set(keys.version, PersistentDataType.INTEGER, state.version)

        item.itemMeta = meta
    }

    fun withSelectedRecipe(item: ItemStack, slotIndex: Int, recipeId: String?): Boolean {
        val state = readState(item) ?: return false
        val tier = plugin.compactorConfig.tiers[state.tierId] ?: return false

        if(slotIndex !in 0 until tier.slots) {
            return false
        }

        val selected = state.selectedRecipes.toMutableList()
        while(selected.size < tier.slots) {
            selected.add(null)
        }

        selected[slotIndex] = recipeId
        writeState(item, state.copy(selectedRecipes = selected.take(tier.slots)))
        return true
    }

    fun findCompactorById(inventory: PlayerInventory, compactorId: String): ItemStack? {
        for(item in inventory.contents) {
            val state = readState(item)
            if(state?.compactorId == compactorId) {
                return item
            }
        }

        val offhand = inventory.itemInOffHand
        val offhandState = readState(offhand)
        if(offhandState?.compactorId == compactorId) {
            return offhand
        }

        return null
    }

    private fun applyDisplay(item: ItemStack, tier: CompactorTier) {
        val meta = item.itemMeta ?: return

        meta.setDisplayName(colorize(tier.displayName))
        meta.lore = tier.lore.map {
            colorize(
                it
                    .replace("{tier}", tier.id)
                    .replace("{slots}", tier.slots.toString())
            )
        }

        item.itemMeta = meta
    }

    private fun colorize(text: String): String {
        return ChatColor.translateAlternateColorCodes('&', text)
    }

    private fun encodeSelectedRecipes(selectedRecipes: List<String?>): String {
        return selectedRecipes.joinToString(",") { it ?: "" }
    }

    private fun decodeSelectedRecipes(raw: String, size: Int): List<String?> {
        val parts = raw.split(",").map { it.takeIf(String::isNotBlank) }
        return List(size) { index -> parts.getOrNull(index) }
    }
}
