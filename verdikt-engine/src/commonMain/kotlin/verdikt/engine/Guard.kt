package verdikt.engine

/**
 * A guard that determines if a rule should run based on external context.
 *
 * ## Guard vs Condition
 *
 * **Guards** gate rule execution based on external context (feature flags, user tiers,
 * environment settings). They receive a [RuleContext] and are evaluated BEFORE examining
 * the fact. Use guards for "can this rule run at all?"
 *
 * **Conditions** evaluate business logic on the fact itself. They receive the fact and
 * determine if the rule should fire for this specific input. Use conditions for "should
 * this rule fire for this fact?"
 *
 * ## Example
 *
 * ```kotlin
 * produce<Order, Discount>("vip-discount") {
 *     // Guard: context-based gating (runs first)
 *     guard("Feature flag must be enabled") { ctx ->
 *         ctx[FeatureFlags].vipDiscountsEnabled
 *     }
 *     // Condition: fact-based logic (runs if guard passes)
 *     condition { order -> order.subtotal > 100 }
 *     output { Discount(it.id, it.subtotal * 0.1) }
 * }
 * ```
 *
 * If the guard is not satisfied, the rule is skipped entirely and the guard's
 * description explains why in the trace output.
 */
public interface Guard {
    /** Human-readable explanation of what this guard requires */
    public val description: String

    /** Returns true if the guard allows the rule to run */
    public fun allows(context: RuleContext): Boolean
}

/**
 * Creates a guard with the given description and predicate.
 */
public fun guard(description: String, predicate: (RuleContext) -> Boolean): Guard =
    object : Guard {
        override val description: String = description
        override fun allows(context: RuleContext): Boolean = predicate(context)
    }
