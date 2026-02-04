package verdikt.engine

import verdikt.AsyncRule
import verdikt.Rule
import kotlin.reflect.KClass

/**
 * DSL builder for creating a production rules engine.
 *
 * Example:
 * ```
 * val pricingEngine = engine {
 *     produce<Customer, VipStatus>("vip-check") {
 *         condition { it.totalSpend > 10_000 }
 *         output { VipStatus(it.id, "gold") }
 *     }
 *
 *     produce<VipStatus, Discount>("vip-discount") {
 *         condition { true }
 *         output { Discount(it.customerId, 20) }
 *     }
 *
 *     validate<Order>("valid-quantity") {
 *         condition { it.quantity > 0 }
 *         onFailure { "Invalid quantity: ${it.quantity}" }
 *     }
 * }
 * ```
 */
@EngineDsl
public class EngineBuilder @PublishedApi internal constructor() {
    @PublishedApi
    internal val phases: MutableList<Phase> = mutableListOf()

    // Rules defined outside of phases go into a "default" phase
    @PublishedApi
    internal val factProducers: MutableList<InternalFactProducer<*, *>> = mutableListOf()
    @PublishedApi
    internal val validationRules: MutableList<InternalValidationRule<*>> = mutableListOf()

    /**
     * Defines a producer that produces output facts when its condition is met.
     *
     * Producers are the core of forward chaining. When a fact of type [In] is
     * in working memory and satisfies the producer's condition, the producer fires and produces
     * a new fact of type [Out] that is added to working memory.
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
     * Validation rules are evaluated after all production rules have reached fixpoint.
     * Their results are collected in [EngineResult.verdict].
     *
     * Each validation rule can have its own failure type, which is inferred from the
     * `onFailure` block.
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
     * This allows reusing rules defined elsewhere in the engine's validation phase.
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
     * When using async rules, you must use [Engine.evaluateAsync] to execute the engine.
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
     * Adds a pre-built [FactProducer] to the engine.
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

    /**
     * Defines a named execution phase.
     *
     * Phases allow you to group rules into ordered execution stages. All producers
     * within a phase run to fixpoint (no new facts produced) before the next phase starts.
     * Validation rules from all phases are collected and run after all production phases.
     *
     * Example:
     * ```
     * val engine = engine {
     *     phase("discounts") {
     *         produce<Order, Discount>("vip-discount") {
     *             priority = 100
     *             guard("Must be VIP") { ctx -> ctx[CustomerTier] == "vip" }
     *             condition { it.subtotal > 100 }
     *             output { Discount(it.id, 10) }
     *         }
     *     }
     *
     *     phase("taxes") {
     *         produce<Order, Tax>("sales-tax") {
     *             condition { true }
     *             output { Tax(it.id, calculateTax(it)) }
     *         }
     *     }
     * }
     * ```
     *
     * @param name Unique name for this phase
     * @param block DSL block to configure the phase
     */
    public inline fun phase(name: String, block: PhaseBuilder.() -> Unit) {
        val builder = PhaseBuilder(name)
        builder.block()
        phases.add(builder.build())
    }

    @Suppress("UNCHECKED_CAST")
    internal fun build(config: EngineConfig = EngineConfig.DEFAULT): Engine {
        // Combine explicit phases with rules defined outside phases
        val allPhases = mutableListOf<PhaseImpl>()

        // If there are rules defined outside phases, add them as a "default" phase first
        if (factProducers.isNotEmpty() || validationRules.isNotEmpty()) {
            allPhases.add(PhaseImpl(
                name = "default",
                factProducers = factProducers.toList(),
                validationRules = validationRules.toList()
            ))
        }

        // Add explicit phases (cast from Phase to PhaseImpl - safe because PhaseBuilder creates PhaseImpl)
        allPhases.addAll(phases.map { it as PhaseImpl })

        return EngineImpl(internalPhases = allPhases, config = config)
    }
}
