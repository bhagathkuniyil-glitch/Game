using System;
using System.Collections.Generic;
using System.Linq;
using UnityEngine;

namespace MCOC.CombatEngine.StatusEffects
{
    #region Enums & Context Contracts

    public enum EffectTier
    {
        BUFF,    // Positive modifier; strippable by Nullify, Stagger, Fate Seal
        DEBUFF,  // Negative modifier; strippable by Purify, Cleanse; subject to tag immunities
        PASSIVE  // Engine-level modifier; strictly immune to all Nullify & Purify actions
    }

    public enum EffectId
    {
        // Buffs
        FURY,
        ARMOR_UP,
        PRECISION,
        CRUELTY,
        REGENERATION,

        // Debuffs
        BLEED,
        POISON,
        SHOCK,
        INCINERATE,
        ARMOR_BREAK,
        STUN,
        DEGENERATION,

        // Passives
        PROWESS,
        UNSTOPPABLE,
        UNBLOCKABLE,
        ROOT,
        POWER_STING,
        DAMNATION
    }

    public class CombatContext
    {
        public CombatEntity Attacker { get; }
        public CombatEntity Defender { get; }
        public float DeltaTime { get; set; }

        public CombatContext(CombatEntity attacker, CombatEntity defender, float deltaTime = 0f)
        {
            Attacker = attacker;
            Defender = defender;
            DeltaTime = deltaTime;
        }
    }

    #endregion

    #region Base Status Effect Instance

    public abstract class StatusEffectInstance
    {
        public EffectId Id { get; }
        public EffectTier Tier { get; }
        public string Name { get; }
        public float Duration { get; }
        public float TimeRemaining { get; set; }
        public float Potency { get; }
        public int Stacks { get; set; }
        public int MaxStacks { get; }
        public bool IsIndefinite { get; }
        public string Source { get; }

        public bool IsExpired => !IsIndefinite && TimeRemaining <= 0f;

        protected StatusEffectInstance(
            EffectId id,
            EffectTier tier,
            string name,
            float duration,
            float potency = 1.0f,
            int stacks = 1,
            int maxStacks = 99,
            bool isIndefinite = false,
            string source = "Engine")
        {
            Id = id;
            Tier = tier;
            Name = name;
            Duration = duration;
            TimeRemaining = duration;
            Potency = potency;
            Stacks = stacks;
            MaxStacks = maxStacks;
            IsIndefinite = isIndefinite;
            Source = source;
        }

        public virtual void OnApplied(CombatEntity host) { }
        public virtual void OnRemoved(CombatEntity host) { }

        // Lifecycle Hooks
        public virtual void OnAttackLanded(CombatContext ctx, AttackType hitType, ref DamagePayload payload) { }
        public virtual void OnAttackReceived(CombatContext ctx, AttackType hitType, ref DamagePayload payload) { }
        public virtual void OnBlock(CombatContext ctx, bool isParry, ref DamagePayload payload) { }
        public virtual void OnTick(CombatContext ctx, float deltaTime)
        {
            if (!IsIndefinite)
            {
                TimeRemaining -= deltaTime;
            }
        }
    }

    #endregion

    #region Concrete Effect Implementations

    /// <summary>
    /// BUFF: Fury increases outgoing strike damage.
    /// Strippable by Nullify / Stagger / Fate Seal.
    /// </summary>
    public class FuryBuff : StatusEffectInstance
    {
        public FuryBuff(float duration = 10f, float potency = 0.40f, int stacks = 1, string source = "Base")
            : base(EffectId.FURY, EffectTier.BUFF, "Fury", duration, potency, stacks, 10, false, source) { }

        public override void OnAttackLanded(CombatContext ctx, AttackType hitType, ref DamagePayload payload)
        {
            // Boost raw damage output by potency * stacks
            payload.RawAttack += (payload.RawAttack * Potency * Stacks);
        }
    }

