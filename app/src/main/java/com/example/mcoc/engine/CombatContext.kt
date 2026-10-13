package com.example.mcoc.engine

import com.example.mcoc.model.StatusEffectId
import com.example.mcoc.model.StatusEffectInstance
import com.example.mcoc.model.StatusEffectType

enum class HitType {
    LIGHT,
    MEDIUM,
    HEAVY,
    SPECIAL_1,
    SPECIAL_2,
    SPECIAL_3
}

data class DamagePayload(
    var rawAttack: Float,
    var isCrit: Boolean = false,
    var critMultiplier: Float = 1.5f,
    var isBlocked: Boolean = false,
    var isParry: Boolean = false,
    var isUnblockable: Boolean = false,
    var isEvaded: Boolean = false,
    var finalDamage: Float = 0f,
    var chipDamage: Float = 0f,
    var powerGainAttacker: Float = 15f,
    var powerGainDefender: Float = 9f,
    val pendingEffects: MutableList<StatusEffectInstance> = mutableListOf()
)

interface CombatLifecycleListener {
    fun onFightStart(ctx: CombatEngineContext) {}
    fun onAttackLanded(ctx: CombatEngineContext, hitType: HitType, payload: DamagePayload) {}
    fun onAttackReceived(ctx: CombatEngineContext, hitType: HitType, payload: DamagePayload) {}
    fun onBlock(ctx: CombatEngineContext, isParry: Boolean, payload: DamagePayload) {}
    fun onSpecialActivated(ctx: CombatEngineContext, specialTier: Int) {}
    fun onBuffGained(ctx: CombatEngineContext, buff: StatusEffectInstance) {}
    fun onBuffNullified(ctx: CombatEngineContext, buff: StatusEffectInstance) {}
    fun onDebuffApplied(ctx: CombatEngineContext, debuff: StatusEffectInstance) {}
    fun onDebuffPurified(ctx: CombatEngineContext, debuff: StatusEffectInstance) {}
    fun onTick(ctx: CombatEngineContext, deltaTime: Float) {}
}

/**
 * Context exposed to modifiers, masteries, and AI behaviors.
 */
interface CombatEngineContext {
    val attacker: CombatEntity
    val defender: CombatEntity
    fun addBanner(text: String, colorHex: Long)
    fun addFloatingCombatText(text: String, isCrit: Boolean = false, isPlayer: Boolean = true)
}
