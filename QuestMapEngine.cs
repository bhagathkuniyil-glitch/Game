using System;
using System.Collections.Generic;
using System.Linq;
using UnityEngine;

namespace MCOC.QuestEngine
{
    #region Enums & Data Contracts

    public enum TileType
    {
        START,
        EMPTY,
        CHEST,
        PORTAL,
        FIGHT,
        BOSS
    }

    public enum TileState
    {
        LOCKED,       // Inaccessible; path has not reached this node
        ACCESSIBLE,   // Connected to current node; can be traversed if energy permits
        CURRENT,      // Player champion is currently standing on this node
        CLEARED       // Objective completed; path explored
    }

    [Serializable]
    public struct QuestReward
    {
        public int Gold;
        public int Iso8;
        public int Units;
        public string ItemDrop;

        public static QuestReward StandardFight => new QuestReward { Gold = 2500, Iso8 = 15, Units = 5 };
        public static QuestReward BossVictory => new QuestReward { Gold = 10000, Iso8 = 50, Units = 25, ItemDrop = "Tier 4 Basic Catalyst" };
        public static QuestReward ChestBonus => new QuestReward { Gold = 5000, Iso8 = 20, Units = 10 };
    }

    #endregion

    #region Linked Node Modifier Contracts (Decorator/Observer Pattern)

    public interface INodeModifier
    {
        string ModifierId { get; }
        string Name { get; }
        string Description { get; }
        bool IsLinked { get; }
        string LinkedSourceNodeId { get; }
        bool IsDeactivated { get; }
        void Deactivate();
    }

    public class BaseNodeModifier : INodeModifier
    {
        public string ModifierId { get; }
        public string Name { get; }
        public string Description { get; }
        public bool IsLinked { get; }
        public string LinkedSourceNodeId { get; }
        public bool IsDeactivated { get; private set; }

        public BaseNodeModifier(string id, string name, string desc, bool isLinked = false, string sourceNodeId = null)
        {
            ModifierId = id;
            Name = name;
            Description = desc;
            IsLinked = isLinked;
            LinkedSourceNodeId = sourceNodeId;
            IsDeactivated = false;
        }

        public void Deactivate()
        {
            IsDeactivated = true;
        }
    }

    #endregion

    #region Quest Node Model

    public class QuestNode
    {
        public string Id { get; }
        public string Name { get; set; }
        public TileType Type { get; }
        public Vector2 Coordinates { get; set; } // Visual map coordinates (normalized or pixel)
        public int EnergyCost { get; set; } = 2;

        public TileState State { get; set; } = TileState.LOCKED;
        public bool HasBeenExplored { get; set; } = false;

        // Graph Connectivity
        public List<string> OutgoingNodeIds { get; } = new List<string>();
        public List<string> IncomingNodeIds { get; } = new List<string>();

        // Modifiers & Puzzle Mechanics
        public List<INodeModifier> LocalModifiers { get; } = new List<INodeModifier>();
        public List<INodeModifier> ActiveLinkedModifiers { get; } = new List<INodeModifier>();

        /// <summary>
        /// If this node is cleared, automatically broadcasts deactivation to linked target node.
        /// E.g. Node 3 clearing deactivates the +300% Health Linked Node on Boss Node 5.
        /// </summary>
        public string LinkedDeactivationTargetId { get; set; }

        // Combat & Rewards
        public string DefenderChampionId { get; set; }
        public QuestReward Reward { get; set; }

        public QuestNode(string id, string name, TileType type, Vector2 coords, int energyCost = 2)
        {
            Id = id;
            Name = name;
            Type = type;
            Coordinates = coords;
            EnergyCost = energyCost;
        }

        public IEnumerable<INodeModifier> GetAllActiveModifiers()
        {
            foreach (var mod in LocalModifiers)
            {
                if (!mod.IsDeactivated) yield return mod;
            }
            foreach (var linked in ActiveLinkedModifiers)
            {
                if (!linked.IsDeactivated) yield return linked;
            }
        }
    }

    #endregion

    #region Energy System

    public class EnergySystem
    {
        public int MaxEnergy { get; }
        public int CurrentEnergy { get; private set; }
        public float SecondsPerEnergyUnit { get; }
        private float _rechargeTimer = 0f;