    /// <summary>
    /// BUFF: Armor Up increases armor rating to mitigate incoming damage.
    /// </summary>
    public class ArmorUpBuff : StatusEffectInstance
    {
        public ArmorUpBuff(float duration = 12f, float potency = 500f, int stacks = 1, string source = "Base")
            : base(EffectId.ARMOR_UP, EffectTier.BUFF, "Armor Up", duration, potency, stacks, 5, false, source) { }
    }

    /// <summary>
    /// DEBUFF: Bleed deals direct physical damage over time.
    /// Strippable by Purify / Cleanse. Nullified by #Robot or Bleed immunity.
    /// </summary>
    public class BleedDebuff : StatusEffectInstance
    {
        private float _tickTimer = 0f;

        public BleedDebuff(float duration = 6f, float dps = 250f, int stacks = 1, string source = "Bleed")
            : base(EffectId.BLEED, EffectTier.DEBUFF, "Bleed", duration, dps, stacks, 20, false, source) { }

        public override void OnTick(CombatContext ctx, float deltaTime)
        {
            base.OnTick(ctx, deltaTime);
            _tickTimer += deltaTime;
            if (_tickTimer >= 0.5f)
            {
                _tickTimer = 0f;
                float tickDamage = Potency * Stacks * 0.5f;
                ctx.Defender?.ApplyHealthReduction(tickDamage);
            }
        }
    }

    /// <summary>
    /// DEBUFF: Poison deals direct DoT and decreases all incoming healing by 30% per stack.
    /// Nullified by #Robot or Poison immunity.
    /// </summary>
    public class PoisonDebuff : StatusEffectInstance
    {
        private float _tickTimer = 0f;

        public PoisonDebuff(float duration = 8f, float dps = 180f, int stacks = 1, string source = "Poison")
            : base(EffectId.POISON, EffectTier.DEBUFF, "Poison", duration, dps, stacks, 10, false, source) { }

        public override void OnTick(CombatContext ctx, float deltaTime)
        {
            base.OnTick(ctx, deltaTime);
            _tickTimer += deltaTime;
            if (_tickTimer >= 0.5f)
            {
                _tickTimer = 0f;
                float tickDamage = Potency * Stacks * 0.5f;
                ctx.Defender?.ApplyHealthReduction(tickDamage);
            }
        }

        public float HealingReductionMultiplier => Mathf.Clamp01(1.0f - (0.30f * Stacks));
    }

    /// <summary>
    /// PASSIVE: Unstoppable prevents hit-stun during attacks.
    /// IMMUNE to Nullify and Purify actions.
    /// </summary>
    public class UnstoppablePassive : StatusEffectInstance
    {
        public UnstoppablePassive(float duration = 5f, string source = "Passive Kit")
            : base(EffectId.UNSTOPPABLE, EffectTier.PASSIVE, "Unstoppable", duration, 1.0f, 1, 1, false, source) { }

        public override void OnAttackReceived(CombatContext ctx, AttackType hitType, ref DamagePayload payload)
        {
            // Negate flinch/hit-stun push
            payload.TriggersKnockdown = false;
        }
    }

    /// <summary>
    /// PASSIVE: Prowess amplifies Special Attack damage.
    /// IMMUNE to Nullify and Purify actions.
    /// </summary>
    public class ProwessPassive : StatusEffectInstance
    {
        public ProwessPassive(float duration = 15f, float specialDmgBonus = 0.25f, int stacks = 1, string source = "Mutant")
            : base(EffectId.PROWESS, EffectTier.PASSIVE, "Prowess", duration, specialDmgBonus, stacks, 10, false, source) { }

        public override void OnAttackLanded(CombatContext ctx, AttackType hitType, ref DamagePayload payload)
        {
            if (hitType == AttackType.SPECIAL_1 || hitType == AttackType.SPECIAL_2 || hitType == AttackType.SPECIAL_3)
            {
                payload.RawAttack += (payload.RawAttack * Potency * Stacks);
            }
        }
    }

    #endregion

    #region Event Bus Interface & Implementation

    public interface IStatusEffectEventBus
    {
        void PublishAttackLanded(CombatContext ctx, AttackType hitType, ref DamagePayload payload);
        void PublishAttackReceived(CombatContext ctx, AttackType hitType, ref DamagePayload payload);
        void PublishBlock(CombatContext ctx, bool isParry, ref DamagePayload payload);
        void PublishTick(CombatContext ctx, float deltaTime);
    }

