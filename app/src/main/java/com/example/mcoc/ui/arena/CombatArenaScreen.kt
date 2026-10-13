package com.example.mcoc.ui.arena

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
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
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.mcoc.engine.*
import com.example.mcoc.model.*
import kotlin.math.cos
import kotlin.math.sin
import kotlin.random.Random

@Composable
fun CombatArenaScreen(
    engine: CombatEngine,
    onExitFight: () -> Unit,
    onInspectNodes: () -> Unit
) {
    BackHandler {
        onExitFight()
    }

    // Shake offset
    val shakeX = remember(engine.screenShakeAmount) {
        if (engine.screenShakeAmount > 0f) (Random.nextFloat() * 2f - 1f) * engine.screenShakeAmount else 0f
    }
    val shakeY = remember(engine.screenShakeAmount) {
        if (engine.screenShakeAmount > 0f) (Random.nextFloat() * 2f - 1f) * engine.screenShakeAmount else 0f
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .offset(x = shakeX.dp, y = shakeY.dp)
            .background(Color(0xFF070B14))
            .testTag("combat_arena_root")
    ) {
        // 1. Dynamic Arena Background Canvas & Character Model Renderer
        Canvas(modifier = Modifier.fillMaxSize()) {
            drawArenaBackground()
            val floorY = size.height * 0.70f
            val playerScreenX = (size.width / 2f) + (engine.playerEntity.positionX * (size.width / 680f))
            val defenderScreenX = (size.width / 2f) + (engine.defenderEntity.positionX * (size.width / 680f))

            CharacterModelRenderer.drawChampion(this, engine.playerEntity, playerScreenX, floorY, scale = 1.15f)
            CharacterModelRenderer.drawChampion(this, engine.defenderEntity, defenderScreenX, floorY, scale = 1.15f)
            drawFloatingTexts(engine.floatingTexts)
        }

        // 2. Gesture Touch Detection Layer (Split-Screen Vertically 50% with Velocity Analysis)
        val leftVelocityEngine = remember { SwipeVelocityEngine() }
        val rightVelocityEngine = remember { SwipeVelocityEngine() }

        Row(modifier = Modifier.fillMaxSize()) {
            // LEFT ZONE: Defense, Evasion & Spatial Control (0%..50%)
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight()
                    .pointerInput(engine) {
                        awaitPointerEventScope {
                            while (true) {
                                val event = awaitPointerEvent()
                                val change = event.changes.firstOrNull() ?: continue
                                val timeMs = System.currentTimeMillis()

                                if (change.pressed && !change.previousPressed) {
                                    leftVelocityEngine.onTouchDown(change.position, timeMs)
                                } else if (change.pressed) {
                                    val gesture = leftVelocityEngine.onTouchMove(change.position, timeMs)
                                    when (gesture) {
                                        RecognizedGesture.SWIPE_LEFT -> engine.onLeftSwipeLeft()
                                        RecognizedGesture.HOLD_START -> engine.onLeftTouchDown()
                                        else -> {}
                                    }
                                } else if (!change.pressed && change.previousPressed) {
                                    val gesture = leftVelocityEngine.onTouchUp(change.position, timeMs)
                                    when (gesture) {
                                        RecognizedGesture.SWIPE_LEFT -> engine.onLeftSwipeLeft()
                                        RecognizedGesture.HOLD_RELEASE -> engine.onLeftTouchUp()
                                        else -> {
                                            if (engine.playerEntity.isBlocking) {
                                                engine.onLeftTouchUp()
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                    .testTag("left_touch_zone")
            ) {
                // Zone Guide HUD
                Text(
                    text = "DEFENSE ZONE\n[HOLD: BLOCK / PARRY] [SWIPE: EVADE]",
                    color = Color.White.copy(alpha = 0.20f),
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Start,
                    modifier = Modifier
                        .align(Alignment.BottomStart)
                        .padding(start = 24.dp, bottom = 75.dp)
                )
            }

            // RIGHT ZONE: Offense & Heavy Attacks (50%..100%)
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight()
                    .pointerInput(engine) {
                        awaitPointerEventScope {
                            while (true) {
                                val event = awaitPointerEvent()
                                val change = event.changes.firstOrNull() ?: continue
                                val timeMs = System.currentTimeMillis()

                                if (change.pressed && !change.previousPressed) {
                                    rightVelocityEngine.onTouchDown(change.position, timeMs)
                                } else if (change.pressed) {
                                    val gesture = rightVelocityEngine.onTouchMove(change.position, timeMs)
                                    when (gesture) {
                                        RecognizedGesture.SWIPE_RIGHT -> engine.onRightSwipeRight()
                                        RecognizedGesture.HOLD_START -> engine.onRightTouchDown()
                                        else -> {}
                                    }
                                } else if (!change.pressed && change.previousPressed) {
                                    val gesture = rightVelocityEngine.onTouchUp(change.position, timeMs)
                                    when (gesture) {
                                        RecognizedGesture.TAP -> engine.onRightTap()
                                        RecognizedGesture.SWIPE_RIGHT -> engine.onRightSwipeRight()
                                        RecognizedGesture.HOLD_RELEASE -> engine.onRightTouchUp()
                                        else -> {}
                                    }
                                }
                            }
                        }
                    }
                    .testTag("right_touch_zone")
            ) {
                // Zone Guide HUD
                Text(
                    text = "OFFENSE ZONE\n[TAP: LIGHT] [SWIPE: MEDIUM] [HOLD: HEAVY]",
                    color = Color.White.copy(alpha = 0.20f),
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.End,
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(end = 24.dp, bottom = 75.dp)
                )
            }
        }

        // 3. Top Combat HUD (Health bars, Class wheel indicator, Buff/Debuff trays, Combo counter)
        CombatTopHud(
            engine = engine,
            onInspectNodes = onInspectNodes,
            modifier = Modifier
                .align(Alignment.TopCenter)
                .fillMaxWidth()
                .statusBarsPadding()
                .padding(horizontal = 12.dp, vertical = 6.dp)
        )

        // 4. Banner Notifications (Center-Top)
        Box(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .padding(top = 115.dp)
        ) {
            engine.banners.lastOrNull()?.let { banner ->
                Surface(
                    color = Color(banner.colorHex).copy(alpha = 0.90f),
                    shape = RoundedCornerShape(8.dp),
                    shadowElevation = 6.dp,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)
                ) {
                    Text(
                        text = banner.text,
                        color = Color.White,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.ExtraBold,
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp)
                    )
                }
            }
        }

        // 5. Bottom Power Meter & Special Buttons (SP1, SP2, SP3)
        CombatBottomHud(
            engine = engine,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(horizontal = 12.dp, vertical = 8.dp)
        )

        // 6. SP3 Cinematic Lock Overlay
        AnimatedVisibility(
            visible = engine.isSp3CinematicActive,
            enter = fadeIn(),
            exit = fadeOut()
        ) {
            Sp3CinematicOverlay(engine = engine)
        }

        // 7. Victory / Defeat Modal
        if (engine.fightResult != FightResult.ONGOING) {
            FightResultDialog(
                result = engine.fightResult,
                playerChampion = engine.playerEntity.champion,
                onDismiss = onExitFight
            )
        }
    }
}

@Composable
private fun CombatTopHud(
    engine: CombatEngine,
    onInspectNodes: () -> Unit,
    modifier: Modifier = Modifier
) {
    val player = engine.playerEntity
    val defender = engine.defenderEntity

    val playerClassMult = player.champion.championClass.getAttackMultiplier(defender.champion.championClass)
    val defenderClassMult = defender.champion.championClass.getAttackMultiplier(player.champion.championClass)

    Column(modifier = modifier) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Player Health & Class Indicator
            FighterHealthBar(
                entity = player,
                classMult = playerClassMult,
                isPlayer = true,
                modifier = Modifier.weight(1f)
            )

            // Center Node / Info Pill
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.padding(horizontal = 8.dp)
            ) {
                if (engine.activeModifiers.isNotEmpty()) {
                    Surface(
                        color = Color(0xFF1E293B),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.clickable { onInspectNodes() }
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                        ) {
                            Text(
                                text = "NODES (${engine.activeModifiers.size})",
                                color = Color(0xFFFBBF24),
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold
                            )
                            Icon(
                                imageVector = Icons.Default.Info,
                                contentDescription = "Inspect Nodes",
                                tint = Color(0xFFFBBF24),
                                modifier = Modifier.size(12.dp).padding(start = 2.dp)
                            )
                        }
                    }
                }

                // Combo Counter
                if (player.comboCounter > 1) {
                    Text(
                        text = "${player.comboCounter} HITS!",
                        color = Color(0xFFFF9F1C),
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Black,
                        modifier = Modifier.padding(top = 2.dp)
                    )
                }
            }

            // Defender Health & Class Indicator
            FighterHealthBar(
                entity = defender,
                classMult = defenderClassMult,
                isPlayer = false,
                modifier = Modifier.weight(1f)
            )
        }

        // Status Effect Badges (Buffs / Debuffs / Passives)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 4.dp),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            // Player status badges
            StatusEffectTray(entity = player, isPlayer = true)
            // Defender status badges
            StatusEffectTray(entity = defender, isPlayer = false)
        }
    }
}

