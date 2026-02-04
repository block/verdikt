package verdikt.engine.rete

import kotlin.concurrent.Volatile

/**
 * A token represents a fact that passed an alpha node test.
 *
 * Tokens are the unit of propagation through the Rete network.
 * Each token wraps a fact and has a unique tag for identity tracking.
 *
 * @param T The type of the wrapped fact
 * @property fact The underlying fact
 * @property tag Unique identifier for this token
 */
internal data class Token<T : Any>(
    val fact: T,
    val tag: Long = nextTag()
) {
    companion object {
        @Volatile
        private var tokenCounter = 0L

        private fun nextTag(): Long = tokenCounter++
    }
}

/**
 * A joined token represents a partial match from a beta node.
 *
 * JoinedTokens accumulate facts as they flow through beta nodes,
 * building up the complete match for multi-fact conditions.
 *
 * @property facts The list of matched facts, in order of matching
 * @property tag Unique identifier for this joined token
 */
internal data class JoinedToken(
    val facts: List<Any>,
    val tag: Long = nextTag()
) {
    companion object {
        @Volatile
        private var joinedTokenCounter = 0L

        private fun nextTag(): Long = joinedTokenCounter++
    }

    /**
     * Creates a new JoinedToken with the given fact appended.
     */
    operator fun plus(fact: Any): JoinedToken = JoinedToken(facts + fact)

    /**
     * Returns the number of facts in this joined token.
     */
    val size: Int get() = facts.size

    /**
     * Gets a fact at the specified index.
     */
    operator fun get(index: Int): Any = facts[index]
}
