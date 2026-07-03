package net.wcompactor.recipe

import org.bukkit.Material

data class CompactorTier(
    val id: String,
    val slots: Int,
    val recipeSlots: List<Int>,
    val fallbackMaterial: Material,
    val displayName: String
)