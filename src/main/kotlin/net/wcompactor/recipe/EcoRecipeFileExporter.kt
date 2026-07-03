package net.wcompactor.recipe

import com.willfp.eco.core.items.Items
import org.bukkit.configuration.file.YamlConfiguration
import org.bukkit.plugin.java.JavaPlugin
import java.io.File
import java.util.Locale

class EcoRecipeFileExporter(
    private val plugin: JavaPlugin
) {
    fun exportTo(recipesDirectory: File, sourceDirectories: List<String>): Int {
        if(!recipesDirectory.exists() && !recipesDirectory.mkdirs()) {
            plugin.logger.warning("Could not create recipes directory: ${recipesDirectory.path}")
            return 0
        }

        val outputFile = File(recipesDirectory, "auto_eco_files.yml")
        val config = YamlConfiguration()
        val usedIds = mutableSetOf<String>()
        var exported = 0

        for(sourceDirectory in sourceDirectories) {
            val directory = resolveSourceDirectory(sourceDirectory)
            if(!directory.exists() || !directory.isDirectory) {
                plugin.logger.warning("Eco recipe directory not found: ${directory.path}")
                continue
            }

            val files = directory
                .walkTopDown()
                .filter { it.isFile && it.extension.equals("yml", ignoreCase = true) }
                .sortedBy { it.relativeTo(directory).path.lowercase(Locale.ROOT) }

            for(file in files) {
                val candidate = candidateFrom(file) ?: continue
                val recipeId = uniqueId(baseRecipeId(file, candidate.outputLookup), usedIds)
                val path = "recipes.$recipeId"

                config.set("$path.enabled", true)
                config.set("$path.input", candidate.inputLookup)
                config.set("$path.input-amount", candidate.inputAmount)
                config.set("$path.output", candidate.outputLookup)
                config.set("$path.output-amount", candidate.outputAmount)
                exported++
            }
        }

        config.save(outputFile)
        plugin.logger.info("Exported $exported eco recipe file recipe(s) to ${outputFile.name}.")
        return exported
    }

    private fun candidateFrom(file: File): EcoRecipeCandidate? {
        val config = YamlConfiguration.loadConfiguration(file)
        return candidateFromEcoItemFile(file, config)
            ?: candidateFromEcoRecipeFile(file, config)
    }

    private fun candidateFromEcoItemFile(file: File, config: YamlConfiguration): EcoRecipeCandidate? {
        if(!config.getBoolean("item.craftable", false)) {
            return null
        }

        val recipeRaw = config.getStringList("item.recipe")
        if(recipeRaw.isEmpty()) {
            return null
        }

        val outputLookup = "ecoitems:${file.nameWithoutExtension}"
        if(!isValidLookup(outputLookup)) {
            plugin.logger.warning("Skipping eco item file '${file.name}': invalid output lookup '$outputLookup'.")
            return null
        }

        val ingredients = recipeRaw
            .mapNotNull { parseLookupAmount(it) }
            .filter { it.lookup.isNotBlank() }

        if(ingredients.isEmpty()) {
            return null
        }

        val inputLookup = ingredients.first().lookup
        if(ingredients.any { it.lookup != inputLookup }) {
            return null
        }

        if(!isValidLookup(inputLookup)) {
            plugin.logger.warning("Skipping eco item file '${file.name}': invalid input lookup '$inputLookup'.")
            return null
        }

        val inputAmount = ingredients.sumOf { it.amount }
        if(inputAmount <= 1) {
            return null
        }

        return EcoRecipeCandidate(
            inputLookup = inputLookup,
            inputAmount = inputAmount,
            outputLookup = outputLookup,
            outputAmount = 1,
        )
    }

    private fun candidateFromEcoRecipeFile(file: File, config: YamlConfiguration): EcoRecipeCandidate? {
        val resultRaw = config.getString("result") ?: return null
        val recipeRaw = config.getStringList("recipe")

        if(recipeRaw.isEmpty()) {
            return null
        }

        val output = parseLookupAmount(resultRaw) ?: return null
        if(output.amount != 1) {
            return null
        }

        if(!isValidLookup(output.lookup)) {
            plugin.logger.warning("Skipping eco recipe file '${file.name}': invalid result lookup '${output.lookup}'.")
            return null
        }

        val ingredients = recipeRaw
            .mapNotNull { parseLookupAmount(it) }
            .filter { it.lookup.isNotBlank() }

        if(ingredients.isEmpty()) {
            return null
        }

        val inputLookup = ingredients.first().lookup
        if(ingredients.any { it.lookup != inputLookup }) {
            return null
        }

        if(!isValidLookup(inputLookup)) {
            plugin.logger.warning("Skipping eco recipe file '${file.name}': invalid input lookup '$inputLookup'.")
            return null
        }

        val inputAmount = ingredients.sumOf { it.amount }
        if(inputAmount <= 1 || output.amount <= 0) {
            return null
        }

        return EcoRecipeCandidate(
            inputLookup = inputLookup,
            inputAmount = inputAmount,
            outputLookup = output.lookup,
            outputAmount = output.amount,
        )
    }

    private fun parseLookupAmount(raw: String): LookupAmount? {
        val trimmed = raw.trim()
        if(trimmed.isBlank()) {
            return null
        }

        val lastSpace = trimmed.lastIndexOf(' ')
        if(lastSpace <= 0) {
            return LookupAmount(trimmed, 1)
        }

        val maybeAmount = trimmed.substring(lastSpace + 1).toIntOrNull()
        if(maybeAmount == null || maybeAmount <= 0) {
            return LookupAmount(trimmed, 1)
        }

        val lookup = trimmed.substring(0, lastSpace).trim()
        if(lookup.isBlank()) {
            return null
        }

        return LookupAmount(lookup, maybeAmount)
    }

    private fun isValidLookup(lookup: String): Boolean {
        return try {
            Items.lookup(lookup)
            true
        } catch (e: Exception) {
            false
        }
    }

    private fun resolveSourceDirectory(path: String): File {
        val file = File(path)
        if(file.isAbsolute) {
            return file
        }

        val pluginsDirectory = plugin.dataFolder.parentFile
        return File(pluginsDirectory, path)
    }

    private fun baseRecipeId(file: File, outputLookup: String): String {
        val raw = file.nameWithoutExtension.ifBlank { outputLookup }

        return "eco_file_" + raw
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

    private data class LookupAmount(
        val lookup: String,
        val amount: Int
    )

    private data class EcoRecipeCandidate(
        val inputLookup: String,
        val inputAmount: Int,
        val outputLookup: String,
        val outputAmount: Int
    )
}
