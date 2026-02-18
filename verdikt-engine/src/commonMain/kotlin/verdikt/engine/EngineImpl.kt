package verdikt.engine

import verdikt.engine.rete.CompilationResult
import verdikt.engine.rete.ReteCompiler

/**
 * Implementation of [Engine].
 *
 * Rete networks are compiled once at construction time. Each [evaluate]/[evaluateAsync]
 * call creates a lightweight copy of the compiled networks (via [verdikt.engine.rete.ReteNetwork.copy])
 * so that sessions have fully independent mutable state while sharing the compiled structure
 * (conditions, producers, type mappings).
 *
 * **Thread-safety**: This class is thread-safe. Concurrent [evaluate] calls each get their
 * own network copies with independent alpha memories, output node fired-sets, and pending
 * activation counters.
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

    // Compile Rete networks once at construction time; reset() clears mutable state per evaluation
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
