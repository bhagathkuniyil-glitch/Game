package com.example.mcoc.model

enum class TileType {
    START,
    FIGHT,
    EMPTY,
    CHEST,
    BOSS
}

data class QuestTile(
    val id: String,
    val name: String,
    val type: TileType,
    val x: Float, // normalized coordinates 0.0f .. 1.0f for map rendering
    val y: Float,
    val energyCost: Int = 2,
    var isCleared: Boolean = false,
    var isCurrent: Boolean = false,
    var isAccessible: Boolean = false,
    val defender: Champion? = null,
    val modifiers: List<NodeModifier> = emptyList(),
    val linkedDeactivationTargetId: String? = null // if this node is cleared, deactivates linked buff on target
)

data class QuestMap(
    val id: String,
    val name: String,
    val subtitle: String,
    val actNumber: Int,
    val chapterNumber: Int,
    var currentEnergy: Int = 30,
    val maxEnergy: Int = 30,
    val tiles: List<QuestTile>,
    val paths: List<Pair<String, String>>, // (fromId, toId)
    val bossTileId: String,
    var completionPercent: Int = 0
) {
    fun getCurrentTile(): QuestTile? = tiles.find { it.isCurrent }
    fun getTile(id: String): QuestTile? = tiles.find { it.id == id }

    fun canMoveTo(targetTileId: String): Boolean {
        val current = getCurrentTile() ?: return false
        val isDirectConnection = paths.any { (from, to) ->
            (from == current.id && to == targetTileId)
        }
        val targetTile = getTile(targetTileId) ?: return false
        return isDirectConnection && currentEnergy >= targetTile.energyCost
    }

    companion object {
        fun createAct1Chapter1(): QuestMap {
            // Build authentic MCOC branching quest:
            // Node 1: Start (0.1, 0.5)
            // Branch Path A (top): Node 2 (Nimrod) -> Node 3 (Linked Overclock Node - Doctor Doom) -> Node 5 (Boss Hercules)
            // Branch Path B (bottom): Node 4 (Biohazard Archangel) -> Node 5 (Boss Hercules)
            // Boss Hercules has LinkedNode buff from Node 3!
            val linkedOverclock = NodeModifier.LinkedNode(linkedFromNodeId = "node_3", bonusHealthPct = 1.5f, bonusAttackPct = 0.8f)
            val biohazard = NodeModifier.Biohazard()
            val lifecycle = NodeModifier.Lifecycle()

            val tiles = listOf(
                QuestTile(
                    id = "node_1",
                    name = "Portal Gate",
                    type = TileType.START,
                    x = 0.15f,
                    y = 0.5f,
                    energyCost = 0,
                    isCleared = true,
                    isCurrent = true,
                    isAccessible = true
                ),
                QuestTile(
                    id = "node_2",
                    name = "Sentinel Outpost",
                    type = TileType.FIGHT,
                    x = 0.40f,
                    y = 0.25f,
                    energyCost = 2,
                    isAccessible = true,
                    defender = Champion.DEFAULT_ROSTER.find { it.championId == "nimrod_tech" },
                    modifiers = listOf(NodeModifier.KineticTransference())
                ),
                QuestTile(
                    id = "node_3",
                    name = "Mystic Relay",
                    type = TileType.FIGHT,
                    x = 0.65f,
                    y = 0.25f,
                    energyCost = 2,
                    defender = Champion.DEFAULT_ROSTER.find { it.championId == "doom_mystic" },
                    modifiers = listOf(NodeModifier.Bane()),
                    linkedDeactivationTargetId = "node_boss" // Clears Boss's linked buff!
                ),
                QuestTile(
                    id = "node_4",
                    name = "Bio-Toxic Corridor",
                    type = TileType.FIGHT,
                    x = 0.50f,
                    y = 0.75f,
                    energyCost = 3,
                    isAccessible = true,
                    defender = Champion.DEFAULT_ROSTER.find { it.championId == "archangel_mutant" },
                    modifiers = listOf(biohazard)
                ),
                QuestTile(
                    id = "node_boss",
                    name = "Grandmaster's Champion",
                    type = TileType.BOSS,
                    x = 0.88f,
                    y = 0.50f,
                    energyCost = 3,
                    defender = Champion.DEFAULT_ROSTER.find { it.championId == "cgr_cosmic" }?.copy(
                        name = "Grandmaster's Cosmic Executioner",
                        stats = ChampionStats(
                            healthMax = 42000f,
                            attackRating = 3200f,
                            armorRating = 750f,
                            blockProficiency = 2600f,
                            critRating = 650f,
                            critDamageRating = 850f
                        )
                    ),
                    modifiers = listOf(lifecycle, linkedOverclock)
                )
            )

            val paths = listOf(
                "node_1" to "node_2",
                "node_2" to "node_3",
                "node_3" to "node_boss",
                "node_1" to "node_4",
                "node_4" to "node_boss"
            )

            return QuestMap(
                id = "act_1_ch_1",
                name = "Act 1: Battlerealm Crucible",
                subtitle = "Chapter 1: The Branching Gauntlet",
                actNumber = 1,
                chapterNumber = 1,
                tiles = tiles,
                paths = paths,
                bossTileId = "node_boss"
            )
        }
    }
}
