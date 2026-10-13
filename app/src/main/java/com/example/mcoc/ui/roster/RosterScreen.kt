package com.example.mcoc.ui.roster

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.mcoc.model.Champion
import com.example.mcoc.model.ChampionClass
import com.example.mcoc.viewmodel.BattlerealmViewModel

@Composable
fun RosterScreen(
    viewModel: BattlerealmViewModel,
    onBack: () -> Unit
) {
    val uiState by viewModel.uiState.collectAsState()
    var selectedClassFilter by remember { mutableStateOf<ChampionClass?>(null) }
    var inspectedChampionId by remember { mutableStateOf(uiState.selectedChampionId) }

    val filteredRoster = remember(uiState.roster, selectedClassFilter) {
        if (selectedClassFilter == null) {
            uiState.roster
        } else {
            uiState.roster.filter { it.championClass == selectedClassFilter }
        }
    }

    val inspectedChampion = uiState.roster.find { it.championId == inspectedChampionId } ?: uiState.selectedChampion

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF090D18))
            .statusBarsPadding()
            .testTag("roster_screen")
    ) {
        // Header
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 10.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text(
                    text = "CHAMPION ROSTER",
                    color = Color.White,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Black
                )
                Text(
                    text = "${uiState.roster.size} Battlerealm Champions • Manage & Upgrade",
                    color = Color(0xFF94A3B8),
                    fontSize = 11.sp
                )
            }

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Surface(
                    color = Color(0xFF1E293B),
                    shape = RoundedCornerShape(6.dp)
                ) {
                    Text(
                        text = "🪙 ${uiState.gold}",
                        color = Color(0xFFFBBF24),
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                    )
                }
                Surface(
                    color = Color(0xFF1E293B),
                    shape = RoundedCornerShape(6.dp)
                ) {
                    Text(
                        text = "💎 ${uiState.iso8}",
                        color = Color(0xFF38BDF8),
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                    )
                }
            }
        }

        // Class Filter Chips
        LazyRow(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            item {
                FilterChip(
                    selected = selectedClassFilter == null,
                    onClick = { selectedClassFilter = null },
                    label = { Text("ALL") }
                )
            }
            items(ChampionClass.values()) { cls ->
                FilterChip(
                    selected = selectedClassFilter == cls,
                    onClick = { selectedClassFilter = cls },
                    label = { Text(cls.displayName) },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = cls.color.copy(alpha = 0.3f),
                        selectedLabelColor = cls.color
                    )
                )
            }
        }

        // Champion Details / Upgrade Card (Top Pane)
        ChampionDetailCard(
            champion = inspectedChampion,
            isActive = inspectedChampion.championId == uiState.selectedChampionId,
            gold = uiState.gold,
            iso8 = uiState.iso8,
            onSelectActive = { viewModel.selectChampion(inspectedChampion.championId) },
            onLevelUp = { viewModel.levelUpChampion(inspectedChampion.championId) },
            onRankUp = { viewModel.rankUpChampion(inspectedChampion.championId) },
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 6.dp)
        )

        // Roster Champion Grid / List (Bottom Pane)
        LazyColumn(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .padding(horizontal = 14.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            items(filteredRoster) { champ ->
                ChampionRowItem(
                    champion = champ,
                    isSelected = champ.championId == inspectedChampion.championId,
                    isActive = champ.championId == uiState.selectedChampionId,
                    onClick = { inspectedChampionId = champ.championId }
                )
            }
            item {
                Spacer(modifier = Modifier.height(60.dp))
            }
        }
    }
}

