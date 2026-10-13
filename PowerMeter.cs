using System;
using UnityEngine;

namespace MCOC.CombatEngine
{
    public enum SpecialTier
    {
        NONE = 0,
        SP1 = 1,
        SP2 = 2,
        SP3 = 3
    }

    /// <summary>
    /// Power Meter Management System (MCOC Specification).
    /// Tracks 300-unit capacity across 3 discrete bars (100 units each)
    /// and calculates gain mechanics for attacking, taking damage, and blocking,
    /// alongside Power Lock, Power Drain, and Power Burn pipelines.
    /// </summary>
    public class PowerMeter
    {
        #region Constants

        public const float MaxCapacity = 300.0f;
        public const float BarCapacity = 100.0f;
        public const int MaxBars = 3;

        // Base MCOC Power Economics
        public const float AttackerHitGainBase = 15.0f;    // +5.0% of max per strike landed
        public const float DefenderUnblockedGainBase = 9.0f; // +3.0% of max per unblocked strike taken
        public const float DefenderBlockedGainBase = 4.5f;   // +1.5% of max per blocked strike absorbed

        #endregion

        #region State Properties

        public float CurrentPower { get; private set; } = 0.0f;
        public bool IsPowerLocked { get; private set; } = false;

        public int AvailableBars => Mathf.FloorToInt(CurrentPower / BarCapacity);
        public float TotalFillPercentage => CurrentPower / MaxCapacity;
        public bool IsFull => CurrentPower >= MaxCapacity;

        #endregion

        #region Events

        public event Action<float, float> OnPowerChanged; // (currentPower, maxPower)
        public event Action<int> OnBarFilled;             // (barNumber: 1, 2, or 3)
        public event Action<SpecialTier> OnSpecialReady;   // (highestTierReady)

        #endregion

        public PowerMeter(float initialPower = 0.0f)
        {
            CurrentPower = Mathf.Clamp(initialPower, 0.0f, MaxCapacity);
        }

        #region Gain Mechanics

        /// <summary>
        /// Called when the entity successfully lands an offensive strike on the opponent.
        /// Base gain: +15.0 units (+5% of max meter).
        /// </summary>
        public float OnAttackLanded(float gainRateMultiplier = 1.0f)
        {
            if (IsPowerLocked) return 0f;

            float gain = AttackerHitGainBase * Mathf.Max(0f, gainRateMultiplier);
            AddPower(gain);
            return gain;
        }

        /// <summary>
        /// Called when the entity receives an incoming hostile strike.
        /// - Unblocked strike: +9.0 units (+3% of max meter).
        /// - Blocked strike: +4.5 units (+1.5% of max meter).
        /// </summary>
        public float OnDamageReceived(bool wasBlocked, float gainRateMultiplier = 1.0f)
        {
            if (IsPowerLocked) return 0f;

            float baseGain = wasBlocked ? DefenderBlockedGainBase : DefenderUnblockedGainBase;
            float gain = baseGain * Mathf.Max(0f, gainRateMultiplier);
            AddPower(gain);
            return gain;
        }

        /// <summary>
        /// Adds a custom amount of power (e.g. Kinetic Transference or passive Power Gain buffs).
        /// </summary>
        public void AddPower(float amount)
        {
            if (IsPowerLocked || amount <= 0f) return;

            int prevBars = AvailableBars;
            CurrentPower = Mathf.Clamp(CurrentPower + amount, 0.0f, MaxCapacity);

            OnPowerChanged?.Invoke(CurrentPower, MaxCapacity);

            int newBars = AvailableBars;
            if (newBars > prevBars)
            {
                for (int b = prevBars + 1; b <= newBars; b++)
                {
                    OnBarFilled?.Invoke(b);
                }
                OnSpecialReady?.Invoke((SpecialTier)newBars);
            }
        }

        #endregion

        #region Consumption & Specials

