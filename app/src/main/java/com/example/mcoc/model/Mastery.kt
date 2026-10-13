package com.example.mcoc.model

import com.example.mcoc.engine.CombatEngineContext
import com.example.mcoc.engine.CombatLifecycleListener
import com.example.mcoc.engine.DamagePayload
import com.example.mcoc.engine.HitType

enum class MasteryBranch(val displayName: String, val colorHex: Long) {
    OFFENSE("Offense", 0xFFEF4444),
    DEFENSE("Defense", 0xFF3B82F6),
    UTILITY("Utility", 0xFFEAB308)
}

data class MasteryNode(
    val id: String,
    val name: String,
    val branch: MasteryBranch,
    val maxRank: Int,
    var currentRank: Int = 0,
    val iconEmoji: String,
    val summary: String,
    val rankDescriptions: List<String>
) : CombatLifecycleListener {

    val isUnlocked: Boolean get() = currentRank > 0

    fun canRankUp(availablePoints: Int): Boolean = currentRank < maxRank && availablePoints > 0

    override fun onBlock(ctx: CombatEngineContext, isParry: Boolean, payload: DamagePayload) {
        if (id == "parry" && isParry && isUnlocked) {
            payload.chipDamage = 0f
        }
    }

    override fun onAttackLanded(ctx: CombatEngineContext, hitType: HitType, payload: DamagePayload) {
        if (id == "cruelty" && isUnlocked && payload.isCrit) {
            payload.critMultiplier += (0.15f * currentRank)
        }
    }
}

