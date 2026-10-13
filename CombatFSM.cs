using System;
using System.Collections.Generic;
using UnityEngine; // Compatible with Unity C# or standalone C# with minimal math types

namespace MCOC.CombatEngine
{
    #region Enums & Data Structures

    public enum CombatStateType
    {
        IDLE,
        DASH_FORWARD,
        DASH_BACK,
        BLOCKING,
        ATTACKING,
        HURT,
        STUNNED,
        KNOCKED_DOWN,
        GET_UP_RECOVERY
    }

    public enum AttackType
    {
        NONE,
        LIGHT_1,
        LIGHT_2,
        LIGHT_3,
        LIGHT_4,
        MEDIUM_1,
        MEDIUM_2,
        HEAVY_CHARGE,
        HEAVY_RELEASE,
        SPECIAL_1,
        SPECIAL_2,
        SPECIAL_3
    }

    public enum InputGesture
    {
        NONE,
        LEFT_TOUCH_DOWN,
        LEFT_TOUCH_UP,
        LEFT_SWIPE_BACK,
        RIGHT_TAP_LIGHT,
        RIGHT_SWIPE_MEDIUM,
        RIGHT_HOLD_HEAVY_START,
        RIGHT_HOLD_HEAVY_RELEASE,
        TRIGGER_SP1,
        TRIGGER_SP2,
        TRIGGER_SP3
    }

    public struct DamagePayload
    {
        public float RawAttack;
        public bool IsCrit;
        public float CritMultiplier;
        public bool IsBlocked;
        public bool IsParry;
        public bool IsUnblockable;
        public bool IsEvaded;
        public float FinalDamage;
        public float ChipDamage;
        public AttackType AttackType;
        public bool TriggersKnockdown;
    }

    #endregion

    #region State Interface & Base Class

    public interface ICombatState
    {
        CombatStateType StateType { get; }
        void Enter(CombatEntity entity);
        void Update(CombatEntity entity, float deltaTime);
        void Exit(CombatEntity entity);
        bool HandleInput(CombatEntity entity, InputGesture gesture);
        void OnHitReceived(CombatEntity entity, ref DamagePayload payload);
    }

    public abstract class BaseCombatState : ICombatState
    {
        public abstract CombatStateType StateType { get; }
        protected float TimeInState;

        public virtual void Enter(CombatEntity entity)
        {
            TimeInState = 0f;
        }

        public virtual void Update(CombatEntity entity, float deltaTime)
        {
            TimeInState += deltaTime;
        }

        public virtual void Exit(CombatEntity entity)
        {
        }

        public virtual bool HandleInput(CombatEntity entity, InputGesture gesture)
        {
            return false;
        }

        public virtual void OnHitReceived(CombatEntity entity, ref DamagePayload payload)
        {
            // By default, receiving an unmitigated strike transitions to HURT or KNOCKED_DOWN
            if (payload.IsEvaded) return;

            if (payload.TriggersKnockdown)
            {
                entity.StateMachine.TransitionTo(CombatStateType.KNOCKED_DOWN);
            }
            else
            {
                entity.StateMachine.TransitionTo(CombatStateType.HURT);
            }
        }
    }

    #endregion

    #region Concrete Combat States

    /// <summary>
    /// IDLE: Neutral tracking state. Evaluates distance, resets combo sequences after timeout.
    /// </summary>
    public class IdleState : BaseCombatState
    {
        public override CombatStateType StateType => CombatStateType.IDLE;
        private const float ComboResetWindow = 0.65f;

        public override void Enter(CombatEntity entity)
        {
            base.Enter(entity);
            entity.Velocity = Vector2.zero;
        }

        public override void Update(CombatEntity entity, float deltaTime)
        {
            base.Update(entity, deltaTime);

            // Reset combo sequence string if idle too long
            if (TimeInState >= ComboResetWindow && entity.ComboStep > 0)
            {
                entity.ResetCombo();
            }

            // Align facing direction towards opponent
            if (entity.Opponent != null)
            {
                entity.FacingDirection = (entity.Opponent.Position.x >= entity.Position.x) ? 1f : -1f;
            }
        }