@Composable
private fun FighterHealthBar(
    entity: CombatEntity,
    classMult: Float,
    isPlayer: Boolean,
    modifier: Modifier = Modifier
) {
    val hpPercent = (entity.currentHealth / entity.maxHealth).coerceIn(0f, 1f)
    val champ = entity.champion

    Column(modifier = modifier) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = if (isPlayer) Arrangement.Start else Arrangement.End,
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (isPlayer) {
                Text(text = champ.avatarEmoji, fontSize = 16.sp, modifier = Modifier.padding(end = 4.dp))
                Text(
                    text = champ.name,
                    color = Color.White,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1
                )
            } else {
                Text(
                    text = champ.name,
                    color = Color.White,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1
                )
                Text(text = champ.avatarEmoji, fontSize = 16.sp, modifier = Modifier.padding(start = 4.dp))
            }
        }

        // Class Advantage Tag
        if (classMult > 1.0f) {
            Text(
                text = "+40% ADVANTAGE",
                color = Color(0xFF22C55E),
                fontSize = 9.sp,
                fontWeight = FontWeight.ExtraBold,
                textAlign = if (isPlayer) TextAlign.Start else TextAlign.End,
                modifier = Modifier.fillMaxWidth()
            )
        } else if (classMult < 1.0f) {
            Text(
                text = "-20% DISADVANTAGE",
                color = Color(0xFFEF4444),
                fontSize = 9.sp,
                fontWeight = FontWeight.ExtraBold,
                textAlign = if (isPlayer) TextAlign.Start else TextAlign.End,
                modifier = Modifier.fillMaxWidth()
            )
        }

        // Health Bar Gauge
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(14.dp)
                .clip(RoundedCornerShape(4.dp))
                .background(Color(0xFF1E293B))
                .border(1.dp, Color(0xFF334155), RoundedCornerShape(4.dp))
        ) {
            val barColor = when {
                hpPercent > 0.5f -> Color(0xFF22C55E)
                hpPercent > 0.25f -> Color(0xFFF59E0B)
                else -> Color(0xFFDC2626)
            }
            Box(
                modifier = Modifier
                    .fillMaxHeight()
                    .fillMaxWidth(hpPercent)
                    .background(
                        Brush.horizontalGradient(
                            listOf(barColor.copy(alpha = 0.8f), barColor)
                        )
                    )
            )

            // Numeric HP
            Text(
                text = "${entity.currentHealth.toInt()} / ${entity.maxHealth.toInt()}",
                color = Color.White,
                fontSize = 9.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.align(Alignment.Center)
            )
        }
    }
}

