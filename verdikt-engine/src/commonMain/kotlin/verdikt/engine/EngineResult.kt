package verdikt.engine

import verdikt.Verdict

/**
 * Result of engine execution.
 *
 * Contains all facts in working memory after execution, the subset that were derived
 * by rules (vs inserted initially), validation results, and execution statistics.
 *
 * Since each validation rule can have its own failure type, the verdict uses a star
 * projection (`Verdict<*>`). Use pattern matching on failure causes when needed.
 */
public data class EngineResult(
    /** All facts in working memory after execution (initial + derived) */
    val facts: Set<Any>,

    /** Facts that were produced by rules during execution (subset of [facts]) */
    val derived: Set<Any>,

    /** Combined validation verdict from all validation rules */
    val verdict: Verdict<*>,

    /**
     * Map of rule names to skip reasons for rules that were skipped due to guard conditions.
     * Key is the rule name, value is the guard description explaining why it was skipped.
     */
    val skipped: Map<String, String>,

    /** Number of times rules fired during execution */
    val ruleActivations: Int,

    /** Number of iterations through the rule network before fixpoint */
    val iterations: Int,

    /**
     * Execution trace showing which rules fired and what they produced.
     *
     * Only populated when [EngineConfig.enableTracing] is true. When tracing is disabled,
     * this list is empty to avoid overhead.
     *
     * The trace is ordered chronologically (by when rules fired), respecting priority
     * ordering within each firing cycle.
     */
    val trace: List<RuleActivation> = emptyList(),

    /**
     * Warnings generated during execution.
     *
     * Currently includes:
     * - Runaway detection: When iterations exceed 100 and rule activations grow
     *   disproportionately (more than 2x iterations × number of producers), indicating
     *   possible infinite rule chains.
     */
    val warnings: List<String> = emptyList()
) {
    /** Get derived facts of a specific type */
    public inline fun <reified T : Any> derivedOfType(): Set<T> =
        derived.filterIsInstance<T>().toSet()

    /** Get all facts of a specific type */
    public inline fun <reified T : Any> factsOfType(): Set<T> =
        facts.filterIsInstance<T>().toSet()

    /** True if all validation rules passed */
    public val passed: Boolean
        get() = verdict.passed

    /** True if any validation rule failed */
    public val failed: Boolean
        get() = verdict.failed
}

/**
 * Record of a single rule activation during engine execution.
 *
 * Useful for debugging and understanding why facts were produced.
 *
 * @property ruleName The name of the rule that fired
 * @property inputFact The fact that triggered the rule
 * @property outputFacts The fact(s) produced by the rule (may be empty if condition matched but produced nothing)
 * @property priority The priority of the rule when it fired
 */
public data class RuleActivation(
    val ruleName: String,
    val inputFact: Any,
    val outputFacts: List<Any>,
    val priority: Int
)