        public override bool HandleInput(CombatEntity entity, InputGesture gesture)
        {
            switch (gesture)
            {
                case InputGesture.LEFT_TOUCH_DOWN:
                    entity.StateMachine.TransitionTo(CombatStateType.BLOCKING);
                    return true;

                case InputGesture.LEFT_SWIPE_BACK:
                    entity.StateMachine.TransitionTo(CombatStateType.DASH_BACK);
                    return true;

                case InputGesture.RIGHT_SWIPE_MEDIUM:
                    entity.StateMachine.TransitionTo(CombatStateType.DASH_FORWARD);
                    return true;

                case InputGesture.RIGHT_TAP_LIGHT:
                    entity.StartAttack(entity.GetNextLightAttack());
                    return true;

                case InputGesture.RIGHT_HOLD_HEAVY_START:
                    entity.StartAttack(AttackType.HEAVY_CHARGE);
                    return true;

                case InputGesture.TRIGGER_SP1:
                    if (entity.PowerMeter >= 100f) entity.StartAttack(AttackType.SPECIAL_1);
                    return true;

                case InputGesture.TRIGGER_SP2:
                    if (entity.PowerMeter >= 200f) entity.StartAttack(AttackType.SPECIAL_2);
                    return true;

                case InputGesture.TRIGGER_SP3:
                    if (entity.PowerMeter >= 300f) entity.StartAttack(AttackType.SPECIAL_3);
                    return true;
            }

            return false;
        }
    }

    /// <summary>
    /// DASH_FORWARD: Velocity impulse toward opponent; can be canceled into Medium attack.
    /// </summary>
    public class DashForwardState : BaseCombatState
    {
        public override CombatStateType StateType => CombatStateType.DASH_FORWARD;
        private const float DashDuration = 0.32f;
        private const float DashSpeed = 14f;

        public override void Enter(CombatEntity entity)
        {
            base.Enter(entity);
            entity.Velocity = new Vector2(entity.FacingDirection * DashSpeed, 0f);
        }

        public override void Update(CombatEntity entity, float deltaTime)
        {
            base.Update(entity, deltaTime);

            // Automatically transition to Medium 1 if closing distance into strike range
            if (entity.Opponent != null && Mathf.Abs(entity.Opponent.Position.x - entity.Position.x) <= entity.StrikeRange)
            {
                entity.StartAttack(AttackType.MEDIUM_1);
                return;
            }

            if (TimeInState >= DashDuration)
            {
                entity.StateMachine.TransitionTo(CombatStateType.IDLE);
            }
        }

        public override bool HandleInput(CombatEntity entity, InputGesture gesture)
        {
            // Can cancel forward dash into immediate Medium strike
            if (gesture == InputGesture.RIGHT_SWIPE_MEDIUM || gesture == InputGesture.RIGHT_TAP_LIGHT)
            {
                entity.StartAttack(AttackType.MEDIUM_1);
                return true;
            }
            return false;
        }
    }

    /// <summary>
    /// DASH_BACK: Velocity impulse away from opponent.
    /// Grants Dexterity invulnerability frames (i-frames) if timed during hostile hitbox overlap.
    /// </summary>
    public class DashBackState : BaseCombatState
    {
        public override CombatStateType StateType => CombatStateType.DASH_BACK;
        private const float DashDuration = 0.38f;
        private const float DashSpeed = 12f;
        public bool HasDexterityIframes { get; private set; }

        public override void Enter(CombatEntity entity)
        {
            base.Enter(entity);
            entity.Velocity = new Vector2(-entity.FacingDirection * DashSpeed, 0f);

            // Dexterity Window Verification:
            // Check if opponent is currently in active attack frames overlapping our hurtbox
            if (entity.Opponent != null && entity.Opponent.IsHitboxActive && entity.HasDexterityMastery)
            {
                HasDexterityIframes = true;
                entity.IsInvulnerable = true;
                entity.ApplyDexterityPrecisionBuff();
                entity.OnDexterityEvadeTriggered();
            }
            else
            {
                HasDexterityIframes = false;
            }
        }

        public override void Update(CombatEntity entity, float deltaTime)
        {
            base.Update(entity, deltaTime);
            if (TimeInState >= DashDuration)
            {
                entity.StateMachine.TransitionTo(CombatStateType.IDLE);
            }
        }

        public override void Exit(CombatEntity entity)
        {
            base.Exit(entity);
            if (HasDexterityIframes)
            {
                entity.IsInvulnerable = false;
            }
        }

        public override void OnHitReceived(CombatEntity entity, ref DamagePayload payload)
        {
            if (HasDexterityIframes || entity.IsInvulnerable)
            {
                payload.IsEvaded = true;
                payload.FinalDamage = 0f;
                return;
            }

            base.OnHitReceived(entity, ref payload);
        }
    }