@Composable
private fun StatusEffectTray(entity: CombatEntity, isPlayer: Boolean) {
    Row(
        horizontalArrangement = if (isPlayer) Arrangement.Start else Arrangement.End,
        modifier = Modifier.padding(horizontal = 2.dp)
    ) {
        // Buffs
        entity.buffList.forEach { buff ->
            StatusBadge(icon = "▲", name = buff.id.defaultName, count = buff.stacks, color = buff.id.color)
        }
        // Debuffs
        entity.debuffList.forEach { debuff ->
            StatusBadge(icon = "▼", name = debuff.id.defaultName, count = debuff.stacks, color = debuff.id.color)
        }
        // Passives
        entity.passiveList.forEach { passive ->
            StatusBadge(icon = "◆", name = passive.id.defaultName, count = passive.stacks, color = passive.id.color)
        }
    }
}

@Composable
private fun StatusBadge(icon: String, name: String, count: Int, color: Color) {
    Surface(
        color = color.copy(alpha = 0.85f),
        shape = RoundedCornerShape(4.dp),
        modifier = Modifier.padding(end = 3.dp)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp)
        ) {
            Text(text = icon, fontSize = 8.sp, color = Color.White)
            Text(
                text = if (count > 1) "$name x$count" else name,
                fontSize = 9.sp,
                fontWeight = FontWeight.Bold,
                color = Color.White,
                modifier = Modifier.padding(start = 2.dp)
            )
        }
    }
}

