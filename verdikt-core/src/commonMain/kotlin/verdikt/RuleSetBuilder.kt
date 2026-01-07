package verdikt

/**
 * Builder for constructing a RuleSet.
 *
 * @param Fact The type of fact the rules evaluate
 * @param Cause The type of failure reasons returned by rules
 */
@RuleDsl
public class RuleSetBuilder<Fact, Cause : Any> internal constructor() {
    private val rules = mutableListOf<InternalRule<Fact, Cause>>()

    /**
     * Defines a rule inline within this rule set.
     */
    public fun rule(name: String, block: RuleBuilder<Fact, Cause>.() -> Unit) {
        val builder = RuleBuilder<Fact, Cause>(name)
        builder.block()
        rules.add(builder.build())
    }

    /**
     * Adds a rule to this rule set.
     *
     * Example:
     * ```
     * object NameLengthRule : Rule<Person, String> {
     *     override val name = "name-length-check"
     *     override val description = "Name must be at least 3 characters"
     *     override fun evaluate(fact: Person) = fact.name.length >= 3
     *     override fun failureReason(fact: Person) = "Name too short"
     * }
     *
     * val rules = rules<Person, String> {
     *     add(NameLengthRule)
     * }
     * ```
     */
    public fun add(rule: Rule<Fact, Cause>) {
        rules.add(rule.toInternalRule())
    }

    /**
     * Adds an async rule to this rule set.
     *
     * Example:
     * ```
     * class DbCheckRule(private val repo: UserRepo) : AsyncRule<User, String> {
     *     override val name = "db-check"
     *     override suspend fun evaluate(fact: User) = repo.isActive(fact.id)
     *     override fun failureReason(fact: User) = "User not active"
     * }
     *
     * val rules = rules<User, String> {
     *     add(DbCheckRule(repo))
     * }
     * ```
     */
    public fun add(rule: AsyncRule<Fact, Cause>) {
        rules.add(rule.toInternalRule())
    }

    /**
     * Includes all rules from another rule set.
     * Rules are added in their original order.
     */
    @Suppress("UNCHECKED_CAST")
    public fun include(ruleSet: RuleSet<Fact, Cause>) {
        when (ruleSet) {
            is RuleSetImpl<Fact, Cause> -> rules.addAll(ruleSet.internalRules)
            else -> {
                // For custom implementations, add rules by evaluating them
                ruleSet.names.forEach { name ->
                    rules.add(InternalRule(
                        name = name,
                        description = "",
                        condition = { fact -> ruleSet.evaluate(fact).passed },
                        asyncCondition = null,
                        failureReasonFn = { "Rule '$name' failed" as Cause }
                    ))
                }
            }
        }
    }

    internal fun build(): RuleSet<Fact, Cause> = RuleSetImpl.create(rules.toList())
}