    /// <summary>
    /// BLOCKING: Dampens incoming damage via BlockProficiency.
    /// Evaluates frame-exact Parry Window (T_parry = [0, 200ms]) upon strike arrival.
    /// Broken immediately by Heavy Attack or Unblockable strikes.
    /// </summary>
    public class BlockingState : BaseCombatState
    {
        public override CombatStateType StateType => CombatStateType.BLOCKING;
        public float BlockHoldDuration => TimeInState;
        private const float ParryWindowDuration = 0.200f; // 12-15 frames @ 60 FPS

        public override void Enter(CombatEntity entity)
        {
            base.Enter(entity);
            entity.Velocity = Vector2.zero;
            entity.IsBlocking = true;
        }

        public override void Exit(CombatEntity entity)
        {
            base.Exit(entity);
            entity.IsBlocking = false;
        }

        public override bool HandleInput(CombatEntity entity, InputGesture gesture)
        {
            if (gesture == InputGesture.LEFT_TOUCH_UP)
            {
                entity.StateMachine.TransitionTo(CombatStateType.IDLE);
                return true;
            }
            if (gesture == InputGesture.LEFT_SWIPE_BACK)
            {
                entity.StateMachine.TransitionTo(CombatStateType.DASH_BACK);
                return true;
            }
            return false;
        }

        public override void OnHitReceived(CombatEntity entity, ref DamagePayload payload)
        {
            // 1. Guard Break Check: Heavy attack or unblockable bypasses block entirely
            if (payload.AttackType == AttackType.HEAVY_RELEASE || payload.IsUnblockable)
            {
                entity.IsBlocking = false;
                payload.IsBlocked = false;
                entity.OnGuardBroken();
                entity.StateMachine.TransitionTo(payload.TriggersKnockdown ? CombatStateType.KNOCKED_DOWN : CombatStateType.HURT);
                return;
            }

            // 2. Frame-Exact Parry Window:
            // Block entered within [0, 200ms] before hostile strike connecting
            if (TimeInState <= ParryWindowDuration && entity.HasParryMastery)
            {
                payload.IsBlocked = true;
                payload.IsParry = true;
                payload.ChipDamage = 0f;
                payload.FinalDamage = 0f;

                // Cancel attacker active attack frames and inflict Stun
                if (entity.Opponent != null)
                {
                    entity.Opponent.InterruptIntoStun(duration: entity.ParryStunDuration);
                }

                entity.OnParrySuccess();
                return;
            }

            // 3. Standard Block Chip Mitigation:
            payload.IsBlocked = true;
            payload.IsParry = false;
            float blockProf = entity.EffectiveBlockProficiency;
            float chipFactor = Mathf.Max(0.05f, 1f - (blockProf / (blockProf + 1000f)));
            payload.FinalDamage = payload.RawAttack * chipFactor;
            payload.ChipDamage = payload.FinalDamage;

            entity.ApplyHealthReduction(payload.FinalDamage);
        }
    }

    /// <summary>
    /// ATTACKING: Handles light, medium, heavy windup/release, and special attacks.
    /// Clamps standard combo sequence strictly to M -> L -> L -> L -> M (or H).
    /// </summary>
    public class AttackingState : BaseCombatState
    {
        public override CombatStateType StateType => CombatStateType.ATTACKING;
        public AttackType CurrentAttack { get; private set; }

        private float _startupDuration;
        private float _activeStart;
        private float _activeEnd;
        private float _recoveryDuration;
        private float _totalDuration;
        private bool _hitRegistered;
        private bool _canCancelIntoNextCombo;

        public bool IsInActiveHitboxFrames => TimeInState >= _activeStart && TimeInState <= _activeEnd;