    /// <summary>
    /// StatusEffectManager:
    /// Manages three strictly separated collections for BUFF, DEBUFF, and PASSIVE tiers.
    /// Enforces Nullify restrictions (Buffs only), Purify restrictions (Debuffs only),
    /// and innate tag immunities (#Robot immune to Bleed & Poison).
    /// </summary>
    public class StatusEffectManager : IStatusEffectEventBus
    {
        private readonly CombatEntity _owner;

        // Three-tier strictly partitioned lists
        private readonly List<StatusEffectInstance> _buffs = new List<StatusEffectInstance>();
        private readonly List<StatusEffectInstance> _debuffs = new List<StatusEffectInstance>();
        private readonly List<StatusEffectInstance> _passives = new List<StatusEffectInstance>();

        public IReadOnlyList<StatusEffectInstance> Buffs => _buffs;
        public IReadOnlyList<StatusEffectInstance> Debuffs => _debuffs;
        public IReadOnlyList<StatusEffectInstance> Passives => _passives;

        // Tag Immunities (#Robot, etc.)
        private readonly HashSet<EffectId> _immunities = new HashSet<EffectId>();

        public event Action<StatusEffectInstance> OnBuffGained;
        public event Action<StatusEffectInstance> OnBuffNullified;
        public event Action<StatusEffectInstance> OnDebuffApplied;
        public event Action<StatusEffectInstance> OnDebuffPurified;
        public event Action<EffectId> OnImmunityTriggered;

        public StatusEffectManager(CombatEntity owner, IEnumerable<string> championTags = null)
        {
            _owner = owner;

            // Innate #Robot tag grants permanent immunity to Bleed and Poison
            if (championTags != null && championTags.Any(t => t.Equals("#Robot", StringComparison.OrdinalIgnoreCase) ||
                                                              t.Equals("Robot", StringComparison.OrdinalIgnoreCase)))
            {
                _immunities.Add(EffectId.BLEED);
                _immunities.Add(EffectId.POISON);
            }
        }

        public void AddImmunity(EffectId effectId) => _immunities.Add(effectId);

        public bool IsImmuneTo(EffectId effectId) => _immunities.Contains(effectId);

        #region Application & Stacking

        public bool ApplyEffect(StatusEffectInstance effect)
        {
            if (effect == null) return false;

            // 1. Tag & Innate Immunity Check for Debuffs
            if (effect.Tier == EffectTier.DEBUFF && IsImmuneTo(effect.Id))
            {
                OnImmunityTriggered?.Invoke(effect.Id);
                return false;
            }

            // 2. Select appropriate tier container
            List<StatusEffectInstance> targetList = effect.Tier switch
            {
                EffectTier.BUFF => _buffs,
                EffectTier.DEBUFF => _debuffs,
                EffectTier.PASSIVE => _passives,
                _ => throw new ArgumentOutOfRangeException()
            };

            // 3. Stacking check
            var existing = targetList.FirstOrDefault(e => e.Id == effect.Id && !e.IsIndefinite);
            if (existing != null && existing.Stacks < existing.MaxStacks)
            {
                existing.Stacks = Mathf.Min(existing.Stacks + effect.Stacks, existing.MaxStacks);
                existing.TimeRemaining = effect.Duration; // Refresh duration
                return true;
            }

            // 4. Register new instance
            targetList.Add(effect);
            effect.OnApplied(_owner);

            if (effect.Tier == EffectTier.BUFF) OnBuffGained?.Invoke(effect);
            else if (effect.Tier == EffectTier.DEBUFF) OnDebuffApplied?.Invoke(effect);

            return true;
        }

        #endregion

        #region Nullify, Purify & Stripping Pipelines

