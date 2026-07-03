package net.wcompactor.recipe

import com.willfp.eco.core.items.Items
import org.bukkit.Bukkit
import org.bukkit.Keyed
import org.bukkit.Material
import org.bukkit.configuration.file.YamlConfiguration
import org.bukkit.inventory.ItemStack
import org.bukkit.inventory.Recipe
import org.bukkit.inventory.ShapedRecipe
import org.bukkit.inventory.ShapelessRecipe
import org.bukkit.plugin.java.JavaPlugin
import java.io.File
import java.util.Locale

class RegistryRecipeExporter(
    private val plugin: JavaPlugin
) {
    fun exportTo(recipeDirectory: File): Int {
        if(!recipeDirectory.exists()) {
            recipeDirectory.mkdirs()
        }

        val outputFile = File(recipeDirectory, "auto_registry.yml")
        val config = YamlConfiguration()
        val userIds = mutableSetOf<String>()
        var exported = 0

        val iterator = Bukkit.recipeIterator()
        while(iterator.hasNext()) {
            val recipe = iterator.next()
            val candidate = candidateFrom(recipe) ?: continue
            val recipeId = uniqueId(baseRecipeId(recipe, candidate.outputLookup), userIds)
            val path = "recipes.$recipeId"

            config.set("$path.enabled", true)
            config.set("$path.input", candidate.inputLookup)
            config.set("$path.input-amount", candidate.inputAmount)
            config.set("$path.output", candidate.outputLookup)
            config.set("$path.output-amount", candidate.outputAmount)
            exported++
        }

        config.save(outputFile)
        plugin.logger.info("Exported $exported registry recipe(s) to ${outputFile.name}.")
        return exported
    }

    private fun candidateFrom(recipe: Recipe): RegistryRecipeCandidate? {
        val output = recipe.result.clone()
        if(output.type.isAir) {
            return null
        }

        val outputAmount = output.amount.coerceAtLeast(1)
        val outputLookup = lookupWithoutAmount(output) ?: return null
        val ingredients = ingredientsFrom(recipe) ?: return null
        if(ingredients.isEmpty()) {
            return null
        }

        val normalized = ingredients.map { ingredient ->
            val lookup = lookupWithoutAmount(ingredient) ?: return null
            RegistryIngredient(
                lookup = lookup,
                amount = ingredient.amount.coerceAtLeast(1),
            )
        }

        val inputLookup = normalized.first().lookup
        if(normalized.any { it.lookup != inputLookup }) {
            return null
        }

        val inputAmount = normalized.sumOf { it.amount }
        if(inputAmount <= 1) {
            return null
        }

        return RegistryRecipeCandidate(
            inputLookup = inputLookup,
            inputAmount = inputAmount,
            outputLookup = outputLookup,
            outputAmount = outputAmount,
        )
    }

    private fun ingredientsFrom(recipe: Recipe): List<ItemStack>? {
        return when(recipe) {
            is ShapedRecipe -> shapedIngredients(recipe)
            is ShapelessRecipe -> recipe.ingredientList
                .filter { !it.type.isAir }
                .map { it.clone() }
            else -> null
        }
    }

    private fun shapedIngredients(recipe: ShapedRecipe): List<ItemStack>? {
        val ingredientMap = recipe.ingredientMap
        val result = mutableListOf<ItemStack>()

        for(row in recipe.shape) {
            for(symbol in row) {
                if(symbol == ' ') {
                    continue
                }

                val ingredient = ingredientMap[symbol] ?: return null
                if(ingredient.type.isAir) {
                    continue
                }

                result += ingredient.clone()
            }
        }

        return result
    }

    private fun lookupWithoutAmount(item: ItemStack): String? {
        if(item.type == Material.AIR || item.type.isAir) {
            return null
        }

        return try {
            val single = item.clone()
            single.amount = 1
            Items.toLookupString(single)
        } catch (e: Exception) {
            plugin.logger.info("Could not convert registry item to eco lookup: ${e.message}")
            null
        }
    }

    private fun baseRecipeId(recipe: Recipe, outputLookup: String): String {
        val raw = if(recipe is Keyed) {
            "${recipe.key.namespace}_${recipe.key.key}"
        } else {
            outputLookup
        }

        return "registry_" + raw
            .lowercase(Locale.ROOT)
            .replace(Regex("[^a-z0-9_]+"), "_")
            .trim('_')
            .ifBlank { "recipe" }
    }

    private fun uniqueId(base: String, usedIds: MutableSet<String>): String {
        var id = base
        var suffix = 2

        while(!usedIds.add(id)) {
            id = "${base}_$suffix"
            suffix++
        }

        return id
    }

    private data class RegistryRecipeCandidate(
        val inputLookup: String,
        val inputAmount: Int,
        val outputLookup: String,
        val outputAmount: Int
    )

    private data class RegistryIngredient(
        val lookup: String,
        val amount: Int
    )
}