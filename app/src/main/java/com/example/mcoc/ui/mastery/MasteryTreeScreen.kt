package com.example.mcoc.ui.mastery

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.mcoc.model.MasteryBranch
import com.example.mcoc.model.MasteryNode
import com.example.mcoc.viewmodel.BattlerealmViewModel

@Composable
fun MasteryTreeScreen(
    viewModel: BattlerealmViewModel,
    onBack: () -> Unit
) {
    val uiState by viewModel.uiState.collectAsState()
    val masteryTree = uiState.masteryTree
    var selectedBranch by remember { mutableStateOf(MasteryBranch.UTILITY) }

    val filteredNodes = remember(masteryTree.nodes, selectedBranch) {
        masteryTree.nodes.filter { it.branch == selectedBranch }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF090D18))
            .statusBarsPadding()
            .testTag("mastery_tree_screen")
    ) {
        // Top Header
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 10.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text(
                    text = "ACCOUNT MASTERIES",
                    color = Color.White,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Black
                )
                Text(
                    text = "Points Available: ${masteryTree.availablePoints}",
                    color = Color(0xFFFBBF24),
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold
                )
            }

            OutlinedButton(
                onClick = { viewModel.respecMasteries() },
                shape = RoundedCornerShape(6.dp),
                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 2.dp),
                modifier = Modifier.height(30.dp).testTag("respec_masteries_btn")
            ) {
                Text("RESPEC ALL", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = Color(0xFFEF4444))
            }
        }

        // Branch Tab Selector
        TabRow(
            selectedTabIndex = selectedBranch.ordinal,
            containerColor = Color(0xFF0F172A),
            contentColor = Color.White
        ) {
            MasteryBranch.values().forEach { branch ->
                Tab(
                    selected = selectedBranch == branch,
                    onClick = { selectedBranch = branch },
                    text = {
                        Text(
                            text = branch.displayName.uppercase(),
                            fontWeight = FontWeight.Bold,
                            fontSize = 12.sp,
                            color = if (selectedBranch == branch) Color(branch.colorHex) else Color(0xFF94A3B8)
                        )
                    }
                )
            }
        }

        // Info Banner
        Surface(
            color = Color(selectedBranch.colorHex).copy(alpha = 0.15f),
            modifier = Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 8.dp),
            shape = RoundedCornerShape(8.dp)
        ) {
            Text(
                text = when (selectedBranch) {
                    MasteryBranch.UTILITY -> "⚡ Utility Masteries empower core combat mechanics: Parry unlocks frame-exact stuns, while Dexterity enables invulnerability i-frames."
                    MasteryBranch.OFFENSE -> "⚔️ Offense Masteries amplify baseline damage, critical strike probability, and fatal critical damage multipliers."
                    MasteryBranch.DEFENSE -> "🛡️ Defense Masteries bolster block proficiency, minimize chip damage, and enhance continuous health recovery."
                },
                color = Color(0xFFE2E8F0),
                fontSize = 11.sp,
                lineHeight = 15.sp,
                modifier = Modifier.padding(10.dp)
            )
        }

        // Mastery Nodes List
        LazyColumn(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .padding(horizontal = 14.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            items(filteredNodes) { node ->
                MasteryNodeCard(
                    node = node,
                    availablePoints = masteryTree.availablePoints,
                    onRankUp = { viewModel.rankUpMastery(node.id) }
                )
            }
            item {
                Spacer(modifier = Modifier.height(60.dp))
            }
        }
    }
}

@Composable
private fun MasteryNodeCard(
    node: MasteryNode,
    availablePoints: Int,
    onRankUp: () -> Unit
) {
    val isMaxed = node.currentRank >= node.maxRank
    val canRankUp = node.canRankUp(availablePoints)

    Card(
        colors = CardDefaults.cardColors(containerColor = Color(0xFF0F172A)),
        shape = RoundedCornerShape(10.dp),
        modifier = Modifier
            .fillMaxWidth()
            .border(
                width = if (node.isUnlocked) 1.5.dp else 1.dp,
                color = if (node.isUnlocked) Color(node.branch.colorHex) else Color(0xFF1E293B),
                shape = RoundedCornerShape(10.dp)
            )
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(text = node.iconEmoji, fontSize = 22.sp, modifier = Modifier.padding(end = 8.dp))
                    Column {
                        Text(
                            text = node.name,
                            color = Color.White,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Black
                        )
                        Text(
                            text = node.summary,
                            color = Color(0xFF94A3B8),
                            fontSize = 11.sp
                        )
                    }
                }

                Surface(
                    color = if (isMaxed) Color(0xFF059669) else if (node.isUnlocked) Color(node.branch.colorHex) else Color(0xFF334155),
                    shape = RoundedCornerShape(6.dp)
                ) {
                    Text(
                        text = "RANK ${node.currentRank}/${node.maxRank}",
                        color = Color.White,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Black,
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 3.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Current / Next Rank Description
            val currentDesc = if (node.currentRank > 0 && node.currentRank <= node.rankDescriptions.size) {
                node.rankDescriptions[node.currentRank - 1]
            } else {
                "Locked: ${node.rankDescriptions.firstOrNull() ?: ""}"
            }
            Text(
                text = currentDesc,
                color = Color(0xFFCBD5E1),
                fontSize = 10.sp
            )

            Spacer(modifier = Modifier.height(10.dp))

            // Rank Up Button
            Button(
                onClick = onRankUp,
                enabled = canRankUp,
                colors = ButtonDefaults.buttonColors(
                    containerColor = Color(node.branch.colorHex),
                    disabledContainerColor = Color(0xFF1E293B)
                ),
                shape = RoundedCornerShape(6.dp),
                modifier = Modifier.fillMaxWidth().testTag("rank_up_mastery_${node.id}")
            ) {
                Text(
                    text = if (isMaxed) "MAXED OUT" else "RANK UP (-1 POINT)",
                    fontWeight = FontWeight.Bold,
                    fontSize = 11.sp,
                    color = if (canRankUp) Color.White else Color.Gray
                )
            }
        }
    }
}
