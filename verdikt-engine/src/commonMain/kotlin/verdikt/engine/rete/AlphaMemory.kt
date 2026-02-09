package verdikt.engine.rete

/**
 * Stores facts that passed an alpha node's condition test.
 *
 * Alpha memory provides:
 * - De-duplication: Each fact is stored at most once
 * - Fast lookup: O(1) containment check
 * - Enumeration: Iterate all stored facts for beta node joins
 *
 * @param T The type of facts stored in this memory
 */
internal class AlphaMemory<T : Any> {
    private val facts = mutableSetOf<T>()

    /**
     * Add a fact to memory.
     * @return true if the fact was added (not already present)
     */
    fun add(fact: T): Boolean = facts.add(fact)

    /**
     * Check if a fact is already in memory.
     */
    fun contains(fact: T): Boolean = fact in facts

    /**
     * Get all stored facts.
     */
    fun allFacts(): Collection<T> = facts

    /**
     * Number of facts in memory.
     */
    fun size(): Int = facts.size

    /**
     * Check if memory is empty.
     */
    fun isEmpty(): Boolean = facts.isEmpty()

    /**
     * Clear all facts from memory.
     */
    fun clear() {
        facts.clear()
    }
}