        public void Configure(AttackType attackType)
        {
            CurrentAttack = attackType;
            _hitRegistered = false;
            _canCancelIntoNextCombo = false;

            switch (attackType)
            {
                case AttackType.LIGHT_1:
                case AttackType.LIGHT_2:
                case AttackType.LIGHT_3:
                case AttackType.LIGHT_4:
                    _startupDuration = 0.08f;
                    _activeStart = 0.08f;
                    _activeEnd = 0.18f;
                    _recoveryDuration = 0.14f;
                    _totalDuration = _activeEnd + _recoveryDuration;
                    break;

                case AttackType.MEDIUM_1:
                case AttackType.MEDIUM_2:
                    _startupDuration = 0.12f;
                    _activeStart = 0.12f;
                    _activeEnd = 0.25f;
                    _recoveryDuration = 0.18f;
                    _totalDuration = _activeEnd + _recoveryDuration;
                    break;

                case AttackType.HEAVY_CHARGE:
                    _startupDuration = 0f;
                    _activeStart = 999f;
                    _activeEnd = 999f;
                    _recoveryDuration = 0f;
                    _totalDuration = 999f; // Indefinite until release
                    break;

                case AttackType.HEAVY_RELEASE:
                    _startupDuration = 0.15f;
                    _activeStart = 0.15f;
                    _activeEnd = 0.35f;
                    _recoveryDuration = 0.25f;
                    _totalDuration = _activeEnd + _recoveryDuration;
                    break;

                case AttackType.SPECIAL_1:
                case AttackType.SPECIAL_2:
                    _startupDuration = 0.20f;
                    _activeStart = 0.20f;
                    _activeEnd = 0.65f;
                    _recoveryDuration = 0.30f;
                    _totalDuration = _activeEnd + _recoveryDuration;
                    break;

                case AttackType.SPECIAL_3:
                    // SP3 Cinematic Lock
                    _startupDuration = 0f;
                    _activeStart = 0f;
                    _activeEnd = 2.5f;
                    _recoveryDuration = 0f;
                    _totalDuration = 2.5f;
                    break;
            }
        }

        public override void Enter(CombatEntity entity)
        {
            base.Enter(entity);
            if (CurrentAttack == AttackType.SPECIAL_3)
            {
                entity.TriggerSp3CinematicLock();
            }
        }

        public override void Update(CombatEntity entity, float deltaTime)
        {
            base.Update(entity, deltaTime);

            // Active Hitbox Registration
            if (IsInActiveHitboxFrames && !_hitRegistered)
            {
                if (entity.Opponent != null && Mathf.Abs(entity.Opponent.Position.x - entity.Position.x) <= entity.StrikeRange)
                {
                    _hitRegistered = true;
                    entity.DeliverHit(CurrentAttack);
                }
            }

            // Cancel window open during recovery
            if (TimeInState >= _activeEnd)
            {
                _canCancelIntoNextCombo = true;
            }

            // Attack complete -> return to Idle
            if (CurrentAttack != AttackType.HEAVY_CHARGE && TimeInState >= _totalDuration)
            {
                entity.StateMachine.TransitionTo(CombatStateType.IDLE);
            }
        }

        public override bool HandleInput(CombatEntity entity, InputGesture gesture)
        {
            // Release Heavy Windup
            if (CurrentAttack == AttackType.HEAVY_CHARGE && gesture == InputGesture.RIGHT_HOLD_HEAVY_RELEASE)
            {
                if (TimeInState >= 0.25f)
                {
                    entity.StartAttack(AttackType.HEAVY_RELEASE);
                }
                else
                {
                    entity.StateMachine.TransitionTo(CombatStateType.IDLE);
                }
                return true;
            }

            // Cancel into next string in combo sequence:
            // Standard: M -> L -> L -> L -> M
            // Heavy: M -> L -> L -> L -> H
            if (_canCancelIntoNextCombo)
            {
                if (gesture == InputGesture.RIGHT_TAP_LIGHT && entity.ComboStep in (1, 2, 3))
                {
                    entity.StartAttack(entity.GetNextLightAttack());
                    return true;
                }
                if (gesture == InputGesture.RIGHT_SWIPE_MEDIUM && entity.ComboStep >= 4)
                {
                    entity.StartAttack(AttackType.MEDIUM_2);
                    return true;
                }
                if (gesture == InputGesture.RIGHT_HOLD_HEAVY_START && entity.ComboStep >= 4)
                {
                    entity.StartAttack(AttackType.HEAVY_CHARGE);
                    return true;
                }
            }

            return false;
        }
    }

    /// <summary>
    /// HURT: Hit-stun recovery state. Drops blocking, locks user input until recovery expires.
    /// </summary>
    public class HurtState : BaseCombatState
    {
        public override CombatStateType StateType => CombatStateType.HURT;
        private const float HitStunDuration = 0.32f;

        public override void Enter(CombatEntity entity)
        {
            base.Enter(entity);
            entity.Velocity = Vector2.zero;
        }

        public override void Update(CombatEntity entity, float deltaTime)
        {
            base.Update(entity, deltaTime);
            if (TimeInState >= HitStunDuration)
            {
                entity.StateMachine.TransitionTo(CombatStateType.IDLE);
            }
        }
    }

