package com.example.mcoc.model

data class ChampionStats(
    val healthMax: Float,
    val attackRating: Float,
    val armorRating: Float,
    val blockProficiency: Float,
    val critRating: Float,
    val critDamageRating: Float,
    val critResistance: Float = 0f
) {
    fun calculatePowerIndex(): Int {
        val base = (healthMax * 0.15f) + (attackRating * 1.5f) + (armorRating * 0.8f) +
                (blockProficiency * 0.4f) + (critRating * 0.6f) + (critDamageRating * 0.5f)
        return base.toInt()
    }
}

data class SignatureAbility(
    val name: String,
    val unlocked: Boolean = true,
    val sigLevel: Int = 20,
    val description: String
)

data class Champion(
    val championId: String,
    val name: String,
    val championClass: ChampionClass,
    val tags: List<String>,
    val stats: ChampionStats,
    val immunities: List<String>,
    val signatureAbility: SignatureAbility,
    val rank: Int = 1,
    val level: Int = 1,
    val maxLevel: Int = 25,
    val starRating: Int = 4,
    val avatarEmoji: String = "⚔️",
    val description: String = ""
) {
    val isRobot: Boolean get() = tags.any { it.equals("#Robot", ignoreCase = true) || it.equals("Robot", ignoreCase = true) }

    fun isImmuneTo(effectId: StatusEffectId): Boolean {
        if (isRobot && (effectId == StatusEffectId.BLEED || effectId == StatusEffectId.POISON)) {
            return true
        }
        return immunities.any { it.equals(effectId.name, ignoreCase = true) }
    }

    fun getScaledStats(): ChampionStats {
        val levelFactor = 1.0f + ((level - 1) * 0.04f) + ((rank - 1) * 0.25f)
        return ChampionStats(
            healthMax = (stats.healthMax * levelFactor),
            attackRating = (stats.attackRating * levelFactor),
            armorRating = stats.armorRating + (level * 8f),
            blockProficiency = stats.blockProficiency + (level * 10f),
            critRating = stats.critRating + (level * 5f),
            critDamageRating = stats.critDamageRating + (level * 6f),
            critResistance = stats.critResistance
        )
    }

    companion object {
        val DEFAULT_ROSTER = listOf(
            Champion(
                championId = "cgr_cosmic",
                name = "Cosmic Ghost Rider",
                championClass = ChampionClass.COSMIC,
                tags = listOf("Cosmic", "Villain", "Offensive: Burst", "Size: L"),
                stats = ChampionStats(
                    healthMax = 28500f,
                    attackRating = 2650f,
                    armorRating = 600f,
                    blockProficiency = 2500f,
                    critRating = 620f,
                    critDamageRating = 850f
                ),
                immunities = listOf("BLEED", "INCINERATE"),
                signatureAbility = SignatureAbility(
                    name = "Damnation",
                    unlocked = true,
                    sigLevel = 60,
                    description = "Inflicts Damnation when 5 unique judgments are sealed, locking power, healing, and buffs."
                ),
                rank = 3,
                level = 18,
                starRating = 5,
                avatarEmoji = "💀",
                description = "Cosmic enforcer dealing staggering armor breaks, incinerates and fate-sealing damnation."
            ),
            Champion(
                championId = "doom_mystic",
                name = "Doctor Doom",
                championClass = ChampionClass.MYSTIC,
                tags = listOf("Mystic", "Villain", "Metal", "Size: M"),
                stats = ChampionStats(
                    healthMax = 31000f,
                    attackRating = 2300f,
                    armorRating = 850f,
                    blockProficiency = 2800f,
                    critRating = 480f,
                    critDamageRating = 650f
                ),
                immunities = listOf("SHOCK", "ARMOR_BREAK"),
                signatureAbility = SignatureAbility(
                    name = "Doombot Protocol",
                    unlocked = true,
                    sigLevel = 45,
                    description = "Staggers buffs on hit, steals power with heavy attacks, and triggers Aura of Haazareth on SP3."
                ),
                rank = 3,
                level = 20,
                starRating = 5,
                avatarEmoji = "🔮",
                description = "Supreme controller of buffs and power flow, nullifying opponent buffs into passive shocks."
            ),
            Champion(
                championId = "nimrod_tech",
                name = "Nimrod",
                championClass = ChampionClass.TECH,
                tags = listOf("Tech", "Villain", "#Robot", "Size: L"),
                stats = ChampionStats(
                    healthMax = 33000f,
                    attackRating = 2400f,
                    armorRating = 950f,
                    blockProficiency = 3000f,
                    critRating = 510f,
                    critDamageRating = 700f
                ),
                immunities = listOf("BLEED", "POISON"),
                signatureAbility = SignatureAbility(
                    name = "Mutant Eradication Protocol",
                    unlocked = true,
                    sigLevel = 40,
                    description = "Converts opponent prowess and regeneration into relentless shock stacks with armor lock."
                ),
                rank = 2,
                level = 15,
                starRating = 4,
                avatarEmoji = "🤖",
                description = "Ultimate sentinel #Robot immune to Bleed & Poison, completely neutralizing mutant prowess."
            ),
            Champion(
                championId = "archangel_mutant",
                name = "Archangel",
                championClass = ChampionClass.MUTANT,
                tags = listOf("Mutant", "Hero", "Offensive: Damage Over Time", "Size: M"),
                stats = ChampionStats(
                    healthMax = 26000f,
                    attackRating = 2800f,
                    armorRating = 450f,
                    blockProficiency = 2200f,
                    critRating = 720f,
                    critDamageRating = 900f
                ),
                immunities = emptyList(),
                signatureAbility = SignatureAbility(
                    name = "Neurotoxin Catalyst",
                    unlocked = true,
                    sigLevel = 50,
                    description = "Pairs Bleed and Poison to synthesize Neurotoxin, shutting down opponent abilities and healing."
                ),
                rank = 3,
                level = 16,
                starRating = 5,
                avatarEmoji = "🪽",
                description = "Feathered assassin stacking deep bleeds and poisons into devastating neurotoxins."
            ),
            Champion(
                championId = "nick_fury_skill",
                name = "Nick Fury",
                championClass = ChampionClass.SKILL,
                tags = listOf("Skill", "Hero", "SHIELD", "Size: M"),
                stats = ChampionStats(
                    healthMax = 27500f,
                    attackRating = 2700f,
                    armorRating = 500f,
                    blockProficiency = 2400f,
                    critRating = 650f,
                    critDamageRating = 800f
                ),
                immunities = emptyList(),
                signatureAbility = SignatureAbility(
                    name = "Life Model Decoy",
                    unlocked = true,
                    sigLevel = 75,
                    description = "Upon lethal hit, destroys LMD and returns in Real Fury mode with permanent +100% Fury buff."
                ),
                rank = 2,
                level = 22,
                starRating = 4,
                avatarEmoji = "🎖️",
                description = "Master tactician dishing heavy lacerating bleeds, tactical charges, and unblockable specials."
            ),
            Champion(
                championId = "human_torch_science",
                name = "Human Torch",
                championClass = ChampionClass.SCIENCE,
                tags = listOf("Science", "Hero", "Fantastic Four", "Size: M"),
                stats = ChampionStats(
                    healthMax = 29000f,
                    attackRating = 2500f,
                    armorRating = 520f,
                    blockProficiency = 2300f,
                    critRating = 580f,
                    critDamageRating = 760f
                ),
                immunities = listOf("INCINERATE"),
                signatureAbility = SignatureAbility(
                    name = "Flame On Pre-Fight",
                    unlocked = true,
                    sigLevel = 35,
                    description = "Transforms incinerates into Nova Flames, causing passive direct damage against Mystic champions."
                ),
                rank = 2,
                level = 14,
                starRating = 4,
                avatarEmoji = "🔥",
                description = "The premier Mystic counter, punishing energy attacks and melting blocks with blazing fire."
            )
        )
    }
}