data class MasteryTree(
    var availablePoints: Int = 12,
    val nodes: List<MasteryNode> = listOf(
        // UTILITY (Core MCOC mechanics)
        MasteryNode(
            id = "parry",
            name = "Parry",
            branch = MasteryBranch.UTILITY,
            maxRank = 3,
            currentRank = 3,
            iconEmoji = "🛡️",
            summary = "Frame-exact Perfect Block stuns incoming attacker and negates chip damage",
            rankDescriptions = listOf(
                "Rank 1: Timed blocks within 200ms inflict Stun for 1.5s.",
                "Rank 2: Stun duration increased to 1.8s; chip damage = 0.",
                "Rank 3: Stun duration increased to 2.0s; chip damage = 0."
            )
        ),
        MasteryNode(
            id = "dexterity",
            name = "Dexterity",
            branch = MasteryBranch.UTILITY,
            maxRank = 1,
            currentRank = 1,
            iconEmoji = "💨",
            summary = "Dashing back during hostile attack grants complete i-frames & Critical Rating buff",
            rankDescriptions = listOf(
                "Rank 1: Timed Dash Back grants 100% invulnerability and +300 Critical Rating for next hit."
            )
        ),
        MasteryNode(
            id = "stupefy",
            name = "Stupefy",
            branch = MasteryBranch.UTILITY,
            maxRank = 3,
            currentRank = 1,
            iconEmoji = "💫",
            summary = "Extends duration of all Stun debuffs by up to 0.5 seconds",
            rankDescriptions = listOf(
                "Rank 1: +0.2s Stun duration.",
                "Rank 2: +0.35s Stun duration.",
                "Rank 3: +0.5s Stun duration."
            )
        ),
        MasteryNode(
            id = "limber",
            name = "Limber",
            branch = MasteryBranch.UTILITY,
            maxRank = 3,
            currentRank = 1,
            iconEmoji = "🏃",
            summary = "Reduces duration of incoming hostile stun debuffs",
            rankDescriptions = listOf(
                "Rank 1: -10% Stun duration.",
                "Rank 2: -20% Stun duration.",
                "Rank 3: -30% Stun duration."
            )
        ),

        // OFFENSE
        MasteryNode(
            id = "greater_strength",
            name = "Greater Strength",
            branch = MasteryBranch.OFFENSE,
            maxRank = 5,
            currentRank = 3,
            iconEmoji = "⚔️",
            summary = "Increases base attack rating for all champions",
            rankDescriptions = listOf(
                "Rank 1: +3% Attack Rating.",
                "Rank 2: +6% Attack Rating.",
                "Rank 3: +9% Attack Rating.",
                "Rank 4: +12% Attack Rating.",
                "Rank 5: +15% Attack Rating."
            )
        ),
        MasteryNode(
            id = "precision",
            name = "Precision",
            branch = MasteryBranch.OFFENSE,
            maxRank = 5,
            currentRank = 3,
            iconEmoji = "🎯",
            summary = "Increases Critical Rating chance on all attacks",
            rankDescriptions = listOf(
                "Rank 1: +100 Critical Rating.",
                "Rank 2: +200 Critical Rating.",
                "Rank 3: +300 Critical Rating.",
                "Rank 4: +400 Critical Rating.",
                "Rank 5: +500 Critical Rating."
            )
        ),
        MasteryNode(
            id = "cruelty",
            name = "Cruelty",
            branch = MasteryBranch.OFFENSE,
            maxRank = 5,
            currentRank = 2,
            iconEmoji = "💥",
            summary = "Increases Critical Damage multiplier when landing crits",
            rankDescriptions = listOf(
                "Rank 1: +150 Critical Damage Rating.",
                "Rank 2: +300 Critical Damage Rating.",
                "Rank 3: +450 Critical Damage Rating.",
                "Rank 4: +600 Critical Damage Rating.",
                "Rank 5: +750 Critical Damage Rating."
            )
        ),
        MasteryNode(
            id = "glass_cannon",
            name = "Glass Cannon",
            branch = MasteryBranch.OFFENSE,
            maxRank = 3,
            currentRank = 0,
            iconEmoji = "⚡",
            summary = "Trades -7.2% Health for +14.4% Attack rating",
            rankDescriptions = listOf(
                "Rank 1: +4.8% Attack, -2.4% Max HP.",
                "Rank 2: +9.6% Attack, -4.8% Max HP.",
                "Rank 3: +14.4% Attack, -7.2% Max HP."
            )
        ),

        // DEFENSE
        MasteryNode(
            id = "block_proficiency",
            name = "Block Proficiency",
            branch = MasteryBranch.DEFENSE,
            maxRank = 4,
            currentRank = 2,
            iconEmoji = "🧱",
            summary = "Increases Block Proficiency, heavily reducing chip damage",
            rankDescriptions = listOf(
                "Rank 1: +200 Block Proficiency.",
                "Rank 2: +400 Block Proficiency.",
                "Rank 3: +600 Block Proficiency.",
                "Rank 4: +800 Block Proficiency."
            )
        ),
        MasteryNode(
            id = "recovery",
            name = "Recovery",
            branch = MasteryBranch.DEFENSE,
            maxRank = 3,
            currentRank = 1,
            iconEmoji = "💖",
            summary = "Increases effectiveness of all health regeneration",
            rankDescriptions = listOf(
                "Rank 1: +5% Regeneration potency.",
                "Rank 2: +10% Regeneration potency.",
                "Rank 3: +15% Regeneration potency."
            )
        ),
        MasteryNode(
            id = "stand_your_ground",
            name = "Stand Your Ground",
            branch = MasteryBranch.DEFENSE,
            maxRank = 3,
            currentRank = 1,
            iconEmoji = "🗿",
            summary = "Grants a chance to resist Heavy Attack guard break while blocking",
            rankDescriptions = listOf(
                "Rank 1: 25% chance to resist Heavy block break.",
                "Rank 2: 40% chance to resist Heavy block break.",
                "Rank 3: 50% chance to resist Heavy block break."
            )
        )
    )
) {
    fun rankUp(nodeId: String): Boolean {
        val node = nodes.find { it.id == nodeId } ?: return false
        if (node.currentRank < node.maxRank && availablePoints > 0) {
            node.currentRank++
            availablePoints--
            return true
        }
        return false
    }

    fun respec() {
        var reclaimed = 0
        nodes.forEach {
            reclaimed += it.currentRank
            it.currentRank = 0
        }
        // Keep baseline parry 1 and dexterity 1
        nodes.find { it.id == "parry" }?.currentRank = 1
        nodes.find { it.id == "dexterity" }?.currentRank = 1
        availablePoints += (reclaimed - 2).coerceAtLeast(0)
    }

    fun hasParry(): Boolean = nodes.find { it.id == "parry" }?.isUnlocked ?: false
    fun hasDexterity(): Boolean = nodes.find { it.id == "dexterity" }?.isUnlocked ?: false
    fun getStunDurationBonus(): Float {
        val rank = nodes.find { it.id == "stupefy" }?.currentRank ?: 0
        return rank * 0.17f
    }
}
