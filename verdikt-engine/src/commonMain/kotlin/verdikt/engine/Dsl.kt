package verdikt.engine

/**
 * DSL marker for engine builders.
 * Prevents scope leakage in nested DSL blocks.
 */
@DslMarker
internal annotation class EngineDsl

/**
 * Creates a production rules engine.
 *
 * Each validation rule can have its own failure type, which is inferred from the
 * `onFailure` block. This allows different rules to return different failure types.
 *
 * Example:
 * ```
 * val pricingEngine = engine {
 *     produce<Customer, VipStatus>("vip-check") {
 *         condition { it.totalSpend > 10_000 }
 *         output { VipStatus(it.id, "gold") }
 *     }
 *
 *     validate<Order>("valid-quantity") {
 *         condition { it.quantity > 0 }
 *         onFailure { "Invalid quantity: ${it.quantity}" }  // Infers String
 *     }
 *
 *     validate<Order>("max-total") {
 *         condition { it.total < 10000 }
 *         onFailure { MaxOrderError(it.total) }  // Infers MaxOrderError
 *     }
 * }
 * ```
 *
 * @param block DSL block to configure the engine
 * @return A configured [Engine] instance
 */
public fun engine(
    block: EngineBuilder.() -> Unit
): Engine {
    val builder = EngineBuilder()
    builder.block()
    return builder.build()
}
