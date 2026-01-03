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
 * @param Reason The type used for failure reasons
 */
@EngineDsl
public class ValidationRuleBuilder<Fact : Any, Reason : Any> @PublishedApi internal constructor(
    private val name: String,
    private val inputType: KClass<Fact>
) {
    /** Human-readable description of what this rule validates */
    public var description: String = ""

    private var condition: ((Fact) -> Boolean)? = null
    private var asyncCondition: (suspend (Fact) -> Boolean)? = null
    private var failureReason: ((Fact) -> Reason)? = null

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
     * When using asyncCondition, you must use [Session.fireAsync] to execute the engine.
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
     * @param block Function that creates a failure reason from the invalid fact
     */
    public fun onFailure(block: (Fact) -> Reason) {
        failureReason = block
    }

    /**
     * Sets a static failure reason.
     *
     * @param reason The failure reason to use when validation fails
     */
    public fun onFailure(reason: Reason) {
        failureReason = { reason }
    }

    @Suppress("UNCHECKED_CAST")
    @PublishedApi
    internal fun build(): InternalValidationRule<Fact, Reason> {
        val resolvedCondition = condition ?: asyncCondition?.let { async ->
            { _: Fact -> error("Async validation rule '$name' must use fireAsync()") }
        }
        requireNotNull(resolvedCondition) { "Validation rule '$name' must have a condition or asyncCondition" }

        val resolvedFailure: (Fact) -> Reason = failureReason ?: {
            (description.ifBlank { "Rule '$name' failed" }) as Reason
        }

        return InternalValidationRule(
            name = name,
            description = description,
            inputType = inputType,
            condition = resolvedCondition,
            asyncCondition = asyncCondition,
            failureReason = resolvedFailure
        )
    }
}

/**
 * Internal implementation of a validation rule.
 */
@PublishedApi
internal class InternalValidationRule<Fact : Any, Reason : Any>(
    val name: String,
    val description: String,
    val inputType: KClass<Fact>,
    private val condition: (Fact) -> Boolean,
    internal val asyncCondition: (suspend (Fact) -> Boolean)?,
    internal val failureReason: (Fact) -> Reason
) {
    internal val isAsync: Boolean
        get() = asyncCondition != null

    fun evaluate(fact: Fact): Boolean = condition(fact)

    suspend fun evaluateAsync(fact: Fact): Boolean =
        asyncCondition?.invoke(fact) ?: condition(fact)

    fun getFailureReason(fact: Fact): Reason = failureReason(fact)
}
