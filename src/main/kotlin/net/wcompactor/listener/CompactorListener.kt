package net.wcompactor.listener

import net.wcompactor.WCompactor
import net.wcompactor.gui.CompactorGuiHolder
import org.bukkit.entity.Player
import org.bukkit.event.EventHandler
import org.bukkit.event.Listener
import org.bukkit.event.block.Action
import org.bukkit.event.entity.EntityPickupItemEvent
import org.bukkit.event.inventory.ClickType
import org.bukkit.event.inventory.InventoryClickEvent
import org.bukkit.event.inventory.InventoryDragEvent
import org.bukkit.event.player.PlayerInteractEvent
import org.bukkit.event.player.PlayerJoinEvent
import java.util.UUID

class CompactorListener(
    private val plugin: WCompactor
) : Listener {
    private val pendingScans = mutableSetOf<UUID>()
    private val lastScanAt = mutableMapOf<UUID, Long>()

    @EventHandler
    fun onInteract(event: PlayerInteractEvent) {
        val action = event.action
        if(action != Action.RIGHT_CLICK_AIR && action != Action.RIGHT_CLICK_BLOCK) {
            return
        }

        val item = event.item ?: return
        val state = plugin.compactorItemService.readState(item) ?: return

        event.isCancelled = true
        plugin.compactorGuiService.open(event.player, state)
    }

    @EventHandler
    fun onInventoryClick(event: InventoryClickEvent) {
        val player = event.whoClicked as? Player ?: return
        val holder = event.view.topInventory.holder as? CompactorGuiHolder

        if(holder == null) {
            if(plugin.compactorConfig.settings.compactOnInventoryClick) {
                requestScan(player)
            }
            return
        }

        if(event.click == ClickType.NUMBER_KEY || event.isShiftClick) {
            event.isCancelled = true
            return
        }

        if(event.rawSlot >= event.view.topInventory.size) {
            return
        }

        event.isCancelled = true

        plugin.debugLog(
            "GUI click: player=${player.name}, rawSlot=${event.rawSlot}, " +
                    "topSize=${event.view.topInventory.size}, cursor=${event.cursor?.type}, current=${event.currentItem?.type}"
        )

        val slotIndex = plugin.compactorGuiService.slotIndexForGuiSlot(holder.tierId, event.rawSlot)
        if (slotIndex == null) {
            plugin.debugLog("GUI register stopped: rawSlot ${event.rawSlot} is not a compactor recipe slot.")
            return
        }

        val compactor = plugin.compactorItemService.findCompactorById(player.inventory, holder.compactorId)
        if (compactor == null) {
            plugin.debugLog("GUI register stopped: compactor item not found. id=${holder.compactorId}")
            player.closeInventory()
            return
        }

        val cursor = event.cursor
        val recipeId = plugin.compactorGuiService.findRecipeIdBySample(cursor)

        plugin.debugLog(
            "GUI register attempt: slotIndex=$slotIndex, cursor=${cursor?.type}, matchedRecipe=$recipeId"
        )

        if (cursor == null || cursor.type.isAir) {
            plugin.compactorItemService.withSelectedRecipe(compactor, slotIndex, null)
            plugin.debugLog("GUI register: cleared slotIndex=$slotIndex")
        } else if (recipeId != null) {
            plugin.compactorItemService.withSelectedRecipe(compactor, slotIndex, recipeId)
            plugin.debugLog("GUI register: saved recipe=$recipeId at slotIndex=$slotIndex")
        } else {
            plugin.debugLog("GUI register stopped: cursor item did not match any recipe output.")
            return
        }

        val newState = plugin.compactorItemService.readState(compactor) ?: return
        plugin.debugLog("GUI register result: selected=${newState.selectedRecipes}")
        plugin.compactorGuiService.open(player, newState)
    }

    @EventHandler
    fun onInventoryDrag(event: InventoryDragEvent) {
        val holder = event.view.topInventory.holder as? CompactorGuiHolder

        if(holder == null) {
            val player = event.whoClicked as? Player ?: return
            if(plugin.compactorConfig.settings.compactOnInventoryDrag) {
                requestScan(player)
            }
            return
        }

        val topSize = holder.inventory.size

        if (event.rawSlots.any { it < topSize }) {
            event.isCancelled = true
        }
    }

    @EventHandler
    fun onPickup(event: EntityPickupItemEvent) {
        val player = event.entity as? Player ?: return
        if(plugin.compactorConfig.settings.compactOnPickup) {
            requestScan(player)
        }
    }

    @EventHandler
    fun onJoin(event: PlayerJoinEvent) {
        if(plugin.compactorConfig.settings.compactOnJoin) {
            requestScan(event.player, 20L)
        }
    }

    private fun requestScan(player: Player, delayTicks: Long = 1L) {
        val playerId = player.uniqueId
        if(!pendingScans.add(playerId)) {
            return
        }

        plugin.server.scheduler.runTaskLater(plugin, Runnable {
            pendingScans.remove(playerId)
            if(!player.isOnline) {
                return@Runnable
            }

            val now = System.currentTimeMillis()
            val cooldownMillis = plugin.compactorConfig.settings.scanCooldownTicks.coerceAtLeast(0L) * 50L
            val lastScan = lastScanAt[playerId] ?: 0L
            if(now - lastScan < cooldownMillis) {
                return@Runnable
            }

            lastScanAt[playerId] = now
            plugin.inventoryCompactor.compact(player)
        }, delayTicks)
    }
}
