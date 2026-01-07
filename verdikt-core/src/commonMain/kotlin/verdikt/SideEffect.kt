package verdikt

/**
 * Wraps this rule with a side effect that executes after each evaluation.
 *
 * Side effects are useful for observability: logging, analytics, metrics, etc.
 * The side effect receives the rule (as `this`), the fact, and whether it passed.
 *
 * Multiple side effects can be chained:
 * ```kotlin
 * val observedRule = ageRule
 *     .sideEffect { fact, passed ->
 *         analytics.track(this.name, mapOf("passed" to passed))
 *     }
 *     .sideEffect { fact, passed ->
 *         logger.info("${this.name} passed: $passed")
 *     }
 * ```
 *
 * @param block The side effect to execute. Receives the fact and pass/fail result.
 *              Inside the block, `this` refers to the original rule for access to name, description, etc.
 * @return A new Rule that wraps this rule and executes the side effect after evaluation.
 */
public fun <Fact, Cause : Any> Rule<Fact, Cause>.sideEffect(
    block: Rule<Fact, Cause>.(fact: Fact, passed: Boolean) -> Unit
): Rule<Fact, Cause> {
    val original = this
    val internalRule = original.toInternalRule()

    return InternalRule(
        name = name,
        description = description,
        condition = { fact ->
            val passed = internalRule.evaluate(fact)
            block(original, fact, passed)
            passed
        },
        asyncCondition = if (internalRule.isAsync) {
            { fact ->
                // Use evaluateToVerdictAsync to properly call the async condition
                val verdict = internalRule.evaluateToVerdictAsync(fact)
                val passed = verdict.passed
                block(original, fact, passed)
                passed
            }
        } else {
            null
        },
        failureReasonFn = internalRule::failureReason
    )
}

/**
 * Wraps this rule set with a side effect that executes after each evaluation.
 *
 * The side effect receives the fact and the verdict. Inside the block, `this` refers
 * to the original RuleSet for access to `names`, `size`, etc.
 *
 * ```kotlin
 * val observedRules = personRules
 *     .sideEffect { fact, verdict ->
 *         val passed = this.passedRules(verdict)
 *         val failed = this.failedRules(verdict)
 *         analytics.track("person_validation", mapOf(
 *             "passed" to passed.map { it.name },
 *             "failed" to failed.map { it.name }
 *         ))
 *     }
 * ```
 *
 * @param block The side effect to execute after evaluation.
 * @return A new RuleSet that wraps this one and executes the side effect after evaluation.
 */
public fun <Fact, Cause : Any> RuleSet<Fact, Cause>.sideEffect(
    block: RuleSet<Fact, Cause>.(fact: Fact, verdict: Verdict<Cause>) -> Unit
): RuleSet<Fact, Cause> {
    val original = this
    return ObservedRuleSet(original, block)
}

/**
 * Internal wrapper that adds side effect observation to a RuleSet.
 */
internal class ObservedRuleSet<Fact, Cause : Any>(
    private val delegate: RuleSet<Fact, Cause>,
    private val observer: RuleSet<Fact, Cause>.(fact: Fact, verdict: Verdict<Cause>) -> Unit
) : RuleSet<Fact, Cause> {

    override val size: Int get() = delegate.size
    override val isEmpty: Boolean get() = delegate.isEmpty
    override val rules: List<Rule<Fact, Cause>> get() = delegate.rules
    override val names: List<String> get() = delegate.names

    override fun evaluate(fact: Fact): Verdict<Cause> {
        val verdict = delegate.evaluate(fact)
        observer(delegate, fact, verdict)
        return verdict
    }

    override suspend fun evaluateAsync(fact: Fact): Verdict<Cause> {
        val verdict = delegate.evaluateAsync(fact)
        observer(delegate, fact, verdict)
        return verdict
    }

    override fun plus(other: RuleSet<Fact, Cause>): RuleSet<Fact, Cause> = delegate.plus(other)
}
