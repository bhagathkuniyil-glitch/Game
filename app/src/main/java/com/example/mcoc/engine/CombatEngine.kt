package com.example.mcoc.engine

import com.example.mcoc.model.*
import kotlin.math.abs
import kotlin.math.max
import kotlin.random.Random

data class FloatingText(
    val id: Long,
    val text: String,
    val isCrit: Boolean,
    val isPlayer: Boolean,
    var x: Float,
    var y: Float,
    var alpha: Float = 1.0f,
    var elapsed: Float = 0f
)

data class BannerEvent(
    val id: Long,
    val text: String,
    val colorHex: Long,
    var elapsed: Float = 0f
)

enum class FightResult {
    ONGOING,
    PLAYER_VICTORY,
    PLAYER_DEFEAT
}

class CombatEngine(
    val playerEntity: CombatEntity,
    val defenderEntity: CombatEntity,
    val activeModifiers: List<NodeModifier> = emptyList(),
    val masteries: MasteryTree = MasteryTree()
) : CombatEngineContext {

    override val attacker: CombatEntity get() = playerEntity
    override val defender: CombatEntity get() = defenderEntity

    var fightResult: FightResult = FightResult.ONGOING
    var fightTimeElapsed: Float = 0f

    // SP3 Cinematic Lock state
    var isSp3CinematicActive: Boolean = false
    var sp3CinematicTime: Float = 0f
    var sp3AttackerIsPlayer: Boolean = true

    // Visual combat banners & damage floaters
    val floatingTexts = mutableListOf<FloatingText>()
    val banners = mutableListOf<BannerEvent>()
    private var nextTextId = 0L

    // Screen Shake effect
    var screenShakeAmount: Float = 0f

    // AI timing tracker
    private var aiDecisionTimer: Float = 0f
    private var aiHoldBlockTimer: Float = 0f

    // Hit registration protection to avoid multi-hitting single strike frames
    private var playerCurrentAttackHitRegistered: Boolean = false
    private var defenderCurrentAttackHitRegistered: Boolean = false

    init {
        // Broadcast OnFightStart
        activeModifiers.forEach { it.onFightStart(this) }
        masteries.nodes.forEach { it.onFightStart(this) }
    }

    override fun addBanner(text: String, colorHex: Long) {
        banners.add(BannerEvent(id = nextTextId++, text = text, colorHex = colorHex))
    }

    override fun addFloatingCombatText(text: String, isCrit: Boolean, isPlayer: Boolean) {
        val targetX = if (isPlayer) defenderEntity.positionX else playerEntity.positionX
        floatingTexts.add(
            FloatingText(
                id = nextTextId++,
                text = text,
                isCrit = isCrit,
                isPlayer = isPlayer,
                x = targetX + Random.nextInt(-20, 20),
                y = -80f + Random.nextInt(-10, 10)
            )
        )
    }

    // --- Input Dispatchers ---

    fun onLeftTouchDown() {
        if (isSp3CinematicActive) return
        playerEntity.startBlock()
    }

    fun onLeftTouchUp() {
        if (isSp3CinematicActive) return
        playerEntity.releaseBlock()
    }

    fun onLeftSwipeLeft() {
        if (isSp3CinematicActive) return
        // Check Dexterity window: is hostile attack hitbox overlapping hurtbox?
        val hostileHitboxActive = defenderEntity.state.type == StateType.ATTACKING &&
                defenderEntity.state.isHitboxActive()
        val inRange = getFighterDistance() < 180f
        val timedDexterity = hostileHitboxActive && inRange && masteries.hasDexterity()

        playerEntity.dashBack(timedDexterity = timedDexterity)

        if (timedDexterity) {
            playerEntity.hasDexterityCritBuff = true
            playerEntity.applyStatusEffect(
                StatusEffectInstance(
                    id = StatusEffectId.PRECISION,
                    type = StatusEffectType.BUFF,
                    durationSeconds = 5.0f,
                    potency = 0.50f,
                    source = "Dexterity"
                )
            )
            addBanner("DEXTERITY! (I-FRAMES)", 0xFF38BDF8)
            addFloatingCombatText("EVADED!", isCrit = false, isPlayer = true)
        }
    }

    fun onRightTap() {
        if (isSp3CinematicActive) return
        if (playerEntity.comboStep in 1..3) {
            val executed = playerEntity.tryLightAttack()
            if (executed) {
                playerCurrentAttackHitRegistered = false
                playerEntity.comboStep++
            }
        } else if (playerEntity.comboStep == 0) {
            // First hit can also be a light
            val executed = playerEntity.tryLightAttack()
            if (executed) {
                playerCurrentAttackHitRegistered = false
                playerEntity.comboStep = 1
            }
        }
    }

    fun onRightSwipeRight() {
        if (isSp3CinematicActive) return
        // Medium Attack / Dash In
        if (playerEntity.state.type == StateType.IDLE) {
            playerEntity.dashForward()
        }
        val executed = playerEntity.tryMediumAttack()
        if (executed) {
            playerCurrentAttackHitRegistered = false
            if (playerEntity.comboStep == 0) {
                playerEntity.comboStep = 1
            } else if (playerEntity.comboStep >= 4) {
                // Combo finisher!
                playerEntity.comboStep = 5
            }
        }
    }

    fun onRightTouchDown() {
        if (isSp3CinematicActive) return
        playerEntity.startHeavyCharge()
    }

    fun onRightTouchUp() {
        if (isSp3CinematicActive) return
        val executed = playerEntity.releaseHeavyAttack()
        if (executed) {
            playerCurrentAttackHitRegistered = false
            playerEntity.comboStep = 5 // Heavy ends combo
        }
    }

    fun onSpecialButton(tier: Int) {
        if (isSp3CinematicActive) return
        val started = playerEntity.startSpecial(tier)
        if (started) {
            playerCurrentAttackHitRegistered = false
            playerEntity.comboStep = 0
            activeModifiers.forEach { it.onSpecialActivated(this, tier) }
            masteries.nodes.forEach { it.onSpecialActivated(this, tier) }

            if (tier == 3) {
                triggerSp3Cinematic(attackerIsPlayer = true)
            } else {
                addBanner("SPECIAL $tier ACTIVATED!", 0xFFF59E0B)
            }
        }
    }

    private fun triggerSp3Cinematic(attackerIsPlayer: Boolean) {
        isSp3CinematicActive = true
        sp3CinematicTime = 0f
        sp3AttackerIsPlayer = attackerIsPlayer
        screenShakeAmount = 35f
        addBanner("SPECIAL 3 CINEMATIC LOCK!", 0xFFEF4444)
    }

    // --- Main 60 FPS Deterministic Simulation Loop ---

    fun tick(deltaTime: Float) {
        if (fightResult != FightResult.ONGOING) return
        fightTimeElapsed += deltaTime

        // Handle Screen Shake dampening
        if (screenShakeAmount > 0f) {
            screenShakeAmount = (screenShakeAmount - deltaTime * 40f).coerceAtLeast(0f)
        }

        // Handle SP3 Cinematic
        if (isSp3CinematicActive) {
            updateSp3Cinematic(deltaTime)
            return
        }

        // 1. Tick entities
        playerEntity.tick(deltaTime)
        defenderEntity.tick(deltaTime)

        // 2. Reset combo step if player returned to IDLE for over 0.6s
        if (playerEntity.state.type == StateType.IDLE && playerEntity.state.timeInState > 0.6f) {
            playerEntity.comboStep = 0
        }
        if (defenderEntity.state.type == StateType.IDLE && defenderEntity.state.timeInState > 0.6f) {
            defenderEntity.comboStep = 0
        }

        // 3. Movement and Spacing
        updatePositions(deltaTime)

        // 4. Intercept Priority Rule
        checkInterceptPriority()

        // 5. Hitbox and Strike Resolution
        resolveStrikes()

        // 6. AI Tick
        updateAi(deltaTime)

        // 7. Modifiers and Masteries Tick
        activeModifiers.forEach { it.onTick(this, deltaTime) }
        masteries.nodes.forEach { it.onTick(this, deltaTime) }

        // 8. Visual banners and floaters decay
        updateFloatingVisuals(deltaTime)

        // 9. Victory / Defeat check
        if (defenderEntity.currentHealth <= 0f) {
            fightResult = FightResult.PLAYER_VICTORY
            addBanner("VICTORY! DEFENDER DEFEATED", 0xFF22C55E)
        } else if (playerEntity.currentHealth <= 0f) {
            fightResult = FightResult.PLAYER_DEFEAT
            addBanner("DEFEAT! KNOCKED OUT", 0xFFEF4444)
        }
    }

    private fun updateSp3Cinematic(deltaTime: Float) {
        sp3CinematicTime += deltaTime
        if (sp3CinematicTime >= 2.4f) {
            // Apply unblockable cinematic lethal / massive direct damage
            isSp3CinematicActive = false
            val attacker = if (sp3AttackerIsPlayer) playerEntity else defenderEntity
            val target = if (sp3AttackerIsPlayer) defenderEntity else playerEntity

            val rawDmg = attacker.getEffectiveAttackRating() * 4.5f
            target.currentHealth = (target.currentHealth - rawDmg).coerceAtLeast(0f)
            target.knockDown()

            addFloatingCombatText("${rawDmg.toInt()} SP3 DIRECT HIT!", isCrit = true, isPlayer = sp3AttackerIsPlayer)
            screenShakeAmount = 50f

            // Neutral IDLE spacing reset
            playerEntity.positionX = -130f
            defenderEntity.positionX = 130f
            playerEntity.idle()
            defenderEntity.idle()
        }
    }

    private fun updatePositions(deltaTime: Float) {
        // Player dash movement
        if (playerEntity.state.type == StateType.DASH_FORWARD) {
            playerEntity.positionX = (playerEntity.positionX + 420f * deltaTime).coerceAtMost(defenderEntity.positionX - 70f)
        } else if (playerEntity.state.type == StateType.DASH_BACK) {
            playerEntity.positionX = (playerEntity.positionX - 380f * deltaTime).coerceAtLeast(-280f)
        }

        // Defender dash movement
        if (defenderEntity.state.type == StateType.DASH_FORWARD) {
            defenderEntity.positionX = (defenderEntity.positionX - 420f * deltaTime).coerceAtLeast(playerEntity.positionX + 70f)
        } else if (defenderEntity.state.type == StateType.DASH_BACK) {
            defenderEntity.positionX = (defenderEntity.positionX + 380f * deltaTime).coerceAtMost(280f)
        }

        // Keep fighters spaced at min distance 65f
        if (defenderEntity.positionX - playerEntity.positionX < 65f) {
            val center = (playerEntity.positionX + defenderEntity.positionX) / 2f
            playerEntity.positionX = center - 32.5f
            defenderEntity.positionX = center + 32.5f
        }
    }

    private fun getFighterDistance(): Float = abs(defenderEntity.positionX - playerEntity.positionX)

    /**
     * Intercept Priority Rule:
     * If both entities dash forward simultaneously, the entity whose active attack frames
     * register first interrupts the opponent's dash, canceling their momentum and landing hit 1.
     */
    private fun checkInterceptPriority() {
        val playerIsAttacking = playerEntity.state.type == StateType.ATTACKING && playerEntity.state.isHitboxActive()
        val defenderIsAttacking = defenderEntity.state.type == StateType.ATTACKING && defenderEntity.state.isHitboxActive()

        if (playerIsAttacking && defenderEntity.state.type == StateType.DASH_FORWARD) {
            // Player intercepts defender dash!
            addBanner("INTERCEPT! DASH CRUSHED", 0xFFF59E0B)
        } else if (defenderIsAttacking && playerEntity.state.type == StateType.DASH_FORWARD) {
            // Defender intercepts player dash!
            addBanner("INTERCEPTED BY DEFENDER!", 0xFFEF4444)
        }
    }

    private fun resolveStrikes() {
        val distance = getFighterDistance()
        val inStrikeRange = distance <= 120f

        // Check Player strike landing on Defender
        if (playerEntity.state.type in listOf(StateType.ATTACKING, StateType.SPECIAL_ATTACK) &&
            playerEntity.state.isHitboxActive() && !playerCurrentAttackHitRegistered && inStrikeRange
        ) {
            playerCurrentAttackHitRegistered = true
            executeHit(attacker = playerEntity, defender = defenderEntity, isPlayerAttacker = true)
        }

        // Check Defender strike landing on Player
        if (defenderEntity.state.type in listOf(StateType.ATTACKING, StateType.SPECIAL_ATTACK) &&
            defenderEntity.state.isHitboxActive() && !defenderCurrentAttackHitRegistered && inStrikeRange
        ) {
            defenderCurrentAttackHitRegistered = true
            executeHit(attacker = defenderEntity, defender = playerEntity, isPlayerAttacker = false)
        }
    }

    /**
     * Complete Mathematical Formulas & Combat Logic:
     * - Class Wheel Advantage (+40% / -20%)
     * - Asymptotic Damage & Armor Mitigation
     * - Chip Damage on Block
     * - Frame-exact Parry Window (0 - 200ms)
     * - Frame-exact Dexterity Evasion (i-frames)
     * - Power economics (+15 / +9 / +4.5)
     */
    private fun executeHit(attacker: CombatEntity, defender: CombatEntity, isPlayerAttacker: Boolean) {
        // 1. Invulnerability check (Dexterity or Knockdown i-frames)
        if (defender.hasIframes) {
            addFloatingCombatText("DODGED!", isCrit = false, isPlayer = !isPlayerAttacker)
            return
        }

        val hitType = when (attacker.state.subtype) {
            AttackSubtype.MEDIUM_1, AttackSubtype.MEDIUM_2 -> HitType.MEDIUM
            AttackSubtype.HEAVY_RELEASE -> HitType.HEAVY
            AttackSubtype.SP1 -> HitType.SPECIAL_1
            AttackSubtype.SP2 -> HitType.SPECIAL_2
            AttackSubtype.SP3 -> HitType.SPECIAL_3
            else -> HitType.LIGHT
        }

        val isHeavy = (hitType == HitType.HEAVY)
        val isUnblockable = attacker.isUnblockable || (attacker.state.isUnblockable)

        // 2. Parry Window Detection:
        // If touch-down / block transition occurs within T_parry = [0, 200ms] prior to hostile strike connecting:
        val isParryWindow = defender.isBlocking && defender.timeSinceBlockStarted <= 0.200f && !isUnblockable
        val canParry = if (!isPlayerAttacker) masteries.hasParry() else true // Player parrying defender

        if (isParryWindow && canParry) {
            // Cancel attacker active attack frames and transition attacker into STUNNED state
            val stunDuration = 1.75f + (if (!isPlayerAttacker) masteries.getStunDurationBonus() else 0f)
            attacker.stun(duration = stunDuration)

            // Chip damage is exactly 0
            val payload = DamagePayload(
                rawAttack = 0f,
                isBlocked = true,
                isParry = true,
                finalDamage = 0f,
                chipDamage = 0f,
                powerGainAttacker = 0f,
                powerGainDefender = 6f
            )

            defender.addPower(payload.powerGainDefender)
            addBanner("PARRY! PERFECT BLOCK STUN", 0xFFEAB308)
            addFloatingCombatText("PARRY! 0 DMG", isCrit = false, isPlayer = isPlayerAttacker)
            screenShakeAmount = 15f

            // Broadcast onBlock with isParry = true
            activeModifiers.forEach { it.onBlock(this, isParry = true, payload = payload) }
            masteries.nodes.forEach { it.onBlock(this, isParry = true, payload = payload) }
            return
        }

        // 3. Blocking & Guard Break calculation
        var isBlocked = defender.isBlocking && !isUnblockable
        if (defender.isBlocking && isHeavy) {
            // Heavy shatters standing guard!
            isBlocked = false
            addBanner("GUARD BROKEN! HEAVY SHATTER", 0xFFEF4444)
        }

        // 4. Class Wheel Multipliers
        val classMult = attacker.champion.championClass.getAttackMultiplier(defender.champion.championClass)
        val rawAttack = attacker.getEffectiveAttackRating() * classMult * (if (isHeavy) 1.6f else 1.0f)

        // 5. Critical Hit Probability:
        // CritChance = CritRating / (CritRating + 1500 + DefenderCritResistance)
        val critRating = attacker.getEffectiveCritRating()
        val critRes = defender.stats.critResistance
        val critChance = (critRating / (critRating + 1500f + critRes)).coerceIn(0.05f, 0.95f)
        val isCrit = (Random.nextFloat() < critChance) || attacker.hasDexterityCritBuff

        // Critical Damage Multiplier:
        // CritMultiplier = 1.50 + (CritDamageRating / (CritDamageRating + 1500))
        val critDmgRating = attacker.getEffectiveCritDamageRating()
        val critMultiplier = 1.50f + (critDmgRating / (critDmgRating + 1500f))

        attacker.hasDexterityCritBuff = false // Consume crit buff

        // 6. Non-Linear Damage & Mitigation Equations:
        // Damage = max(1, RawAttack * (1 - (ArmorRating / (ArmorRating + 1500))))
        val armorRating = defender.getEffectiveArmorRating()
        val armorMitigation = (armorRating / (armorRating + 1500f)).coerceIn(0f, 0.85f)
        var calculatedDamage = max(1f, rawAttack * (1f - armorMitigation))
        if (isCrit) calculatedDamage *= critMultiplier

        // 7. Block Chip Damage:
        // ChipDamage = IncomingDamage * max(0.05, 1 - (BlockProficiency / (BlockProficiency + 1000)))
        var finalDamageDealt: Float
        if (isBlocked) {
            val blockProf = defender.getEffectiveBlockProficiency()
            val chipFactor = max(0.05f, 1f - (blockProf / (blockProf + 1000f)))
            finalDamageDealt = calculatedDamage * chipFactor
        } else {
            finalDamageDealt = calculatedDamage
        }

        // 8. Power Meter Economics:
        // Attacker lands hit: +15.0 units (+5% of max)
        // Defender receives unblocked hit: +9.0 units (+3% of max)
        // Defender absorbs blocked hit: +4.5 units (+1.5% of max)
        val powerAttacker = 15.0f
        val powerDefender = if (isBlocked) 4.5f else 9.0f

        attacker.addPower(powerAttacker)
        defender.addPower(powerDefender)

        val payload = DamagePayload(
            rawAttack = rawAttack,
            isCrit = isCrit,
            critMultiplier = critMultiplier,
            isBlocked = isBlocked,
            isParry = false,
            isUnblockable = isUnblockable,
            finalDamage = finalDamageDealt,
            chipDamage = if (isBlocked) finalDamageDealt else 0f,
            powerGainAttacker = powerAttacker,
            powerGainDefender = powerDefender
        )

        // 9. Lifecycle Event Hooks
        activeModifiers.forEach {
            it.onAttackLanded(this, hitType, payload)
            it.onAttackReceived(this, hitType, payload)
            if (isBlocked) it.onBlock(this, isParry = false, payload)
        }
        masteries.nodes.forEach {
            it.onAttackLanded(this, hitType, payload)
            it.onAttackReceived(this, hitType, payload)
            if (isBlocked) it.onBlock(this, isParry = false, payload)
        }

        // Apply health reduction
        defender.currentHealth = (defender.currentHealth - payload.finalDamage).coerceAtLeast(0f)
        attacker.comboCounter++

        // Screen shake & floating text
        screenShakeAmount = if (isCrit || isHeavy) 22f else 8f
        val textLabel = if (isBlocked) "BLOCK -${payload.finalDamage.toInt()}" else "-${payload.finalDamage.toInt()}"
        addFloatingCombatText(textLabel, isCrit = isCrit, isPlayer = !isPlayerAttacker)

        // Knockdown if Heavy or Special
        val shouldKnockDown = (hitType == HitType.HEAVY || hitType in listOf(HitType.SPECIAL_1, HitType.SPECIAL_2, HitType.SPECIAL_3))
        defender.takeHit(payload, knockDown = shouldKnockDown)
    }

    /**
     * AI Behavior Model:
     * - Power in [100, 199]: Roll random check to fire SP1.
     * - Power in [200, 299]: Roll random check to fire SP2.
     * - Power == 300: Immediately cast SP3 (unblockable cinematic lethal check).
     * - Basic tactical footsies, parry reaction, and dash-in medium attacks.
     */
    private fun updateAi(deltaTime: Float) {
        aiDecisionTimer += deltaTime
        if (aiHoldBlockTimer > 0f) {
            aiHoldBlockTimer -= deltaTime
            if (aiHoldBlockTimer <= 0f) {
                defenderEntity.releaseBlock()
            }
        }

        if (aiDecisionTimer >= 0.45f) {
            aiDecisionTimer = 0f
            if (!defenderEntity.canAct()) return

            val distance = getFighterDistance()

            // 1. Power Threshold SP3 check:
            if (defenderEntity.powerMeter >= 300f) {
                val executed = defenderEntity.startSpecial(3)
                if (executed) {
                    defenderCurrentAttackHitRegistered = false
                    triggerSp3Cinematic(attackerIsPlayer = false)
                    return
                }
            }

            // 2. SP2 check
            if (defenderEntity.powerMeter in 200f..299f && Random.nextFloat() < 0.65f) {
                val executed = defenderEntity.startSpecial(2)
                if (executed) {
                    defenderCurrentAttackHitRegistered = false
                    addBanner("DEFENDER FIRED SP2!", 0xFFEF4444)
                    return
                }
            }

            // 3. SP1 check
            if (defenderEntity.powerMeter in 100f..199f && distance < 140f && Random.nextFloat() < 0.50f) {
                val executed = defenderEntity.startSpecial(1)
                if (executed) {
                    defenderCurrentAttackHitRegistered = false
                    addBanner("DEFENDER FIRED SP1!", 0xFFEF4444)
                    return
                }
            }

            // 4. Tactical footsies
            val roll = Random.nextFloat()
            when {
                distance > 150f && roll < 0.60f -> {
                    // Dash in with Medium
                    defenderEntity.dashForward()
                    defenderEntity.tryMediumAttack()
                    defenderCurrentAttackHitRegistered = false
                }
                distance < 110f && roll < 0.40f -> {
                    // Start combo with light
                    defenderEntity.tryLightAttack()
                    defenderCurrentAttackHitRegistered = false
                }
                distance < 110f && roll in 0.40f..0.70f -> {
                    // Defensive block
                    defenderEntity.startBlock()
                    aiHoldBlockTimer = Random.nextFloat() * 0.8f + 0.3f
                }
                distance < 110f && roll > 0.85f -> {
                    // Heavy attack bait
                    defenderEntity.startHeavyCharge()
                }
                else -> {
                    // Dash back reposition
                    defenderEntity.dashBack()
                }
            }
        }

        // Release AI Heavy if charging
        if (defenderEntity.isHeavyCharging && defenderEntity.heavyChargeDuration >= 0.30f) {
            defenderEntity.releaseHeavyAttack()
            defenderCurrentAttackHitRegistered = false
        }
    }

    private fun updateFloatingVisuals(deltaTime: Float) {
        floatingTexts.forEach {
            it.elapsed += deltaTime
            it.y -= 35f * deltaTime
            it.alpha = (1.0f - (it.elapsed / 1.1f)).coerceIn(0f, 1f)
        }
        floatingTexts.removeAll { it.elapsed >= 1.1f }

        banners.forEach { it.elapsed += deltaTime }
        banners.removeAll { it.elapsed >= 2.2f }
    }
}