@Composable
private fun ChampionDetailCard(
    champion: Champion,
    isActive: Boolean,
    gold: Int,
    iso8: Int,
    onSelectActive: () -> Unit,
    onLevelUp: () -> Unit,
    onRankUp: () -> Unit,
    modifier: Modifier = Modifier
) {
    val stats = champion.getScaledStats()
    Card(
        colors = CardDefaults.cardColors(containerColor = Color(0xFF0F172A)),
        shape = RoundedCornerShape(12.dp),
        modifier = modifier.border(1.5.dp, champion.championClass.color.copy(alpha = 0.7f), RoundedCornerShape(12.dp))
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(text = champion.avatarEmoji, fontSize = 28.sp, modifier = Modifier.padding(end = 8.dp))
                    Column {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = champion.name,
                                color = Color.White,
                                fontSize = 15.sp,
                                fontWeight = FontWeight.Black
                            )
                            Surface(
                                color = champion.championClass.color.copy(alpha = 0.25f),
                                shape = RoundedCornerShape(4.dp),
                                modifier = Modifier.padding(start = 6.dp)
                            ) {
                                Text(
                                    text = champion.championClass.displayName,
                                    color = champion.championClass.color,
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier.padding(horizontal = 5.dp, vertical = 2.dp)
                                )
                            }
                        }
                        Text(
                            text = "Power Index (PI): ${stats.calculatePowerIndex()} • Rank ${champion.rank}/5 • Lv ${champion.level}/${champion.maxLevel}",
                            color = Color(0xFFFBBF24),
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }

                if (isActive) {
                    Surface(color = Color(0xFF22C55E), shape = RoundedCornerShape(6.dp)) {
                        Text(
                            text = "ACTIVE",
                            color = Color.White,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Black,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                        )
                    }
                } else {
                    Button(
                        onClick = onSelectActive,
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2563EB)),
                        shape = RoundedCornerShape(6.dp),
                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                        modifier = Modifier.height(28.dp).testTag("select_active_champion_btn")
                    ) {
                        Text("SELECT", fontSize = 10.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Stat Grid
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                StatChip(label = "Health", value = "${stats.healthMax.toInt()}", color = Color(0xFF22C55E))
                StatChip(label = "Attack", value = "${stats.attackRating.toInt()}", color = Color(0xFFEF4444))
                StatChip(label = "Armor", value = "${stats.armorRating.toInt()}", color = Color(0xFF38BDF8))
                StatChip(label = "Block Prof", value = "${stats.blockProficiency.toInt()}", color = Color(0xFFF59E0B))
                StatChip(label = "Crit Rate", value = "${stats.critRating.toInt()}", color = Color(0xFFA855F7))
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Immunities & Tags
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                if (champion.immunities.isNotEmpty() || champion.isRobot) {
                    val imms = if (champion.isRobot) (champion.immunities + listOf("BLEED", "POISON")).distinct() else champion.immunities
                    Surface(color = Color(0xFF334155), shape = RoundedCornerShape(4.dp)) {
                        Text(
                            text = "IMMUNE: ${imms.joinToString(", ")}",
                            color = Color(0xFFFBBF24),
                            fontSize = 9.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                        )
                    }
                }
                champion.tags.take(2).forEach { tag ->
                    Surface(color = Color(0xFF1E293B), shape = RoundedCornerShape(4.dp)) {
                        Text(
                            text = tag,
                            color = Color(0xFF94A3B8),
                            fontSize = 9.sp,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Signature Ability
            Text(
                text = "SIGNATURE: ${champion.signatureAbility.name} (Lv ${champion.signatureAbility.sigLevel})",
                color = Color(0xFF38BDF8),
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = champion.signatureAbility.description,
                color = Color(0xFFCBD5E1),
                fontSize = 10.sp,
                lineHeight = 13.sp
            )

            Spacer(modifier = Modifier.height(10.dp))

            // Upgrade Buttons Row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                // Level Up Button
                val canLevelUp = champion.level < champion.maxLevel && gold >= 1500 && iso8 >= 10
                Button(
                    onClick = onLevelUp,
                    enabled = canLevelUp,
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF059669)),
                    shape = RoundedCornerShape(6.dp),
                    modifier = Modifier.weight(1f).testTag("level_up_champion_btn")
                ) {
                    Text(
                        text = if (champion.level >= champion.maxLevel) "MAX LEVEL" else "LEVEL UP (1.5k Gold, 10 ISO)",
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold
                    )
                }

                // Rank Up Button
                val canRankUp = champion.level >= champion.maxLevel && champion.rank < 5 && gold >= 10000
                Button(
                    onClick = onRankUp,
                    enabled = canRankUp,
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFD97706)),
                    shape = RoundedCornerShape(6.dp),
                    modifier = Modifier.weight(1f).testTag("rank_up_champion_btn")
                ) {
                    Text(
                        text = if (champion.rank >= 5) "MAX RANK" else "RANK UP (10k Gold)",
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }
    }
}

@Composable
private fun StatChip(label: String, value: String, color: Color) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(text = label, color = Color(0xFF94A3B8), fontSize = 9.sp)
        Text(text = value, color = color, fontSize = 11.sp, fontWeight = FontWeight.Bold)
    }
}

@Composable
private fun ChampionRowItem(
    champion: Champion,
    isSelected: Boolean,
    isActive: Boolean,
    onClick: () -> Unit
) {
    val stats = champion.getScaledStats()
    Surface(
        color = if (isSelected) Color(0xFF1E293B) else Color(0xFF0F172A),
        shape = RoundedCornerShape(8.dp),
        border = androidx.compose.foundation.BorderStroke(
            width = if (isSelected) 1.5.dp else 1.dp,
            color = if (isSelected) champion.championClass.color else Color(0xFF1E293B)
        ),
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() }
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(text = champion.avatarEmoji, fontSize = 22.sp, modifier = Modifier.padding(end = 8.dp))
                Column {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = champion.name,
                            color = Color.White,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold
                        )
                        if (isActive) {
                            Text(
                                text = " [ACTIVE]",
                                color = Color(0xFF22C55E),
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Black
                            )
                        }
                    }
                    Text(
                        text = "PI: ${stats.calculatePowerIndex()} • Rank ${champion.rank} Lv ${champion.level}",
                        color = Color(0xFF94A3B8),
                        fontSize = 10.sp
                    )
                }
            }

            Surface(
                color = champion.championClass.color.copy(alpha = 0.2f),
                shape = RoundedCornerShape(4.dp)
            ) {
                Text(
                    text = champion.championClass.displayName,
                    color = champion.championClass.color,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                )
            }
        }
    }
}
