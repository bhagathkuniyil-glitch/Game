package com.example.mcoc.model

import androidx.compose.ui.graphics.Color

/**
 * 6 Core Cyclic Classes in MCOC:
 * Cosmic -> Tech -> Mutant -> Skill -> Science -> Mystic -> Cosmic
 */
enum class ChampionClass(val displayName: String, val color: Color, val hexColor: Long) {
    COSMIC("Cosmic", Color(0xFF06B6D4), 0xFF06B6D4),   // Cyan / Cosmic Blue
    TECH("Tech", Color(0xFF3B82F6), 0xFF3B82F6),       // Electric Tech Blue
    MUTANT("Mutant", Color(0xFFEAB308), 0xFFEAB308),   // Mutant Amber / Gold
    SKILL("Skill", Color(0xFFEF4444), 0xFFEF4444),     // Skill Crimson Red
    SCIENCE("Science", Color(0xFF22C55E), 0xFF22C55E), // Science Emerald Green
    MYSTIC("Mystic", Color(0xFFA855F7), 0xFFA855F7);   // Mystic Arcane Purple

    /**
     * Cyclic Class Wheel:
     * Cosmic beats Tech
     * Tech beats Mutant
     * Mutant beats Skill
     * Skill beats Science
     * Science beats Mystic
     * Mystic beats Cosmic
     */
    fun hasAdvantageOver(other: ChampionClass): Boolean {
        return when (this) {
            COSMIC -> other == TECH
            TECH -> other == MUTANT
            MUTANT -> other == SKILL
            SKILL -> other == SCIENCE
            SCIENCE -> other == MYSTIC
            MYSTIC -> other == COSMIC
        }
    }

    fun hasDisadvantageAgainst(other: ChampionClass): Boolean {
        return other.hasAdvantageOver(this)
    }

    /**
     * Class Advantage Multipliers:
     * Advantage: Attacker receives +40% Attack Rating; Defender receives -20% Attack Rating.
     * Disadvantage: Attacker receives -20% Attack Rating; Defender receives +40% Attack Rating.
     * Neutral: 1.0x baseline.
     */
    fun getAttackMultiplier(opponentClass: ChampionClass): Float {
        return when {
            hasAdvantageOver(opponentClass) -> 1.40f
            hasDisadvantageAgainst(opponentClass) -> 0.80f
            else -> 1.0f
        }
    }

    fun getOpponentDefenseMultiplier(opponentClass: ChampionClass): Float {
        return when {
            hasAdvantageOver(opponentClass) -> 0.80f
            hasDisadvantageAgainst(opponentClass) -> 1.40f
            else -> 1.0f
        }
    }
}
