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

    /** Pending single-fact activations waiting to be fired */
    private val pendingSingleFacts = mutableListOf<Any>()

    /** Pending multi-fact activations waiting to be fired */
    private val pendingMultiActivations = mutableListOf<List<Any>>()

    /**
     * Reusable single-element list for producer calls (avoids per-fact allocation).
     * IMPORTANT: This list is mutated in-place between calls. The producer lambda
     * (created in ReteCompiler) MUST extract values immediately via facts.first()
     * and must NOT retain a reference to this list.
     */
    private val reusableSingleFactList = ArrayList<Any>(1).apply { add(Unit) }

    /** Callback to insert produced facts into working memory */
    var onProduce: ((Out) -> Unit)? = null

    /** Reference to parent network for pending activation counting */
    internal var network: ReteNetwork? = null

    /** Whether this node is skipped due to guards (avoids Set lookup in hot loop) */
    internal var isSkipped: Boolean = false

    override fun leftActivateFact(fact: Any) {
        if (fact in firedForSingle) return
        firedForSingle.add(fact)
        pendingSingleFacts.add(fact)
        network?.let { it.pendingActivationCount++ }
    }

    override fun leftActivate(token: Token<*>) {
        // Delegate to fact-based activation
        leftActivateFact(token.fact)
    }

    override fun leftActivate(token: JoinedToken) {
        val facts = token.facts
        if (facts in firedForMulti) return
        firedForMulti.add(facts)
        pendingMultiActivations.add(facts)
        network?.let { it.pendingActivationCount++ }
    }

    /**
     * Fire all pending activations and return the produced outputs.
     * Clears the pending queue after firing.
     *
     * @return List of all outputs produced by this firing
     */
    fun firePending(): List<Out> {
        if (pendingSingleFacts.isEmpty() && pendingMultiActivations.isEmpty()) return emptyList()
        return firePendingWithInputs().flatMap { it.second }
    }

    /**
     * Fire all pending activations and return paired input facts with their outputs.
     * Clears the pending queue after firing.
     *
     * @return List of (inputFacts, outputs) pairs for each activation
     */
    fun firePendingWithInputs(): List<Pair<List<Any>, List<Out>>> {
        if (pendingSingleFacts.isEmpty() && pendingMultiActivations.isEmpty()) return emptyList()

        val totalSize = pendingSingleFacts.size + pendingMultiActivations.size
        val results = ArrayList<Pair<List<Any>, List<Out>>>(totalSize)
        val callback = onProduce
        val reusable = reusableSingleFactList

        // Fire single-fact pending
        for (fact in pendingSingleFacts) {
            reusable[0] = fact
            val output = producer(reusable)
            val outputs = if (output != null) listOf(output) else emptyList()
            results.add(listOf(fact) to outputs)
            if (callback != null && output != null) {
                callback(output)
            }
        }

        // Fire multi-fact pending
        for (facts in pendingMultiActivations) {
            val output = producer(facts)
            val outputs = if (output != null) listOf(output) else emptyList()
            results.add(facts to outputs)
            if (callback != null && output != null) {
                callback(output)
            }
        }

        network?.let { it.pendingActivationCount -= totalSize }
        pendingSingleFacts.clear()
        pendingMultiActivations.clear()
        return results
    }

    /**
     * Discard all pending activations without firing the producer.
     * Used when a guard blocks execution — prevents side effects.
     */
    fun clearPending() {
        val totalSize = pendingSingleFacts.size + pendingMultiActivations.size
        if (totalSize > 0) {
            network?.let { it.pendingActivationCount -= totalSize }
            pendingSingleFacts.clear()
            pendingMultiActivations.clear()
        }
    }

    /**
     * Check if there are pending activations.
     */
    fun hasPendingActivations(): Boolean = pendingSingleFacts.isNotEmpty() || pendingMultiActivations.isNotEmpty()

    /**
     * Get the number of pending activations.
     */
    fun pendingCount(): Int = pendingSingleFacts.size + pendingMultiActivations.size

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
     * Note: Does NOT adjust network.pendingActivationCount -- caller (ReteNetwork.reset())
     * zeroes the counter directly before calling this.
     */
    fun reset() {
        firedForSingle.clear()
        firedForMulti.clear()
        pendingSingleFacts.clear()
        pendingMultiActivations.clear()
        isSkipped = false
    }
}
