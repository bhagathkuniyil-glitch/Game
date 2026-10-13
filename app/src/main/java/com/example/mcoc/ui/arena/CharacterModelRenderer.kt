package com.example.mcoc.ui.arena

import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import com.example.mcoc.engine.AttackSubtype
import com.example.mcoc.engine.CombatEntity
import com.example.mcoc.engine.StateType
import kotlin.math.cos
import kotlin.math.sin

/**
 * CharacterModelRenderer:
 * Renders distinct 2D anatomical champion models in Compose Canvas.
 * Each champion features unique gear, armor geometry, facial aesthetics,
 * weapons, and special energy particles driven by combat FSM states.
 */
object CharacterModelRenderer {

    fun drawChampion(
        scope: DrawScope,
        entity: CombatEntity,
        screenX: Float,
        baseY: Float,
        scale: Float = 1.0f
    ) {
        with(scope) {
            val facing = entity.facingDirection
            val state = entity.state
            val champId = entity.champion.championId
            val time = state.timeInState

            // Ground Shadow
            drawOval(
                color = Color(0x66000000),
                topLeft = Offset(screenX - (38f * scale), baseY - (8f * scale)),
                size = Size(76f * scale, 16f * scale)
            )

            // Knocked Down State: Flat on ground
            if (state.type == StateType.KNOCKED_DOWN || state.type == StateType.GET_UP_RECOVERY) {
                drawKnockedDownPose(entity, screenX, baseY, scale)
                return
            }

            // Kinematic Pose Calculations
            val idleBob = if (state.type == StateType.IDLE) sin(time * 5f) * 4f * scale else 0f
            val leanAngle = when (state.type) {
                StateType.DASH_FORWARD -> facing * 18f
                StateType.DASH_BACK -> -facing * 16f
                StateType.HURT -> -facing * 14f
                else -> 0f
            }

            val hipY = baseY - (60f * scale) + idleBob
            val hipX = screenX + leanAngle
            val chestY = hipY - (30f * scale)
            val headY = chestY - (30f * scale)

            // 1. Draw Character Wings / Back Accents (e.g. Archangel Razor Wings)
            if (champId == "archangel_mutant") {
                drawArchangelWings(hipX, chestY, facing, scale, time)
            }

            // 2. Draw Legs & Feet
            drawLegs(entity, screenX, baseY, hipX, hipY, facing, scale)

            // 3. Draw Torso / Armor Body
            drawTorso(entity, hipX, hipY, chestY, facing, scale)

            // 4. Draw Champion-Specific Head & Faceplate/Mask
            drawHead(entity, hipX + (facing * 4f * scale), headY, facing, scale, time)

            // 5. Draw Arms & Weapon Strikes
            drawArmsAndWeapons(entity, hipX, chestY, facing, scale, time)

            // 6. Special Visual FX (Stun Stars, Heavy Aura, Forcefield Barrier)
            drawStatusAndCombatVfx(entity, screenX, headY, chestY, scale, time)
        }
    }

    private fun DrawScope.drawLegs(
        entity: CombatEntity,
        screenX: Float,
        baseY: Float,
        hipX: Float,
        hipY: Float,
        facing: Float,
        scale: Float
    ) {
        val state = entity.state.type
        val legColor = when (entity.champion.championId) {
            "cgr_cosmic" -> Color(0xFF1E293B)     // Biker leather pants
            "doom_mystic" -> Color(0xFF0F3820)    // Dark green royal tunic
            "nimrod_tech" -> Color(0xFFE2E8F0)    // White sentinel plating
            "archangel_mutant" -> Color(0xFF1E3A8A) // Cobalt blue suit
            "nick_fury_skill" -> Color(0xFF1E293B) // Tactical cargo
            "human_torch_science" -> Color(0xFFEA580C) // Fiery orange
            else -> Color(0xFF1E293B)
        }

        val stride = if (state == StateType.DASH_FORWARD || state == StateType.ATTACKING) 24f * scale else 14f * scale

        // Back Leg
        drawLine(
            color = legColor.copy(alpha = 0.8f),
            start = Offset(hipX - (facing * 8f * scale), hipY),
            end = Offset(screenX - (facing * stride), baseY),
            strokeWidth = 10f * scale,
            cap = StrokeCap.Round
        )

        // Front Leg
        drawLine(
            color = legColor,
            start = Offset(hipX + (facing * 8f * scale), hipY),
            end = Offset(screenX + (facing * (stride * 0.8f)), baseY),
            strokeWidth = 11f * scale,
            cap = StrokeCap.Round
        )
    }

