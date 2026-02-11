package verdikt.engine

import verdikt.engine.rete.CompilationResult
import verdikt.engine.rete.ReteCompiler

/**
 * Implementation of [Engine].
 *
 * Rete networks are compiled once at construction and reused across evaluations.
 * Session state is reset at the start of each phase via [ReteNetwork.reset][verdikt.engine.rete.ReteNetwork.reset].
 *
 * **Thread safety:** This class is NOT safe for concurrent [evaluate]/[evaluateAsync] calls
 * from multiple threads. Each call mutates shared network state (alpha memories, output node
 * pending lists). For concurrent use, create separate [Engine] instances or synchronize externally.
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

    // Compile Rete networks once at construction (amortized across evaluations)
    private val compilationResults: List<CompilationResult> =
        processedPhases.map { ReteCompiler().compile(it.factProducers) }

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

    override fun evaluate(
        facts: Collection<Any>,
        context: RuleContext,
        collector: EngineEventCollector
    ): EngineResult {
        val session = ReteSessionImpl(processedPhases, compilationResults, config, context, collector)
        session.insertAll(facts)
        return session.fire()
    }

    override suspend fun evaluateAsync(
        facts: Collection<Any>,
        context: RuleContext,
        collector: EngineEventCollector
    ): EngineResult {
        val session = ReteSessionImpl(processedPhases, compilationResults, config, context, collector)
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
