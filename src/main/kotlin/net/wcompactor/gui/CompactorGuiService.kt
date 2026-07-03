package net.wcompactor.gui

import com.willfp.eco.core.items.Items
import net.wcompactor.WCompactor
import net.wcompactor.item.CompactorState
import net.wcompactor.recipe.CompactorRecipe
import org.bukkit.Bukkit
import org.bukkit.ChatColor
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

            inventory.setItem(slot, recipe?.let { selectedRecipeIcon(it) } ?: emptySlotIcon(index))
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
        val fillerConfig = plugin.compactorConfig.guiFiller
        val filler = ItemStack(fillerConfig.material)
        val meta = filler.itemMeta
        meta?.setDisplayName(colorize(fillerConfig.name))
        meta?.lore = fillerConfig.lore.map { colorize(it) }
        filler.itemMeta = meta

        for(slot in 0 until inventory.size) {
            inventory.setItem(slot, filler)
        }
    }

    private fun emptySlotIcon(slotIndex: Int): ItemStack {
        val emptySlot = plugin.compactorConfig.guiEmptySlot
        val item = ItemStack(emptySlot.material)
        val meta = item.itemMeta
        meta?.setDisplayName(colorize(emptySlot.name.replace("{slot}", (slotIndex + 1).toString())))
        meta?.lore = emptySlot.lore.map { colorize(it.replace("{slot}", (slotIndex + 1).toString())) }

        item.itemMeta = meta
        return item
    }

    private fun selectedRecipeIcon(recipe: CompactorRecipe): ItemStack {
        val item = recipe.outputTemplate.clone()
        val appendLore = plugin.compactorConfig.guiSelectedRecipeAppendLore
        if(appendLore.isEmpty()) {
            return item
        }

        val meta = item.itemMeta ?: return item
        val existingLore = meta.lore ?: emptyList()
        meta.lore = existingLore + appendLore.map {
            colorize(
                it
                    .replace("{recipe_id}", recipe.id)
                    .replace("{input_amount}", recipe.inputAmount.toString())
                    .replace("{output_amount}", recipe.outputAmount.toString())
            )
        }
        item.itemMeta = meta
        return item
    }

    private fun colorize(text: String): String {
        return ChatColor.translateAlternateColorCodes('&', text)
    }
}