        public event Action<int, int> OnEnergyChanged;

        public EnergySystem(int maxEnergy = 30, float secondsPerUnit = 300f) // 5 min per energy unit
        {
            MaxEnergy = maxEnergy;
            CurrentEnergy = maxEnergy;
            SecondsPerEnergyUnit = secondsPerUnit;
        }

        public bool HasEnergy(int amount) => CurrentEnergy >= amount;

        public bool Consume(int amount)
        {
            if (CurrentEnergy >= amount)
            {
                CurrentEnergy -= amount;
                OnEnergyChanged?.Invoke(CurrentEnergy, MaxEnergy);
                return true;
            }
            return false;
        }

        public void Refill()
        {
            CurrentEnergy = MaxEnergy;
            OnEnergyChanged?.Invoke(CurrentEnergy, MaxEnergy);
        }

        public void UpdateRecharge(float deltaTime)
        {
            if (CurrentEnergy >= MaxEnergy) return;

            _rechargeTimer += deltaTime;
            if (_rechargeTimer >= SecondsPerEnergyUnit)
            {
                _rechargeTimer = 0f;
                CurrentEnergy = Mathf.Min(CurrentEnergy + 1, MaxEnergy);
                OnEnergyChanged?.Invoke(CurrentEnergy, MaxEnergy);
            }
        }
    }

    #endregion

    #region Quest Map Graph Engine

    /// <summary>
    /// Quest Map Graph Engine:
    /// Manages graph traversal, branching paths, energy deductions,
    /// linked node deactivations, exploration percentages, and fight triggers.
    /// </summary>
    public class QuestMapEngine
    {
        public string MapId { get; }
        public string Title { get; }
        public int Act { get; }
        public int Chapter { get; }

        public EnergySystem Energy { get; }
        private readonly Dictionary<string, QuestNode> _nodes = new Dictionary<string, QuestNode>();

        public QuestNode CurrentNode { get; private set; }
        public QuestNode BossNode { get; private set; }
        public float ExplorationPercentage { get; private set; }

        // Event Bus Observers
        public event Action<QuestNode, QuestNode, int> OnPlayerTraversed; // (fromNode, toNode, energyCost)
        public event Action<QuestNode> OnCombatEncounterTriggered;         // (fightNode)
        public event Action<QuestNode, QuestReward> OnRewardClaimed;       // (node, reward)
        public event Action<string, string, INodeModifier> OnLinkedNodeDeactivated; // (sourceId, targetId, modifier)
        public event Action<float> OnExplorationUpdated;                   // (percent)
        public event Action OnQuestCompleted;

        public QuestMapEngine(string mapId, string title, int act, int chapter, int maxEnergy = 30)
        {
            MapId = mapId;
            Title = title;
            Act = act;
            Chapter = chapter;
            Energy = new EnergySystem(maxEnergy);
        }

        #region Graph Construction

        public void AddNode(QuestNode node)
        {
            _nodes[node.Id] = node;
            if (node.Type == TileType.BOSS)
            {
                BossNode = node;
            }
        }

        public void ConnectNodes(string fromId, string toId)
        {
            if (_nodes.TryGetValue(fromId, out var fromNode) && _nodes.TryGetValue(toId, out var toNode))
            {
                if (!fromNode.OutgoingNodeIds.Contains(toId)) fromNode.OutgoingNodeIds.Add(toId);
                if (!toNode.IncomingNodeIds.Contains(fromId)) toNode.IncomingNodeIds.Add(fromId);
            }
        }

        public void SetStartNode(string startId)
        {
            if (_nodes.TryGetValue(startId, out var startNode))
            {
                CurrentNode = startNode;
                startNode.State = TileState.CURRENT;
                startNode.HasBeenExplored = true;
                UpdateAdjacentAccessibility(startNode);
                RecalculateExploration();
            }
        }

        #endregion

        #region Traversal & Validation

