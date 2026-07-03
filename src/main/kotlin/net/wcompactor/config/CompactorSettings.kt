package net.wcompactor.config

data class CompactorSettings(
    val scanCooldownTicks: Long,
    val maxCraftsPerScan: Int,
    val compactOnPickup: Boolean,
    val compactOnInventoryClick: Boolean,
    val compactOnInventoryDrag: Boolean,
    val compactOnJoin: Boolean,
    val debug: Boolean,
    val silent: Boolean,
    val skipWhenOutputDoesNotFit: Boolean
)
