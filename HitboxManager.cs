using System;
using System.Collections.Generic;
using UnityEngine;

namespace MCOC.CombatEngine.Collision
{
    #region Enums & Data Contracts

    public enum ChampionClass
    {
        COSMIC,
        TECH,
        MUTANT,
        SKILL,
        SCIENCE,
        MYSTIC
    }

    public enum HurtboxType
    {
        FULL_BODY,
        HIGH,
        MID,
        LOW
    }

    public struct CombatStats
    {
        public float AttackRating;
        public float ArmorRating;
        public float BlockProficiency;
        public float CritRating;
        public float CritDamageRating;
        public float DefenderCritResistance;
    }

    public struct HitDefinition
    {
        public string AttackId;
        public AttackType AttackType;
        public float BaseMultiplier;
        public float HitStunDuration;
        public bool TriggersKnockdown;
        public bool IsUnblockable;
        public Vector2 HitboxCenter;
        public Vector2 HitboxSize;
    }

    public struct CollisionResult
    {
        public bool DidConnect;
        public DamagePayload Payload;
        public float HitStunDuration;
        public bool AttackerInterruptedByParry;
        public bool TargetKnockedDown;
    }

    #endregion

    #region Class Wheel Resolver

    public static class ClassWheel
    {
        /// <summary>
        /// Cyclic Class Wheel Matrix:
        /// Cosmic -> Tech -> Mutant -> Skill -> Science -> Mystic -> Cosmic
        /// </summary>
        public static bool HasAdvantage(ChampionClass attacker, ChampionClass defender)
        {
            return (attacker == ChampionClass.COSMIC && defender == ChampionClass.TECH) ||
                   (attacker == ChampionClass.TECH && defender == ChampionClass.MUTANT) ||
                   (attacker == ChampionClass.MUTANT && defender == ChampionClass.SKILL) ||
                   (attacker == ChampionClass.SKILL && defender == ChampionClass.SCIENCE) ||
                   (attacker == ChampionClass.SCIENCE && defender == ChampionClass.MYSTIC) ||
                   (attacker == ChampionClass.MYSTIC && defender == ChampionClass.COSMIC);
        }

        public static float GetAttackerMultiplier(ChampionClass attacker, ChampionClass defender)
        {
            if (HasAdvantage(attacker, defender)) return 1.40f; // +40% Attack
            if (HasAdvantage(defender, attacker)) return 0.80f; // -20% Attack
            return 1.0f;
        }

        public static float GetDefenderMultiplier(ChampionClass attacker, ChampionClass defender)
        {
            if (HasAdvantage(attacker, defender)) return 0.80f; // -20% Attack for Defender
            if (HasAdvantage(defender, attacker)) return 1.40f; // +40% Attack for Defender
            return 1.0f;
        }
    }

    #endregion

    #region Hitbox & Hurtbox Components

    /// <summary>
    /// Hurtbox Component: Represents the vulnerable collision volume of a combat entity.
    /// Supports Unity Collider2D/Trigger or custom geometric AABB evaluation.
    /// </summary>
    public class HurtboxComponent : MonoBehaviour
    {
        public CombatEntity Owner { get; private set; }
        public HurtboxType Type = HurtboxType.FULL_BODY;
        public Vector2 Offset = Vector2.zero;
        public Vector2 Size = new Vector2(1.2f, 2.4f);

        [SerializeField] private Collider2D _triggerCollider;

        public Bounds WorldBounds
        {
            get
            {
                if (_triggerCollider != null) return _triggerCollider.bounds;
                Vector3 center = transform.position + (Vector3)Offset;
                return new Bounds(center, (Vector3)Size);
            }
        }

        public void Initialize(CombatEntity owner)
        {
            Owner = owner;
            if (_triggerCollider == null)
            {
                _triggerCollider = GetComponent<Collider2D>();
            }
            if (_triggerCollider != null)
            {
                _triggerCollider.isTrigger = true;
            }
        }

        public bool IsVulnerable => Owner != null && !Owner.IsInvulnerable;
    }

    /// <summary>
    /// Hitbox Component: Represents the active offensive strike volume of an attack.
    /// Carries unique HitId to strictly guarantee single-hit registration per strike frame window.
    /// </summary>
    public class HitboxComponent : MonoBehaviour
    {
        public CombatEntity Owner { get; private set; }
        public HitDefinition CurrentHit { get; private set; }
        public bool IsActive { get; private set; }

        private readonly HashSet<CombatEntity> _registeredTargets = new HashSet<CombatEntity>();
        [SerializeField] private Collider2D _triggerCollider;

        public Bounds WorldBounds
        {
            get
            {
                if (_triggerCollider != null) return _triggerCollider.bounds;
                Vector3 center = transform.position + (Vector3)CurrentHit.HitboxCenter;
                return new Bounds(center, (Vector3)CurrentHit.HitboxSize);
            }
        }

        public void Initialize(CombatEntity owner)
        {
            Owner = owner;
            if (_triggerCollider == null)
            {
                _triggerCollider = GetComponent<Collider2D>();
            }
            if (_triggerCollider != null)
            {
                _triggerCollider.isTrigger = true;
                _triggerCollider.enabled = false;
            }
            IsActive = false;
        }

