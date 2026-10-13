package com.example.mcoc.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.mcoc.engine.*
import com.example.mcoc.model.*
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

enum class AppScreen {
    QUEST_MAP,
    COMBAT_ARENA,
    ROSTER,
    MASTERIES,
    PUZZLE_GUIDE
}

data class BattlerealmUiState(
    val currentScreen: AppScreen = AppScreen.QUEST_MAP,
    val roster: List<Champion> = Champion.DEFAULT_ROSTER,
    val selectedChampionId: String = "cgr_cosmic",
    val questMap: QuestMap = QuestMap.createAct1Chapter1(),
    val selectedTileId: String = "node_2",
    val masteryTree: MasteryTree = MasteryTree(),
    val gold: Int = 45000,
    val iso8: Int = 120,
    val units: Int = 350,
    val activeCombatEngine: CombatEngine? = null,
    val isInspectingNode: Boolean = false,
    val inspectedTile: QuestTile? = null
) {
    val selectedChampion: Champion
        get() = roster.find { it.championId == selectedChampionId } ?: roster.first()

    val currentQuestTile: QuestTile?
        get() = questMap.tiles.find { it.id == selectedTileId }
}

class BattlerealmViewModel : ViewModel() {

    private val _uiState = MutableStateFlow(BattlerealmUiState())
    val uiState: StateFlow<BattlerealmUiState> = _uiState.asStateFlow()

    private var combatLoopJob: Job? = null

    init {
        // Start passive energy regeneration
        viewModelScope.launch {
            while (isActive) {
                delay(30_000) // 30s per 1 energy point
                _uiState.update { state ->
                    val map = state.questMap
                    val newEnergy = (map.currentEnergy + 1).coerceAtMost(map.maxEnergy)
                    state.copy(questMap = map.copy(currentEnergy = newEnergy))
                }
            }
        }
    }

    fun navigateTo(screen: AppScreen) {
        if (screen != AppScreen.COMBAT_ARENA && _uiState.value.currentScreen == AppScreen.COMBAT_ARENA) {
            stopCombatLoop()
        }
        _uiState.update { it.copy(currentScreen = screen) }
    }

    fun selectChampion(championId: String) {
        _uiState.update { it.copy(selectedChampionId = championId) }
    }

    fun selectQuestTile(tileId: String) {
        val tile = _uiState.value.questMap.getTile(tileId)
        _uiState.update { it.copy(selectedTileId = tileId, inspectedTile = tile) }
    }

    fun openNodeInspector(tile: QuestTile) {
        _uiState.update { it.copy(isInspectingNode = true, inspectedTile = tile) }
    }

    fun closeNodeInspector() {
        _uiState.update { it.copy(isInspectingNode = false) }
    }

    fun moveToTile(targetTileId: String): Boolean {
        val state = _uiState.value
        val map = state.questMap
        if (!map.canMoveTo(targetTileId)) return false

        val targetTile = map.getTile(targetTileId) ?: return false
        val newEnergy = map.currentEnergy - targetTile.energyCost

        val updatedTiles = map.tiles.map { tile ->
            when (tile.id) {
                targetTileId -> tile.copy(isCurrent = true, isAccessible = true)
                map.getCurrentTile()?.id -> tile.copy(isCurrent = false)
                else -> tile
            }
        }

        // Update accessibility of adjacent nodes
        val adjacentTargetIds = map.paths.filter { it.first == targetTileId }.map { it.second }.toSet()
        val finalTiles = updatedTiles.map { tile ->
            if (adjacentTargetIds.contains(tile.id)) {
                tile.copy(isAccessible = true)
            } else {
                tile
            }
        }

        val updatedMap = map.copy(
            currentEnergy = newEnergy,
            tiles = finalTiles
        )

        _uiState.update {
            it.copy(
                questMap = updatedMap,
                selectedTileId = targetTileId,
                inspectedTile = targetTile
            )
        }
        return true
    }

    fun startFightForTile(tile: QuestTile) {
        val defender = tile.defender ?: return
        val playerChamp = _uiState.value.selectedChampion

        val playerEntity = CombatEntity(
            champion = playerChamp,
            isPlayer = true,
            positionX = -130f,
            facingDirection = 1.0f
        )
        val defenderEntity = CombatEntity(
            champion = defender,
            isPlayer = false,
            positionX = 130f,
            facingDirection = -1.0f
        )

        val engine = CombatEngine(
            playerEntity = playerEntity,
            defenderEntity = defenderEntity,
            activeModifiers = tile.modifiers,
            masteries = _uiState.value.masteryTree
        )

        _uiState.update {
            it.copy(
                activeCombatEngine = engine,
                currentScreen = AppScreen.COMBAT_ARENA
            )
        }

        startCombatLoop(engine)
    }

