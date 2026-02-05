package verdikt.engine

/**
 * Events emitted during engine evaluation.
 *
 * Use [EngineEventCollector] to receive events as evaluation progresses.
 *
 * Example:
 * ```kotlin
 * engine.evaluate(facts) { event ->
 *     when (event) {
 *         is EngineEvent.FactInserted -> println("Inserted: ${event.fact}")
 *         is EngineEvent.RuleFired -> println("Fired: ${event.ruleName}")
 *         is EngineEvent.Completed -> println("Done: ${event.result.passed}")
 *         else -> {}
 *     }
 * }
 * ```
 */
public sealed class EngineEvent {
    /**
     * A fact was inserted into working memory.
     *
     * Emitted for both initial facts and derived facts.
     */
    public data class FactInserted(
        val fact: Any,
        /** True if this fact was derived by a rule, false if it was an initial fact */
        val isDerived: Boolean
    ) : EngineEvent()

    /**
     * A production rule fired and produced output.
     */
    public data class RuleFired(
        val ruleName: String,
        val inputFact: Any,
        val outputFacts: List<Any>,
        val priority: Int
    ) : EngineEvent()

    /**
     * A rule was skipped because its guard condition was not satisfied.
     */
    public data class RuleSkipped(
        val ruleName: String,
        val guardDescription: String
    ) : EngineEvent()

    /**
     * A validation rule passed for a fact.
     */
    public data class ValidationPassed(
        val ruleName: String,
        val fact: Any
    ) : EngineEvent()

    /**
     * A validation rule failed for a fact.
     */
    public data class ValidationFailed(
        val ruleName: String,
        val fact: Any,
        val reason: Any
    ) : EngineEvent()

    /**
     * Engine evaluation completed.
     *
     * This is always the last event emitted.
     */
    public data class Completed(
        val result: EngineResult
    ) : EngineEvent()
}