        public void Activate(HitDefinition hitDef)
        {
            CurrentHit = hitDef;
            _registeredTargets.Clear();
            IsActive = true;
            if (_triggerCollider != null)
            {
                _triggerCollider.enabled = true;
            }
        }

        public void Deactivate()
        {
            IsActive = false;
            _registeredTargets.Clear();
            if (_triggerCollider != null)
            {
                _triggerCollider.enabled = false;
            }
        }

        public bool HasHitTarget(CombatEntity target) => _registeredTargets.Contains(target);

        public void MarkTargetHit(CombatEntity target) => _registeredTargets.Add(target);
    }

    #endregion

    #region Hurtbox/Hitbox Collision Manager

    /// <summary>
    /// Core Collision & Damage Resolution System.
    /// Evaluates trigger collisions and computes non-linear formulas for damage,
    /// chip mitigation, crit multipliers, hit-stun durations, and power economics.
    /// </summary>
    public class HitboxCollisionManager : MonoBehaviour
    {
        public static HitboxCollisionManager Instance { get; private set; }

        private readonly List<HitboxComponent> _activeHitboxes = new List<HitboxComponent>();
        private readonly List<HurtboxComponent> _activeHurtboxes = new List<HurtboxComponent>();

        private void Awake()
        {
            if (Instance == null) Instance = this;
            else Destroy(gameObject);
        }

        public void RegisterHurtbox(HurtboxComponent hurtbox) => _activeHurtboxes.Add(hurtbox);
        public void UnregisterHurtbox(HurtboxComponent hurtbox) => _activeHurtboxes.Remove(hurtbox);

        public void RegisterHitbox(HitboxComponent hitbox) => _activeHitboxes.Add(hitbox);
        public void UnregisterHitbox(HitboxComponent hitbox) => _activeHitboxes.Remove(hitbox);

        /// <summary>
        /// Manual deterministic collision tick (useful for frame-exact simulation independent of Unity Physics).
        /// </summary>
        public void EvaluateFrameCollisions()
        {
            for (int i = 0; i < _activeHitboxes.Count; i++)
            {
                HitboxComponent hitbox = _activeHitboxes[i];
                if (!hitbox.IsActive) continue;

                for (int j = 0; j < _activeHurtboxes.Count; j++)
                {
                    HurtboxComponent hurtbox = _activeHurtboxes[j];
                    if (hitbox.Owner == hurtbox.Owner) continue; // Ignore friendly fire
                    if (hitbox.HasHitTarget(hurtbox.Owner)) continue; // Already hit by this swing

                    // Test AABB overlap
                    if (hitbox.WorldBounds.Intersects(hurtbox.WorldBounds))
                    {
                        hitbox.MarkTargetHit(hurtbox.Owner);
                        ProcessHit(hitbox.Owner, hurtbox.Owner, hitbox.CurrentHit);
                    }
                }
            }
        }

        /// <summary>
        /// Trigger callback bridge for Unity Physics2D (OnTriggerEnter2D integration).
        /// </summary>
        public void OnHitboxTriggerEnter(HitboxComponent hitbox, HurtboxComponent hurtbox)
        {
            if (!hitbox.IsActive) return;
            if (hitbox.Owner == hurtbox.Owner) return;
            if (hitbox.HasHitTarget(hurtbox.Owner)) return;

            hitbox.MarkTargetHit(hurtbox.Owner);
            ProcessHit(hitbox.Owner, hurtbox.Owner, hitbox.CurrentHit);
        }

