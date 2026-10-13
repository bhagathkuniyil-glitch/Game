package com.example.mcoc.ui.quest

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.mcoc.model.*
import com.example.mcoc.viewmodel.BattlerealmViewModel

@Composable
fun QuestMapScreen(
    viewModel: BattlerealmViewModel,
    onStartFight: (QuestTile) -> Unit,
    onOpenGuide: (QuestTile) -> Unit,
    onChangeChampion: () -> Unit
) {
    val uiState by viewModel.uiState.collectAsState()
    val questMap = uiState.questMap
    val currentTile = questMap.getCurrentTile()
    val selectedTile = uiState.inspectedTile ?: currentTile

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF090D18))
            .statusBarsPadding()
            .testTag("quest_map_screen")
    ) {
        // 1. Top Header: Act & Chapter Title, Resources, Energy Bar
        QuestTopHeader(
            questMap = questMap,
            gold = uiState.gold,
            iso8 = uiState.iso8,
            units = uiState.units,
            onRefillEnergy = { viewModel.refillEnergy() }
        )

        // 2. Active Selected Champion Banner with Switch Button
        ActiveChampionBar(
            champion = uiState.selectedChampion,
            onChangeChampion = onChangeChampion
        )

        // 3. Quest Map Interactive Graph Area
        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 6.dp)
        ) {
            // Draw Map Network and Connection Lines
            Canvas(modifier = Modifier.fillMaxSize()) {
                val w = size.width
                val h = size.height

                // 1. Draw Paths between tiles
                questMap.paths.forEach { (fromId, toId) ->
                    val fromTile = questMap.getTile(fromId)
                    val toTile = questMap.getTile(toId)
                    if (fromTile != null && toTile != null) {
                        val p1 = Offset(fromTile.x * w, fromTile.y * h)
                        val p2 = Offset(toTile.x * w, toTile.y * h)
                        val isPathActive = fromTile.isCleared || fromTile.isCurrent

                        drawLine(
                            color = if (isPathActive) Color(0xFF38BDF8) else Color(0xFF334155),
                            start = p1,
                            end = p2,
                            strokeWidth = if (isPathActive) 5f else 3f,
                            cap = StrokeCap.Round
                        )
                    }
                }

                // 2. Draw Linked Node Laser Beam to Boss
                val linkedSourceTile = questMap.tiles.find { it.linkedDeactivationTargetId != null }
                val bossTile = questMap.tiles.find { it.type == TileType.BOSS }
                if (linkedSourceTile != null && bossTile != null && !linkedSourceTile.isCleared) {
                    val pSource = Offset(linkedSourceTile.x * w, linkedSourceTile.y * h)
                    val pBoss = Offset(bossTile.x * w, bossTile.y * h)

                    drawLine(
                        color = Color(0xFFEC4899),
                        start = pSource,
                        end = pBoss,
                        strokeWidth = 3f,
                        pathEffect = PathEffect.dashPathEffect(floatArrayOf(15f, 15f), 0f)
                    )
                }
            }

            // Render Tiles as Interactive Node Badges
            BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
                val w = maxWidth
                val h = maxHeight

                questMap.tiles.forEach { tile ->
                    val isSelected = selectedTile?.id == tile.id
                    val isBoss = tile.type == TileType.BOSS

                    Box(
                        modifier = Modifier
                            .offset(
                                x = (w * tile.x) - 28.dp,
                                y = (h * tile.y) - 28.dp
                            )
                            .size(56.dp)
                            .clip(CircleShape)
                            .background(
                                when {
                                    tile.isCurrent -> Color(0xFFF59E0B)
                                    tile.isCleared -> Color(0xFF059669)
                                    isBoss -> Color(0xFFDC2626)
                                    tile.isAccessible -> Color(0xFF2563EB)
                                    else -> Color(0xFF1E293B)
                                }
                            )
                            .border(
                                width = if (isSelected) 3.dp else 1.5.dp,
                                color = if (isSelected) Color.White else Color(0xFF94A3B8),
                                shape = CircleShape
                            )
                            .clickable {
                                viewModel.selectQuestTile(tile.id)
                            }
                            .testTag("quest_tile_${tile.id}"),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            when {
                                tile.isCleared -> Icon(
                                    imageVector = Icons.Default.Check,
                                    contentDescription = "Cleared",
                                    tint = Color.White,
                                    modifier = Modifier.size(20.dp)
                                )
                                isBoss -> Text("👑", fontSize = 18.sp)
                                tile.defender != null -> Text(tile.defender.avatarEmoji, fontSize = 16.sp)
                                else -> Icon(
                                    imageVector = Icons.Default.PlayArrow,
                                    contentDescription = "Start",
                                    tint = Color.White,
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                        }
                    }
                }
            }
        }

        // 4. Selected Tile Inspection Card (Bottom)
        selectedTile?.let { tile ->
            TileInspectionCard(
                tile = tile,
                playerChampion = uiState.selectedChampion,
                currentEnergy = questMap.currentEnergy,
                onStartFight = { onStartFight(tile) },
                onMoveToTile = { viewModel.moveToTile(tile.id) },
                onOpenGuide = { onOpenGuide(tile) }
            )
        }
    }
}

