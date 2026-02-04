package verdikt.engine.rete

import kotlin.reflect.KClass

/**
 * Tests individual facts against a type and condition.
 *
 * Alpha nodes are the first level of the Rete network. They:
 * 1. Filter facts by type (only facts of [inputType] pass through)
 * 2. Test each fact against a [condition]
 * 3. Store passing facts in [memory]
 * 4. Propagate tokens to [successors]
 *
 * Alpha nodes implement the "intra-element" conditions - tests that
 * involve only a single fact.
 *
 * @param In The input fact type this node tests
 * @property id Unique identifier for debugging
 * @property inputType The KClass of facts this node accepts
 * @property condition The test to apply to each fact
 */
internal class AlphaNode<In : Any>(
    override val id: String,
    val inputType: KClass<In>,
    val condition: (In) -> Boolean,
    override val successors: MutableList<ReteNode> = mutableListOf()
) : ReteNode {

    /** Memory storing facts that passed this node's test */
    val memory = AlphaMemory<In>()

    /**
     * Test a fact against this node's type and condition.
     *
     * If the fact passes:
     * 1. A token is created and stored in memory
     * 2. The token is propagated to all successors
     *
     * @param fact The fact to test
     * @return true if fact passed and was added to memory
     */
    fun activate(fact: Any): Boolean {
        // Type check
        if (!inputType.isInstance(fact)) return false

        @Suppress("UNCHECKED_CAST")
        val typedFact = fact as In

        // Already processed?
        if (memory.contains(typedFact)) return false

        // Test condition
        if (!condition(typedFact)) return false

        // Add to memory and propagate
        val token = Token(typedFact)
        memory.add(token)

        propagate(token)

        return true
    }

    /**
     * Propagate a token to all successor nodes.
     */
    private fun propagate(token: Token<In>) {
        for (successor in successors) {
            successor.leftActivate(token)
        }
    }

    override fun leftActivate(token: Token<*>) {
        // Alpha nodes receive facts directly via activate(), not tokens
        // But this can be called if chained from another alpha node
        activate(token.fact)
    }

    override fun leftActivate(token: JoinedToken) {
        // For joined tokens, activate with the last fact (most recent)
        token.facts.lastOrNull()?.let { activate(it) }
    }

    /**
     * Re-propagate all stored tokens to successors.
     * Useful when a new successor is added after facts were already processed.
     */
    fun repropagate() {
        for (token in memory.all()) {
            propagate(token)
        }
    }
}
