package verdikt.engine.rete

/**
 * Stores joined tokens from beta node matches.
 *
 * Beta memory provides:
 * - De-duplication: Each unique fact combination is stored once
 * - Indexing by left fact: For efficient right-activation lookups
 * - Enumeration: Iterate all stored joined tokens
 *
 * The indexing allows beta nodes to quickly find existing partial
 * matches when a new fact arrives on the right input.
 *
 * ## Future Work
 *
 * This class is infrastructure for future multi-fact join support.
 * It will store the results of beta node joins (partial matches)
 * that span multiple facts. When a rule requires facts A, B, and C
 * to all be present with matching keys, the beta memory stores
 * partial matches (A,B) while waiting for C.
 *
 * See [BetaNode] for more details on the planned multi-fact condition support.
 */
internal class BetaMemory {
    private val tokens = mutableSetOf<JoinedToken>()
    private val byLeftFact = mutableMapOf<Any, MutableSet<JoinedToken>>()

    /**
     * Add a joined token to memory.
     * @return true if the token was added (not already present)
     */
    fun add(token: JoinedToken): Boolean {
        if (!tokens.add(token)) return false

        // Index by first (left) fact for right-activation lookups
        val leftFact = token.facts.firstOrNull() ?: return true
        byLeftFact.getOrPut(leftFact) { mutableSetOf() }.add(token)
        return true
    }

    /**
     * Get all stored joined tokens.
     */
    fun all(): Set<JoinedToken> = tokens

    /**
     * Get joined tokens that have a specific left (first) fact.
     */
    fun byLeft(fact: Any): Set<JoinedToken> = byLeftFact[fact] ?: emptySet()

    /**
     * Number of joined tokens in memory.
     */
    fun size(): Int = tokens.size

    /**
     * Check if memory is empty.
     */
    fun isEmpty(): Boolean = tokens.isEmpty()

    /**
     * Check if a specific joined token exists.
     */
    fun contains(token: JoinedToken): Boolean = token in tokens

    /**
     * Clear all tokens from memory.
     */
    fun clear() {
        tokens.clear()
        byLeftFact.clear()
    }
}
