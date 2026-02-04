package verdikt.engine.rete

/**
 * Joins facts from left input (alpha or beta) with right input (alpha).
 *
 * Beta nodes implement "inter-element" conditions - tests that span
 * multiple facts. They perform joins between:
 * - Left input: Tokens from an alpha node or another beta node
 * - Right input: Tokens from an alpha node
 *
 * When activated from either side, the node:
 * 1. Stores the incoming token/fact
 * 2. Tries to join with all tokens from the other side
 * 3. Propagates successful joins to successors
 *
 * ## Future Work
 *
 * This class is infrastructure for future multi-fact join support. Currently,
 * the engine only supports single-fact conditions (`condition { fact -> ... }`).
 * Beta nodes will enable multi-fact conditions like:
 *
 * ```kotlin
 * produce<Order, Discount>("vip-discount") {
 *     // Future syntax - join Order with VipStatus
 *     conditionJoin<VipStatus> { order, vip ->
 *         vip.customerId == order.customerId
 *     }
 *     output { order -> Discount(order.id, 20) }
 * }
 * ```
 *
 * The beta network would join Order facts with VipStatus facts on customerId,
 * only firing when both are present and the join condition is satisfied.
 *
 * @param R The type of facts from the right (alpha) input
 * @property id Unique identifier for debugging
 * @property rightInput The alpha node providing right-side facts
 * @property joinCondition Tests if a left token and right fact should join
 */
internal class BetaNode<R : Any>(
    override val id: String,
    val rightInput: AlphaNode<R>,
    val joinCondition: (List<Any>, R) -> Boolean,
    override val successors: MutableList<ReteNode> = mutableListOf()
) : ReteNode {

    /** Memory storing successful joins */
    val memory = BetaMemory()

    /** Left tokens waiting for right matches */
    private val leftTokens = mutableListOf<JoinedToken>()

    override fun leftActivate(token: Token<*>) {
        leftActivate(JoinedToken(listOf(token.fact)))
    }

    override fun leftActivate(token: JoinedToken) {
        leftTokens.add(token)

        // Try to join with all facts in right alpha memory
        for (rightToken in rightInput.memory.all()) {
            tryJoin(token, rightToken.fact)
        }
    }

    /**
     * Called when right input receives a new fact.
     * Tries to join with all pending left tokens.
     */
    fun rightActivate(token: Token<R>) {
        for (leftToken in leftTokens) {
            tryJoin(leftToken, token.fact)
        }
    }

    /**
     * Called when right input receives a new fact (by fact, not token).
     */
    fun rightActivate(fact: R) {
        for (leftToken in leftTokens) {
            tryJoin(leftToken, fact)
        }
    }

    /**
     * Attempt to join a left token with a right fact.
     * If the join condition passes, stores the result and propagates.
     */
    private fun tryJoin(leftToken: JoinedToken, rightFact: R) {
        if (joinCondition(leftToken.facts, rightFact)) {
            val joined = leftToken + rightFact
            if (memory.add(joined)) {
                propagate(joined)
            }
        }
    }

    /**
     * Propagate a joined token to all successors.
     */
    private fun propagate(token: JoinedToken) {
        for (successor in successors) {
            successor.leftActivate(token)
        }
    }

    /**
     * Get all left tokens currently stored.
     */
    fun getLeftTokens(): List<JoinedToken> = leftTokens.toList()

    /**
     * Clear all state (for testing or reset).
     */
    fun clear() {
        leftTokens.clear()
        memory.clear()
    }
}