    private fun DrawScope.drawTorso(
        entity: CombatEntity,
        hipX: Float,
        hipY: Float,
        chestY: Float,
        facing: Float,
        scale: Float
    ) {
        val champId = entity.champion.championId
        val classColor = entity.champion.championClass.color

        when (champId) {
            "cgr_cosmic" -> {
                // Black Biker Jacket with Spiked Shoulders & Cyan Core
                drawRoundRect(
                    color = Color(0xFF0F172A),
                    topLeft = Offset(hipX - (16f * scale), chestY - (10f * scale)),
                    size = Size(32f * scale, 38f * scale),
                    cornerRadius = CornerRadius(6f * scale, 6f * scale)
                )
                // Cosmic core star
                drawCircle(
                    color = Color(0xFF06B6D4),
                    radius = 6f * scale,
                    center = Offset(hipX + (facing * 2f), chestY + (6f * scale))
                )
            }
            "doom_mystic" -> {
                // Green Cloak with Golden Clasps & Titanium Chest
                drawRoundRect(
                    color = Color(0xFF14532D),
                    topLeft = Offset(hipX - (18f * scale), chestY - (10f * scale)),
                    size = Size(36f * scale, 40f * scale),
                    cornerRadius = CornerRadius(6f * scale, 6f * scale)
                )
                // Gold Medallion Clasps
                drawCircle(color = Color(0xFFFBBF24), radius = 4f * scale, center = Offset(hipX - (8f * scale), chestY))
                drawCircle(color = Color(0xFFFBBF24), radius = 4f * scale, center = Offset(hipX + (8f * scale), chestY))
                // Titanium plate center
                drawRect(
                    color = Color(0xFF94A3B8),
                    topLeft = Offset(hipX - (6f * scale), chestY + (4f * scale)),
                    size = Size(12f * scale, 18f * scale)
                )
            }
            "nimrod_tech" -> {
                // White Sentinel Torso with Magenta Energy Ribs
                drawRoundRect(
                    color = Color(0xFFF8FAFC),
                    topLeft = Offset(hipX - (20f * scale), chestY - (12f * scale)),
                    size = Size(40f * scale, 42f * scale),
                    cornerRadius = CornerRadius(8f * scale, 8f * scale)
                )
                // Magenta Power Cores
                drawRoundRect(
                    color = Color(0xFFEC4899),
                    topLeft = Offset(hipX - (12f * scale), chestY),
                    size = Size(24f * scale, 14f * scale),
                    cornerRadius = CornerRadius(4f * scale, 4f * scale)
                )
            }
            "human_torch_science" -> {
                // Incandescent Blazing Flame Body
                drawRoundRect(
                    color = Color(0xFFF97316),
                    topLeft = Offset(hipX - (16f * scale), chestY - (10f * scale)),
                    size = Size(32f * scale, 38f * scale),
                    cornerRadius = CornerRadius(10f * scale, 10f * scale)
                )
                drawCircle(
                    color = Color(0xFFFACC15),
                    radius = 9f * scale,
                    center = Offset(hipX, chestY + (8f * scale))
                )
            }
            else -> {
                // Default armored torso with Class Color Accent
                drawRoundRect(
                    color = Color(0xFF1E293B),
                    topLeft = Offset(hipX - (16f * scale), chestY - (10f * scale)),
                    size = Size(32f * scale, 38f * scale),
                    cornerRadius = CornerRadius(6f * scale, 6f * scale)
                )
                drawCircle(
                    color = classColor,
                    radius = 7f * scale,
                    center = Offset(hipX, chestY + (8f * scale))
                )
            }
        }
    }

