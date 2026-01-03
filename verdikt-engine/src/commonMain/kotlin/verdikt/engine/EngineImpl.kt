package verdikt.engine

/**
 * Implementation of [Engine].
 */
internal class EngineImpl<Reason : Any>(
    internal val productionRules: List<InternalProductionRule<*, *>>,
    internal val validationRules: List<InternalValidationRule<*, Reason>>
) : Engine<Reason> {

    override val productionRuleNames: List<String>
        get() = productionRules.map { it.name }

    override val validationRuleNames: List<String>
        get() = validationRules.map { it.name }

    override val size: Int
        get() = productionRules.size + validationRules.size

    override val hasAsyncRules: Boolean
        get() = productionRules.any { it.isAsync } || validationRules.any { it.isAsync }

    override fun session(): Session<Reason> = SessionImpl(
        productionRules = productionRules,
        validationRules = validationRules
    )
}
