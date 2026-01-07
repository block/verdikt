package verdikt.engine

/**
 * Implementation of [Engine].
 */
internal class EngineImpl(
    private val internalPhases: List<PhaseImpl>
) : Engine {

    override val phases: List<Phase>
        get() = internalPhases

    override val factProducerNames: List<String>
        get() = internalPhases.flatMap { phase -> phase.factProducers.map { it.name } }

    override val validationRuleNames: List<String>
        get() = internalPhases.flatMap { phase -> phase.validationRules.map { it.name } }

    override val size: Int
        get() = internalPhases.sumOf { it.factProducers.size + it.validationRules.size }

    override val hasAsyncRules: Boolean
        get() = internalPhases.any { phase ->
            phase.factProducers.any { it.isAsync } || phase.validationRules.any { it.isAsync }
        }

    override fun evaluate(facts: Collection<Any>, context: RuleContext): EngineResult {
        val session = SessionImpl(internalPhases, context)
        session.insertAll(facts)
        return session.fire()
    }

    override suspend fun evaluateAsync(facts: Collection<Any>, context: RuleContext): EngineResult {
        val session = SessionImpl(internalPhases, context)
        session.insertAll(facts)
        return session.fireAsync()
    }
}
