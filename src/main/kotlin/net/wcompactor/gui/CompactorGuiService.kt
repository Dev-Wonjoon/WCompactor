package net.wcompactor.gui

import com.willfp.eco.core.items.Items
import net.wcompactor.WCompactor
import net.wcompactor.item.CompactorState
import org.bukkit.Bukkit
import org.bukkit.Material
import org.bukkit.entity.Player
import org.bukkit.inventory.Inventory
import org.bukkit.inventory.ItemStack

class CompactorGuiService(
    private val plugin: WCompactor
) {
    fun open(player: Player, state: CompactorState) {
        val tier = plugin.compactorConfig.tiers[state.tierId] ?: return
        val size = plugin.compactorConfig.guiRows.coerceIn(1, 6) * 9
        val holder = CompactorGuiHolder(state.compactorId, state.tierId)
        val inventory = Bukkit.createInventory(holder, size, plugin.compactorConfig.guiTitle)

        holder.backingInventory = inventory
        fill(inventory)

        for((index, slot) in tier.recipeSlots.withIndex()) {
            if(slot !in 0 until size) {
                continue
            }

            val recipeId = state.selectedRecipes.getOrNull(index)
            val recipe = recipeId?.let { plugin.compactorConfig.recipes[it] }

            inventory.setItem(slot, recipe?.outputTemplate?.clone() ?: emptySlotIcon())
        }

        player.openInventory(inventory)
    }

    fun slotIndexForGuiSlot(tierId: String, rawSlot: Int): Int? {
        val tier = plugin.compactorConfig.tiers[tierId] ?: return null
        val index = tier.recipeSlots.indexOf(rawSlot)
        return index.takeIf { it >= 0 }
    }

    fun findRecipeIdBySample(sample: ItemStack?): String? {
        if (sample == null || sample.type.isAir) {
            plugin.debugLog("Recipe sample match stopped: sample is empty.")
            return null
        }

        val sampleLookup = runCatching { Items.toLookupString(sample).substringBefore(" ") }.getOrNull()

        for (recipe in plugin.compactorConfig.recipes.values) {
            val freshOutput = Items.lookup(recipe.outputLookup)
            val freshTemplate = freshOutput.item.clone()
            if (recipe.outputTemplate.type.isAir && !freshTemplate.type.isAir) {
                freshTemplate.amount = recipe.outputAmount.coerceAtMost(freshTemplate.maxStackSize)
                recipe.outputTemplate = freshTemplate
                plugin.debugLog("Recipe ${recipe.id}: output template refreshed from eco lookup.")
            }

            val templateMatches = !recipe.outputTemplate.type.isAir && recipe.outputTemplate.isSimilar(sample)
            val lookupMatches = sampleLookup.equals(recipe.outputLookup, ignoreCase = true)
            val matches = freshOutput.matches(sample) || recipe.output.matches(sample) || templateMatches || lookupMatches
            plugin.debugLog(
                "Recipe sample check: sample=${sample.type}, lookup=$sampleLookup, recipe=${recipe.id}, output=${recipe.outputTemplate.type}, matches=$matches"
            )

            if (matches) {
                if (recipe.outputTemplate.type.isAir || lookupMatches) {
                    recipe.outputTemplate = sample.clone().also {
                        it.amount = recipe.outputAmount.coerceAtMost(it.maxStackSize)
                    }
                    plugin.debugLog("Recipe ${recipe.id}: runtime output template captured from GUI sample.")
                }
                return recipe.id
            }
        }

        return null
    }

    private fun fill(inventory: Inventory) {
        val filler = ItemStack(Material.BLACK_STAINED_GLASS_PANE)
        val meta = filler.itemMeta
        meta?.setDisplayName(" ")
        filler.itemMeta = meta

        for(slot in 0 until inventory.size) {
            inventory.setItem(slot, filler)
        }
    }

    private fun emptySlotIcon(): ItemStack {
        val item = ItemStack(Material.GREEN_STAINED_GLASS_PANE)
        val meta = item.itemMeta
        meta?.setDisplayName("Empty Compactor Slot.")
        meta?.lore = listOf("Click with an output item sample.")

        item.itemMeta = meta
        return item
    }
}
