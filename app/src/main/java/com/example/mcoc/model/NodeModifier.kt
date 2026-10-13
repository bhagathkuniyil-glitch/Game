package com.example.mcoc.model

import com.example.mcoc.engine.CombatEngineContext
import com.example.mcoc.engine.CombatLifecycleListener
import com.example.mcoc.engine.DamagePayload
import com.example.mcoc.engine.HitType

/**
 * Node Modifiers for MCOC Quest and Boss Puzzles using Decorator / Observer Pattern.
 */
sealed class NodeModifier(
    val id: String,
    val name: String,
    val summary: String,
    val description: String,
    val iconEmoji: String,
    val badgeColorHex: Long = 0xFFEF4444
) : CombatLifecycleListener {

    /**
     * Biohazard: Striking the defender or being struck has a 100% chance to inflict Bleed,
     * unless champion has immunity or #Robot tag.
     */
    class Biohazard : NodeModifier(
        id = "biohazard",
        name = "Biohazard",
        summary = "Bleed on contact",
        description = "Striking the defender has a 100% chance to inflict a Bleed debuff dealing 20% attack over 5s. Immune champions & #Robots take no damage.",
        iconEmoji = "☣️",
        badgeColorHex = 0xFFDC2626
    ) {
        override fun onAttackLanded(ctx: CombatEngineContext, hitType: HitType, payload: DamagePayload) {
            // When defender is struck, attacker bleeds unless immune
            val playerEntity = ctx.attacker
            if (!playerEntity.champion.isImmuneTo(StatusEffectId.BLEED)) {
                playerEntity.applyStatusEffect(
                    StatusEffectInstance(
                        id = StatusEffectId.BLEED,
                        type = StatusEffectType.DEBUFF,
                        durationSeconds = 5.0f,
                        potency = 0.20f,
                        source = "Biohazard Node"
                    )
                )
                ctx.addBanner("BIOHAZARD BLEED!", 0xFFDC2626)
            }
        }
    }

    /**
     * Bane: An ethereal curse that constantly deals 2% max HP per second.
     * Landing any hit transfers Bane to the opponent!
     */
    class Bane : NodeModifier(
        id = "bane",
        name = "Bane",
        summary = "Curse transfers on hit",
        description = "A curse dealing 2.5% Max HP/sec is active. Whoever is hit transfers Bane to the other fighter!",
        iconEmoji = "☠️",
        badgeColorHex = 0xFF9333EA
    ) {
        var hostIsDefender: Boolean = true
        var timer: Float = 0f

        override fun onFightStart(ctx: CombatEngineContext) {
            hostIsDefender = true
            timer = 0f
            ctx.addBanner("BANE ACTIVE ON DEFENDER!", 0xFF9333EA)
        }

        override fun onAttackLanded(ctx: CombatEngineContext, hitType: HitType, payload: DamagePayload) {
            // Transfer bane
            if (hostIsDefender) {
                // Defender was hit by player -> transfer to player!
                hostIsDefender = false
                ctx.addBanner("BANE TRANSFERRED TO YOU!", 0xFFEF4444)
            } else {
                // Player landed hit on defender -> transfer back to defender!
                hostIsDefender = true
                ctx.addBanner("BANE TRANSFERRED TO BOSS!", 0xFF22C55E)
            }
        }

        override fun onTick(ctx: CombatEngineContext, deltaTime: Float) {
            timer += deltaTime
            if (timer >= 1.0f) {
                timer = 0f
                val target = if (hostIsDefender) ctx.defender else ctx.attacker
                val dmg = target.maxHealth * 0.025f
                target.currentHealth = (target.currentHealth - dmg).coerceAtLeast(1f)
                ctx.addFloatingCombatText("-${dmg.toInt()} BANE", isCrit = false, isPlayer = !hostIsDefender)
            }
        }
    }

    /**
     * Lifecycle: Defender is indestructible (HP clamped to 1) until knocked down with a Heavy or Special attack,
     * which disables the shield for 6 seconds.
     */
    class Lifecycle : NodeModifier(
        id = "lifecycle",
        name = "Lifecycle",
        summary = "Indestructible until Knockdown",
        description = "Defender cannot be defeated while Lifecycle shield is active. Land a HEAVY attack or Special to shatter the shield for 6 seconds!",
        iconEmoji = "🛡️",
        badgeColorHex = 0xFFF59E0B
    ) {
        var shieldActive: Boolean = true
        var disabledTimer: Float = 0f

        override fun onFightStart(ctx: CombatEngineContext) {
            shieldActive = true
            disabledTimer = 0f
            ctx.defender.applyStatusEffect(
                StatusEffectInstance(
                    id = StatusEffectId.ARMOR_UP,
                    type = StatusEffectType.PASSIVE,
                    durationSeconds = 999f,
                    isIndefinite = true,
                    source = "Lifecycle Shield"
                )
            )
        }

        override fun onAttackLanded(ctx: CombatEngineContext, hitType: HitType, payload: DamagePayload) {
            if (hitType == HitType.HEAVY || hitType == HitType.SPECIAL_1 || hitType == HitType.SPECIAL_2 || hitType == HitType.SPECIAL_3) {
                shieldActive = false
                disabledTimer = 6.0f
                ctx.addBanner("LIFECYCLE SHIELD BROKEN! (6s)", 0xFF22C55E)
            }
        }

        override fun onTick(ctx: CombatEngineContext, deltaTime: Float) {
            if (!shieldActive) {
                disabledTimer -= deltaTime
                if (disabledTimer <= 0f) {
                    shieldActive = true
                    ctx.addBanner("LIFECYCLE SHIELD RESTORED!", 0xFFEF4444)
                }
            } else {
                // Enforce indestructible minimum 1 HP
                if (ctx.defender.currentHealth < 1f) {
                    ctx.defender.currentHealth = 1f
                }
            }
        }
    }

    /**
     * Aspect of War: Defender strikes become Unblockable if attacker blocks too long;
     * defender gains Fury when knocked down.
     */
    class AspectOfWar : NodeModifier(
        id = "aspect_of_war",
        name = "Aspect of War",
        summary = "Unblockable & Knockdown Fury",
        description = "Defender gains a +50% Fury Buff when knocked down. Prolonged blocking triggers unblockable attacks.",
        iconEmoji = "⚔️",
        badgeColorHex = 0xFFEF4444
    ) {
        override fun onAttackLanded(ctx: CombatEngineContext, hitType: HitType, payload: DamagePayload) {
            if (hitType == HitType.HEAVY) {
                ctx.defender.applyStatusEffect(
                    StatusEffectInstance(
                        id = StatusEffectId.FURY,
                        type = StatusEffectType.BUFF,
                        durationSeconds = 8.0f,
                        potency = 0.50f,
                        source = "Aspect of War"
                    )
                )
                ctx.addBanner("DEFENDER GAINED FURY!", 0xFFEF4444)
            }
        }
    }

    /**
     * Kinetic Transference: Defender gains +50% extra Power when blocking incoming hits.
     */
    class KineticTransference : NodeModifier(
        id = "kinetic_transference",
        name = "Kinetic Transference",
        summary = "Blocks generate high power",
        description = "Defender absorbs strike energy into kinetic power, gaining +100% additional power when blocking.",
        iconEmoji = "⚡",
        badgeColorHex = 0xFF06B6D4
    ) {
        override fun onBlock(ctx: CombatEngineContext, isParry: Boolean, payload: DamagePayload) {
            payload.powerGainDefender += 15f
        }
    }

    /**
     * Linked Node: Decorator that grants massive stats to the boss unless the connected node is cleared.
     */
    class LinkedNode(
        val linkedFromNodeId: String,
        val bonusHealthPct: Float = 2.0f, // +200% HP
        val bonusAttackPct: Float = 1.0f  // +100% Attack
    ) : NodeModifier(
        id = "linked_node_$linkedFromNodeId",
        name = "Linked: Overclock",
        summary = "Linked node boosts Boss stats",
        description = "Connected to Node $linkedFromNodeId. Grants Boss +${(bonusHealthPct * 100).toInt()}% Health and +${(bonusAttackPct * 100).toInt()}% Attack until cleared!",
        iconEmoji = "🔗",
        badgeColorHex = 0xFFEC4899
    ) {
        var isDeactivated: Boolean = false

        fun deactivate() {
            isDeactivated = true
        }

        override fun onFightStart(ctx: CombatEngineContext) {
            if (!isDeactivated) {
                ctx.defender.maxHealth *= (1.0f + bonusHealthPct)
                ctx.defender.currentHealth = ctx.defender.maxHealth
                ctx.addBanner("LINKED NODE ACTIVE: +${(bonusHealthPct * 100).toInt()}% HP!", 0xFFEC4899)
            }
        }
    }
}
