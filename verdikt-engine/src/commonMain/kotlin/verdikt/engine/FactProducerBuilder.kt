package verdikt.engine

import kotlin.reflect.KClass

/**
 * DSL builder for creating fact producers.
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
 * @param In The input fact type this fact producer matches
 * @param Out The output fact type this fact producer produces
 */
@EngineDsl
public class FactProducerBuilder<In : Any, Out : Any> @PublishedApi internal constructor(
    private val name: String,
    private val inputType: KClass<In>
) {
    /** Human-readable description of what this fact producer does */
    public var description: String = ""

    /**
     * Execution priority. Higher values run first within a phase.
     * Default is 0. Fact producers with the same priority run in definition order.
     */
    public var priority: Int = 0

    private var ruleGuard: Guard? = null
    private var condition: ((In) -> Boolean)? = null
    private var asyncCondition: (suspend (In) -> Boolean)? = null
    private var outputFn: ((In) -> Out)? = null
    private var asyncOutputFn: (suspend (In) -> Out)? = null

    /**
     * Sets the synchronous condition for when this fact producer should fire.
     *
     * @param block Predicate that returns true if the fact producer should fire
     */
    public fun condition(block: (In) -> Boolean) {
        require(asyncCondition == null) { "Cannot set both condition and asyncCondition" }
        condition = block
    }

    /**
     * Sets the async condition for fact producers that need I/O to determine if they match.
     *
     * When using asyncCondition, you must use [Engine.evaluateAsync] to execute the engine.
     *
     * @param block Suspend predicate that returns true if the fact producer should fire
     */
    public fun asyncCondition(block: suspend (In) -> Boolean) {
        require(condition == null) { "Cannot set both condition and asyncCondition" }
        asyncCondition = block
    }

    /**
     * Defines the output fact produced when this fact producer fires.
     *
     * @param block Function that creates the output fact from the input
     */
    public fun output(block: (In) -> Out) {
        require(asyncOutputFn == null) { "Cannot set both output and asyncOutput" }
        outputFn = block
    }

    /**
     * Defines the async output for fact producers that need I/O to produce output.
     *
     * When using asyncOutput, you must use [Engine.evaluateAsync] to execute the engine.
     *
     * @param block Suspend function that creates the output fact from the input
     */
    public fun asyncOutput(block: suspend (In) -> Out) {
        require(outputFn == null) { "Cannot set both output and asyncOutput" }
        asyncOutputFn = block
    }

    /**
     * Sets a guard that must be satisfied for this fact producer to run.
     * If the guard is not satisfied, the fact producer is skipped and the description is recorded.
     *
     * Example:
     * ```
     * produce<Order, Discount>("vip-discount") {
     *     guard("Customer must be VIP tier") { ctx ->
     *         ctx[CustomerTier] in listOf("gold", "platinum")
     *     }
     *     condition { it.subtotal > 5000 }
     *     output { Discount(it.id, it.subtotal / 10) }
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
    internal fun build(): InternalFactProducer<In, Out> {
        val resolvedCondition = condition ?: asyncCondition?.let { async ->
            { _: In -> error("Async fact producer '$name' must use fireAsync()") }
        }
        requireNotNull(resolvedCondition) { "FactProducer '$name' must have a condition or asyncCondition" }

        val resolvedOutput = outputFn ?: asyncOutputFn?.let { async ->
            { _: In -> error("Async fact producer '$name' must use fireAsync()") }
        }
        requireNotNull(resolvedOutput) { "FactProducer '$name' must have an output or asyncOutput" }

        return InternalFactProducer(
            name = name,
            description = description,
            priority = priority,
            guard = ruleGuard,
            inputType = inputType,
            condition = resolvedCondition,
            asyncCondition = asyncCondition,
            outputFn = resolvedOutput,
            asyncOutputFn = asyncOutputFn
        )
    }
}

/**
 * Internal implementation of a fact producer.
 */
@PublishedApi
internal class InternalFactProducer<In : Any, Out : Any>(
    override val name: String,
    override val description: String,
    override val priority: Int = 0,
    override val guard: Guard? = null,
    override val inputType: KClass<In>,
    private val condition: (In) -> Boolean,
    internal val asyncCondition: (suspend (In) -> Boolean)?,
    private val outputFn: (In) -> Out,
    internal val asyncOutputFn: (suspend (In) -> Out)?
) : FactProducer<In, Out> {

    internal val isAsync: Boolean
        get() = asyncCondition != null || asyncOutputFn != null

    override fun matches(fact: In): Boolean = condition(fact)

    override fun produce(fact: In): Out = outputFn(fact)

    internal suspend fun matchesAsync(fact: In): Boolean =
        asyncCondition?.invoke(fact) ?: condition(fact)

    internal suspend fun produceAsync(fact: In): Out =
        asyncOutputFn?.invoke(fact) ?: outputFn(fact)
}
