package verdikt.engine

/**
 * A forward-chaining production rules engine.
 *
 * An engine contains production rules (which derive new facts) and validation rules
 * (which check facts). Use [session] to create a working memory and execute rules.
 *
 * Example:
 * ```
 * val pricingEngine = engine<String> {
 *     produce<Customer, VipStatus>("vip-check") {
 *         condition { it.totalSpend > 10_000 }
 *         output { VipStatus(it.id, "gold") }
 *     }
 * }
 *
 * val session = pricingEngine.session()
 * session.insert(customer)
 * val result = session.fire()
 * ```
 *
 * @param Reason The type used for validation failure reasons
 */
public interface Engine<Reason : Any> {
    /** Names of all production rules in this engine, in definition order */
    public val productionRuleNames: List<String>

    /** Names of all validation rules in this engine, in definition order */
    public val validationRuleNames: List<String>

    /** Total number of rules (production + validation) */
    public val size: Int

    /** True if this engine contains any async rules */
    public val hasAsyncRules: Boolean

    /**
     * Creates a new session (working memory) for this engine.
     *
     * Each session is independent - facts inserted in one session do not affect others.
     * Sessions are not thread-safe; use one session per thread/coroutine.
     *
     * @return A new [Session] bound to this engine's rules
     */
    public fun session(): Session<Reason>
}
