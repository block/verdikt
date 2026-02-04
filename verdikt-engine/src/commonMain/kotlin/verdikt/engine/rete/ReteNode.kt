package verdikt.engine.rete

/**
 * Base interface for all Rete network nodes.
 *
 * Rete networks are composed of different node types:
 * - Alpha nodes: Test individual facts against type and condition
 * - Beta nodes: Join multiple facts with cross-fact conditions
 * - Output nodes: Terminal nodes that produce new facts
 *
 * All nodes can propagate tokens to their successors.
 */
internal interface ReteNode {
    /** Unique identifier for this node (used for debugging) */
    val id: String

    /** Successor nodes that receive tokens from this node */
    val successors: MutableList<ReteNode>

    /**
     * Called when a single-fact token arrives from the left (or only) input.
     * Alpha nodes and first-level beta nodes receive tokens this way.
     */
    fun leftActivate(token: Token<*>)

    /**
     * Called when a joined token arrives from a beta node.
     * Used for multi-fact matching in deeper parts of the network.
     *
     * Default implementation wraps the first fact in a Token for nodes
     * that don't distinguish between single and joined tokens.
     */
    fun leftActivate(token: JoinedToken) {
        // Default: just use the first fact
        if (token.facts.isNotEmpty()) {
            leftActivate(Token(token.facts.first()))
        }
    }
}
