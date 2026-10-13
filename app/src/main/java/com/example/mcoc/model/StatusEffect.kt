package com.example.mcoc.model

import androidx.compose.ui.graphics.Color

/**
 * Three-Tier Status Effect Classification per MCOC Architecture:
 * 1. BUFF (Active Positive Modifier) - strippable by Nullify, Stagger, Fate Seal.
 * 2. DEBUFF (Active Negative Modifier) - strippable by Purify, Cleanse, Shrug-Off; checked against immunities.
 * 3. PASSIVE (Permanent / Engine Modifier) - immune to Nullify and Purify actions.
 */
enum class StatusEffectType {
    BUFF,
    DEBUFF,
    PASSIVE
}

enum class StatusEffectId(
    val defaultName: String,
    val defaultType: StatusEffectType,
    val color: Color,
    val description: String
) {
    // BUFFS
    FURY("Fury", StatusEffectType.BUFF, Color(0xFFF97316), "+Attack rating based on potency"),
    ARMOR_UP("Armor Up", StatusEffectType.BUFF, Color(0xFF38BDF8), "+Armor rating mitigating incoming damage"),
    PRECISION("Precision", StatusEffectType.BUFF, Color(0xFFFACC15), "+Critical rating chance"),
    CRUELTY("Cruelty", StatusEffectType.BUFF, Color(0xFFA855F7), "+Critical damage rating multiplier"),
    REGENERATION("Regeneration", StatusEffectType.BUFF, Color(0xFF22C55E), "Restores % Max HP over time"),

    // DEBUFFS
    BLEED("Bleed", StatusEffectType.DEBUFF, Color(0xFFDC2626), "Direct physical damage over time"),
    POISON("Poison", StatusEffectType.DEBUFF, Color(0xFF16A34A), "Direct DoT and reduces all healing by 30% per stack"),
    SHOCK("Shock", StatusEffectType.DEBUFF, Color(0xFF3B82F6), "Energy damage over time and disrupts tech circuits"),
    INCINERATE("Incinerate", StatusEffectType.DEBUFF, Color(0xFFEA580C), "Energy DoT that completely removes block proficiency"),
    ARMOR_BREAK("Armor Break", StatusEffectType.DEBUFF, Color(0xFF64748B), "Reduces defender armor rating, increasing damage taken"),
    STUN("Stun", StatusEffectType.DEBUFF, Color(0xFFEAB308), "Disables input, AI, and block for duration"),
    DEGEN("Degeneration", StatusEffectType.DEBUFF, Color(0xFF9333EA), "Direct health degradation"),

    // PASSIVES
    PROWESS("Prowess", StatusEffectType.PASSIVE, Color(0xFFEC4899), "Amplifies Special Attack damage"),
    UNSTOPPABLE("Unstoppable", StatusEffectType.PASSIVE, Color(0xFFF59E0B), "Immune to hit-stun during attacks"),
    UNBLOCKABLE("Unblockable", StatusEffectType.PASSIVE, Color(0xFFEF4444), "Strikes bypass defender block"),
    ROOT("Root", StatusEffectType.PASSIVE, Color(0xFF10B981), "Entangled in place, cannot dash or evade"),
    POWER_STING("Power Sting", StatusEffectType.PASSIVE, Color(0xFF8B5CF6), "Deals direct damage when activating a Special Attack"),
    DAMNATION("Damnation", StatusEffectType.PASSIVE, Color(0xFFDC2626), "Locks power, healing, and buffs over time")
}

data class StatusEffectInstance(
    val id: StatusEffectId,
    val type: StatusEffectType = id.defaultType,
    val durationSeconds: Float,
    var timeRemaining: Float = durationSeconds,
    val potency: Float = 1.0f,
    var stacks: Int = 1,
    val isIndefinite: Boolean = false,
    val source: String = "Engine"
) {
    val isExpired: Boolean get() = !isIndefinite && timeRemaining <= 0f

    fun canBeNullified(): Boolean = type == StatusEffectType.BUFF
    fun canBePurified(): Boolean = type == StatusEffectType.DEBUFF
}
