package verdikt.engine

import verdikt.AsyncRule
import verdikt.Rule

/**
 * DSL builder for creating an execution phase.
 *
 * A phase groups producers and validation rules that execute together.
 * Within a phase, producers run to fixpoint before the next phase starts.
 *
 * Example:
 * ```
 * phase("discounts") {
 *     produce<Order, Discount>("loyalty-discount") {
 *         priority = 100
 *         condition { it.customerPoints > 1000 }
 *         output { Discount(it.id, 5) }
 *     }
 *
 *     validate<Order>("has-customer") {
 *         condition { it.customerId != null }
 *         onFailure { "Order must have a customer" }
 *     }
 * }
 * ```
 */
@EngineDsl
public class PhaseBuilder @PublishedApi internal constructor(
    private val phaseName: String
) {
    @PublishedApi
    internal val factProducers: MutableList<InternalFactProducer<*, *>> = mutableListOf()
    @PublishedApi
    internal val validationRules: MutableList<InternalValidationRule<*>> = mutableListOf()

    /**
     * Defines a producer that produces output facts when its condition is met.
     *
     * @param In The input fact type this producer matches
     * @param Out The output fact type this producer produces
     * @param name Unique name for this producer
     * @param block DSL block to configure the producer
     */
    public inline fun <reified In : Any, reified Out : Any> produce(
        name: String,
        block: FactProducerBuilder<In, Out>.() -> Unit
    ) {
        val builder = FactProducerBuilder<In, Out>(name, In::class)
        builder.block()
        factProducers.add(builder.build())
    }

    /**
     * Defines a validation rule that checks facts without producing new ones.
     *
     * Each validation rule can have its own failure type, which is inferred from the
     * `onFailure` block.
     *
     * @param Fact The fact type this rule validates
     * @param name Unique name for this rule
     * @param block DSL block to configure the rule
     */
    public inline fun <reified Fact : Any> validate(
        name: String,
        block: ValidationRuleBuilder<Fact>.() -> Unit
    ) {
        val builder = ValidationRuleBuilder<Fact>(name, Fact::class)
        builder.block()
        validationRules.add(builder.build())
    }

    /**
     * Adds an existing [Rule] from core verdikt as a validation rule.
     *
     * @param Fact The fact type this rule validates
     * @param Cause The failure reason type
     * @param rule The existing rule to add
     */
    public inline fun <reified Fact : Any, Cause : Any> addValidation(rule: Rule<Fact, Cause>) {
        validationRules.add(
            InternalValidationRule(
                name = rule.name,
                description = rule.description,
                priority = 0,  // core rules don't have priority
                guard = null,  // core rules don't have guard
                inputType = Fact::class,
                condition = rule::evaluate,
                asyncCondition = null,
                failureReason = { fact -> rule.failureReason(fact) }
            )
        )
    }

    /**
     * Adds an existing [AsyncRule] from core verdikt as a validation rule.
     *
     * @param Fact The fact type this rule validates
     * @param Cause The failure reason type
     * @param rule The existing async rule to add
     */
    public inline fun <reified Fact : Any, Cause : Any> addValidation(rule: AsyncRule<Fact, Cause>) {
        validationRules.add(
            InternalValidationRule(
                name = rule.name,
                description = rule.description,
                priority = 0,  // core rules don't have priority
                guard = null,  // core rules don't have guard
                inputType = Fact::class,
                condition = { error("Async rule '${rule.name}' must use fireAsync()") },
                asyncCondition = rule::evaluate,
                failureReason = { fact -> rule.failureReason(fact) }
            )
        )
    }

    /**
     * Adds a pre-built [FactProducer] to this phase.
     *
     * @param In The input fact type
     * @param Out The output fact type
     * @param rule The fact producer to add
     */
    public inline fun <reified In : Any, Out : Any> add(rule: FactProducer<In, Out>) {
        factProducers.add(
            InternalFactProducer(
                name = rule.name,
                description = rule.description,
                priority = rule.priority,
                guard = rule.guard,
                inputType = In::class,
                condition = rule::matches,
                asyncCondition = null,
                outputFn = rule::produce,
                asyncOutputFn = null
            )
        )
    }

    @PublishedApi
    internal fun build(): Phase = PhaseImpl(
        name = phaseName,
        factProducers = factProducers.toList(),
        validationRules = validationRules.toList()
    )
}
