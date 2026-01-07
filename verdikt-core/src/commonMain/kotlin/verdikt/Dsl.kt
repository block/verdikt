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
 * val personRules = rules<Person, EligibilityCause> {
 *     rule("name-length-check") {
 *         condition { it.name.length >= 3 }
 *         onFailure { EligibilityCause.NAME_TOO_SHORT }
 *     }
 * }
 *
 * val verdict = personRules.evaluate(person)
 * if (verdict is Verdict.Fail) {
 *     verdict.failures[0].reason  // typed as EligibilityCause
 * }
 * ```
 *
 * @param Fact The type of fact the rules evaluate
 * @param Cause The type of failure reasons (must be consistent across all rules)
 */
public fun <Fact, Cause : Any> rules(block: RuleSetBuilder<Fact, Cause>.() -> Unit): RuleSet<Fact, Cause> {
    val builder = RuleSetBuilder<Fact, Cause>()
    builder.block()
    return builder.build()
}

/**
 * Creates a standalone rule with typed failure reason.
 *
 * Example:
 * ```
 * val nameLengthRule = rule<Person, EligibilityCause>("name-length-check") {
 *     description = "Name must be at least 3 characters"
 *     condition { it.name.length >= 3 }
 *     onFailure { EligibilityCause.NAME_TOO_SHORT }
 * }
 * ```
 */
public fun <Fact, Cause : Any> rule(name: String, block: RuleBuilder<Fact, Cause>.() -> Unit): Rule<Fact, Cause> {
    val builder = RuleBuilder<Fact, Cause>(name)
    builder.block()
    return builder.build()
}
