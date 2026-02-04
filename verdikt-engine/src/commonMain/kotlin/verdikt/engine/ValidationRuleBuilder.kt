package verdikt.engine

import kotlin.reflect.KClass

/**
 * DSL builder for creating validation rules within an engine.
 *
 * Validation rules check facts without producing new ones. They contribute to the
 * [EngineResult.verdict] after all production rules have reached fixpoint.
 *
 * Example:
 * ```
 * validate<Order>("valid-quantity") {
 *     description = "Order must have positive quantity"
 *     condition { it.quantity > 0 }
 *     onFailure { order -> "Invalid quantity: ${order.quantity}" }
 * }
 * ```
 *
 * @param Fact The fact type this rule validates
 */
@EngineDsl
public class ValidationRuleBuilder<Fact : Any> @PublishedApi internal constructor(
    private val name: String,
    private val inputType: KClass<Fact>
) {
    /** Human-readable description of what this rule validates */
    public var description: String = ""

    /**
     * Execution priority. Higher values run first within a phase.
     * Default is 0. Rules with the same priority run in definition order.
     */
    public var priority: Int = 0

    private var ruleGuard: Guard? = null
    private var condition: ((Fact) -> Boolean)? = null
    private var asyncCondition: (suspend (Fact) -> Boolean)? = null
    private var failureReason: ((Fact) -> Any)? = null

    /**
     * Sets the synchronous condition that must be true for validation to pass.
     *
     * @param block Predicate that returns true if the fact is valid
     */
    public fun condition(block: (Fact) -> Boolean) {
        require(asyncCondition == null) { "Cannot set both condition and asyncCondition" }
        condition = block
    }

    /**
     * Sets the async condition for validation rules that need I/O.
     *
     * When using asyncCondition, you must use [Engine.evaluateAsync] to execute the engine.
     *
     * @param block Suspend predicate that returns true if the fact is valid
     */
    public fun asyncCondition(block: suspend (Fact) -> Boolean) {
        require(condition == null) { "Cannot set both condition and asyncCondition" }
        asyncCondition = block
    }

    /**
     * Sets the failure reason as a dynamic function of the fact.
     *
     * Example:
     * ```
     * onFailure { order -> "Invalid quantity: ${order.quantity}" }
     * ```
     *
     * @param Cause The type of the failure reason (inferred from the lambda)
     * @param block Function that creates a failure reason from the invalid fact
     */
    public fun <Cause : Any> onFailure(block: (Fact) -> Cause) {
        failureReason = block
    }

    /**
     * Sets a guard that must be satisfied for this rule to run.
     * If the guard is not satisfied, the rule is skipped and the description is recorded.
     *
     * Example:
     * ```
     * validate<Order>("vip-discount-valid") {
     *     guard("Only applies to VIP orders") { ctx ->
     *         ctx[CustomerTier] in listOf("gold", "platinum")
     *     }
     *     condition { it.discount <= it.subtotal * 0.2 }
     *     onFailure { "VIP discount cannot exceed 20%" }
     * }
     * ```
     *
     * @param description Human-readable explanation of what this guard requires
     * @param predicate The guard condition that must return true for the rule to run
     */
    public fun guard(description: String, predicate: (RuleContext) -> Boolean) {
        ruleGuard = object : Guard {
            override val description: String = description
            override fun allows(context: RuleContext): Boolean = predicate(context)
        }
    }

    @PublishedApi
    internal fun build(): InternalValidationRule<Fact> {
        val resolvedCondition = condition ?: asyncCondition?.let { async ->
            { _: Fact -> error("Async validation rule '$name' must use fireAsync()") }
        }
        requireNotNull(resolvedCondition) { "Validation rule '$name' must have a condition or asyncCondition" }

        val resolvedFailure: (Fact) -> Any = failureReason ?: {
            description.ifBlank { "Rule '$name' failed" }
        }

        return InternalValidationRule(
            name = name,
            description = description,
            priority = priority,
            guard = ruleGuard,
            inputType = inputType,
            condition = resolvedCondition,
            asyncCondition = asyncCondition,
            failureReason = resolvedFailure
        )
    }
}

/**
 * Internal implementation of a validation rule.
 *
 * **This class is an internal implementation detail and should not be used directly.**
 * It is only visible due to Kotlin's `@PublishedApi` requirement for inline functions.
 *
 * Failure reasons are stored as `Any` to allow each rule to have its own failure type.
 *
 * Use the DSL builder functions like [EngineBuilder.validate] instead.
 */
@PublishedApi
internal class InternalValidationRule<Fact : Any>(
    val name: String,
    val description: String,
    val priority: Int = 0,
    val guard: Guard? = null,
    val inputType: KClass<Fact>,
    private val condition: (Fact) -> Boolean,
    internal val asyncCondition: (suspend (Fact) -> Boolean)?,
    internal val failureReason: (Fact) -> Any
) {
    internal val isAsync: Boolean
        get() = asyncCondition != null

    fun evaluate(fact: Fact): Boolean = condition(fact)

    suspend fun evaluateAsync(fact: Fact): Boolean =
        asyncCondition?.invoke(fact) ?: condition(fact)

    fun getFailureCause(fact: Fact): Any = failureReason(fact)
}
