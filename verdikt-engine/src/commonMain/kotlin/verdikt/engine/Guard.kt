package verdikt.engine

/**
 * A guard that determines if a rule should run based on context.
 *
 * Guards are checked before a rule's condition is evaluated. If a guard
 * is not satisfied, the rule is skipped and the guard's description
 * explains why.
 *
 * Example:
 * ```kotlin
 * rule("vip-discount") {
 *     guard("Customer must be VIP tier") { ctx ->
 *         ctx[CustomerTier] in listOf("gold", "platinum")
 *     }
 *     condition { it.subtotal > 100 }
 *     onFailure { "Order subtotal too low" }
 * }
 * ```
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