    private fun DrawScope.drawHead(
        entity: CombatEntity,
        headX: Float,
        headY: Float,
        facing: Float,
        scale: Float,
        time: Float
    ) {
        val champId = entity.champion.championId

        when (champId) {
            "cgr_cosmic" -> {
                // Demonic Flaming Skull: Cosmic Hellfire Halo + Bone Skull
                for (i in 0..4) {
                    val flamePulse = sin(time * 12f + i) * 6f * scale
                    drawCircle(
                        color = Color(0xFF06B6D4).copy(alpha = 0.65f),
                        radius = (14f * scale) + flamePulse,
                        center = Offset(headX + (i * 3f) - 6f, headY - (6f * scale))
                    )
                }
                // White Skull
                drawRoundRect(
                    color = Color(0xFFF1F5F9),
                    topLeft = Offset(headX - (10f * scale), headY - (12f * scale)),
                    size = Size(20f * scale, 24f * scale),
                    cornerRadius = CornerRadius(5f * scale, 5f * scale)
                )
                // Glowing Blue Eye Sockets
                drawCircle(color = Color(0xFF06B6D4), radius = 2.5f * scale, center = Offset(headX + (facing * 4f * scale), headY - (2f * scale)))
            }
            "doom_mystic" -> {
                // Green Hood Cowl
                drawCircle(
                    color = Color(0xFF15803D),
                    radius = 16f * scale,
                    center = Offset(headX, headY)
                )
                // Silver Titanium Faceplate Mask
                drawRoundRect(
                    color = Color(0xFFCBD5E1),
                    topLeft = Offset(headX - (8f * scale), headY - (6f * scale)),
                    size = Size(16f * scale, 18f * scale),
                    cornerRadius = CornerRadius(4f * scale, 4f * scale)
                )
                // Dark Eye Slits
                drawRect(color = Color(0xFF0F172A), topLeft = Offset(headX + (facing * 2f), headY - (1f * scale)), size = Size(5f * scale, 2f * scale))
            }
            "nimrod_tech" -> {
                // Sleek White Sentinel Helmet with Ruby Optic Slit
                drawRoundRect(
                    color = Color(0xFFF8FAFC),
                    topLeft = Offset(headX - (12f * scale), headY - (12f * scale)),
                    size = Size(24f * scale, 24f * scale),
                    cornerRadius = CornerRadius(6f * scale, 6f * scale)
                )
                // Red Laser Visor
                drawRoundRect(
                    color = Color(0xFFEF4444),
                    topLeft = Offset(headX - (8f * scale) + (facing * 4f), headY - (3f * scale)),
                    size = Size(14f * scale, 5f * scale),
                    cornerRadius = CornerRadius(2f * scale, 2f * scale)
                )
            }
            "human_torch_science" -> {
                // Blazing Plasma Head with Yellow Flame Corona
                drawCircle(
                    color = Color(0xFFEA580C),
                    radius = 17f * scale,
                    center = Offset(headX, headY)
                )
                drawCircle(
                    color = Color(0xFFFACC15),
                    radius = 11f * scale,
                    center = Offset(headX, headY)
                )
            }
            else -> {
                // Standard Superhero Head
                drawCircle(
                    color = Color(0xFFE2E8F0),
                    radius = 13f * scale,
                    center = Offset(headX, headY)
                )
            }
        }
    }

    private fun DrawScope.drawArchangelWings(
        hipX: Float,
        chestY: Float,
        facing: Float,
        scale: Float,
        time: Float
    ) {
        val wingFlap = sin(time * 6f) * 8f * scale
        val wingRoot = Offset(hipX - (facing * 12f * scale), chestY)

        // Upper Segmented Metallic Blades
        for (i in 0..3) {
            val spreadX = (i * 26f * scale) + wingFlap
            val spreadY = (i * 18f * scale)
            drawLine(
                color = Color(0xFF38BDF8),
                start = wingRoot,
                end = Offset(wingRoot.x - (facing * spreadX), wingRoot.y - 45f * scale + spreadY),
                strokeWidth = 6f * scale,
                cap = StrokeCap.Round
            )
            // Golden razor blade tip
            drawCircle(
                color = Color(0xFFFBBF24),
                radius = 3.5f * scale,
                center = Offset(wingRoot.x - (facing * spreadX), wingRoot.y - 45f * scale + spreadY)
            )
        }
    }

