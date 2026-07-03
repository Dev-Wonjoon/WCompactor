package net.wcompactor.gui

import org.bukkit.inventory.Inventory
import org.bukkit.inventory.InventoryHolder

class CompactorGuiHolder(
    val compactorId: String,
    val tierId: String
) : InventoryHolder {
    lateinit var backingInventory: Inventory

    override fun getInventory(): Inventory {
        return backingInventory
    }
}