    /// <summary>
    /// STUNNED: Complete input lock and AI disable triggered by Parry or special attacks.
    /// </summary>
    public class StunnedState : BaseCombatState
    {
        public override CombatStateType StateType => CombatStateType.STUNNED;
        public float StunDuration { get; private set; } = 1.75f;

        public void SetDuration(float duration)
        {
            StunDuration = duration;
        }

        public override void Enter(CombatEntity entity)
        {
            base.Enter(entity);
            entity.Velocity = Vector2.zero;
            entity.IsBlocking = false;
        }

        public override void Update(CombatEntity entity, float deltaTime)
        {
            base.Update(entity, deltaTime);
            if (TimeInState >= StunDuration)
            {
                entity.StateMachine.TransitionTo(CombatStateType.IDLE);
            }
        }
    }

    /// <summary>
    /// KNOCKED_DOWN: Grounded state triggered by Heavy or Special attacks.
    /// Invincible to standard strikes until stand-up animation completes.
    /// </summary>
    public class KnockedDownState : BaseCombatState
    {
        public override CombatStateType StateType => CombatStateType.KNOCKED_DOWN;
        private const float GroundedDuration = 1.15f;

        public override void Enter(CombatEntity entity)
        {
            base.Enter(entity);
            entity.Velocity = Vector2.zero;
            entity.IsInvulnerable = true;
        }

        public override void Update(CombatEntity entity, float deltaTime)
        {
            base.Update(entity, deltaTime);
            if (TimeInState >= GroundedDuration)
            {
                entity.StateMachine.TransitionTo(CombatStateType.GET_UP_RECOVERY);
            }
        }

        public override void Exit(CombatEntity entity)
        {
            base.Exit(entity);
        }

        public override void OnHitReceived(CombatEntity entity, ref DamagePayload payload)
        {
            // Fully invincible to strikes while grounded
            payload.IsEvaded = true;
            payload.FinalDamage = 0f;
        }
    }

    /// <summary>
    /// GET_UP_RECOVERY: Recovery transition returning from Knocked Down to Neutral IDLE.
    /// </summary>
    public class GetUpRecoveryState : BaseCombatState
    {
        public override CombatStateType StateType => CombatStateType.GET_UP_RECOVERY;
        private const float GetUpDuration = 0.35f;

        public override void Enter(CombatEntity entity)
        {
            base.Enter(entity);
            entity.IsInvulnerable = true;
        }

        public override void Update(CombatEntity entity, float deltaTime)
        {
            base.Update(entity, deltaTime);
            if (TimeInState >= GetUpDuration)
            {
                entity.StateMachine.TransitionTo(CombatStateType.IDLE);
            }
        }

        public override void Exit(CombatEntity entity)
        {
            base.Exit(entity);
            entity.IsInvulnerable = false;
        }

        public override void OnHitReceived(CombatEntity entity, ref DamagePayload payload)
        {
            payload.IsEvaded = true;
            payload.FinalDamage = 0f;
        }
    }

    #endregion

    #region State Machine Controller

    public class CombatStateMachine
    {
        private readonly CombatEntity _entity;
        private readonly Dictionary<CombatStateType, ICombatState> _states;

        public ICombatState CurrentState { get; private set; }
        public CombatStateType CurrentStateType => CurrentState?.StateType ?? CombatStateType.IDLE;
        public ICombatState PreviousState { get; private set; }

        public event Action<CombatStateType, CombatStateType> OnStateChanged;

        public CombatStateMachine(CombatEntity entity)
        {
            _entity = entity;
            _states = new Dictionary<CombatStateType, ICombatState>
            {
                { CombatStateType.IDLE, new IdleState() },
                { CombatStateType.DASH_FORWARD, new DashForwardState() },
                { CombatStateType.DASH_BACK, new DashBackState() },
                { CombatStateType.BLOCKING, new BlockingState() },
                { CombatStateType.ATTACKING, new AttackingState() },
                { CombatStateType.HURT, new HurtState() },
                { CombatStateType.STUNNED, new StunnedState() },
                { CombatStateType.KNOCKED_DOWN, new KnockedDownState() },
                { CombatStateType.GET_UP_RECOVERY, new GetUpRecoveryState() }
            };

            CurrentState = _states[CombatStateType.IDLE];
            CurrentState.Enter(_entity);
        }

