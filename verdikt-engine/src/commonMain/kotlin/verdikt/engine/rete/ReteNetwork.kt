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
    val alphaNodes: Map<KClass<*>, MutableList<AlphaNode<*>>>,
    val betaNodes: List<BetaNode<*>>,
    val outputNodes: List<OutputNode<*>>
) {
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
    fun activate(fact: Any): Boolean {
        var activated = false

        // Find alpha nodes that accept this fact's type
        val factClass = fact::class
        val nodes = alphaNodes[factClass]

        if (nodes != null) {
            for (alphaNode in nodes) {
                if (alphaNode.activate(fact)) {
                    activated = true
                }
            }
        }

        // Also check for interface/supertype matches
        // This is less efficient but necessary for polymorphic rules
        for ((type, typeNodes) in alphaNodes) {
            if (type != factClass && type.isInstance(fact)) {
                for (alphaNode in typeNodes) {
                    if (alphaNode.activate(fact)) {
                        activated = true
                    }
                }
            }
        }

        return activated
    }

    /**
     * Get all output nodes, sorted by priority (descending).
     */
    fun outputNodesByPriority(): List<OutputNode<*>> =
        outputNodes.sortedByDescending { it.priority }

    /**
     * Fire all pending activations in priority order.
     *
     * This collects all output nodes with pending activations, sorts them by priority
     * (highest first), and fires them. Returns the outputs produced.
     *
     * @return List of all outputs produced, in priority order
     */
    fun firePendingByPriority(): List<Any> {
        val allOutputs = mutableListOf<Any>()

        // Get output nodes with pending activations, sorted by priority (highest first)
        val nodesWithPending = outputNodes
            .filter { it.hasPendingActivations() }
            .sortedByDescending { it.priority }

        for (node in nodesWithPending) {
            val outputs = node.firePending()
            allOutputs.addAll(outputs)
        }

        return allOutputs
    }

    /**
     * Check if any output nodes have pending activations.
     */
    fun hasPendingActivations(): Boolean =
        outputNodes.any { it.hasPendingActivations() }

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
     * Reset all node memories (for session reset).
     */
    fun reset() {
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