@Composable
private fun CombatBottomHud(
    engine: CombatEngine,
    modifier: Modifier = Modifier
) {
    val player = engine.playerEntity
    val currentPower = player.powerMeter

    Column(modifier = modifier) {
        // 3-Bar Power Meter (0..100, 100..200, 200..300)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 6.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            // Bar 1 (SP1 threshold: 100)
            PowerMeterSegment(
                fillPercent = (currentPower / 100f).coerceIn(0f, 1f),
                readyColor = Color(0xFF22C55E),
                isFull = currentPower >= 100f,
                modifier = Modifier.weight(1f)
            )
            // Bar 2 (SP2 threshold: 200)
            PowerMeterSegment(
                fillPercent = ((currentPower - 100f) / 100f).coerceIn(0f, 1f),
                readyColor = Color(0xFFF59E0B),
                isFull = currentPower >= 200f,
                modifier = Modifier.weight(1f)
            )
            // Bar 3 (SP3 threshold: 300)
            PowerMeterSegment(
                fillPercent = ((currentPower - 200f) / 100f).coerceIn(0f, 1f),
                readyColor = Color(0xFFEF4444),
                isFull = currentPower >= 300f,
                modifier = Modifier.weight(1f)
            )
        }

        // Special Buttons Row
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            // SP1 Button
            SpecialAttackButton(
                tier = 1,
                name = "SP1",
                isAvailable = currentPower >= 100f,
                color = Color(0xFF22C55E),
                onClick = { engine.onSpecialButton(1) },
                modifier = Modifier.weight(1f).padding(end = 4.dp)
            )

            // SP2 Button
            SpecialAttackButton(
                tier = 2,
                name = "SP2",
                isAvailable = currentPower >= 200f,
                color = Color(0xFFF59E0B),
                onClick = { engine.onSpecialButton(2) },
                modifier = Modifier.weight(1f).padding(horizontal = 2.dp)
            )

            // SP3 Button
            SpecialAttackButton(
                tier = 3,
                name = "SP3 LETHAL",
                isAvailable = currentPower >= 300f,
                color = Color(0xFFEF4444),
                onClick = { engine.onSpecialButton(3) },
                modifier = Modifier.weight(1f).padding(start = 4.dp)
            )
        }
    }
}

@Composable
private fun PowerMeterSegment(
    fillPercent: Float,
    readyColor: Color,
    isFull: Boolean,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .height(8.dp)
            .clip(RoundedCornerShape(3.dp))
            .background(Color(0xFF1E293B))
            .border(
                width = 1.dp,
                color = if (isFull) readyColor else Color(0xFF334155),
                shape = RoundedCornerShape(3.dp)
            )
    ) {
        if (fillPercent > 0f) {
            Box(
                modifier = Modifier
                    .fillMaxHeight()
                    .fillMaxWidth(fillPercent)
                    .background(readyColor)
            )
        }
    }
}

@Composable
private fun SpecialAttackButton(
    tier: Int,
    name: String,
    isAvailable: Boolean,
    color: Color,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Button(
        onClick = onClick,
        enabled = isAvailable,
        colors = ButtonDefaults.buttonColors(
            containerColor = color,
            disabledContainerColor = Color(0xFF1E293B).copy(alpha = 0.6f)
        ),
        shape = RoundedCornerShape(8.dp),
        contentPadding = PaddingValues(vertical = 8.dp),
        modifier = modifier.testTag("special_attack_btn_$tier")
    ) {
        Text(
            text = name,
            fontSize = 12.sp,
            fontWeight = FontWeight.ExtraBold,
            color = if (isAvailable) Color.White else Color.Gray
        )
    }
}

// -------------------------------------------------------------
// Canvas 2D Fighter and Arena Rendering
// -------------------------------------------------------------

