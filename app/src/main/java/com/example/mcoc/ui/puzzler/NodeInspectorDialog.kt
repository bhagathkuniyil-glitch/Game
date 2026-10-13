package com.example.mcoc.ui.puzzler

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.mcoc.model.NodeModifier
import com.example.mcoc.model.QuestTile

@Composable
fun NodeInspectorDialog(
    tile: QuestTile?,
    onDismiss: () -> Unit
) {
    if (tile == null) return

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = "🧩 NODE PUZZLE INTEL",
                    fontWeight = FontWeight.Black,
                    color = Color(0xFFFBBF24),
                    fontSize = 18.sp
                )
            }
        },
        text = {
            LazyColumn(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                item {
                    Text(
                        text = "Tile: ${tile.name}",
                        fontWeight = FontWeight.Bold,
                        color = Color.White,
                        fontSize = 14.sp
                    )
                    Text(
                        text = "Analyze node mechanics to choose the correct counter champion, immunity tags, or path routing.",
                        color = Color(0xFF94A3B8),
                        fontSize = 11.sp
                    )
                }

                if (tile.modifiers.isEmpty()) {
                    item {
                        Text(
                            text = "Standard battle node. No special puzzle modifiers active.",
                            color = Color(0xFFCBD5E1),
                            fontSize = 12.sp
                        )
                    }
                } else {
                    items(tile.modifiers) { mod ->
                        Card(
                            colors = CardDefaults.cardColors(containerColor = Color(0xFF0F172A)),
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .border(1.dp, Color(mod.badgeColorHex), RoundedCornerShape(8.dp))
                        ) {
                            Column(modifier = Modifier.padding(10.dp)) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = "${mod.iconEmoji} ${mod.name}",
                                        color = Color(mod.badgeColorHex),
                                        fontSize = 13.sp,
                                        fontWeight = FontWeight.Black
                                    )
                                    Surface(
                                        color = Color(mod.badgeColorHex).copy(alpha = 0.2f),
                                        shape = RoundedCornerShape(4.dp)
                                    ) {
                                        Text(
                                            text = mod.summary,
                                            color = Color(mod.badgeColorHex),
                                            fontSize = 9.sp,
                                            fontWeight = FontWeight.Bold,
                                            modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp)
                                        )
                                    }
                                }

                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = mod.description,
                                    color = Color(0xFFE2E8F0),
                                    fontSize = 11.sp,
                                    lineHeight = 14.sp
                                )

                                Spacer(modifier = Modifier.height(6.dp))
                                // Strategic Counter Tip
                                Text(
                                    text = when (mod) {
                                        is NodeModifier.Biohazard -> "💡 Counter: Bring a #Robot (e.g. Nimrod) or Bleed-immune champion (e.g. Cosmic Ghost Rider) to take zero damage."
                                        is NodeModifier.Bane -> "💡 Counter: Stay offensive! Pass Bane before the 1-second timer ticks. Do not turtle or hold block."
                                        is NodeModifier.Lifecycle -> "💡 Counter: Land Heavy attacks or Specials to shatter the shield before executing the final blow."
                                        is NodeModifier.LinkedNode -> "💡 Counter: Clear Node 3 on Path A to disable this +150% HP buff on the Boss."
                                        is NodeModifier.AspectOfWar -> "💡 Counter: Evade with Dexterity instead of holding block to prevent unblockable strikes."
                                        is NodeModifier.KineticTransference -> "💡 Counter: Bait heavy attacks or specials; avoid attacking into defender's block."
                                    },
                                    color = Color(0xFFFBBF24),
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Medium
                                )
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = onDismiss,
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2563EB)),
                modifier = Modifier.testTag("dismiss_puzzle_guide_btn")
            ) {
                Text("UNDERSTOOD", fontWeight = FontWeight.Bold)
            }
        }
    )
}
