package verdikt

/**
 * DSL marker to prevent scope leakage in nested builders.
 */
@DslMarker
internal annotation class RuleDsl

/**
 * Entry point for creating a rule set with typed failure reasons.
 *
 * Use this when you want compile-time type safety for failure reasons:
 *
 * ```
 * val personRules = rules<Person, EligibilityReason> {
 *     rule("name-length-check") {
 *         condition { it.name.length >= 3 }
 *         onFailure { EligibilityReason.NAME_TOO_SHORT }
 *     }
 * }
 *
 * val verdict = personRules.evaluate(person)
 * if (verdict is Verdict.Fail) {
 *     verdict.failures[0].reason  // typed as EligibilityReason
 * }
 * ```
 *
 * @param Fact The type of fact the rules evaluate
 * @param Reason The type of failure reasons (must be consistent across all rules)
 */
public fun <Fact, Reason : Any> rules(block: RuleSetBuilder<Fact, Reason>.() -> Unit): RuleSet<Fact, Reason> {
    val builder = RuleSetBuilder<Fact, Reason>()
    builder.block()
    return builder.build()
}

/**
 * Creates a standalone rule with typed failure reason.
 *
 * Example:
 * ```
 * val nameLengthRule = rule<Person, EligibilityReason>("name-length-check") {
 *     description = "Name must be at least 3 characters"
 *     condition { it.name.length >= 3 }
 *     onFailure { EligibilityReason.NAME_TOO_SHORT }
 * }
 * ```
 */
public fun <Fact, Reason : Any> rule(name: String, block: RuleBuilder<Fact, Reason>.() -> Unit): Rule<Fact, Reason> {
    val builder = RuleBuilder<Fact, Reason>(name)
    builder.block()
    return builder.build()
}