private fun DrawScope.drawArenaBackground() {
    val w = size.width
    val h = size.height

    // Deep Cosmic Arena Radial Gradient
    drawRect(
        brush = Brush.radialGradient(
            colors = listOf(Color(0xFF131D33), Color(0xFF090D18), Color(0xFF04060B)),
            center = Offset(w / 2f, h * 0.45f),
            radius = w * 0.8f
        )
    )

    // Arena Floor Perspective Line
    val floorY = h * 0.68f
    drawLine(
        color = Color(0xFF334155),
        start = Offset(0f, floorY),
        end = Offset(w, floorY),
        strokeWidth = 3f
    )

    // Hexagonal Grid / Arena Markings
    val cx = w / 2f
    for (i in -3..3) {
        val lineX = cx + (i * 90f)
        drawLine(
            color = Color(0x1A64748B),
            start = Offset(lineX, floorY),
            end = Offset(cx + (i * 180f), h),
            strokeWidth = 2f
        )
    }

    // Glowing Arena Center Diamond
    drawCircle(
        color = Color(0x1A38BDF8),
        center = Offset(cx, floorY + 40f),
        radius = 70f
    )
}


private fun DrawScope.drawFloatingTexts(texts: List<FloatingText>) {
    val w = size.width
    val h = size.height
    val floorY = h * 0.68f

    // Note: Compose Canvas does not draw formatted Text natively without TextMeasurer,
    // so we render small impact circles / indicators or rely on overlay composables.
    texts.forEach { ft ->
        val screenX = (w / 2f) + (ft.x * (w / 650f))
        val screenY = floorY + ft.y
        val color = if (ft.isCrit) Color(0xFFEF4444) else Color(0xFFFBBF24)
        drawCircle(
            color = color.copy(alpha = ft.alpha * 0.7f),
            radius = if (ft.isCrit) 14f else 8f,
            center = Offset(screenX, screenY)
        )
    }
}

@Composable
private fun Sp3CinematicOverlay(engine: CombatEngine) {
    val attacker = if (engine.sp3AttackerIsPlayer) engine.playerEntity else engine.defenderEntity
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.85f))
    ) {
        Column(
            modifier = Modifier.align(Alignment.Center),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = "SPECIAL 3 CINEMATIC",
                color = Color(0xFFEF4444),
                fontSize = 26.sp,
                fontWeight = FontWeight.Black,
                letterSpacing = 2.sp
            )
            Text(
                text = "${attacker.champion.name.uppercase()} UNLEASHES FULL POWER!",
                color = Color.White,
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(top = 8.dp)
            )
            Text(
                text = "UNBLOCKABLE DIRECT STRIKE",
                color = Color(0xFFFBBF24),
                fontSize = 12.sp,
                fontWeight = FontWeight.Medium,
                modifier = Modifier.padding(top = 4.dp)
            )
        }
    }
}

@Composable
private fun FightResultDialog(
    result: FightResult,
    playerChampion: Champion,
    onDismiss: () -> Unit
) {
    val isWin = result == FightResult.PLAYER_VICTORY
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = if (isWin) "VICTORY!" else "DEFEAT",
                fontWeight = FontWeight.Black,
                color = if (isWin) Color(0xFF22C55E) else Color(0xFFEF4444),
                fontSize = 22.sp
            )
        },
        text = {
            Column {
                Text(
                    text = if (isWin) {
                        "Tile cleared! You demonstrated superior combat mastery and neutralized the node puzzle."
                    } else {
                        "Your champion was knocked out. Review the active node puzzle mechanics and try a different counter."
                    },
                    fontSize = 13.sp,
                    color = Color(0xFFE2E8F0)
                )

                if (isWin) {
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        text = "REWARDS EARNED:",
                        fontWeight = FontWeight.Bold,
                        fontSize = 12.sp,
                        color = Color(0xFFFBBF24)
                    )
                    Text("• +5,000 Gold", fontSize = 12.sp, color = Color.White)
                    Text("• +25 ISO-8 Crystals", fontSize = 12.sp, color = Color.White)
                    Text("• +15 Battlerealm Units", fontSize = 12.sp, color = Color.White)
                }
            }
        },
        confirmButton = {
            Button(
                onClick = onDismiss,
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (isWin) Color(0xFF22C55E) else Color(0xFFEF4444)
                ),
                modifier = Modifier.testTag("fight_result_confirm_btn")
            ) {
                Text(text = if (isWin) "CLAIM REWARDS" else "RETURN TO MAP", fontWeight = FontWeight.Bold)
            }
        }
    )
}
