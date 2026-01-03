package verdikt.engine

/**
 * DSL marker for engine builders.
 * Prevents scope leakage in nested DSL blocks.
 */
@DslMarker
public annotation class EngineDsl

/**
 * Creates a production rules engine with the specified failure reason type.
 *
 * Example:
 * ```
 * val pricingEngine = engine<String> {
 *     produce<Customer, VipStatus>("vip-check") {
 *         condition { it.totalSpend > 10_000 }
 *         output { VipStatus(it.id, "gold") }
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
 * @param block DSL block to configure the engine
 * @return A configured [Engine] instance
 */
public fun <Reason : Any> engine(
    block: EngineBuilder<Reason>.() -> Unit
): Engine<Reason> {
    val builder = EngineBuilder<Reason>()
    builder.block()
    return builder.build()
}
