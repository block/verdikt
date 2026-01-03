package verdikt.engine

import verdikt.AsyncRule
import verdikt.Rule
import kotlin.reflect.KClass

/**
 * DSL builder for creating a production rules engine.
 *
 * Example:
 * ```
 * val pricingEngine = engine<String> {
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
 *
 * @param Reason The type used for validation failure reasons
 */
@EngineDsl
public class EngineBuilder<Reason : Any> @PublishedApi internal constructor() {
    @PublishedApi
    internal val productionRules: MutableList<InternalProductionRule<*, *>> = mutableListOf()
    @PublishedApi
    internal val validationRules: MutableList<InternalValidationRule<*, Reason>> = mutableListOf()

    /**
     * Defines a production rule that produces output facts when its condition is met.
     *
     * Production rules are the core of forward chaining. When a fact of type [In] is
     * in working memory and satisfies the rule's condition, the rule fires and produces
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
     * @param In The input fact type this rule matches
     * @param Out The output fact type this rule produces
     * @param name Unique name for this rule
     * @param block DSL block to configure the rule
     */
    public inline fun <reified In : Any, reified Out : Any> produce(
        name: String,
        block: ProductionRuleBuilder<In, Out>.() -> Unit
    ) {
        val builder = ProductionRuleBuilder<In, Out>(name, In::class)
        builder.block()
        productionRules.add(builder.build())
    }

    /**
     * Defines a validation rule that checks facts without producing new ones.
     *
     * Validation rules are evaluated after all production rules have reached fixpoint.
     * Their results are collected in [EngineResult.verdict].
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
        block: ValidationRuleBuilder<Fact, Reason>.() -> Unit
    ) {
        val builder = ValidationRuleBuilder<Fact, Reason>(name, Fact::class)
        builder.block()
        validationRules.add(builder.build())
    }

    /**
     * Adds an existing [Rule] from core verdikt as a validation rule.
     *
     * This allows reusing rules defined elsewhere in the engine's validation phase.
     *
     * @param Fact The fact type this rule validates
     * @param rule The existing rule to add
     */
    public inline fun <reified Fact : Any> addValidation(rule: Rule<Fact, Reason>) {
        validationRules.add(
            InternalValidationRule(
                name = rule.name,
                description = rule.description,
                inputType = Fact::class,
                condition = rule::evaluate,
                asyncCondition = null,
                failureReason = rule::failureReason
            )
        )
    }

    /**
     * Adds an existing [AsyncRule] from core verdikt as a validation rule.
     *
     * When using async rules, you must use [Session.fireAsync] to execute the engine.
     *
     * @param Fact The fact type this rule validates
     * @param rule The existing async rule to add
     */
    public inline fun <reified Fact : Any> addValidation(rule: AsyncRule<Fact, Reason>) {
        validationRules.add(
            InternalValidationRule(
                name = rule.name,
                description = rule.description,
                inputType = Fact::class,
                condition = { error("Async rule '${rule.name}' must use fireAsync()") },
                asyncCondition = rule::evaluate,
                failureReason = rule::failureReason
            )
        )
    }

    /**
     * Adds a pre-built [ProductionRule] to the engine.
     *
     * @param In The input fact type
     * @param Out The output fact type
     * @param rule The production rule to add
     */
    public inline fun <reified In : Any, Out : Any> add(rule: ProductionRule<In, Out>) {
        productionRules.add(
            InternalProductionRule(
                name = rule.name,
                description = rule.description,
                inputType = In::class,
                condition = rule::matches,
                asyncCondition = null,
                outputFn = rule::produce,
                asyncOutputFn = null
            )
        )
    }

    internal fun build(): Engine<Reason> = EngineImpl(
        productionRules = productionRules.toList(),
        validationRules = validationRules.toList()
    )
}