        public bool TransitionTo(CombatStateType nextStateType)
        {
            if (CurrentState == null || CurrentState.StateType == nextStateType) return false;

            // Transition Constraints:
            if ((CurrentState.StateType == CombatStateType.STUNNED ||
                 CurrentState.StateType == CombatStateType.KNOCKED_DOWN ||
                 CurrentState.StateType == CombatStateType.GET_UP_RECOVERY) &&
                nextStateType != CombatStateType.IDLE &&
                nextStateType != CombatStateType.KNOCKED_DOWN &&
                nextStateType != CombatStateType.GET_UP_RECOVERY)
            {
                return false; // Stun and grounded states reject voluntary transitions
            }

            ICombatState nextState = _states[nextStateType];
            PreviousState = CurrentState;
            CurrentState.Exit(_entity);

            CurrentState = nextState;
            CurrentState.Enter(_entity);

            OnStateChanged?.Invoke(PreviousState.StateType, CurrentState.StateType);
            return true;
        }

        public void Update(float deltaTime)
        {
            CurrentState?.Update(_entity, deltaTime);
        }

        public bool HandleInput(InputGesture gesture)
        {
            return CurrentState?.HandleInput(_entity, gesture) ?? false;
        }

        public void OnHitReceived(ref DamagePayload payload)
        {
            CurrentState?.OnHitReceived(_entity, ref payload);
        }

        public ICombatState GetState(CombatStateType type) => _states[type];
    }

    #endregion

    #region Combat Entity Integration Stub

    public class CombatEntity
    {
        public CombatStateMachine StateMachine { get; }
        public CombatEntity Opponent { get; set; }

        public Vector2 Position;
        public Vector2 Velocity;
        public float FacingDirection = 1f;
        public float StrikeRange = 2.2f;

        public float Health = 10000f;
        public float MaxHealth = 10000f;
        public float PowerMeter = 0f; // 0..300 units

        public bool IsBlocking { get; set; }
        public bool IsInvulnerable { get; set; }
        public bool HasParryMastery { get; set; } = true;
        public bool HasDexterityMastery { get; set; } = true;
        public float ParryStunDuration { get; set; } = 1.85f;
        public float EffectiveBlockProficiency => 2500f;

        public int ComboStep { get; private set; } // 0..5
        public bool IsHitboxActive => (StateMachine.CurrentState is AttackingState atk) && atk.IsInActiveHitboxFrames;

        public CombatEntity()
        {
            StateMachine = new CombatStateMachine(this);
        }

        public void Update(float deltaTime)
        {
            Position += Velocity * deltaTime;
            StateMachine.Update(deltaTime);
        }

        public void StartAttack(AttackType type)
        {
            if (StateMachine.GetState(CombatStateType.ATTACKING) is AttackingState atkState)
            {
                atkState.Configure(type);
                StateMachine.TransitionTo(CombatStateType.ATTACKING);
                ComboStep++;
            }
        }

        public AttackType GetNextLightAttack()
        {
            return ComboStep switch
            {
                1 => AttackType.LIGHT_1,
                2 => AttackType.LIGHT_2,
                3 => AttackType.LIGHT_3,
                _ => AttackType.LIGHT_4
            };
        }

        public void ResetCombo()
        {
            ComboStep = 0;
        }

        public void InterruptIntoStun(float duration)
        {
            if (StateMachine.GetState(CombatStateType.STUNNED) is StunnedState stunState)
            {
                stunState.SetDuration(duration);
                StateMachine.TransitionTo(CombatStateType.STUNNED);
            }
        }

        public void DeliverHit(AttackType attackType)
        {
            if (Opponent == null) return;

            bool triggersKnockdown = attackType == AttackType.HEAVY_RELEASE ||
                                     attackType == AttackType.SPECIAL_1 ||
                                     attackType == AttackType.SPECIAL_2 ||
                                     attackType == AttackType.SPECIAL_3;

            DamagePayload payload = new DamagePayload
            {
                RawAttack = 1500f,
                AttackType = attackType,
                TriggersKnockdown = triggersKnockdown,
                IsUnblockable = attackType == AttackType.HEAVY_RELEASE || attackType == AttackType.SPECIAL_3
            };

            Opponent.StateMachine.OnHitReceived(ref payload);
        }

        public void ApplyHealthReduction(float damage)
        {
            Health = Mathf.Max(0f, Health - damage);
        }

        public void TriggerSp3CinematicLock()
        {
            Velocity = Vector2.zero;
            if (Opponent != null)
            {
                Opponent.Velocity = Vector2.zero;
                Opponent.IsBlocking = false;
            }
        }

        public void ApplyDexterityPrecisionBuff() { }
        public void OnDexterityEvadeTriggered() { }
        public void OnParrySuccess() { }
        public void OnGuardBroken() { }
    }

    #endregion
}