        public bool CanTraverse(string targetNodeId, out string failureReason)
        {
            if (!_nodes.TryGetValue(targetNodeId, out var targetNode))
            {
                failureReason = "Target node does not exist.";
                return false;
            }

            if (CurrentNode == null)
            {
                failureReason = "No current position set.";
                return false;
            }

            // Must have a direct directed edge from CurrentNode
            if (!CurrentNode.OutgoingNodeIds.Contains(targetNodeId))
            {
                failureReason = $"No direct path exists from {CurrentNode.Name} to {targetNode.Name}.";
                return false;
            }

            // Node must be accessible
            if (targetNode.State != TileState.ACCESSIBLE)
            {
                failureReason = "Target node is locked.";
                return false;
            }

            // Energy check
            if (!Energy.HasEnergy(targetNode.EnergyCost))
            {
                failureReason = $"Insufficient energy ({Energy.CurrentEnergy}/{targetNode.EnergyCost} required).";
                return false;
            }

            failureReason = string.Empty;
            return true;
        }

        public bool TraverseToNode(string targetNodeId)
        {
            if (!CanTraverse(targetNodeId, out _)) return false;

            QuestNode prevNode = CurrentNode;
            QuestNode targetNode = _nodes[targetNodeId];

            // 1. Consume Energy
            Energy.Consume(targetNode.EnergyCost);

            // 2. Update Node States
            prevNode.State = TileState.CLEARED;
            CurrentNode = targetNode;
            targetNode.State = TileState.CURRENT;
            targetNode.HasBeenExplored = true;

            RecalculateExploration();
            OnPlayerTraversed?.Invoke(prevNode, targetNode, targetNode.EnergyCost);

            // 3. Process Tile Type Trigger
            switch (targetNode.Type)
            {
                case TileType.CHEST:
                    ClaimReward(targetNode);
                    UpdateAdjacentAccessibility(targetNode);
                    break;

                case TileType.EMPTY:
                case TileType.START:
                    UpdateAdjacentAccessibility(targetNode);
                    break;

                case TileType.FIGHT:
                case TileType.BOSS:
                    // Stop traversal until combat concludes
                    OnCombatEncounterTriggered?.Invoke(targetNode);
                    break;
            }

            return true;
        }

        #endregion

        #region Combat Completion & Linked Node Resolution

        /// <summary>
        /// Called when the player finishes combat on the active fight/boss tile.
        /// </summary>
        public void CompleteCombat(bool victory)
        {
            if (CurrentNode == null) return;
            if (CurrentNode.Type != TileType.FIGHT && CurrentNode.Type != TileType.BOSS) return;

            if (victory)
            {
                // 1. Resolve Linked Node Deactivations (Observer / Decorator Trigger)
                if (!string.IsNullOrEmpty(CurrentNode.LinkedDeactivationTargetId))
                {
                    ResolveLinkedDeactivation(CurrentNode.Id, CurrentNode.LinkedDeactivationTargetId);
                }

                // 2. Claim Rewards
                ClaimReward(CurrentNode);

                // 3. Unlock subsequent paths
                UpdateAdjacentAccessibility(CurrentNode);

                // 4. Boss clearance check
                if (CurrentNode.Type == TileType.BOSS)
                {
                    OnQuestCompleted?.Invoke();
                }
            }
        }

        private void ResolveLinkedDeactivation(string sourceNodeId, string targetNodeId)
        {
            if (_nodes.TryGetValue(targetNodeId, out var targetNode))
            {
                var linkedMods = targetNode.ActiveLinkedModifiers
                    .Where(m => m.LinkedSourceNodeId == sourceNodeId && !m.IsDeactivated)
                    .ToList();

                foreach (var mod in linkedMods)
                {
                    mod.Deactivate();
                    OnLinkedNodeDeactivated?.Invoke(sourceNodeId, targetNodeId, mod);
                }
            }
        }

        private void ClaimReward(QuestNode node)
        {
            if (node.Reward.Gold > 0 || node.Reward.Iso8 > 0 || node.Reward.Units > 0)
            {
                OnRewardClaimed?.Invoke(node, node.Reward);
            }
        }

        private void UpdateAdjacentAccessibility(QuestNode node)
        {
            foreach (var outgoingId in node.OutgoingNodeIds)
            {
                if (_nodes.TryGetValue(outgoingId, out var nextNode))
                {
                    if (nextNode.State == TileState.LOCKED)
                    {
                        nextNode.State = TileState.ACCESSIBLE;
                    }
                }
            }
        }

