package verdikt.engine.rete

/**
 * Stores tokens that passed an alpha node's condition test.
 *
 * Alpha memory provides:
 * - De-duplication: Each fact is stored at most once
 * - Fast lookup: O(1) containment check
 * - Enumeration: Iterate all stored tokens for beta node joins
 *
 * @param T The type of facts stored in this memory
 */
internal class AlphaMemory<T : Any> {
    private val tokens = mutableMapOf<T, Token<T>>()

    /**
     * Add a token to memory.
     * @return true if the token was added (fact was not already present)
     */
    fun add(token: Token<T>): Boolean {
        if (token.fact in tokens) return false
        tokens[token.fact] = token
        return true
    }

    /**
     * Check if a fact is already in memory.
     */
    fun contains(fact: T): Boolean = fact in tokens

    /**
     * Get the token for a specific fact.
     */
    fun get(fact: T): Token<T>? = tokens[fact]

    /**
     * Get all stored tokens.
     */
    fun all(): Collection<Token<T>> = tokens.values

    /**
     * Get all stored facts.
     */
    fun allFacts(): Collection<T> = tokens.keys

    /**
     * Number of tokens in memory.
     */
    fun size(): Int = tokens.size

    /**
     * Check if memory is empty.
     */
    fun isEmpty(): Boolean = tokens.isEmpty()

    /**
     * Clear all tokens from memory.
     */
    fun clear() {
        tokens.clear()
    }
}
