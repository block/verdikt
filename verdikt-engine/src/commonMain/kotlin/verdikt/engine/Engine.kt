package verdikt.engine

/**
 * A forward-chaining rules engine.
 *
 * An engine contains producers (which derive new facts) and validation rules
 * (which check facts). Use [evaluate] to insert facts and execute rules.
 *
 * ## Fact Requirements
 *
 * Facts should implement `equals()` and `hashCode()` correctly (e.g., use data classes).
 * The engine uses these methods to:
 * - Prevent duplicate facts in working memory
 * - Track which facts have been processed by each rule
 * - Detect fixpoint (no new facts produced)
 *
 * ## Thread Safety
 *
 * Engine instances are thread-safe after construction. Multiple threads can call
 * [evaluate] concurrently on the same engine—each call creates its own internal session.
 *
 * ## Example
 *
 * ```
 * val pricingEngine = engine {
 *     produce<Customer, VipStatus>("vip-check") {
 *         condition { it.totalSpend > 10_000 }
 *         output { VipStatus(it.id, "gold") }
 *     }
 * }
 *
 * val result = pricingEngine.evaluate(listOf(customer))
 * val vipStatus = result.derivedOfType<VipStatus>().firstOrNull()
 * ```
 */
public interface Engine {
    /** All phases in this engine, in execution order */
    public val phases: List<Phase>

    /** Names of all fact producers in this engine, in definition order */
    public val factProducerNames: List<String>

    /** Names of all validation rules in this engine, in definition order */
    public val validationRuleNames: List<String>

    /** Total number of rules (producers + validation) */
    public val size: Int

    /** True if this engine contains any async rules */
    public val hasAsyncRules: Boolean

    /**
     * Evaluates facts against this engine's rules synchronously.
     *
     * 1. Inserts facts into working memory
     * 2. Iterates through production rules until no new facts are produced (fixpoint)
     * 3. Evaluates all validation rules against working memory
     * 4. Returns the combined result
     *
     * @param facts The facts to evaluate
     * @param context Optional context for guard evaluation. Guards use this context
     *                to determine if rules should run.
     * @param collector Optional collector to receive events during evaluation.
     *                  See [EngineEventCollector] for usage examples.
     * @return The result containing all facts, derived facts, validation verdict, and stats
     * @throws IllegalStateException if the engine contains async rules (use [evaluateAsync] instead)
     */
    public fun evaluate(
        facts: Collection<Any>,
        context: RuleContext = RuleContext.EMPTY,
        collector: EngineEventCollector = EngineEventCollector.EMPTY
    ): EngineResult

    /**
     * Evaluates facts against this engine's rules asynchronously.
     *
     * Use this when the engine contains rules with async conditions or outputs.
     * Async rules are executed concurrently where possible.
     *
     * @param facts The facts to evaluate
     * @param context Optional context for guard evaluation. Guards use this context
     *                to determine if rules should run.
     * @param collector Optional collector to receive events during evaluation.
     *                  See [EngineEventCollector] for usage examples.
     * @return The result containing all facts, derived facts, validation verdict, and stats
     */
    public suspend fun evaluateAsync(
        facts: Collection<Any>,
        context: RuleContext = RuleContext.EMPTY,
        collector: EngineEventCollector = EngineEventCollector.EMPTY
    ): EngineResult
}