        /// <summary>
        /// Checks if the entity has sufficient power to activate a Special Attack tier (1, 2, or 3).
        /// </summary>
        public bool CanCastSpecial(SpecialTier tier)
        {
            int requiredUnits = (int)tier * (int)BarCapacity;
            return (int)tier > 0 && CurrentPower >= requiredUnits;
        }

        /// <summary>
        /// Consumes 100, 200, or 300 units of power for the specified Special Attack tier.
        /// Returns true if power was successfully deducted.
        /// </summary>
        public bool ConsumeForSpecial(SpecialTier tier)
        {
            float requiredUnits = (float)tier * BarCapacity;
            if (CurrentPower >= requiredUnits)
            {
                CurrentPower = Mathf.Clamp(CurrentPower - requiredUnits, 0.0f, MaxCapacity);
                OnPowerChanged?.Invoke(CurrentPower, MaxCapacity);
                return true;
            }
            return false;
        }

        #endregion

        #region Power Manipulation (Drain, Burn, Lock)

        /// <summary>
        /// Drains a flat amount of power units directly from the meter.
        /// </summary>
        public float ApplyPowerDrain(float amount)
        {
            if (amount <= 0f) return 0f;

            float previous = CurrentPower;
            CurrentPower = Mathf.Clamp(CurrentPower - amount, 0.0f, MaxCapacity);
            float drained = previous - CurrentPower;

            if (drained > 0f)
            {
                OnPowerChanged?.Invoke(CurrentPower, MaxCapacity);
            }
            return drained;
        }

        /// <summary>
        /// Drains power and deals direct damage proportional to the amount drained (Power Burn).
        /// </summary>
        public float ApplyPowerBurn(float amount, float damageMultiplierPerUnit, out float damageDealt)
        {
            float drained = ApplyPowerDrain(amount);
            damageDealt = drained * damageMultiplierPerUnit;
            return drained;
        }

        /// <summary>
        /// Engages or disengages Power Lock (e.g. from Damnation, Tech debuffs, or nodes).
        /// Prevents all power generation while active.
        /// </summary>
        public void SetPowerLock(bool locked)
        {
            IsPowerLocked = locked;
        }

        #endregion

        #region UI & Bar Helpers

        /// <summary>
        /// Returns the normalized fill ratio (0.0 to 1.0) of a specific bar index (1, 2, or 3).
        /// </summary>
        public float GetBarFillRatio(int barNumber)
        {
            if (barNumber < 1 || barNumber > MaxBars) return 0.0f;

            float barStart = (barNumber - 1) * BarCapacity;
            float powerInThisBar = Mathf.Clamp(CurrentPower - barStart, 0.0f, BarCapacity);
            return powerInThisBar / BarCapacity;
        }

        #endregion

        #region AI Decision Model

        /// <summary>
        /// Evaluates AI decision thresholds defined in Section 4.3:
        /// - Power in [100, 199]: Roll random check to fire SP1.
        /// - Power in [200, 299]: Roll check to fire SP2; hold SP1.
        /// - Power == 300: Immediately cast SP3 (unblockable cinematic lethal check).
        /// </summary>
        public SpecialTier EvaluateAiSpecialThreshold(float targetDistance, float strikeRange, float randomRoll01)
        {
            // Power == 300: Immediate SP3 lethal check
            if (CurrentPower >= 300.0f)
            {
                return SpecialTier.SP3;
            }

            // Power in [200, 299]: Roll check for SP2 (e.g. 65% chance when in range or baited)
            if (CurrentPower >= 200.0f)
            {
                bool inRange = targetDistance <= strikeRange * 1.8f;
                if (inRange && randomRoll01 < 0.65f)
                {
                    return SpecialTier.SP2;
                }
                return SpecialTier.NONE; // Hold SP1 to save for SP2
            }

            // Power in [100, 199]: Roll check for SP1 based on proximity
            if (CurrentPower >= 100.0f)
            {
                bool inProximity = targetDistance <= strikeRange * 1.2f;
                if (inProximity && randomRoll01 < 0.50f)
                {
                    return SpecialTier.SP1;
                }
            }

            return SpecialTier.NONE;
        }

        #endregion
    }
}
