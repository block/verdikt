package verdikt.engine

import kotlin.reflect.KClass

/**
 * DSL builder for creating production rules.
 *
 * Example:
 * ```
 * produce<Customer, VipStatus>("vip-check") {
 *     description = "Customers who spent over 10k are VIPs"
 *     condition { it.totalSpend > 10_000 }
 *     output { customer -> VipStatus(customer.id, tier = "gold") }
 * }
 * ```
 *
 * @param In The input fact type this rule matches
 * @param Out The output fact type this rule produces
 */
@EngineDsl
public class ProductionRuleBuilder<In : Any, Out : Any> @PublishedApi internal constructor(
    private val name: String,
    private val inputType: KClass<In>
) {
    /** Human-readable description of what this rule does */
    public var description: String = ""

    private var condition: ((In) -> Boolean)? = null
    private var asyncCondition: (suspend (In) -> Boolean)? = null
    private var outputFn: ((In) -> Out)? = null
    private var asyncOutputFn: (suspend (In) -> Out)? = null

    /**
     * Sets the synchronous condition for when this rule should fire.
     *
     * @param block Predicate that returns true if the rule should fire
     */
    public fun condition(block: (In) -> Boolean) {
        require(asyncCondition == null) { "Cannot set both condition and asyncCondition" }
        condition = block
    }

    /**
     * Sets the async condition for rules that need I/O to determine if they match.
     *
     * When using asyncCondition, you must use [Session.fireAsync] to execute the engine.
     *
     * @param block Suspend predicate that returns true if the rule should fire
     */
    public fun asyncCondition(block: suspend (In) -> Boolean) {
        require(condition == null) { "Cannot set both condition and asyncCondition" }
        asyncCondition = block
    }

    /**
     * Defines the output fact produced when this rule fires.
     *
     * @param block Function that creates the output fact from the input
     */
    public fun output(block: (In) -> Out) {
        require(asyncOutputFn == null) { "Cannot set both output and asyncOutput" }
        outputFn = block
    }

    /**
     * Defines the async output for rules that need I/O to produce output.
     *
     * When using asyncOutput, you must use [Session.fireAsync] to execute the engine.
     *
     * @param block Suspend function that creates the output fact from the input
     */
    public fun asyncOutput(block: suspend (In) -> Out) {
        require(outputFn == null) { "Cannot set both output and asyncOutput" }
        asyncOutputFn = block
    }

    @PublishedApi
    internal fun build(): InternalProductionRule<In, Out> {
        val resolvedCondition = condition ?: asyncCondition?.let { async ->
            { _: In -> error("Async rule '$name' must use fireAsync()") }
        }
        requireNotNull(resolvedCondition) { "Rule '$name' must have a condition or asyncCondition" }

        val resolvedOutput = outputFn ?: asyncOutputFn?.let { async ->
            { _: In -> error("Async rule '$name' must use fireAsync()") }
        }
        requireNotNull(resolvedOutput) { "Rule '$name' must have an output or asyncOutput" }

        return InternalProductionRule(
            name = name,
            description = description,
            inputType = inputType,
            condition = resolvedCondition,
            asyncCondition = asyncCondition,
            outputFn = resolvedOutput,
            asyncOutputFn = asyncOutputFn
        )
    }
}

/**
 * Internal implementation of a production rule.
 */
@PublishedApi
internal class InternalProductionRule<In : Any, Out : Any>(
    override val name: String,
    override val description: String,
    override val inputType: KClass<In>,
    private val condition: (In) -> Boolean,
    internal val asyncCondition: (suspend (In) -> Boolean)?,
    private val outputFn: (In) -> Out,
    internal val asyncOutputFn: (suspend (In) -> Out)?
) : ProductionRule<In, Out> {

    internal val isAsync: Boolean
        get() = asyncCondition != null || asyncOutputFn != null

    override fun matches(fact: In): Boolean = condition(fact)

    override fun produce(fact: In): Out = outputFn(fact)

    internal suspend fun matchesAsync(fact: In): Boolean =
        asyncCondition?.invoke(fact) ?: condition(fact)

    internal suspend fun produceAsync(fact: In): Out =
        asyncOutputFn?.invoke(fact) ?: outputFn(fact)
}
