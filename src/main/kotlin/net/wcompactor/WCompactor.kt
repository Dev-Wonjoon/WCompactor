package net.wcompactor

import net.wcompactor.command.WCompactorCommand
import net.wcompactor.config.CompactorConfig
import net.wcompactor.gui.CompactorGuiService
import net.wcompactor.item.CompactorItemService
import net.wcompactor.listener.CompactorListener
import net.wcompactor.scan.InventoryCompactor
import org.bukkit.plugin.java.JavaPlugin

class WCompactor : JavaPlugin() {
    lateinit var compactorConfig: CompactorConfig
        private set

    lateinit var compactorItemService: CompactorItemService
        private set
    lateinit var compactorGuiService: CompactorGuiService
        private set
    lateinit var inventoryCompactor: InventoryCompactor
        private set


    override fun onEnable() {
        saveDefaultConfig()
        inventoryCompactor = InventoryCompactor(this)
        compactorConfig = CompactorConfig.load(this)
        compactorItemService = CompactorItemService(this)
        compactorGuiService = CompactorGuiService(this)
        server.pluginManager.registerEvents(CompactorListener(this), this)

        val commandExecutor = WCompactorCommand(this)

        getCommand("wcompactor")?.setExecutor(commandExecutor)
        getCommand("wcompactor")?.tabCompleter = commandExecutor
        getCommand("compactor")?.setExecutor(commandExecutor)
        getCommand("compactor")?.tabCompleter = commandExecutor

        logger.info("Loaded ${compactorConfig.tiers.size} compactor tiers.")
        logger.info("Loaded ${compactorConfig.recipes.size} compactor recipes.")
        logger.info("WCompactor Enabled")
    }

    override fun onDisable() {
        logger.info("WCompactor Disabled")
    }

    fun reloadWCompactor() {
        compactorConfig = CompactorConfig.load(this)
    }

    fun debugLog(message: String) {
        if(compactorConfig.settings.debug) {
            logger.info(message)
        }
    }
}