    private fun DrawScope.drawArmsAndWeapons(
        entity: CombatEntity,
        hipX: Float,
        chestY: Float,
        facing: Float,
        scale: Float,
        time: Float
    ) {
        val state = entity.state
        val champId = entity.champion.championId
        val shoulder = Offset(hipX + (facing * 8f * scale), chestY)

        when (state.type) {
            StateType.BLOCKING -> {
                // Crossed Guard Forearms
                drawLine(
                    color = Color(0xFF94A3B8),
                    start = shoulder,
                    end = Offset(shoulder.x + (facing * 16f * scale), chestY - (4f * scale)),
                    strokeWidth = 10f * scale,
                    cap = StrokeCap.Round
                )
                drawLine(
                    color = Color(0xFFCBD5E1),
                    start = Offset(shoulder.x - (facing * 6f * scale), chestY + (6f * scale)),
                    end = Offset(shoulder.x + (facing * 16f * scale), chestY + (8f * scale)),
                    strokeWidth = 10f * scale,
                    cap = StrokeCap.Round
                )
            }
            StateType.ATTACKING, StateType.SPECIAL_ATTACK -> {
                val reach = when (state.subtype) {
                    AttackSubtype.MEDIUM_1, AttackSubtype.MEDIUM_2 -> 55f * scale
                    AttackSubtype.HEAVY_RELEASE -> 48f * scale
                    AttackSubtype.SP1, AttackSubtype.SP2, AttackSubtype.SP3 -> 65f * scale
                    else -> 38f * scale
                }

                val strikeEnd = Offset(shoulder.x + (facing * reach), chestY - (6f * scale))

                // Extended Strike Arm
                drawLine(
                    color = Color(0xFFE2E8F0),
                    start = shoulder,
                    end = strikeEnd,
                    strokeWidth = 11f * scale,
                    cap = StrokeCap.Round
                )

                // Weapon / Attack Visuals
                when (champId) {
                    "cgr_cosmic" -> {
                        // Cosmic Chain / Blazing Scythe Arc
                        drawArc(
                            color = Color(0xFF06B6D4),
                            startAngle = if (facing > 0) -60f else 120f,
                            sweepAngle = 110f,
                            useCenter = false,
                            topLeft = Offset(strikeEnd.x - (25f * scale), strikeEnd.y - (25f * scale)),
                            size = Size(50f * scale, 50f * scale),
                            style = Stroke(width = 6f * scale, cap = StrokeCap.Round)
                        )
                    }
                    "doom_mystic" -> {
                        // Arcane Green Energy Disc
                        drawCircle(
                            color = Color(0xFF22C55E).copy(alpha = 0.8f),
                            radius = 18f * scale,
                            center = strikeEnd
                        )
                        drawCircle(
                            color = Color(0xFF86EFAC),
                            radius = 10f * scale,
                            center = strikeEnd
                        )
                    }
                    "human_torch_science" -> {
                        // Fiery Incinerating Blast
                        drawCircle(
                            color = Color(0xFFEA580C),
                            radius = 22f * scale,
                            center = strikeEnd
                        )
                        drawCircle(
                            color = Color(0xFFFDE047),
                            radius = 12f * scale,
                            center = strikeEnd
                        )
                    }
                    else -> {
                        // High-Speed Combat Trail
                        drawArc(
                            color = if (state.isUnblockable) Color(0xFFEF4444) else Color(0xFFFBBF24),
                            startAngle = if (facing > 0) -45f else 135f,
                            sweepAngle = 90f,
                            useCenter = false,
                            topLeft = Offset(strikeEnd.x - (20f * scale), strikeEnd.y - (20f * scale)),
                            size = Size(40f * scale, 40f * scale),
                            style = Stroke(width = 5f * scale, cap = StrokeCap.Round)
                        )
                    }
                }
            }
            else -> {
                // Default Idle Arm Stance
                drawLine(
                    color = Color(0xFF94A3B8),
                    start = shoulder,
                    end = Offset(shoulder.x + (facing * 16f * scale), chestY + (10f * scale)),
                    strokeWidth = 9f * scale,
                    cap = StrokeCap.Round
                )
            }
        }
    }

    private fun DrawScope.drawStatusAndCombatVfx(
        entity: CombatEntity,
        screenX: Float,
        headY: Float,
        chestY: Float,
        scale: Float,
        time: Float
    ) {
        // Hexagonal Forcefield Barrier when Blocking
        if (entity.isBlocking) {
            val barrierCenter = Offset(screenX + (entity.facingDirection * 15f * scale), chestY)
            drawCircle(
                color = Color(0x3338BDF8),
                radius = 48f * scale,
                center = barrierCenter
            )
            drawCircle(
                color = Color(0xFF38BDF8),
                radius = 48f * scale,
                center = barrierCenter,
                style = Stroke(width = 2.5f * scale)
            )
        }

        // Heavy Attack Charge Windup Aura
        if (entity.isHeavyCharging) {
            val pulse = (sin(entity.heavyChargeDuration * 14f) + 1f) * 0.5f
            drawCircle(
                color = Color(0x55EF4444),
                radius = (40f * scale) + (pulse * 18f * scale),
                center = Offset(screenX, chestY)
            )
        }

        // Orbiting Stun Stars
        if (entity.isStunned) {
            val starSpeed = time * 7f
            for (i in 0..2) {
                val angle = starSpeed + (i * 2.094f)
                val starX = screenX + (cos(angle) * 26f * scale)
                val starY = headY - (14f * scale) + (sin(angle) * 8f * scale)
                drawCircle(color = Color(0xFFFBBF24), radius = 5f * scale, center = Offset(starX, starY))
            }
        }
    }

    private fun DrawScope.drawKnockedDownPose(
        entity: CombatEntity,
        screenX: Float,
        baseY: Float,
        scale: Float
    ) {
        val classColor = entity.champion.championClass.color
        drawRoundRect(
            color = classColor.copy(alpha = 0.85f),
            topLeft = Offset(screenX - (36f * scale), baseY - (14f * scale)),
            size = Size(72f * scale, 14f * scale),
            cornerRadius = CornerRadius(6f * scale, 6f * scale)
        )
        // Impact dust rings
        drawOval(
            color = Color(0x44CBD5E1),
            topLeft = Offset(screenX - (45f * scale), baseY - (18f * scale)),
            size = Size(90f * scale, 12f * scale),
            style = Stroke(width = 2f)
        )
    }
}