        private void RecalculateExploration()
        {
            if (_nodes.Count == 0) return;
            int explored = _nodes.Values.Count(n => n.HasBeenExplored);
            ExplorationPercentage = ((float)explored / _nodes.Count) * 100f;
            OnExplorationUpdated?.Invoke(ExplorationPercentage);
        }

        #endregion

        #region Query Helpers

        public QuestNode GetNode(string id) => _nodes.TryGetValue(id, out var n) ? n : null;
        public IEnumerable<QuestNode> GetAllNodes() => _nodes.Values;

        #endregion

        #region Factory for Authentic MCOC Branching Gauntlet

        /// <summary>
        /// Constructs a classic MCOC Branching Gauntlet:
        /// - Node 1: Start Portal
        /// - Branch A (Top Route): Node 2 (Sentinel) -> Node 3 (Linked Overclock Relay) -> Boss
        /// - Branch B (Bottom Route): Node 4 (Biohazard Corridor) -> Boss
        /// - Boss Node: Has LinkedNode modifier (+150% HP) tied to Node 3.
        /// Clearing Node 3 shatters the Boss's Linked buff!
        /// </summary>
        public static QuestMapEngine CreateAct1Chapter1()
        {
            var map = new QuestMapEngine("act1_ch1", "Act 1: Battlerealm Crucible", 1, 1, maxEnergy: 30);

            // Define Nodes
            var n1 = new QuestNode("node_1", "Portal Ingress", TileType.START, new Vector2(0.10f, 0.50f), energyCost: 0);

            var n2 = new QuestNode("node_2", "Tech Sentinel Outpost", TileType.FIGHT, new Vector2(0.35f, 0.25f), energyCost: 2)
            {
                DefenderChampionId = "nimrod_tech",
                Reward = QuestReward.StandardFight
            };
            n2.LocalModifiers.Add(new BaseNodeModifier("mod_kinetic", "Kinetic Transference", "Blocking gains extra power"));

            var n3 = new QuestNode("node_3", "Mystic Power Relay", TileType.FIGHT, new Vector2(0.65f, 0.25f), energyCost: 2)
            {
                DefenderChampionId = "doom_mystic",
                Reward = QuestReward.StandardFight,
                LinkedDeactivationTargetId = "node_boss" // Clears Linked buff on Boss!
            };
            n3.LocalModifiers.Add(new BaseNodeModifier("mod_bane", "Bane", "Curse ticks 2.5% HP and transfers on strike"));

            var n4 = new QuestNode("node_4", "Bio-Toxic Corridor", TileType.FIGHT, new Vector2(0.50f, 0.75f), energyCost: 3)
            {
                DefenderChampionId = "archangel_mutant",
                Reward = QuestReward.StandardFight
            };
            n4.LocalModifiers.Add(new BaseNodeModifier("mod_biohazard", "Biohazard", "100% chance to bleed on contact unless Robot/Immune"));

            var nBoss = new QuestNode("node_boss", "Grandmaster's Champion", TileType.BOSS, new Vector2(0.90f, 0.50f), energyCost: 3)
            {
                DefenderChampionId = "cgr_cosmic",
                Reward = QuestReward.BossVictory
            };
            nBoss.LocalModifiers.Add(new BaseNodeModifier("mod_lifecycle", "Lifecycle", "Boss indestructible until knocked down by Heavy strike"));

            // Add Linked Modifier to Boss (connected to Node 3)
            nBoss.ActiveLinkedModifiers.Add(new BaseNodeModifier(
                "mod_linked_overclock",
                "Linked: Overclock",
                "Grants Boss +150% Health & +80% Attack until Node 3 is cleared!",
                isLinked: true,
                sourceNodeId: "node_3"
            ));

            // Register Nodes
            map.AddNode(n1);
            map.AddNode(n2);
            map.AddNode(n3);
            map.AddNode(n4);
            map.AddNode(nBoss);

            // Connect Branching Paths
            map.ConnectNodes("node_1", "node_2");
            map.ConnectNodes("node_2", "node_3");
            map.ConnectNodes("node_3", "node_boss"); // Path A
            map.ConnectNodes("node_1", "node_4");
            map.ConnectNodes("node_4", "node_boss"); // Path B

            // Initialize Player at Start
            map.SetStartNode("node_1");

            return map;
        }

        #endregion
    }
}
