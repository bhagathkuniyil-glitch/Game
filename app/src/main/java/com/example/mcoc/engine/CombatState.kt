package com.example.mcoc.engine

enum class StateType {
    IDLE,
    DASH_FORWARD,
    DASH_BACK,
    BLOCKING,
    ATTACKING,
    SPECIAL_ATTACK,
    HURT,
    STUNNED,
    KNOCKED_DOWN,
    GET_UP_RECOVERY
}

enum class AttackSubtype {
    NONE,
    MEDIUM_1,
    LIGHT_1,
    LIGHT_2,
    LIGHT_3,
    MEDIUM_2,
    HEAVY_CHARGE,
    HEAVY_RELEASE,
    SP1,
    SP2,
    SP3
}

data class StateInfo(
    val type: StateType,
    val subtype: AttackSubtype = AttackSubtype.NONE,
    var timeInState: Float = 0f,
    val totalDuration: Float,
    val hitboxStart: Float = 0f,
    val hitboxEnd: Float = 0f,
    val isUnblockable: Boolean = false,
    val hasIframes: Boolean = false,
    val canCancelToAttack: Boolean = false
) {
    val isFinished: Boolean get() = totalDuration > 0f && timeInState >= totalDuration

    fun isHitboxActive(): Boolean =
        timeInState in hitboxStart..hitboxEnd
}