        /// <summary>
        /// Nullify: Mystic archetype action.
        /// Strips BUFFS only. Fails unconditionally against PASSIVES and DEBUFFS.
        /// </summary>
        public int NullifyBuffs(EffectId? targetId = null)
        {
            List<StatusEffectInstance> toRemove = targetId.HasValue
                ? _buffs.Where(b => b.Id == targetId.Value).ToList()
                : new List<StatusEffectInstance>(_buffs);

            for (int i = 0; i < toRemove.Count; i++)
            {
                StatusEffectInstance buff = toRemove[i];
                _buffs.Remove(buff);
                buff.OnRemoved(_owner);
                OnBuffNullified?.Invoke(buff);
            }

            return toRemove.Count;
        }

        /// <summary>
        /// Purify: Skill archetype action (Cleanse / Shrug-Off).
        /// Strips DEBUFFS only. Fails unconditionally against PASSIVES and BUFFS.
        /// </summary>
        public int PurifyDebuffs(EffectId? targetId = null)
        {
            List<StatusEffectInstance> toRemove = targetId.HasValue
                ? _debuffs.Where(d => d.Id == targetId.Value).ToList()
                : new List<StatusEffectInstance>(_debuffs);

            for (int i = 0; i < toRemove.Count; i++)
            {
                StatusEffectInstance debuff = toRemove[i];
                _debuffs.Remove(debuff);
                debuff.OnRemoved(_owner);
                OnDebuffPurified?.Invoke(debuff);
            }

            return toRemove.Count;
        }

        #endregion

        #region Event Bus Lifecycle Hooks Dispatch

        public void PublishAttackLanded(CombatContext ctx, AttackType hitType, ref DamagePayload payload)
        {
            for (int i = 0; i < _buffs.Count; i++) _buffs[i].OnAttackLanded(ctx, hitType, ref payload);
            for (int i = 0; i < _debuffs.Count; i++) _debuffs[i].OnAttackLanded(ctx, hitType, ref payload);
            for (int i = 0; i < _passives.Count; i++) _passives[i].OnAttackLanded(ctx, hitType, ref payload);
        }

        public void PublishAttackReceived(CombatContext ctx, AttackType hitType, ref DamagePayload payload)
        {
            for (int i = 0; i < _buffs.Count; i++) _buffs[i].OnAttackReceived(ctx, hitType, ref payload);
            for (int i = 0; i < _debuffs.Count; i++) _debuffs[i].OnAttackReceived(ctx, hitType, ref payload);
            for (int i = 0; i < _passives.Count; i++) _passives[i].OnAttackReceived(ctx, hitType, ref payload);
        }

        public void PublishBlock(CombatContext ctx, bool isParry, ref DamagePayload payload)
        {
            for (int i = 0; i < _buffs.Count; i++) _buffs[i].OnBlock(ctx, isParry, ref payload);
            for (int i = 0; i < _debuffs.Count; i++) _debuffs[i].OnBlock(ctx, isParry, ref payload);
            for (int i = 0; i < _passives.Count; i++) _passives[i].OnBlock(ctx, isParry, ref payload);
        }

        public void PublishTick(CombatContext ctx, float deltaTime)
        {
            // Tick & prune Buffs
            for (int i = _buffs.Count - 1; i >= 0; i--)
            {
                _buffs[i].OnTick(ctx, deltaTime);
                if (_buffs[i].IsExpired)
                {
                    var exp = _buffs[i];
                    _buffs.RemoveAt(i);
                    exp.OnRemoved(_owner);
                }
            }

            // Tick & prune Debuffs
            for (int i = _debuffs.Count - 1; i >= 0; i--)
            {
                _debuffs[i].OnTick(ctx, deltaTime);
                if (_debuffs[i].IsExpired)
                {
                    var exp = _debuffs[i];
                    _debuffs.RemoveAt(i);
                    exp.OnRemoved(_owner);
                }
            }

            // Tick & prune Passives
            for (int i = _passives.Count - 1; i >= 0; i--)
            {
                _passives[i].OnTick(ctx, deltaTime);
                if (_passives[i].IsExpired)
                {
                    var exp = _passives[i];
                    _passives.RemoveAt(i);
                    exp.OnRemoved(_owner);
                }
            }
        }

        #endregion
    }

    #endregion
}
