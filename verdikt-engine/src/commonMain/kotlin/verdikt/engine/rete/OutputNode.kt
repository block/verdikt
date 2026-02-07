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

    /** Tracks which single-fact inputs have already fired (avoids List wrapper allocation) */
    private val firedForSingle = mutableSetOf<Any>()

    /** Tracks which multi-fact input combinations have already fired */
    private val firedForMulti = mutableSetOf<List<Any>>()

    /** Pending activations waiting to be fired (for priority ordering) */
    private val pendingActivations = mutableListOf<List<Any>>()

    /** Callback to insert produced facts into working memory */
    var onProduce: ((Out) -> Unit)? = null

    /** Reference to parent network for pending activation counting */
    internal var network: ReteNetwork? = null

    /** Whether this node is skipped due to guards (avoids Set lookup in hot loop) */
    internal var isSkipped: Boolean = false

    override fun leftActivate(token: Token<*>) {
        val fact = token.fact
        // Fast dedup using Set<Any> instead of Set<List<Any>>
        if (fact in firedForSingle) return
        firedForSingle.add(fact)
        val factList = listOf(fact)
        pendingActivations.add(factList)
        network?.let { it.pendingActivationCount++ }
    }

    override fun leftActivate(token: JoinedToken) {
        val facts = token.facts
        if (facts in firedForMulti) return
        firedForMulti.add(facts)
        pendingActivations.add(facts)
        network?.let { it.pendingActivationCount++ }
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

        val results = ArrayList<Pair<List<Any>, List<Out>>>(pendingActivations.size)
        val callback = onProduce

        for (facts in pendingActivations) {
            val output = producer(facts)
            val outputs = if (output != null) listOf(output) else emptyList()
            results.add(facts to outputs)
            if (callback != null && output != null) {
                callback(output)
            }
        }

        network?.let { it.pendingActivationCount -= pendingActivations.size }
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
    fun hasFiredFor(facts: List<Any>): Boolean {
        if (facts.size == 1) return facts.first() in firedForSingle
        return facts in firedForMulti
    }

    /**
     * Get the number of times this node has fired.
     */
    fun fireCount(): Int = firedForSingle.size + firedForMulti.size

    /**
     * Reset the fired state and pending activations (for testing or session reset).
     * Note: Does NOT adjust network.pendingActivationCount — caller (ReteNetwork.reset())
     * zeroes the counter directly before calling this.
     */
    fun reset() {
        firedForSingle.clear()
        firedForMulti.clear()
        pendingActivations.clear()
        isSkipped = false
    }
}
