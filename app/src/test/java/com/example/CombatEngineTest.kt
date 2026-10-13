package com.example

import com.example.mcoc.engine.*
import com.example.mcoc.model.*
import org.junit.Assert.*
import org.junit.Test

class CombatEngineTest {

    @Test
    fun testCyclicClassWheelMatrix() {
        // Cosmic beats Tech
        assertTrue(ChampionClass.COSMIC.hasAdvantageOver(ChampionClass.TECH))
        assertEquals(1.40f, ChampionClass.COSMIC.getAttackMultiplier(ChampionClass.TECH), 0.001f)
        assertEquals(0.80f, ChampionClass.TECH.getAttackMultiplier(ChampionClass.COSMIC), 0.001f)

        // Tech beats Mutant
        assertTrue(ChampionClass.TECH.hasAdvantageOver(ChampionClass.MUTANT))

        // Mutant beats Skill
        assertTrue(ChampionClass.MUTANT.hasAdvantageOver(ChampionClass.SKILL))

        // Skill beats Science
        assertTrue(ChampionClass.SKILL.hasAdvantageOver(ChampionClass.SCIENCE))

        // Science beats Mystic
        assertTrue(ChampionClass.SCIENCE.hasAdvantageOver(ChampionClass.MYSTIC))

        // Mystic beats Cosmic
        assertTrue(ChampionClass.MYSTIC.hasAdvantageOver(ChampionClass.COSMIC))
    }

    @Test
    fun testAsymptoticDamageAndMitigationFormulas() {
        val rawAttack = 2000f
        val armorRating = 1500f // 1500 / (1500 + 1500) = 50% mitigation
        val expectedMitigation = armorRating / (armorRating + 1500f)
        val expectedDamage = rawAttack * (1f - expectedMitigation)
        assertEquals(0.5f, expectedMitigation, 0.001f)
        assertEquals(1000f, expectedDamage, 0.001f)

        // Block Chip Damage
        val blockProf = 1000f // 1000 / (1000 + 1000) = 50% mitigation on chip
        val chipFactor = 1f - (blockProf / (blockProf + 1000f))
        val chipDamage = expectedDamage * chipFactor
        assertEquals(0.5f, chipFactor, 0.001f)
        assertEquals(500f, chipDamage, 0.001f)

        // Critical Hit Probability
        val critRating = 1500f
        val critRes = 0f
        val critChance = critRating / (critRating + 1500f + critRes)
        assertEquals(0.5f, critChance, 0.001f)

        // Critical Damage Multiplier
        val critDmgRating = 1500f
        val critMult = 1.50f + (critDmgRating / (critDmgRating + 1500f))
        assertEquals(2.0f, critMult, 0.001f)
    }

    @Test
    fun testThreeTierStatusEffectPipelineAndRobotImmunities() {
        val robot = Champion.DEFAULT_ROSTER.find { it.isRobot }!!
        assertTrue(robot.isRobot)
        assertTrue(robot.isImmuneTo(StatusEffectId.BLEED))
        assertTrue(robot.isImmuneTo(StatusEffectId.POISON))

        val entity = CombatEntity(champion = robot, isPlayer = true, positionX = 0f)

        // Attempt to apply Bleed debuff to Robot -> should be rejected by immunity
        entity.applyStatusEffect(
            StatusEffectInstance(
                id = StatusEffectId.BLEED,
                type = StatusEffectType.DEBUFF,
                durationSeconds = 5f
            )
        )
        assertTrue(entity.debuffList.isEmpty())

        // Apply Fury Buff -> should succeed
        entity.applyStatusEffect(
            StatusEffectInstance(
                id = StatusEffectId.FURY,
                type = StatusEffectType.BUFF,
                durationSeconds = 10f,
                potency = 0.50f
            )
        )
        assertEquals(1, entity.buffList.size)
        assertTrue(entity.buffList[0].canBeNullified())

        // Apply Passive -> should succeed and cannot be nullified or purified
        entity.applyStatusEffect(
            StatusEffectInstance(
                id = StatusEffectId.UNSTOPPABLE,
                type = StatusEffectType.PASSIVE,
                durationSeconds = 4f
            )
        )
        assertEquals(1, entity.passiveList.size)
        assertFalse(entity.passiveList[0].canBeNullified())
        assertFalse(entity.passiveList[0].canBePurified())
    }

    @Test
    fun testParryWindowAndStunTransition() {
        val cgr = Champion.DEFAULT_ROSTER.find { it.championId == "cgr_cosmic" }!!
        val doom = Champion.DEFAULT_ROSTER.find { it.championId == "doom_mystic" }!!

        val player = CombatEntity(champion = cgr, isPlayer = true, positionX = -100f)
        val defender = CombatEntity(champion = doom, isPlayer = false, positionX = -10f)

        val engine = CombatEngine(playerEntity = player, defenderEntity = defender)

        // Player blocks just in time (timeSinceBlockStarted = 0.05s <= 0.20s)
        player.startBlock()
        player.timeSinceBlockStarted = 0.05f

        // Defender executes an attack
        defender.tryMediumAttack()
        defender.state.timeInState = 0.15f // active hitbox

        // Tick engine to resolve strike
        engine.tick(0.016f)

        // Defender should be STUNNED by Parry!
        assertTrue(defender.isStunned)
        assertEquals(StateType.STUNNED, defender.state.type)
    }

    @Test
    fun testPowerMeterEconomicsAndSpecialAttackUsage() {
        val cgr = Champion.DEFAULT_ROSTER.find { it.championId == "cgr_cosmic" }!!
        val entity = CombatEntity(champion = cgr, isPlayer = true, positionX = 0f)

        assertEquals(0f, entity.powerMeter, 0.01f)
        entity.addPower(15f) // Attacker hit
        assertEquals(15f, entity.powerMeter, 0.01f)

        entity.addPower(100f)
        assertEquals(115f, entity.powerMeter, 0.01f)

        // Fire SP1 (cost 100)
        val firedSp1 = entity.startSpecial(1)
        assertTrue(firedSp1)
        assertEquals(15f, entity.powerMeter, 0.01f)
        assertEquals(StateType.SPECIAL_ATTACK, entity.state.type)
        assertEquals(AttackSubtype.SP1, entity.state.subtype)
    }

    @Test
    fun testNodeModifierLifecycleIndestructible() {
        val lifecycle = NodeModifier.Lifecycle()
        val cgr = Champion.DEFAULT_ROSTER.find { it.championId == "cgr_cosmic" }!!
        val doom = Champion.DEFAULT_ROSTER.find { it.championId == "doom_mystic" }!!

        val player = CombatEntity(champion = cgr, isPlayer = true, positionX = -100f)
        val defender = CombatEntity(champion = doom, isPlayer = false, positionX = 100f)

        val engine = CombatEngine(
            playerEntity = player,
            defenderEntity = defender,
            activeModifiers = listOf(lifecycle)
        )

        // Simulate defender dropping to lethal damage while lifecycle shield is active
        defender.currentHealth = 0f
        lifecycle.onTick(engine, 0.016f)

        // Lifecycle must enforce clamp to at least 1 HP until knocked down
        assertTrue(defender.currentHealth >= 1f)

        // Heavy attack breaks the shield
        lifecycle.onAttackLanded(engine, HitType.HEAVY, DamagePayload(rawAttack = 1000f))
        assertFalse(lifecycle.shieldActive)
    }
}
