package verdikt.engine

/**
 * A named group of rules that execute together before the next phase.
 *
 * Phases allow you to group rules into ordered execution stages. All producers
 * within a phase run to fixpoint (no new facts produced) before the next phase starts.
 *
 * Example:
 * ```
 * val engine = engine {
 *     phase("discounts") {
 *         produce<Order, Discount>("vip-discount") {
 *             condition { it.customerTier == "gold" }
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
 */
public interface Phase {
    /** The name of this phase */
    public val name: String

    /** Names of fact producers in this phase */
    public val factProducerNames: List<String>

    /** Names of validation rules in this phase */
    public val validationRuleNames: List<String>

    /** Total number of rules in this phase */
    public val size: Int get() = factProducerNames.size + validationRuleNames.size
}

/**
 * Default implementation of [Phase].
 */
internal class PhaseImpl(
    override val name: String,
    val factProducers: List<InternalFactProducer<*, *>>,
    val validationRules: List<InternalValidationRule<*>>
) : Phase {
    override val factProducerNames: List<String>
        get() = factProducers.map { it.name }

    override val validationRuleNames: List<String>
        get() = validationRules.map { it.name }
}