@Composable
private fun QuestTopHeader(
    questMap: QuestMap,
    gold: Int,
    iso8: Int,
    units: Int,
    onRefillEnergy: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(Color(0xFF0F172A))
            .padding(horizontal = 14.dp, vertical = 8.dp)
    ) {
        // Quest Title & Currencies
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text(
                    text = questMap.name,
                    color = Color(0xFFFBBF24),
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Black
                )
                Text(
                    text = "${questMap.subtitle} (${questMap.completionPercent}% Explored)",
                    color = Color(0xFF94A3B8),
                    fontSize = 11.sp
                )
            }

            // Currency row
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                CurrencyBadge(icon = "🪙", amount = gold)
                CurrencyBadge(icon = "💎", amount = iso8)
                CurrencyBadge(icon = "💠", amount = units)
            }
        }

        Spacer(modifier = Modifier.height(6.dp))

        // Energy Bar Row
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "⚡ Energy: ${questMap.currentEnergy}/${questMap.maxEnergy}",
                color = Color(0xFF38BDF8),
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(end = 8.dp)
            )

            val energyRatio = (questMap.currentEnergy.toFloat() / questMap.maxEnergy).coerceIn(0f, 1f)
            Box(
                modifier = Modifier
                    .weight(1f)
                    .height(8.dp)
                    .clip(RoundedCornerShape(4.dp))
                    .background(Color(0xFF1E293B))
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxHeight()
                        .fillMaxWidth(energyRatio)
                        .background(Color(0xFF38BDF8))
                )
            }

            TextButton(
                onClick = onRefillEnergy,
                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp),
                modifier = Modifier.height(28.dp).padding(start = 6.dp)
            ) {
                Text("+ REFILL", color = Color(0xFFFBBF24), fontSize = 10.sp, fontWeight = FontWeight.Bold)
            }
        }
    }
}

@Composable
private fun CurrencyBadge(icon: String, amount: Int) {
    Surface(
        color = Color(0xFF1E293B),
        shape = RoundedCornerShape(6.dp)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
        ) {
            Text(text = icon, fontSize = 11.sp)
            Text(
                text = "$amount",
                color = Color.White,
                fontSize = 10.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(start = 3.dp)
            )
        }
    }
}

@Composable
private fun ActiveChampionBar(
    champion: Champion,
    onChangeChampion: () -> Unit
) {
    Surface(
        color = Color(0xFF131D33),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(text = champion.avatarEmoji, fontSize = 20.sp, modifier = Modifier.padding(end = 8.dp))
                Column {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = champion.name,
                            color = Color.White,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Surface(
                            color = champion.championClass.color.copy(alpha = 0.2f),
                            shape = RoundedCornerShape(4.dp),
                            modifier = Modifier.padding(start = 6.dp)
                        ) {
                            Text(
                                text = champion.championClass.displayName,
                                color = champion.championClass.color,
                                fontSize = 9.sp,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp)
                            )
                        }
                    }
                    Text(
                        text = "PI: ${champion.getScaledStats().calculatePowerIndex()} • Rank ${champion.rank} Lv ${champion.level}",
                        color = Color(0xFF94A3B8),
                        fontSize = 10.sp
                    )
                }
            }

            OutlinedButton(
                onClick = onChangeChampion,
                shape = RoundedCornerShape(6.dp),
                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 2.dp),
                modifier = Modifier.height(28.dp).testTag("change_champion_btn")
            ) {
                Text("SWAP", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = Color(0xFF38BDF8))
            }
        }
    }
}

