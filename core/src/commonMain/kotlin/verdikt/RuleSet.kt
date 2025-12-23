package verdikt

import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope

/**
 * An ordered collection of rules that can be evaluated against a fact.
 *
 * Create a rule set using the DSL:
 * ```
 * // With typed failure reasons (e.g., enum)
 * val personRules = rules<Person, EligibilityReason> {
 *     rule("name-length-check") {
 *         condition { it.name.length >= 3 }
 *         onFailure { EligibilityReason.NAME_TOO_SHORT }
 *     }
 * }
 *
 * // With string failure reasons
 * val simpleRules = rules<Person, String> {
 *     rule("name-length-check") {
 *         condition { it.name.length >= 3 }
 *         onFailure { "Name must be at least 3 characters" }
 *     }
 * }
 * ```
 *
 * @param Fact The type of fact these rules evaluate
 * @param Reason The type of failure reasons returned by rules
 */
public interface RuleSet<Fact, out Reason : Any> {
    /**
     * Number of rules in this set.
     */
    public val size: Int

    /**
     * Returns true if this set contains no rules.
     */
    public val isEmpty: Boolean

    /**
     * All rules in this set, in order.
     */
    public val rules: List<Rule<Fact, Reason>>

    /**
     * Names of all rules in this set, in order.
     */
    public val names: List<String>

    /**
     * Evaluates all rules sequentially, collecting all failures.
     *
     * @throws IllegalStateException if any rule uses asyncCondition
     * @throws Exception if any rule's condition throws
     * @return Pass if all rules pass, Fail with all failures otherwise
     */
    public fun evaluate(fact: Fact): Verdict<Reason>

    /**
     * Evaluates all rules, running async conditions concurrently.
     * Use this when your rule set contains asyncCondition rules.
     *
     * @throws Exception if any rule's condition throws
     * @return Pass if all rules pass, Fail with all failures otherwise
     */
    public suspend fun evaluateAsync(fact: Fact): Verdict<Reason>

    /**
     * Combines two rule sets into one.
     * Rules from the other set are appended after this set's rules.
     */
    public operator fun plus(other: RuleSet<Fact, @UnsafeVariance Reason>): RuleSet<Fact, Reason>
}

/**
 * Internal implementation of RuleSet.
 */
internal open class RuleSetImpl<Fact, Reason : Any>(
    internal val internalRules: List<InternalRule<Fact, Reason>>
) : RuleSet<Fact, Reason> {

    override val size: Int get() = internalRules.size

    override val isEmpty: Boolean get() = internalRules.isEmpty()

    internal val hasAsyncRules: Boolean get() = internalRules.any { it.isAsync }

    override val rules: List<Rule<Fact, Reason>> get() = internalRules

    override val names: List<String> get() = internalRules.map { it.name }

    internal operator fun get(name: String): InternalRule<Fact, Reason>? = internalRules.find { it.name == name }

    internal operator fun get(index: Int): InternalRule<Fact, Reason> = internalRules[index]

    override fun plus(other: RuleSet<Fact, Reason>): RuleSet<Fact, Reason> {
        val otherRules = when (other) {
            is RuleSetImpl<Fact, Reason> -> other.internalRules
            else -> other.names.map { name ->
                // Fallback for custom implementations - wrap in a simple rule
                @Suppress("UNCHECKED_CAST")
                InternalRule<Fact, Reason>(
                    name = name,
                    description = "",
                    condition = { fact -> other.evaluate(fact).passed },
                    asyncCondition = null,
                    failureReasonFn = { "Rule '$name' failed" as Reason }
                )
            }
        }
        return RuleSetImpl(internalRules + otherRules)
    }

    override fun evaluate(fact: Fact): Verdict<Reason> {
        check(!hasAsyncRules) {
            "RuleSet contains async rules. Use evaluateAsync() instead."
        }
        return computeVerdict(fact)
    }

    override suspend fun evaluateAsync(fact: Fact): Verdict<Reason> {
        return computeVerdictAsync(fact)
    }

    private fun computeVerdict(fact: Fact): Verdict<Reason> {
        if (internalRules.isEmpty()) return Verdict.Pass

        val failures = mutableListOf<Failure<Reason>>()

        for (rule in internalRules) {
            when (val result = rule.evaluateToVerdict(fact)) {
                is Verdict.Pass -> continue
                is Verdict.Fail -> failures.addAll(result.failures)
            }
        }

        return if (failures.isEmpty()) {
            Verdict.Pass
        } else {
            Verdict.Fail(failures)
        }
    }

    private suspend fun computeVerdictAsync(fact: Fact): Verdict<Reason> = coroutineScope {
        if (internalRules.isEmpty()) return@coroutineScope Verdict.Pass

        val results = internalRules.map { rule ->
            async { rule.evaluateToVerdictAsync(fact) }
        }.awaitAll()

        val failures = results
            .filterIsInstance<Verdict.Fail<Reason>>()
            .flatMap { it.failures }

        if (failures.isEmpty()) {
            Verdict.Pass
        } else {
            Verdict.Fail(failures)
        }
    }

    internal companion object {
        fun <Fact, Reason : Any> create(rules: List<InternalRule<Fact, Reason>>): RuleSet<Fact, Reason> = RuleSetImpl(rules)
    }
}

/**
 * Returns the rules that failed for the given verdict.
 *
 * Example:
 * ```
 * val verdict = myRules.evaluate(fact)
 * val failed = myRules.failedRules(verdict)
 * val passed = myRules.passedRules(verdict)
 * ```
 *
 * @param verdict The result of evaluating this rule set
 * @return List of rules that failed (empty if verdict is Pass)
 */
public fun <Fact, Reason : Any> RuleSet<Fact, Reason>.failedRules(verdict: Verdict<Reason>): List<Rule<Fact, Reason>> {
    val failedNames = when (verdict) {
        is Verdict.Pass -> emptySet()
        is Verdict.Fail -> verdict.failures.map { it.ruleName }.toSet()
    }
    return rules.filter { it.name in failedNames }
}

/**
 * Returns the rules that passed for the given verdict.
 *
 * Example:
 * ```
 * val verdict = myRules.evaluate(fact)
 * val passed = myRules.passedRules(verdict)
 * val failed = myRules.failedRules(verdict)
 * ```
 *
 * @param verdict The result of evaluating this rule set
 * @return List of rules that passed
 */
public fun <Fact, Reason : Any> RuleSet<Fact, Reason>.passedRules(verdict: Verdict<Reason>): List<Rule<Fact, Reason>> {
    val failedNames = when (verdict) {
        is Verdict.Pass -> emptySet()
        is Verdict.Fail -> verdict.failures.map { it.ruleName }.toSet()
    }
    return rules.filter { it.name !in failedNames }
}
