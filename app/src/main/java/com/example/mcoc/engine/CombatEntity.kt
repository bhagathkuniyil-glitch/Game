package com.example.mcoc.engine

import com.example.mcoc.model.Champion
import com.example.mcoc.model.StatusEffectId
import com.example.mcoc.model.StatusEffectInstance
import com.example.mcoc.model.StatusEffectType
import kotlin.random.Random

class CombatEntity(
    val champion: Champion,
    val isPlayer: Boolean,
    var positionX: Float,
    var facingDirection: Float = if (isPlayer) 1.0f else -1.0f
) {
    val stats = champion.getScaledStats()
    var maxHealth: Float = stats.healthMax
    var currentHealth: Float = maxHealth
    var powerMeter: Float = 0f // 0 to 300 units (3 bars @ 100 each)
    val maxPower: Float = 300f

    var comboCounter: Int = 0
    var comboStep: Int = 0 // 0 to 5 in combo sequence
    val comboHistory = mutableListOf<AttackSubtype>()

    var state: StateInfo = StateInfo(type = StateType.IDLE, totalDuration = 0f)
    var timeSinceBlockStarted: Float = 999f
    var isHoldingBlock: Boolean = false
    var isHeavyCharging: Boolean = false
    var heavyChargeDuration: Float = 0f

    // Dexterity evasion flag for next attack
    var hasDexterityCritBuff: Boolean = false

    // Three-tier Status Effect storage
    val buffList = mutableListOf<StatusEffectInstance>()
    val debuffList = mutableListOf<StatusEffectInstance>()
    val passiveList = mutableListOf<StatusEffectInstance>()

    val isStunned: Boolean get() = state.type == StateType.STUNNED || debuffList.any { it.id == StatusEffectId.STUN }
    val isBlocking: Boolean get() = state.type == StateType.BLOCKING
    val isKnockedDown: Boolean get() = state.type == StateType.KNOCKED_DOWN || state.type == StateType.GET_UP_RECOVERY
    val isUnstoppable: Boolean get() = passiveList.any { it.id == StatusEffectId.UNSTOPPABLE }
    val isUnblockable: Boolean get() = state.isUnblockable || passiveList.any { it.id == StatusEffectId.UNBLOCKABLE }
    val hasIframes: Boolean get() = state.hasIframes || state.type == StateType.KNOCKED_DOWN

    // Effective dynamic stats calculation
    fun getEffectiveAttackRating(): Float {
        var base = stats.attackRating
        // Fury buffs
        val furyBonus = buffList.filter { it.id == StatusEffectId.FURY }
            .sumOf { (it.potency * base).toDouble() }.toFloat()
        return base + furyBonus
    }

    fun getEffectiveArmorRating(): Float {
        var base = stats.armorRating
        val armorBonus = buffList.filter { it.id == StatusEffectId.ARMOR_UP }
            .sumOf { (it.potency * 500f).toDouble() }.toFloat()
        val armorBreakReduction = debuffList.filter { it.id == StatusEffectId.ARMOR_BREAK }
            .sumOf { (it.potency * 500f).toDouble() }.toFloat()
        return (base + armorBonus - armorBreakReduction).coerceAtLeast(0f)
    }

    fun getEffectiveBlockProficiency(): Float {
        // Incinerate disables block proficiency!
        if (debuffList.any { it.id == StatusEffectId.INCINERATE }) return 0f
        return stats.blockProficiency
    }

    fun getEffectiveCritRating(): Float {
        var base = stats.critRating
        val precisionBonus = buffList.filter { it.id == StatusEffectId.PRECISION }
            .sumOf { (it.potency * 400f).toDouble() }.toFloat()
        if (hasDexterityCritBuff) precisionBonus.plus(450f)
        return base + precisionBonus
    }

    fun getEffectiveCritDamageRating(): Float {
        var base = stats.critDamageRating
        val crueltyBonus = buffList.filter { it.id == StatusEffectId.CRUELTY }
            .sumOf { (it.potency * 500f).toDouble() }.toFloat()
        return base + crueltyBonus
    }

    // --- State Transitions ---

    fun transitionTo(newState: StateInfo) {
        state = newState
    }

    fun idle() {
        isHeavyCharging = false
        heavyChargeDuration = 0f
        transitionTo(StateInfo(type = StateType.IDLE, totalDuration = 0f))
    }

    fun startBlock() {
        if (canAct()) {
            isHoldingBlock = true
            timeSinceBlockStarted = 0f
            transitionTo(StateInfo(type = StateType.BLOCKING, totalDuration = 0f))
        }
    }

    fun releaseBlock() {
        isHoldingBlock = false
        timeSinceBlockStarted = 999f
        if (state.type == StateType.BLOCKING) {
            idle()
        }
    }

    fun dashBack(timedDexterity: Boolean = false) {
        if (canAct() && state.type != StateType.DASH_BACK) {
            // Dexterity i-frames for 0.35s
            transitionTo(
                StateInfo(
                    type = StateType.DASH_BACK,
                    totalDuration = 0.40f,
                    hasIframes = timedDexterity
                )
            )
        }
    }

    fun dashForward() {
        if (canAct() && state.type != StateType.DASH_FORWARD) {
            transitionTo(
                StateInfo(
                    type = StateType.DASH_FORWARD,
                    totalDuration = 0.35f,
                    canCancelToAttack = true
                )
            )
        }
    }

    // Combo builder: M -> L -> L -> L -> M (or H)
    fun tryMediumAttack(): Boolean {
        if (!canAct() && !(state.type == StateType.DASH_FORWARD && state.canCancelToAttack)) return false
        val subtype = if (comboStep == 0) AttackSubtype.MEDIUM_1 else AttackSubtype.MEDIUM_2
        transitionTo(
            StateInfo(
                type = StateType.ATTACKING,
                subtype = subtype,
                totalDuration = 0.42f,
                hitboxStart = 0.08f,
                hitboxEnd = 0.25f,
                canCancelToAttack = true
            )
        )
        return true
    }

    fun tryLightAttack(): Boolean {
        if (!canAct() && !(state.type == StateType.ATTACKING && state.canCancelToAttack && state.subtype != AttackSubtype.MEDIUM_2)) return false
        val subtype = when (comboStep) {
            1 -> AttackSubtype.LIGHT_1
            2 -> AttackSubtype.LIGHT_2
            else -> AttackSubtype.LIGHT_3
        }
        transitionTo(
            StateInfo(
                type = StateType.ATTACKING,
                subtype = subtype,
                totalDuration = 0.32f,
                hitboxStart = 0.05f,
                hitboxEnd = 0.18f,
                canCancelToAttack = true
            )
        )
        return true
    }

    fun startHeavyCharge() {
        if (canAct()) {
            isHeavyCharging = true
            heavyChargeDuration = 0f
            transitionTo(
                StateInfo(
                    type = StateType.ATTACKING,
                    subtype = AttackSubtype.HEAVY_CHARGE,
                    totalDuration = 0f
                )
            )
        }
    }

    fun releaseHeavyAttack(): Boolean {
        if (isHeavyCharging && heavyChargeDuration >= 0.25f) {
            isHeavyCharging = false
            transitionTo(
                StateInfo(
                    type = StateType.ATTACKING,
                    subtype = AttackSubtype.HEAVY_RELEASE,
                    totalDuration = 0.55f,
                    hitboxStart = 0.12f,
                    hitboxEnd = 0.32f,
                    isUnblockable = true // Heavy shatters guard!
                )
            )
            return true
        } else {
            isHeavyCharging = false
            idle()
            return false
        }
    }

    fun startSpecial(tier: Int): Boolean {
        val requiredPower = tier * 100f
        if (powerMeter >= requiredPower && canAct()) {
            powerMeter = (powerMeter - requiredPower).coerceAtLeast(0f)
            val subtype = when (tier) {
                1 -> AttackSubtype.SP1
                2 -> AttackSubtype.SP2
                else -> AttackSubtype.SP3
            }
            val duration = if (tier == 3) 2.5f else 0.85f
            transitionTo(
                StateInfo(
                    type = StateType.SPECIAL_ATTACK,
                    subtype = subtype,
                    totalDuration = duration,
                    hitboxStart = if (tier == 3) 0.0f else 0.20f,
                    hitboxEnd = if (tier == 3) 2.5f else 0.65f,
                    isUnblockable = (tier == 3)
                )
            )
            return true
        }
        return false
    }

    fun takeHit(payload: DamagePayload, knockDown: Boolean = false) {
        if (knockDown) {
            knockDown()
        } else if (!isUnstoppable && !isBlocking) {
            transitionTo(StateInfo(type = StateType.HURT, totalDuration = 0.30f))
        }
    }

    fun stun(duration: Float) {
        isHoldingBlock = false
        isHeavyCharging = false
        transitionTo(StateInfo(type = StateType.STUNNED, totalDuration = duration))
        applyStatusEffect(
            StatusEffectInstance(
                id = StatusEffectId.STUN,
                type = StatusEffectType.DEBUFF,
                durationSeconds = duration,
                source = "Parry / Special"
            )
        )
    }

    fun knockDown() {
        isHoldingBlock = false
        isHeavyCharging = false
        transitionTo(StateInfo(type = StateType.KNOCKED_DOWN, totalDuration = 1.2f, hasIframes = true))
    }

    fun canAct(): Boolean {
        return state.type == StateType.IDLE ||
                state.type == StateType.BLOCKING ||
                (state.type == StateType.DASH_FORWARD && state.canCancelToAttack)
    }

    // --- Status Effect Management ---

    fun applyStatusEffect(effect: StatusEffectInstance) {
        // Check immunities for debuffs
        if (effect.type == StatusEffectType.DEBUFF && champion.isImmuneTo(effect.id)) {
            return
        }
        when (effect.type) {
            StatusEffectType.BUFF -> {
                val existing = buffList.find { it.id == effect.id && !it.isIndefinite }
                if (existing != null) {
                    existing.stacks++
                    existing.timeRemaining = effect.durationSeconds
                } else {
                    buffList.add(effect)
                }
            }
            StatusEffectType.DEBUFF -> {
                val existing = debuffList.find { it.id == effect.id && !it.isIndefinite }
                if (existing != null) {
                    existing.stacks++
                    existing.timeRemaining = effect.durationSeconds
                } else {
                    debuffList.add(effect)
                }
            }
            StatusEffectType.PASSIVE -> {
                passiveList.add(effect)
            }
        }
    }

    fun nullifyBuffs(): Int {
        val count = buffList.size
        buffList.clear()
        return count
    }

    fun purifyDebuffs(): Int {
        val count = debuffList.size
        debuffList.clear()
        return count
    }

    fun addPower(amount: Float) {
        powerMeter = (powerMeter + amount).coerceIn(0f, maxPower)
    }

    fun tick(deltaTime: Float) {
        state.timeInState += deltaTime
        if (state.type == StateType.BLOCKING) {
            timeSinceBlockStarted += deltaTime
        }
        if (isHeavyCharging) {
            heavyChargeDuration += deltaTime
        }

        // State timeout transitions
        if (state.isFinished) {
            when (state.type) {
                StateType.DASH_FORWARD, StateType.DASH_BACK, StateType.ATTACKING, StateType.HURT -> {
                    idle()
                }
                StateType.SPECIAL_ATTACK -> {
                    idle()
                }
                StateType.STUNNED -> {
                    idle()
                }
                StateType.KNOCKED_DOWN -> {
                    transitionTo(StateInfo(type = StateType.GET_UP_RECOVERY, totalDuration = 0.40f, hasIframes = true))
                }
                StateType.GET_UP_RECOVERY -> {
                    idle()
                }
                else -> {}
            }
        }

        // Tick buffs
        buffList.forEach { it.timeRemaining -= deltaTime }
        buffList.removeAll { it.isExpired }

        // Tick debuffs
        debuffList.forEach { it.timeRemaining -= deltaTime }
        debuffList.removeAll { it.isExpired }

        // Tick passives
        passiveList.forEach { if (!it.isIndefinite) it.timeRemaining -= deltaTime }
        passiveList.removeAll { it.isExpired }
    }
}
