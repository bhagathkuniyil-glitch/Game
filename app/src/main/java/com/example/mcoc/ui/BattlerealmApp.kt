package com.example.mcoc.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.mcoc.ui.arena.CombatArenaScreen
import com.example.mcoc.ui.mastery.MasteryTreeScreen
import com.example.mcoc.ui.puzzler.NodeInspectorDialog
import com.example.mcoc.ui.quest.QuestMapScreen
import com.example.mcoc.ui.roster.RosterScreen
import com.example.mcoc.viewmodel.AppScreen
import com.example.mcoc.viewmodel.BattlerealmViewModel

@Composable
fun BattlerealmApp(
    viewModel: BattlerealmViewModel = viewModel()
) {
    val uiState by viewModel.uiState.collectAsState()

    Scaffold(
        bottomBar = {
            // Show bottom navigation bar only when NOT in full-screen combat arena
            if (uiState.currentScreen != AppScreen.COMBAT_ARENA) {
                NavigationBar(
                    containerColor = Color(0xFF0F172A),
                    contentColor = Color.White,
                    modifier = Modifier
                        .windowInsetsPadding(WindowInsets.navigationBars)
                        .testTag("main_navigation_bar")
                ) {
                    NavigationBarItem(
                        selected = uiState.currentScreen == AppScreen.QUEST_MAP,
                        onClick = { viewModel.navigateTo(AppScreen.QUEST_MAP) },
                        icon = { Icon(Icons.Default.Explore, contentDescription = "Quest Map") },
                        label = { Text("Quest", fontWeight = FontWeight.Bold, fontSize = 11.sp) },
                        colors = NavigationBarItemDefaults.colors(
                            selectedIconColor = Color(0xFF38BDF8),
                            selectedTextColor = Color(0xFF38BDF8),
                            indicatorColor = Color(0xFF1E293B)
                        ),
                        modifier = Modifier.testTag("nav_quest_map")
                    )

                    NavigationBarItem(
                        selected = uiState.currentScreen == AppScreen.ROSTER,
                        onClick = { viewModel.navigateTo(AppScreen.ROSTER) },
                        icon = { Icon(Icons.Default.Person, contentDescription = "Roster") },
                        label = { Text("Roster", fontWeight = FontWeight.Bold, fontSize = 11.sp) },
                        colors = NavigationBarItemDefaults.colors(
                            selectedIconColor = Color(0xFFFBBF24),
                            selectedTextColor = Color(0xFFFBBF24),
                            indicatorColor = Color(0xFF1E293B)
                        ),
                        modifier = Modifier.testTag("nav_roster")
                    )

                    NavigationBarItem(
                        selected = uiState.currentScreen == AppScreen.MASTERIES,
                        onClick = { viewModel.navigateTo(AppScreen.MASTERIES) },
                        icon = { Icon(Icons.Default.Shield, contentDescription = "Masteries") },
                        label = { Text("Masteries", fontWeight = FontWeight.Bold, fontSize = 11.sp) },
                        colors = NavigationBarItemDefaults.colors(
                            selectedIconColor = Color(0xFFEF4444),
                            selectedTextColor = Color(0xFFEF4444),
                            indicatorColor = Color(0xFF1E293B)
                        ),
                        modifier = Modifier.testTag("nav_masteries")
                    )

                    NavigationBarItem(
                        selected = false,
                        onClick = {
                            // Instant Arena Duel practice
                            val currentTile = uiState.questMap.getCurrentTile() ?: uiState.questMap.tiles[1]
                            viewModel.startFightForTile(currentTile)
                        },
                        icon = { Icon(Icons.Default.Bolt, contentDescription = "Quick Fight") },
                        label = { Text("Quick Fight", fontWeight = FontWeight.Bold, fontSize = 11.sp) },
                        colors = NavigationBarItemDefaults.colors(
                            unselectedIconColor = Color(0xFFEC4899),
                            unselectedTextColor = Color(0xFFEC4899)
                        ),
                        modifier = Modifier.testTag("nav_quick_fight")
                    )
                }
            }
        },
        containerColor = Color(0xFF090D18),
        modifier = Modifier.fillMaxSize()
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(if (uiState.currentScreen == AppScreen.COMBAT_ARENA) PaddingValues(0.dp) else innerPadding)
        ) {
            when (uiState.currentScreen) {
                AppScreen.QUEST_MAP -> {
                    QuestMapScreen(
                        viewModel = viewModel,
                        onStartFight = { tile -> viewModel.startFightForTile(tile) },
                        onOpenGuide = { tile -> viewModel.openNodeInspector(tile) },
                        onChangeChampion = { viewModel.navigateTo(AppScreen.ROSTER) }
                    )
                }

                AppScreen.COMBAT_ARENA -> {
                    uiState.activeCombatEngine?.let { engine ->
                        CombatArenaScreen(
                            engine = engine,
                            onExitFight = { viewModel.navigateTo(AppScreen.QUEST_MAP) },
                            onInspectNodes = {
                                uiState.currentQuestTile?.let { tile ->
                                    viewModel.openNodeInspector(tile)
                                }
                            }
                        )
                    }
                }

                AppScreen.ROSTER -> {
                    BackHandler { viewModel.navigateTo(AppScreen.QUEST_MAP) }
                    RosterScreen(
                        viewModel = viewModel,
                        onBack = { viewModel.navigateTo(AppScreen.QUEST_MAP) }
                    )
                }

                AppScreen.MASTERIES -> {
                    BackHandler { viewModel.navigateTo(AppScreen.QUEST_MAP) }
                    MasteryTreeScreen(
                        viewModel = viewModel,
                        onBack = { viewModel.navigateTo(AppScreen.QUEST_MAP) }
                    )
                }

                AppScreen.PUZZLE_GUIDE -> {
                    BackHandler { viewModel.navigateTo(AppScreen.QUEST_MAP) }
                }
            }

            // Node Inspector Dialog Overlay
            if (uiState.isInspectingNode) {
                NodeInspectorDialog(
                    tile = uiState.inspectedTile,
                    onDismiss = { viewModel.closeNodeInspector() }
                )
            }
        }
    }
}
