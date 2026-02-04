package verdikt.engine

import kotlin.reflect.KClass

/**
 * Working memory implementation with type-based indexing for O(1) type lookups.
 *
 * Instead of scanning all facts to find those matching a type (O(n)),
 * this maintains a HashMap from KClass to the set of facts of that exact type,
 * enabling O(1) lookup for the common case of exact type matching.
 *
 * ## Implementation Notes
 *
 * The type index stores facts by their exact runtime class. When querying by a type T,
 * we first check the exact type index. For subtypes (when T is a supertype), we fall back
 * to the O(n) scan. In practice, Verdikt rules use concrete types like `Customer` rather
 * than interfaces, so the O(1) path is the common case.
 *
 * ## Thread Safety
 *
 * This class is not thread-safe. The engine processes rules sequentially within a session.
 */
internal class IndexedWorkingMemory {
    private val allFacts = mutableSetOf<Any>()
    private val typeIndex = mutableMapOf<KClass<*>, MutableSet<Any>>()

    /**
     * Add a fact to working memory.
     * @return true if the fact was added (not already present)
     */
    fun add(fact: Any): Boolean {
        val added = allFacts.add(fact)
        if (added) {
            typeIndex.getOrPut(fact::class) { mutableSetOf() }.add(fact)
        }
        return added
    }

    /**
     * Add multiple facts to working memory.
     */
    fun addAll(facts: Iterable<Any>) {
        for (fact in facts) {
            add(fact)
        }
    }

    /**
     * Get all facts in working memory.
     */
    fun all(): Set<Any> = allFacts.toSet()

    /**
     * Get all facts of the specified type.
     *
     * If the exact type is indexed, this is O(1). If not (querying by supertype),
     * falls back to O(n) filtering.
     */
    @Suppress("UNCHECKED_CAST")
    fun <T : Any> ofType(type: KClass<T>): Set<T> {
        // Try exact type match first (O(1))
        val exactMatch = typeIndex[type]
        if (exactMatch != null) {
            return exactMatch.toSet() as Set<T>
        }

        // Fall back to filtering for subtypes (O(n))
        // This handles cases like querying for an interface
        return allFacts.filter { type.isInstance(it) }.map { it as T }.toSet()
    }

    /**
     * Check if any fact of the specified type exists.
     */
    fun <T : Any> hasAny(type: KClass<T>): Boolean {
        // Try exact type match first (O(1))
        val exactMatch = typeIndex[type]
        if (exactMatch != null && exactMatch.isNotEmpty()) {
            return true
        }

        // Fall back to checking for subtypes (O(n))
        return allFacts.any { type.isInstance(it) }
    }

    /**
     * Check if the exact fact exists in working memory.
     */
    fun contains(fact: Any): Boolean = fact in allFacts

    /**
     * Get all facts matching the specified type using instance checking.
     * This always uses O(n) filtering but maintains compatibility with
     * the original behavior.
     */
    @Suppress("UNCHECKED_CAST")
    fun <T : Any> filterByType(type: KClass<T>): List<T> =
        allFacts.filter { type.isInstance(it) }.map { it as T }

    /**
     * Total number of facts in working memory.
     */
    val size: Int get() = allFacts.size

    /**
     * Check if working memory is empty.
     */
    fun isEmpty(): Boolean = allFacts.isEmpty()
}
