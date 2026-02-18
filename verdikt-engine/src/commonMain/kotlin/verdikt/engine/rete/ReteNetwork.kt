package verdikt.engine.rete

import kotlin.reflect.KClass

/**
 * A compiled Rete network for a phase.
 *
 * The network contains:
 * - [alphaNodes]: Entry points organized by input type
 * - [betaNodes]: Join nodes for multi-fact conditions (future work - currently always empty)
 * - [outputNodes]: Terminal nodes that produce facts
 *
 * Facts enter the network via [activate], which routes them to
 * the appropriate alpha nodes based on their type.
 *
 * ## Current Implementation
 *
 * Currently, the network only supports single-fact rules where each rule's condition
 * tests one fact at a time. Facts flow: alpha node -> output node.
 *
 * ## Future Work: Multi-Fact Joins
 *
 * The [betaNodes] parameter exists for future support of multi-fact conditions
 * (joining facts A and B on a common key). When implemented, the flow will be:
 * alpha node -> beta node(s) -> output node. See [BetaNode] for details.
 *
 * @property alphaNodes Type-indexed entry points to the network
 * @property betaNodes Join nodes (infrastructure for future multi-fact rules - currently empty)
 * @property outputNodes Terminal nodes that produce outputs
 */
internal class ReteNetwork(
    val alphaNodes: Map<KClass<*>, List<AlphaNode<*>>>,
    val betaNodes: List<BetaNode<*>>,
    val outputNodes: List<OutputNode<*>>
) {
    /** Counter tracking total pending activations across all output nodes. O(1) check. */
    private var _pendingActivationCount: Int = 0

    /** Read-only accessor for the pending activation count. */
    val pendingActivationCount: Int get() = _pendingActivationCount

    /** Increment the pending activation count (called by OutputNode on enqueue). */
    fun incrementPendingActivations() { _pendingActivationCount++ }

    /** Decrement the pending activation count by [n] (called by OutputNode on fire/clear). */
    fun decrementPendingActivations(n: Int) { _pendingActivationCount -= n }

    /** Cache of polymorphic (supertype/interface) alpha nodes per fact class. */
    private val polymorphicNodeCache: MutableMap<KClass<*>, List<AlphaNode<*>>> = mutableMapOf()

    /**
     * Activate a fact through all applicable alpha nodes.
     *
     * The fact is routed to alpha nodes that accept its type.
     * If the fact passes the alpha node's test, it flows through
     * the network, potentially triggering output nodes.
     *
     * @param fact The fact to activate
     * @return true if any alpha node accepted the fact
     */
    @Suppress("UNCHECKED_CAST")
    fun activate(fact: Any): Boolean {
        var activated = false

        // Find alpha nodes that accept this fact's exact type
        val factClass = fact::class
        val nodes = alphaNodes[factClass]

        if (nodes != null) {
            // Use activateTyped fast-path: type is already guaranteed by exact-match dispatch
            for (alphaNode in nodes) {
                if ((alphaNode as AlphaNode<Any>).activateTyped(fact)) {
                    activated = true
                }
            }
        }

        // Also check for interface/supertype matches.
        // This handles polymorphic rules AND Kotlin/JS where runtime class
        // for primitives may not match the compile-time KClass in the map.
        val polyNodes = polymorphicNodeCache.getOrPut(factClass) {
            buildList {
                for ((type, typeNodes) in alphaNodes) {
                    if (type != factClass && type.isInstance(fact)) {
                        addAll(typeNodes)
                    }
                }
            }
        }

        for (alphaNode in polyNodes) {
            if (alphaNode.activate(fact)) {
                activated = true
            }
        }

        return activated
    }

    /**
     * Check if any output nodes have pending activations. O(1) via counter.
     */
    fun hasPendingActivations(): Boolean = _pendingActivationCount > 0

    /**
     * Get statistics about the network.
     */
    fun stats(): NetworkStats = NetworkStats(
        alphaNodeCount = alphaNodes.values.sumOf { it.size },
        betaNodeCount = betaNodes.size,
        outputNodeCount = outputNodes.size,
        totalTokensInAlphaMemory = alphaNodes.values.flatten().sumOf { it.memory.size() },
        totalTokensInBetaMemory = betaNodes.sumOf { it.memory.size() }
    )

    /**
     * Create a lightweight copy of this network with fresh mutable state.
     *
     * The copy shares the same structural configuration (conditions, producers, types,
     * priorities) but has independent mutable state (alpha memories, output node fired-sets,
     * pending activation counters). This allows the original compiled network to be reused
     * as a template across concurrent evaluations while each evaluation gets its own
     * independent mutable state.
     *
     * The [polymorphicNodeCache] is NOT copied — each copy rebuilds it on first use.
     * This is cheap (one O(N) scan per new KClass) and avoids sharing mutable maps.
     */
    fun copy(): ReteNetwork {
        // Create new output nodes with same config but fresh mutable state
        val oldToNewOutput = mutableMapOf<OutputNode<*>, OutputNode<*>>()
        val newOutputNodes = outputNodes.map { old ->
            @Suppress("UNCHECKED_CAST")
            val castProducer = old.producer as (List<Any>) -> Any?
            val new = OutputNode<Any>(
                id = old.id,
                ruleName = old.ruleName,
                priority = old.priority,
                producer = castProducer
            )
            oldToNewOutput[old] = new
            new
        }

        // Create new alpha nodes with same config, rewired to new output nodes
        val newAlphaNodes = alphaNodes.mapValues { (_, nodes) ->
            nodes.map { old ->
                @Suppress("UNCHECKED_CAST")
                val newAlpha = AlphaNode(
                    id = old.id,
                    inputType = old.inputType as kotlin.reflect.KClass<Any>,
                    condition = old.condition as (Any) -> Boolean
                )
                for (successor in old.successors) {
                    val newSuccessor = oldToNewOutput[successor]
                    if (newSuccessor != null) {
                        newAlpha.successors.add(newSuccessor)
                    }
                }
                newAlpha
            }
        }

        val newNetwork = ReteNetwork(newAlphaNodes, emptyList(), newOutputNodes)
        for (node in newOutputNodes) {
            node.network = newNetwork
        }
        return newNetwork
    }

    /**
     * Reset all node memories (for session reset).
     *
     * Note: [polymorphicNodeCache] is NOT cleared here because it depends only on
     * the network's structure (alpha node types), which is invariant across sessions.
     * Preserving it avoids redundant KClass.isInstance scans on subsequent evaluations.
     */
    fun reset() {
        _pendingActivationCount = 0
        for (nodes in alphaNodes.values) {
            for (node in nodes) {
                node.memory.clear()
            }
        }
        for (node in betaNodes) {
            node.clear()
        }
        for (node in outputNodes) {
            node.reset()
        }
    }
}

/**
 * Statistics about a Rete network.
 */
internal data class NetworkStats(
    val alphaNodeCount: Int,
    val betaNodeCount: Int,
    val outputNodeCount: Int,
    val totalTokensInAlphaMemory: Int,
    val totalTokensInBetaMemory: Int
)
