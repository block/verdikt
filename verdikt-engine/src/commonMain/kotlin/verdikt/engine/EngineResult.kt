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
    val iterations: Int
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
