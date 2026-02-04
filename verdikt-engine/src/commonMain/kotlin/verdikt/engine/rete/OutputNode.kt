package verdikt.engine.rete

/**
 * Terminal node that produces output facts when activated.
 *
 * Output nodes are the "action" part of rules. When tokens arrive:
 * 1. Check if this input combination has already fired
 * 2. If not, queue the activation (or fire immediately if not using priority ordering)
 * 3. When explicitly fired, invoke the producer to create output fact(s)
 * 4. Invoke the callback to insert produced facts into working memory
 *
 * The de-duplication prevents the same rule from firing multiple times
 * for the same input facts.
 *
 * @param Out The type of facts this node produces
 * @property id Unique identifier for debugging
 * @property ruleName The name of the rule this node represents
 * @property priority The rule's priority (for ordering)
 * @property producer Function to produce a single output fact (may return null to skip)
 */
internal class OutputNode<Out : Any>(
    override val id: String,
    val ruleName: String,
    val priority: Int,
    val producer: (List<Any>) -> Out?,
    override val successors: MutableList<ReteNode> = mutableListOf()
) : ReteNode {

    /** Tracks which input combinations have already fired */
    private val firedFor = mutableSetOf<List<Any>>()

    /** Pending activations waiting to be fired (for priority ordering) */
    private val pendingActivations = mutableListOf<List<Any>>()

    /** Callback to insert produced facts into working memory */
    var onProduce: ((Out) -> Unit)? = null

    override fun leftActivate(token: Token<*>) {
        queueActivation(listOf(token.fact))
    }

    override fun leftActivate(token: JoinedToken) {
        queueActivation(token.facts)
    }

    /**
     * Queue an activation for later firing (supports priority ordering).
     */
    private fun queueActivation(facts: List<Any>) {
        // Prevent re-firing for same input combination
        if (facts in firedFor) return
        firedFor.add(facts)
        pendingActivations.add(facts)
    }

    /**
     * Fire all pending activations and return the produced outputs.
     * Clears the pending queue after firing.
     *
     * @return List of all outputs produced by this firing
     */
    fun firePending(): List<Out> {
        return firePendingWithInputs().flatMap { it.second }
    }

    /**
     * Fire all pending activations and return paired input facts with their outputs.
     * Clears the pending queue after firing.
     *
     * @return List of (inputFacts, outputs) pairs for each activation
     */
    fun firePendingWithInputs(): List<Pair<List<Any>, List<Out>>> {
        if (pendingActivations.isEmpty()) return emptyList()

        val results = mutableListOf<Pair<List<Any>, List<Out>>>()
        val callback = onProduce

        for (facts in pendingActivations) {
            // Produce output
            val output = producer(facts)
            val outputs = listOfNotNull(output)

            results.add(facts to outputs)

            // Also invoke callback for backward compatibility
            if (callback != null && output != null) {
                callback(output)
            }
        }

        pendingActivations.clear()
        return results
    }

    /**
     * Check if there are pending activations.
     */
    fun hasPendingActivations(): Boolean = pendingActivations.isNotEmpty()

    /**
     * Get the number of pending activations.
     */
    fun pendingCount(): Int = pendingActivations.size

    /**
     * Check if this node has fired for a given input combination.
     */
    fun hasFiredFor(facts: List<Any>): Boolean = facts in firedFor

    /**
     * Get the number of times this node has fired.
     */
    fun fireCount(): Int = firedFor.size

    /**
     * Reset the fired state and pending activations (for testing or session reset).
     */
    fun reset() {
        firedFor.clear()
        pendingActivations.clear()
    }
}
