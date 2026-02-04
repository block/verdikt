package verdikt.engine

import verdikt.engine.rete.ReteCompiler

/**
 * Implementation of [Engine].
 *
 * Rete networks are compiled once at engine construction time and reused across
 * all evaluation sessions. Each session resets the network state before use.
 */
internal class EngineImpl(
    private val internalPhases: List<PhaseImpl>,
    private val config: EngineConfig = EngineConfig.DEFAULT
) : Engine {

    // Pre-process phases: sort fact producers by priority within each phase
    private val processedPhases: List<ProcessedPhase> = internalPhases.map { phase ->
        ProcessedPhase(
            name = phase.name,
            factProducers = phase.factProducers.sortedByDescending { it.priority },
            validationRules = phase.validationRules.sortedByDescending { it.priority }
        )
    }

    // Compile Rete networks once at engine construction - this is the key optimization
    private val compilationResults = processedPhases.map { phase ->
        ReteCompiler().compile(phase.factProducers)
    }

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
        val session = ReteSessionImpl(processedPhases, compilationResults, config, context)
        session.insertAll(facts)
        return session.fire()
    }

    override suspend fun evaluateAsync(facts: Collection<Any>, context: RuleContext): EngineResult {
        val session = ReteSessionImpl(processedPhases, compilationResults, config, context)
        session.insertAll(facts)
        return session.fireAsync()
    }
}

/**
 * Pre-processed phase with rules sorted by priority.
 */
internal data class ProcessedPhase(
    val name: String,
    val factProducers: List<InternalFactProducer<*, *>>,
    val validationRules: List<InternalValidationRule<*>>
)
