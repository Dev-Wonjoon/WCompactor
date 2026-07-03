package net.wcompactor.config

import com.willfp.eco.core.items.Items
import net.wcompactor.recipe.CompactorRecipe
import net.wcompactor.recipe.CompactorTier
import org.bukkit.Material
import org.bukkit.configuration.file.FileConfiguration
import org.bukkit.configuration.file.YamlConfiguration
import org.bukkit.inventory.ItemStack
import org.bukkit.plugin.java.JavaPlugin
import java.io.File

class CompactorConfig(
    val settings: CompactorSettings,
    val tiers: Map<String, CompactorTier>,
    val recipes: Map<String, CompactorRecipe>,
    val guiTitle: String,
    val guiRows: Int
) {
    companion object {
        fun load(plugin: JavaPlugin): CompactorConfig {
            plugin.reloadConfig()

            val config = plugin.config
            val settings = loadSettings(config)
            val guiTitle = config.getString("gui.title", "Personal Compactor")!!
            val guiRows = config.getInt("gui.rows", 3).coerceIn(1, 6)
            val tiers = loadTiers(config)
            val recipes = loadRecipes(plugin)

            return CompactorConfig(
                settings = settings,
                tiers = tiers,
                recipes = recipes,
                guiTitle = guiTitle,
                guiRows = guiRows
            )
        }

        private fun loadSettings(config: FileConfiguration): CompactorSettings {
            return CompactorSettings(
                scanCooldownTicks = config.getLong("settings.scan-cooldown-ticks", 4L),
                maxCraftsPerScan = config.getInt("settings.max-crafts-per-scan", 16),
                compactOnPickup = config.getBoolean("settings.compact-on-pickup", true),
                compactOnInventoryClick = config.getBoolean("settings.compact-on-inventory-click", true),
                compactOnInventoryDrag = config.getBoolean("settings.compact-on-inventory-drag", true),
                compactOnJoin = config.getBoolean("settings.compact-on-join", true),
                debug = config.getBoolean("settings.debug", false),
                silent = config.getBoolean("settings.silent", true),
                skipWhenOutputDoesNotFit = config.getBoolean("settings.skip-when-output-does-not-fit", true)
            )
        }

        private fun loadTiers(config: FileConfiguration): Map<String, CompactorTier> {
            val tiersSection = config.getConfigurationSection("tiers") ?: return emptyMap()
            val result = linkedMapOf<String, CompactorTier>()

            for(tierId in tiersSection.getKeys(false)) {
                val path = "tiers.$tierId"
                val materialName = config.getString("$path.fallback-material", "DROPPER")!!
                val material = Material.matchMaterial(materialName) ?: Material.DROPPER
                val recipeSlots = config.getIntegerList("gui.recipe-slots.$tierId")

                result[tierId] = CompactorTier(
                    id = tierId,
                    slots = config.getInt("$path.slots", recipeSlots.size).coerceAtLeast(1),
                    recipeSlots = recipeSlots,
                    fallbackMaterial = material,
                    displayName = config.getString("$path.display-name", tierId)!!
                )
            }
            return result
        }

        private fun loadRecipes(plugin: JavaPlugin): Map<String, CompactorRecipe> {
            val result = linkedMapOf<String, CompactorRecipe>()
            val recipesDirectory = File(plugin.dataFolder, "recipes")
            ensureRecipesDirectory(plugin, recipesDirectory)

            val recipeFiles = recipesDirectory
                .listFiles { file -> file.isFile && file.extension.equals("yml", ignoreCase = true) }
                ?.sortedBy { it.nameWithoutExtension.lowercase() }
                ?: emptyList()

            for(recipeFile in recipeFiles) {
                val recipeConfig = YamlConfiguration.loadConfiguration(recipeFile)

                if(recipeConfig.isConfigurationSection("recipes")) {
                    val recipesSection = recipeConfig.getConfigurationSection("recipes") ?: continue
                    for(recipeId in recipesSection.getKeys(false)) {
                        loadRecipe(
                            plugin = plugin,
                            config = recipeConfig,
                            path = "recipes.$recipeId",
                            recipeId = recipeId,
                            result = result
                        )
                    }

                    continue
                }

                val recipeId = recipeConfig.getString("id", recipeFile.nameWithoutExtension)!!
                loadRecipe(
                    plugin = plugin,
                    config = recipeConfig,
                    path = "",
                    recipeId = recipeId,
                    result = result
                )
            }
            return result
        }

        private fun ensureRecipesDirectory(plugin: JavaPlugin, recipesDirectory: File) {
            if(!recipesDirectory.exists()) {
                recipesDirectory.mkdirs()
            }

            val defaultRecipe = File(recipesDirectory, "enchanted_diamond.yml")
            if(!defaultRecipe.exists()) {
                plugin.saveResource("recipes/enchanted_diamond.yml", false)
            }
        }

        private fun loadRecipe(
            plugin: JavaPlugin,
            config: FileConfiguration,
            path: String,
            recipeId: String,
            result: MutableMap<String, CompactorRecipe>
        ) {
            if(!config.getBoolean(path.child("enabled"), true)) {
                return
            }

            val inputLookup = config.getString(path.child("input"))
            val outputLookup = config.getString(path.child("output"))
            val inputAmount = config.getInt(path.child("input-amount"), 0)
            val outputAmount = config.getInt(path.child("output-amount"), 1)

            if(inputLookup.isNullOrBlank() || outputLookup.isNullOrBlank()) {
                plugin.logger.warning("Skipping recipe '$recipeId': input/output is missing.")
                return
            }

            if(inputAmount <= 0 || outputAmount <= 0) {
                plugin.logger.warning("Skipping recipe '$recipeId': amounts must be positive.")
                return
            }

            try {
                val input = Items.lookup(inputLookup)
                val output = Items.lookup(outputLookup)
                val outputTemplate = output.item.clone()
                outputTemplate.amount = outputAmount.coerceAtMost(outputTemplate.maxStackSize)

                result[recipeId] = CompactorRecipe(
                    id = recipeId,
                    input = input,
                    inputAmount = inputAmount,
                    output = output,
                    outputLookup = outputLookup,
                    outputAmount = outputAmount,
                    outputTemplate = outputTemplate,
                )
            } catch (ex: Exception) {
                plugin.logger.warning("Skipping recipe '$recipeId': ${ex.message}")
            }
        }

        private fun String.child(key: String): String {
            return if(isBlank()) key else "$this.$key"
        }
    }
}
