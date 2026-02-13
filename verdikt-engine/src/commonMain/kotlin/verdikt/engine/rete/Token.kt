package verdikt.engine.rete

/**
 * A token represents a fact that passed an alpha node test.
 *
 * Tokens are the unit of propagation through the Rete network.
 * Each token wraps a fact.
 *
 * @param T The type of the wrapped fact
 * @property fact The underlying fact
 */
internal data class Token<T : Any>(
    val fact: T
)

/**
 * A joined token represents a partial match from a beta node.
 *
 * JoinedTokens accumulate facts as they flow through beta nodes,
 * building up the complete match for multi-fact conditions.
 *
 * @property facts The list of matched facts, in order of matching
 */
internal data class JoinedToken(
    val facts: List<Any>
) {
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