@Composable
private fun TileInspectionCard(
    tile: QuestTile,
    playerChampion: Champion,
    currentEnergy: Int,
    onStartFight: () -> Unit,
    onMoveToTile: () -> Unit,
    onOpenGuide: () -> Unit
) {
    Card(
        colors = CardDefaults.cardColors(containerColor = Color(0xFF0F172A)),
        shape = RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp),
        modifier = Modifier
            .fillMaxWidth()
            .border(1.dp, Color(0xFF334155), RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp))
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            // Node Name & Type
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = tile.name,
                        color = Color.White,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Black
                    )
                    Text(
                        text = "Cost: ${tile.energyCost} Energy • ${if (tile.isCleared) "CLEARED" else "ACTIVE"}",
                        color = if (tile.isCleared) Color(0xFF22C55E) else Color(0xFF94A3B8),
                        fontSize = 11.sp
                    )
                }

                if (tile.modifiers.isNotEmpty()) {
                    TextButton(onClick = onOpenGuide) {
                        Text(
                            text = "PUZZLE GUIDE",
                            color = Color(0xFFFBBF24),
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }

            // Defender Info if Fight / Boss
            tile.defender?.let { defender ->
                Spacer(modifier = Modifier.height(8.dp))
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(Color(0xFF1E293B), RoundedCornerShape(8.dp))
                        .padding(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(text = defender.avatarEmoji, fontSize = 24.sp, modifier = Modifier.padding(end = 8.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = defender.name,
                            color = Color.White,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "Class: ${defender.championClass.displayName} • HP: ${defender.getScaledStats().healthMax.toInt()}",
                            color = Color(0xFF94A3B8),
                            fontSize = 10.sp
                        )
                    }

                    // Class Matchup Pill
                    val mult = playerChampion.championClass.getAttackMultiplier(defender.championClass)
                    Surface(
                        color = if (mult > 1.0f) Color(0xFF059669) else if (mult < 1.0f) Color(0xFFDC2626) else Color(0xFF475569),
                        shape = RoundedCornerShape(4.dp)
                    ) {
                        Text(
                            text = if (mult > 1.0f) "+40% ADV" else if (mult < 1.0f) "-20% DIS" else "NEUTRAL",
                            color = Color.White,
                            fontSize = 9.sp,
                            fontWeight = FontWeight.ExtraBold,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                        )
                    }
                }
            }

            // Active Node Modifiers List
            if (tile.modifiers.isNotEmpty()) {
                Spacer(modifier = Modifier.height(6.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    tile.modifiers.forEach { mod ->
                        Surface(
                            color = Color(mod.badgeColorHex).copy(alpha = 0.25f),
                            shape = RoundedCornerShape(6.dp),
                            border = androidx.compose.foundation.BorderStroke(1.dp, Color(mod.badgeColorHex))
                        ) {
                            Text(
                                text = "${mod.iconEmoji} ${mod.name}",
                                color = Color(mod.badgeColorHex),
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                            )
                        }
                    }
                }
            }

            // Linked Node Warning if Boss
            if (tile.type == TileType.BOSS) {
                val hasActiveLinked = tile.modifiers.filterIsInstance<NodeModifier.LinkedNode>().any { !it.isDeactivated }
                if (hasActiveLinked) {
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = "⚠️ WARNING: Linked Node Active! Clear Node 3 first to shatter Boss's Overclock shield!",
                        color = Color(0xFFEC4899),
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Action Button
            when {
                tile.isCurrent && tile.defender != null && !tile.isCleared -> {
                    Button(
                        onClick = onStartFight,
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFEF4444)),
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.fillMaxWidth().testTag("enter_fight_btn")
                    ) {
                        Text(
                            text = "FIGHT DEFENDER",
                            fontWeight = FontWeight.Black,
                            fontSize = 13.sp
                        )
                    }
                }
                tile.isAccessible && !tile.isCurrent -> {
                    Button(
                        onClick = onMoveToTile,
                        enabled = currentEnergy >= tile.energyCost,
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2563EB)),
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.fillMaxWidth().testTag("move_to_tile_btn")
                    ) {
                        Text(
                            text = "MOVE HERE (${tile.energyCost} Energy)",
                            fontWeight = FontWeight.Bold,
                            fontSize = 13.sp
                        )
                    }
                }
                tile.isCleared -> {
                    OutlinedButton(
                        onClick = onStartFight,
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("REPLAY FIGHT (PRACTICE)", color = Color(0xFF38BDF8), fontWeight = FontWeight.Bold)
                    }
                }
                else -> {
                    Text(
                        text = "Path locked. Move along connected nodes to unlock.",
                        color = Color.Gray,
                        fontSize = 11.sp,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }
        }
    }
}
