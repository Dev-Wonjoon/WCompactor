package net.wcompactor.item

data class CompactorState(
    val compactorId: String,
    val tierId: String,
    val selectedRecipes: List<String?>,
    val version: Int = 1
)