        /// <summary>
        /// Full mathematical resolution pipeline for strike landing.
        /// </summary>
        public CollisionResult ProcessHit(CombatEntity attacker, CombatEntity defender, HitDefinition hit)
        {
            CollisionResult result = new CollisionResult { DidConnect = true };

            // 1. Invulnerability Check (Dexterity i-frames or Knockdown grounded state)
            if (defender.IsInvulnerable)
            {
                result.Payload = new DamagePayload
                {
                    IsEvaded = true,
                    FinalDamage = 0f,
                    AttackType = hit.AttackType
                };
                defender.StateMachine.OnHitReceived(ref result.Payload);
                return result;
            }

            // 2. Class Wheel Matrix Multipliers
            float classMultiplier = ClassWheel.GetAttackerMultiplier(attacker.Class, defender.Class);
            float rawAttack = attacker.Stats.AttackRating * hit.BaseMultiplier * classMultiplier;

            // 3. Parry Window Verification: [0, 200ms] prior to strike landing
            bool isBlocking = defender.IsBlocking;
            bool isUnblockable = hit.IsUnblockable || attacker.IsUnblockable;
            bool parryWindowMet = isBlocking &&
                                  defender.BlockHoldDuration <= 0.200f &&
                                  defender.HasParryMastery &&
                                  !isUnblockable;

            if (parryWindowMet)
            {
                result.AttackerInterruptedByParry = true;
                result.Payload = new DamagePayload
                {
                    RawAttack = rawAttack,
                    IsBlocked = true,
                    IsParry = true,
                    FinalDamage = 0f,
                    ChipDamage = 0f,
                    AttackType = hit.AttackType
                };

                // Attacker active attack canceled and transitioned into STUNNED state
                float stunDuration = 1.75f + defender.StunDurationBonus;
                attacker.InterruptIntoStun(stunDuration);

                // Defender power gain on parry
                defender.AddPower(6.0f);

                defender.StateMachine.OnHitReceived(ref result.Payload);
                return result;
            }

            // 4. Guard Break Evaluation
            bool isHeavy = hit.AttackType == AttackType.HEAVY_RELEASE;
            if (isBlocking && isHeavy)
            {
                isBlocking = false; // Heavy shatters standing guard!
                defender.OnGuardBroken();
            }

            // 5. Critical Hit Probability Equation:
            // CritChance = CritRating / (CritRating + 1500 + DefenderCritResistance)
            float critRating = attacker.Stats.CritRating + (attacker.HasDexterityCritBuff ? 450f : 0f);
            float critResistance = defender.Stats.DefenderCritResistance;
            float critChance = Mathf.Clamp(critRating / (critRating + 1500f + critResistance), 0.05f, 0.95f);
            bool isCrit = (UnityEngine.Random.value < critChance) || attacker.HasDexterityCritBuff;

            attacker.ConsumeDexterityCritBuff();

            // 6. Critical Damage Multiplier Equation:
            // CritMultiplier = 1.50 + (CritDamageRating / (CritDamageRating + 1500))
            float critDmgRating = attacker.Stats.CritDamageRating;
            float critMultiplier = 1.50f + (critDmgRating / (critDmgRating + 1500f));

            // 7. Mitigated Damage Equation:
            // Damage = max(1, RawAttack * (1 - (ArmorRating / (ArmorRating + 1500))))
            float armorRating = Mathf.Max(0f, defender.Stats.ArmorRating);
            float armorMitigation = Mathf.Clamp(armorRating / (armorRating + 1500f), 0f, 0.85f);
            float calculatedDamage = Mathf.Max(1f, rawAttack * (1f - armorMitigation));

            if (isCrit)
            {
                calculatedDamage *= critMultiplier;
            }

            // 8. Block Chip Damage Equation:
            // ChipDamage = IncomingDamage * max(0.05, 1 - (BlockProficiency / (BlockProficiency + 1000)))
            float finalDamage;
            float chipDamage = 0f;

            if (isBlocking && !isUnblockable)
            {
                float blockProf = defender.Stats.BlockProficiency;
                float chipFactor = Mathf.Max(0.05f, 1f - (blockProf / (blockProf + 1000f)));
                chipDamage = calculatedDamage * chipFactor;
                finalDamage = chipDamage;
            }
            else
            {
                finalDamage = calculatedDamage;
            }

            // 9. Power Meter Economics:
            // Attacker lands hit: +15.0 units (+5%)
            // Defender receives unblocked hit: +9.0 units (+3%)
            // Defender absorbs blocked hit: +4.5 units (+1.5%)
            float attackerPowerGain = 15.0f;
            float defenderPowerGain = (isBlocking && !isUnblockable) ? 4.5f : 9.0f;

            attacker.AddPower(attackerPowerGain);
            defender.AddPower(defenderPowerGain);

            // 10. Hit-Stun & Knockdown Duration
            bool triggersKnockdown = hit.TriggersKnockdown || isHeavy;
            float calculatedHitStun = triggersKnockdown ? 1.20f : hit.HitStunDuration;

            result.TargetKnockedDown = triggersKnockdown;
            result.HitStunDuration = calculatedHitStun;
            result.Payload = new DamagePayload
            {
                RawAttack = rawAttack,
                IsCrit = isCrit,
                CritMultiplier = critMultiplier,
                IsBlocked = isBlocking && !isUnblockable,
                IsParry = false,
                IsUnblockable = isUnblockable,
                FinalDamage = finalDamage,
                ChipDamage = chipDamage,
                AttackType = hit.AttackType,
                TriggersKnockdown = triggersKnockdown
            };

            // Apply health and push state payload
            defender.ApplyHealthReduction(finalDamage);
            defender.StateMachine.OnHitReceived(ref result.Payload);

            return result;
        }
    }

    #endregion

    #region Entity Extensions Stub

    public partial class CombatEntity
    {
        public ChampionClass Class = ChampionClass.COSMIC;
        public CombatStats Stats = new CombatStats
        {
            AttackRating = 2500f,
            ArmorRating = 600f,
            BlockProficiency = 2600f,
            CritRating = 550f,
            CritDamageRating = 800f,
            DefenderCritResistance = 0f
        };

        public float BlockHoldDuration { get; set; }
        public float StunDurationBonus { get; set; } = 0.25f;
        public bool HasDexterityCritBuff { get; private set; }

        public void ConsumeDexterityCritBuff() => HasDexterityCritBuff = false;
        public void AddPower(float amount) => PowerMeter = Mathf.Clamp(PowerMeter + amount, 0f, 300f);
    }

    #endregion
}
