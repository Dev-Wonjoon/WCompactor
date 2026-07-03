package net.wcompactor.scan

import com.willfp.eco.core.items.Items
import net.wcompactor.WCompactor
import net.wcompactor.recipe.CompactorRecipe
import org.bukkit.entity.Player
import org.bukkit.inventory.ItemStack
import java.util.UUID

class InventoryCompactor(
    private val plugin: WCompactor
) {
    private val running = mutableSetOf<UUID>()

    fun compact(player: Player) {
        if(!running.add(player.uniqueId)) {
            return
        }

        try {
            val recipes = selectedRecipes(player)
            plugin.debugLog("Compactor scan for ${player.name}: selected recipes=${recipes.map { it.id }}")
            if(recipes.isEmpty()) {
                plugin.debugLog("Compactor scan stopped: no selected recipes.")
                return
            }

            var remainingCrafts = plugin.compactorConfig.settings.maxCraftsPerScan

            for(recipe in recipes) {
                if(remainingCrafts <= 0) {
                    break
                }

                val totalInput = countInput(player, recipe)
                plugin.debugLog("Recipe ${recipe.id}: totalInput=$totalInput, required=${recipe.inputAmount}")

                val possibleCrafts = (totalInput / recipe.inputAmount).coerceAtMost(remainingCrafts)
                if(possibleCrafts <= 0) {
                    continue
                }

                val crafts = findCraftsThatFit(player, recipe, possibleCrafts)
                if(crafts <= 0) {
                    continue
                }

                val inputToRemove = recipe.inputAmount * crafts
                val outputAmount = recipe.outputAmount * crafts

                if(!removeInput(player, recipe, inputToRemove)) {
                    continue
                }

                addOutput(player, recipe, outputAmount)
                remainingCrafts -= crafts
            }
        } finally {
            running.remove(player.uniqueId)
        }
    }

    private fun selectedRecipes(player: Player): List<CompactorRecipe> {
        val result = mutableListOf<CompactorRecipe>()
        val seenCompactors = mutableSetOf<String>()

        for(item in player.inventory.contents) {
            val state = plugin.compactorItemService.readState(item) ?: continue
            if(!seenCompactors.add(state.compactorId)) {
                continue
            }

            for(recipeId in state.selectedRecipes) {
                val recipe = recipeId?.let { plugin.compactorConfig.recipes[it] } ?: continue
                result.add(recipe)
            }
        }

        return result
    }

    private fun countInput(player: Player, recipe: CompactorRecipe): Int {
        var total = 0

        for(item in player.inventory.storageContents) {
            if(item == null || item.type.isAir) {
                continue
            }

            if(plugin.compactorItemService.isCompactor(item)) {
                continue
            }

            if(recipe.input.matches(item)) {
                total += item.amount
            }
        }

        return total
    }

    private fun findCraftsThatFit(
        player: Player,
        recipe: CompactorRecipe,
        maxCrafts: Int
    ): Int {
        for(crafts in maxCrafts downTo 1) {
            if(canFitOutput(player, recipe, recipe.outputAmount * crafts)) {
                return crafts
            }
        }

        return 0
    }

    private fun canFitOutput(
        player: Player,
        recipe: CompactorRecipe,
        totalOutputAmount: Int): Boolean {
        val simulated = player.inventory.storageContents.map { it?.clone() }.toTypedArray()

        for(output in createOutputStacks(recipe, totalOutputAmount)) {
            var remaining = output.amount

            for(index in simulated.indices) {
                val current = simulated[index]

                if(current == null || current.type.isAir) {
                    simulated[index] = output.clone().also { it.amount = remaining }
                    remaining = 0
                    break
                }

                if(current.isSimilar(output) && current.amount < current.maxStackSize) {
                    val move = (current.maxStackSize - current.amount).coerceAtMost(remaining)
                    current.amount += move
                    remaining -= move

                    if(remaining <= 0) {
                        break
                    }
                }
            }
            if(remaining > 0) {
                return false
            }
        }
        return true
    }

    private fun removeInput(
        player: Player,
        recipe: CompactorRecipe,
        amountToRemove: Int
    ): Boolean {
        var remaining = amountToRemove
        val contents = player.inventory.storageContents

        for(index in contents.indices) {
            val item = contents[index] ?: continue

            if(item.type.isAir || plugin.compactorItemService.isCompactor(item)) {
                continue
            }

            if(!recipe.input.matches(item)) {
                continue
            }

            val remove = item.amount.coerceAtMost(remaining)
            item.amount -= remove
            remaining -= remove

            if(item.amount <= 0) {
                contents[index] = null
            }

            if(remaining <= 0) {
                break
            }
        }
        if(remaining > 0) {
            return false
        }

        player.inventory.storageContents = contents
        return true
    }

    private fun addOutput(
        player: Player,
        recipe: CompactorRecipe,
        totalOutputAmount: Int
    ) {
        val leftovers = player.inventory.addItem(*createOutputStacks(recipe, totalOutputAmount).toTypedArray())

        if(leftovers.isNotEmpty()) {
            plugin.logger.warning("Compactor output overflowed after fit check for ${player.name}.")
        }
    }

    private fun createOutputStacks(
        recipe: CompactorRecipe,
        totalAmount: Int
    ): List<ItemStack> {
        val template = resolveOutputTemplate(recipe) ?: return emptyList()

        if (template.type.isAir) {
            plugin.logger.warning("Cannot create output for recipe ${recipe.id}: output template is AIR.")
            return emptyList()
        }

        val result = mutableListOf<ItemStack>()
        var remaining = totalAmount

        while(remaining > 0) {
            val stack = template.clone()
            val amount = remaining.coerceAtMost(stack.maxStackSize)

            stack.amount = amount
            result.add(stack)
            remaining -= amount
        }

        return result
    }

    private fun resolveOutputTemplate(recipe: CompactorRecipe): ItemStack? {
        if (!recipe.outputTemplate.type.isAir) {
            return recipe.outputTemplate
        }

        val freshTemplate = Items.lookup(recipe.outputLookup).item.clone()
        if (!freshTemplate.type.isAir) {
            recipe.outputTemplate = freshTemplate
            plugin.debugLog("Recipe ${recipe.id}: output template refreshed from eco lookup during scan.")
            return recipe.outputTemplate
        }

        plugin.logger.warning("Recipe ${recipe.id}: eco lookup '${recipe.outputLookup}' produced AIR.")
        return null
    }
}