    private fun startCombatLoop(engine: CombatEngine) {
        combatLoopJob?.cancel()
        combatLoopJob = viewModelScope.launch {
            var lastTime = System.nanoTime()
            while (isActive && engine.fightResult == FightResult.ONGOING) {
                delay(16) // ~60 FPS
                val now = System.nanoTime()
                val delta = ((now - lastTime) / 1_000_000_000f).coerceIn(0.010f, 0.033f)
                lastTime = now

                engine.tick(delta)

                // Trigger recomposition state
                _uiState.update { it.copy() }
            }

            // Fight ended
            if (engine.fightResult != FightResult.ONGOING) {
                onFightFinished(engine.fightResult)
            }
        }
    }

    private fun stopCombatLoop() {
        combatLoopJob?.cancel()
        combatLoopJob = null
    }

    private fun onFightFinished(result: FightResult) {
        if (result == FightResult.PLAYER_VICTORY) {
            val state = _uiState.value
            val currentTile = state.questMap.getTile(state.selectedTileId) ?: return

            // If tile has a linked deactivation target, deactivate that modifier!
            currentTile.linkedDeactivationTargetId?.let { targetId ->
                val targetTile = state.questMap.getTile(targetId)
                targetTile?.modifiers?.filterIsInstance<NodeModifier.LinkedNode>()?.forEach {
                    it.deactivate()
                }
            }

            // Mark tile cleared
            val updatedTiles = state.questMap.tiles.map {
                if (it.id == currentTile.id) it.copy(isCleared = true) else it
            }

            val clearedCount = updatedTiles.count { it.isCleared }
            val completion = ((clearedCount.toFloat() / updatedTiles.size) * 100).toInt()
            val updatedMap = state.questMap.copy(tiles = updatedTiles, completionPercent = completion)

            _uiState.update {
                it.copy(
                    gold = it.gold + 5000,
                    iso8 = it.iso8 + 25,
                    units = it.units + 15,
                    questMap = updatedMap
                )
            }
        }
    }

    // Level up champion
    fun levelUpChampion(championId: String) {
        val state = _uiState.value
        val champ = state.roster.find { it.championId == championId } ?: return
        if (champ.level < champ.maxLevel && state.gold >= 1500 && state.iso8 >= 10) {
            val updated = champ.copy(level = champ.level + 1)
            val updatedRoster = state.roster.map { if (it.championId == championId) updated else it }
            _uiState.update {
                it.copy(
                    roster = updatedRoster,
                    gold = it.gold - 1500,
                    iso8 = it.iso8 - 10
                )
            }
        }
    }

    fun rankUpChampion(championId: String) {
        val state = _uiState.value
        val champ = state.roster.find { it.championId == championId } ?: return
        if (champ.rank < 5 && champ.level == champ.maxLevel && state.gold >= 10000) {
            val updated = champ.copy(rank = champ.rank + 1, level = 1, maxLevel = champ.maxLevel + 10)
            val updatedRoster = state.roster.map { if (it.championId == championId) updated else it }
            _uiState.update {
                it.copy(
                    roster = updatedRoster,
                    gold = it.gold - 10000
                )
            }
        }
    }

    // Mastery allocation
    fun rankUpMastery(nodeId: String) {
        val tree = _uiState.value.masteryTree
        if (tree.rankUp(nodeId)) {
            _uiState.update { it.copy(masteryTree = tree.copy()) }
        }
    }

    fun respecMasteries() {
        val tree = _uiState.value.masteryTree
        tree.respec()
        _uiState.update { it.copy(masteryTree = tree.copy()) }
    }

    fun refillEnergy() {
        val state = _uiState.value
        if (state.units >= 30) {
            val updatedMap = state.questMap.copy(currentEnergy = state.questMap.maxEnergy)
            _uiState.update {
                it.copy(
                    units = it.units - 30,
                    questMap = updatedMap
                )
            }
        }
    }

    override fun onCleared() {
        super.onCleared()
        stopCombatLoop()
    }
}
