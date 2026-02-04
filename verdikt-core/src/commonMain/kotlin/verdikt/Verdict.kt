package verdikt

/**
 * Represents the result of evaluating one or more rules.
 *
 * @param Cause The type of failure reasons contained in this verdict
 */
public sealed interface Verdict<out Cause : Any> {
    /**
     * All rules passed.
     */
    public data object Pass : Verdict<Nothing>

    /**
     * One or more rules failed.
     * @property failures List of structured failures from each failed rule
     */
    public data class Fail<out Cause : Any>(val failures: List<Failure<Cause>>) : Verdict<Cause> {
        public constructor(failure: Failure<Cause>) : this(listOf(failure))

        /**
         * Failure messages as formatted strings (includes rule names).
         */
        public val messages: List<String> get() = failures.map { it.toString() }
    }

    /**
     * Returns true if this result represents a passing evaluation.
     */
    public val passed: Boolean get() = this is Pass

    /**
     * Returns true if this result represents a failed evaluation.
     */
    public val failed: Boolean get() = this is Fail
}

/** Number of failures (0 if passed) */
public val <Cause : Any> Verdict<Cause>.failureCount: Int
    get() = when (this) {
        is Verdict.Pass -> 0
        is Verdict.Fail -> failures.size
    }

/** Names of all failed rules (empty list if passed) */
public val <Cause : Any> Verdict<Cause>.failedRuleNames: List<String>
    get() = when (this) {
        is Verdict.Pass -> emptyList()
        is Verdict.Fail -> failures.map { it.ruleName }
    }

/** Check if a specific rule failed */
public fun <Cause : Any> Verdict<Cause>.hasFailure(ruleName: String): Boolean =
    when (this) {
        is Verdict.Pass -> false
        is Verdict.Fail -> failures.any { it.ruleName == ruleName }
    }

/** Filter failures by predicate */
public fun <Cause : Any> Verdict<Cause>.failuresMatching(
    predicate: (Failure<Cause>) -> Boolean
): List<Failure<Cause>> = when (this) {
    is Verdict.Pass -> emptyList()
    is Verdict.Fail -> failures.filter(predicate)
}

/**
 * Handles the verdict result by invoking the appropriate callback.
 *
 * Example:
 * ```
 * rules.evaluate(fact).handle(
 *     onPass = { println("All rules passed!") },
 *     onFail = { failures -> failures.forEach { println(it.reason) } }
 * )
 * ```
 *
 * @param onPass Called when all rules pass
 * @param onFail Called with the list of failures when any rule fails
 * @return The result of whichever callback was invoked
 */
public inline fun <Cause : Any, R> Verdict<Cause>.handle(
    onPass: () -> R,
    onFail: (List<Failure<Cause>>) -> R
): R = when (this) {
    is Verdict.Pass -> onPass()
    is Verdict.Fail -> onFail(failures)
}

/**
 * Combines this result with another, accumulating failures.
 */
internal operator fun <Cause : Any> Verdict<Cause>.plus(other: Verdict<Cause>): Verdict<Cause> = when {
    this is Verdict.Pass && other is Verdict.Pass -> Verdict.Pass
    this is Verdict.Pass && other is Verdict.Fail -> other
    this is Verdict.Fail && other is Verdict.Pass -> this
    this is Verdict.Fail && other is Verdict.Fail -> Verdict.Fail(this.failures + other.failures)
    else -> error("Unreachable")
}
