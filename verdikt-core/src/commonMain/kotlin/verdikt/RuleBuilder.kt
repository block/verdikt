package verdikt

/**
 * Builder for constructing a single Rule.
 *
 * @param Fact The type of fact this rule evaluates
 * @param Cause The type of failure reason this rule returns
 */
@RuleDsl
public class RuleBuilder<Fact, Cause : Any> internal constructor(private val name: String) {
    /**
     * Human-readable description of what this rule checks.
     * Used in auto-generated failure messages when onFailure is not specified.
     */
    public var description: String = ""

    private var condition: ((Fact) -> Boolean)? = null
    private var asyncCondition: (suspend (Fact) -> Boolean)? = null
    private var failureReason: ((Fact) -> Cause)? = null

    /**
     * Defines the synchronous condition for this rule.
     * The rule passes if this returns true.
     */
    public fun condition(block: (Fact) -> Boolean) {
        require(asyncCondition == null) { "Cannot set both condition and asyncCondition" }
        condition = block
    }

    /**
     * Defines an asynchronous condition for this rule.
     * Use for I/O operations like database or network calls.
     * Async conditions run concurrently with other async conditions during evaluation.
     */
    public fun asyncCondition(block: suspend (Fact) -> Boolean) {
        require(condition == null) { "Cannot set both condition and asyncCondition" }
        asyncCondition = block
        // Set a placeholder sync condition that throws, for safety
        condition = { error("Async rule '$name' must be evaluated with evaluateAsync()") }
    }

    /**
     * Defines the failure reason when this rule doesn't pass.
     * The reason must match the Cause type parameter of the containing RuleSet.
     *
     * Example with enum:
     * ```
     * val rules = rules<Person, EligibilityCause> {
     *     rule("insufficient-balance") {
     *         condition { it.balance >= minimumRequired }
     *         onFailure { EligibilityCause.INSUFFICIENT_BALANCE }
     *     }
     * }
     * ```
     *
     * Example with dynamic message:
     * ```
     * val rules = rules<Person, String> {
     *     rule("name-length-check") {
     *         condition { it.name.length >= 3 }
     *         onFailure { person -> "Name '${person.name}' is too short (minimum 3 characters)" }
     *     }
     * }
     * ```
     *
     * Optional: If not specified, a default message is generated (requires Cause to be Any).
     */
    public fun onFailure(block: (Fact) -> Cause) {
        failureReason = block
    }

    /**
     * Defines a static failure reason.
     * The reason must match the Cause type parameter of the containing RuleSet.
     *
     * Optional: If not specified, a default message is generated (requires Cause to be Any).
     */
    public fun onFailure(reason: Cause) {
        failureReason = { reason }
    }

    @Suppress("UNCHECKED_CAST")
    internal fun build(): InternalRule<Fact, Cause> {
        requireNotNull(condition) { "Rule '$name' must have a condition or asyncCondition" }

        // Generate default failure reason if not provided
        // This cast is safe when Cause is Any (the default case)
        val resolvedFailureCause: (Fact) -> Cause = failureReason ?: {
            (description.ifBlank { "Rule '$name' failed" }) as Cause
        }

        return InternalRule(
            name = name,
            description = description,
            condition = condition!!,
            asyncCondition = asyncCondition,
            failureReasonFn = resolvedFailureCause
        )
    }
}
