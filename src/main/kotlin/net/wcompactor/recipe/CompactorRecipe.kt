package net.wcompactor.recipe

import com.willfp.eco.core.items.TestableItem
import org.bukkit.inventory.ItemStack

data class CompactorRecipe(
    val id: String,
    val input: TestableItem,
    val inputAmount: Int,
    val output: TestableItem,
    val outputLookup: String,
    val outputAmount: Int,
    var outputTemplate: ItemStack
)
