package verdikt.engine

import verdikt.Failure
import verdikt.Verdict
import verdikt.engine.rete.CompilationResult
import verdikt.engine.rete.ReteNetwork
import kotlin.reflect.KClass

/**
 * Session implementation using Rete network for optimized execution.
 *
 * This implementation uses pre-compiled Rete networks from [EngineImpl],
 * providing significant performance improvements for:
 * - Large numbers of facts (avoid re-scanning all facts each iteration)
 * - Chained rules (incremental propagation through network)
 * - Repeated evaluations (network compiled once, reused across sessions)
 *
 * Limitations:
 * - Async producers fall back to linear scan
 * - No fact retraction (matches current insert-only model)
 *
 * @param phases Pre-processed phases with rules sorted by priority
 * @param compilationResults Pre-compiled Rete networks from EngineImpl
 * @param config Engine configuration including iteration limits
 * @param context Rule context for guard evaluation
 */
internal class ReteSessionImpl(
    private val phases: List<ProcessedPhase>,
    compilationResults: List<CompilationResult>,
    private val config: EngineConfig = EngineConfig.DEFAULT,
    override val context: RuleContext = RuleContext.EMPTY,
    private val collector: EngineEventCollector = EngineEventCollector.EMPTY
) : Session {

    // Extract networks and fallback producers from pre-compiled results
    private val networks: List<ReteNetwork> = compilationResults.map { it.network }
    private val fallbackProducers: List<List<InternalFactProducer<*, *>>> =
        compilationResults.map { it.fallbackProducers }

    // Collect all validation rules from all phases
    private val allValidationRules: List<InternalValidationRule<*>> =
        phases.flatMap { it.validationRules }

    // Working memory
    private val workingMemory = IndexedWorkingMemory()

    // Facts derived during execution
    private val derivedFacts = mutableSetOf<Any>()

    // Rules that were skipped due to guard conditions
    private val skippedRules = mutableMapOf<String, String>()

    // Tracking
    private var ruleActivations = 0
    private var iterations = 0

    // Execution trace (only populated when tracing enabled)
    private val traceEntries = if (config.enableTracing) mutableListOf<RuleActivation>() else null

    // Warnings generated during execution
    private val warnings = mutableListOf<String>()
    private var runawayWarningEmitted = false

    override fun insert(vararg facts: Any) {
        for (fact in facts) {
            if (workingMemory.add(fact)) {
                collector.collect(EngineEvent.FactInserted(fact, isDerived = false))
            }
        }
    }

    override fun insertAll(facts: Iterable<Any>) {
        for (fact in facts) {
            if (workingMemory.add(fact)) {
                collector.collect(EngineEvent.FactInserted(fact, isDerived = false))
            }
        }
    }

    override fun getAllFacts(): Set<Any> = workingMemory.all()

    override fun <T : Any> getFacts(type: KClass<T>): Set<T> =
        workingMemory.ofType(type)

    override fun fire(): EngineResult {
        // Check for async rules in fallback producers
        val hasAsync = fallbackProducers.any { producers ->
            producers.any { it.isAsync }
        } || allValidationRules.any { it.isAsync }

        if (hasAsync) {
            error("Engine contains async rules. Use fireAsync() instead.")
        }

        // Execute each phase
        for ((index, network) in networks.withIndex()) {
            executePhase(network, phases[index], fallbackProducers[index])
        }

        // Evaluate validation rules
        val verdict = evaluateValidationRules()

        val result = EngineResult(
            facts = workingMemory.all(),
            derived = derivedFacts.toSet(),
            verdict = verdict,
            skipped = skippedRules.toMap(),
            ruleActivations = ruleActivations,
            iterations = iterations,
            trace = traceEntries?.toList() ?: emptyList(),
            warnings = warnings.toList()
        )

        collector.collect(EngineEvent.Completed(result))
        return result
    }

    private fun executePhase(
        network: ReteNetwork,
        phase: ProcessedPhase,
        fallback: List<InternalFactProducer<*, *>>
    ) {
        // Reset network state from any previous session using this network
        network.reset()

        // Build set of skipped output nodes (due to guards)
        val skippedOutputNodes = mutableSetOf<String>()
        for (outputNode in network.outputNodes) {
            val producer = phase.factProducers.find { it.name == outputNode.ruleName }
            val guard = producer?.guard
            if (guard != null && !guard.allows(context)) {
                skippedRules[outputNode.ruleName] = guard.description
                skippedOutputNodes.add(outputNode.id)
                collector.collect(EngineEvent.RuleSkipped(outputNode.ruleName, guard.description))
            }
        }

        // Activate initial facts through Rete network (queues activations)
        for (fact in workingMemory.all().toList()) {
            network.activate(fact)
        }

        // Fire pending activations in priority order until stable
        while (network.hasPendingActivations()) {
            iterations++

            // Check iteration limit to prevent infinite loops
            if (iterations > config.maxIterations) {
                throw MaxIterationsExceededException(iterations, config.maxIterations)
            }

            // Get output nodes with pending activations, sorted by priority (highest first)
            val nodesWithPending = network.outputNodes
                .filter { it.hasPendingActivations() && it.id !in skippedOutputNodes }
                .sortedByDescending { it.priority }

            if (nodesWithPending.isEmpty()) {
                // Only skipped nodes have pending activations - clear them
                for (node in network.outputNodes.filter { it.id in skippedOutputNodes }) {
                    node.firePending() // Discard outputs from skipped nodes
                }
                break
            }

            // Fire the highest priority node's pending activations
            val nodeToFire = nodesWithPending.first()
            val activationsWithOutputs = nodeToFire.firePendingWithInputs()

            for ((inputFacts, outputs) in activationsWithOutputs) {
                val addedOutputs = mutableListOf<Any>()

                for (output in outputs) {
                    // Add to working memory and track
                    if (workingMemory.add(output)) {
                        derivedFacts.add(output)
                        ruleActivations++
                        addedOutputs.add(output)
                        collector.collect(EngineEvent.FactInserted(output, isDerived = true))
                        // Propagate new fact through the network (queues more activations)
                        network.activate(output)
                    }
                }

                // Record trace entry and emit event if outputs were added
                if (addedOutputs.isNotEmpty()) {
                    val inputFact = inputFacts.first()
                    traceEntries?.add(RuleActivation(
                        ruleName = nodeToFire.ruleName,
                        inputFact = inputFact,
                        outputFacts = addedOutputs,
                        priority = nodeToFire.priority
                    ))
                    collector.collect(EngineEvent.RuleFired(
                        ruleName = nodeToFire.ruleName,
                        inputFact = inputFact,
                        outputFacts = addedOutputs,
                        priority = nodeToFire.priority
                    ))
                }
            }
        }

        // Run fallback producers (async) with naive loop
        if (fallback.isNotEmpty()) {
            executeFallbackProducers(fallback)
        }
    }

    private fun executeFallbackProducers(producers: List<InternalFactProducer<*, *>>) {
        val processedFacts = mutableMapOf<String, MutableSet<Any>>()
        var newFactsProduced: Boolean

        do {
            newFactsProduced = false
            iterations++

            // Check iteration limit to prevent infinite loops
            if (iterations > config.maxIterations) {
                throw MaxIterationsExceededException(iterations, config.maxIterations)
            }

            // Check for possible runaway execution (heuristic warning)
            if (!runawayWarningEmitted && iterations > 100) {
                val expectedActivations = iterations * producers.size * 2
                if (ruleActivations > expectedActivations) {
                    warnings.add(
                        "Possible runaway rule execution detected: $iterations iterations with " +
                        "$ruleActivations rule activations. Consider adding more specific conditions " +
                        "to limit rule triggering."
                    )
                    runawayWarningEmitted = true
                }
            }

            for (rule in producers) {
                // Check guard
                val guard = rule.guard
                if (guard != null && !guard.allows(context)) {
                    if (rule.name !in skippedRules) {
                        skippedRules[rule.name] = guard.description
                        collector.collect(EngineEvent.RuleSkipped(rule.name, guard.description))
                    }
                    continue
                }

                val activations = tryFireFallbackProducerWithTracing(rule, processedFacts)
                for ((inputFact, outputs) in activations) {
                    val addedOutputs = mutableListOf<Any>()
                    for (output in outputs) {
                        if (workingMemory.add(output)) {
                            derivedFacts.add(output)
                            ruleActivations++
                            addedOutputs.add(output)
                            newFactsProduced = true
                            collector.collect(EngineEvent.FactInserted(output, isDerived = true))
                        }
                    }

                    // Record trace entry and emit event if outputs were added
                    if (addedOutputs.isNotEmpty()) {
                        traceEntries?.add(RuleActivation(
                            ruleName = rule.name,
                            inputFact = inputFact,
                            outputFacts = addedOutputs,
                            priority = rule.priority
                        ))
                        collector.collect(EngineEvent.RuleFired(
                            ruleName = rule.name,
                            inputFact = inputFact,
                            outputFacts = addedOutputs,
                            priority = rule.priority
                        ))
                    }
                }
            }
        } while (newFactsProduced)
    }

    /**
     * Fire fallback producer and return input-output pairs for tracing.
     */
    @Suppress("UNCHECKED_CAST")
    private fun <In : Any, Out : Any> tryFireFallbackProducerWithTracing(
        rule: InternalFactProducer<In, Out>,
        processedFacts: MutableMap<String, MutableSet<Any>>
    ): List<Pair<Any, List<Out>>> {
        val processedForRule = processedFacts.getOrPut(rule.name) { mutableSetOf() }
        val results = mutableListOf<Pair<Any, List<Out>>>()

        val matchingFacts = workingMemory.filterByType(rule.inputType)
            .filter { it !in processedForRule }

        for (fact in matchingFacts) {
            processedForRule.add(fact)

            if (rule.matches(fact as In)) {
                val output = rule.produce(fact)
                if (!workingMemory.contains(output)) {
                    results.add(fact to listOf(output))
                }
            }
        }

        return results
    }

    @Suppress("UNCHECKED_CAST")
    private fun evaluateValidationRules(): Verdict<Any> {
        val failures = mutableListOf<Failure<Any>>()

        for (rule in allValidationRules) {
            // Check guard
            val guard = rule.guard
            if (guard != null && !guard.allows(context)) {
                if (rule.name !in skippedRules) {
                    skippedRules[rule.name] = guard.description
                    collector.collect(EngineEvent.RuleSkipped(rule.name, guard.description))
                }
                continue
            }

            val matchingFacts = workingMemory.filterByType(rule.inputType)

            for (fact in matchingFacts) {
                val typedRule = rule as InternalValidationRule<Any>

                if (typedRule.evaluate(fact)) {
                    collector.collect(EngineEvent.ValidationPassed(rule.name, fact))
                } else {
                    val reason = typedRule.getFailureCause(fact)
                    failures.add(Failure(rule.name, reason))
                    collector.collect(EngineEvent.ValidationFailed(rule.name, fact, reason))
                }
            }
        }

        return if (failures.isEmpty()) {
            Verdict.Pass
        } else {
            Verdict.Fail(failures)
        }
    }

    override suspend fun fireAsync(): EngineResult {
        // For now, delegate to sync execution for Rete rules,
        // then run async fallback producers
        // Full async Rete is future work

        // Execute each phase
        for ((index, network) in networks.withIndex()) {
            executePhaseAsync(network, phases[index], fallbackProducers[index])
        }

        // Evaluate validation rules (async)
        val verdict = evaluateValidationRulesAsync()

        val result = EngineResult(
            facts = workingMemory.all(),
            derived = derivedFacts.toSet(),
            verdict = verdict,
            skipped = skippedRules.toMap(),
            ruleActivations = ruleActivations,
            iterations = iterations,
            trace = traceEntries?.toList() ?: emptyList(),
            warnings = warnings.toList()
        )

        collector.collect(EngineEvent.Completed(result))
        return result
    }

    private suspend fun executePhaseAsync(
        network: ReteNetwork,
        phase: ProcessedPhase,
        fallback: List<InternalFactProducer<*, *>>
    ) {
        // Reset network state from any previous session using this network
        network.reset()

        // Build set of skipped output nodes (due to guards)
        val skippedOutputNodes = mutableSetOf<String>()
        for (outputNode in network.outputNodes) {
            val producer = phase.factProducers.find { it.name == outputNode.ruleName }
            val guard = producer?.guard
            if (guard != null && !guard.allows(context)) {
                skippedRules[outputNode.ruleName] = guard.description
                skippedOutputNodes.add(outputNode.id)
                collector.collect(EngineEvent.RuleSkipped(outputNode.ruleName, guard.description))
            }
        }

        // Activate initial facts through Rete network (queues activations)
        for (fact in workingMemory.all().toList()) {
            network.activate(fact)
        }

        // Fire pending activations in priority order until stable
        while (network.hasPendingActivations()) {
            iterations++

            // Check iteration limit to prevent infinite loops
            if (iterations > config.maxIterations) {
                throw MaxIterationsExceededException(iterations, config.maxIterations)
            }

            // Get output nodes with pending activations, sorted by priority (highest first)
            val nodesWithPending = network.outputNodes
                .filter { it.hasPendingActivations() && it.id !in skippedOutputNodes }
                .sortedByDescending { it.priority }

            if (nodesWithPending.isEmpty()) {
                // Only skipped nodes have pending activations - clear them
                for (node in network.outputNodes.filter { it.id in skippedOutputNodes }) {
                    node.firePending() // Discard outputs from skipped nodes
                }
                break
            }

            // Fire the highest priority node's pending activations
            val nodeToFire = nodesWithPending.first()
            val activationsWithOutputs = nodeToFire.firePendingWithInputs()

            for ((inputFacts, outputs) in activationsWithOutputs) {
                val addedOutputs = mutableListOf<Any>()

                for (output in outputs) {
                    // Add to working memory and track
                    if (workingMemory.add(output)) {
                        derivedFacts.add(output)
                        ruleActivations++
                        addedOutputs.add(output)
                        collector.collect(EngineEvent.FactInserted(output, isDerived = true))
                        // Propagate new fact through the network (queues more activations)
                        network.activate(output)
                    }
                }

                // Record trace entry and emit event if outputs were added
                if (addedOutputs.isNotEmpty()) {
                    val inputFact = inputFacts.first()
                    traceEntries?.add(RuleActivation(
                        ruleName = nodeToFire.ruleName,
                        inputFact = inputFact,
                        outputFacts = addedOutputs,
                        priority = nodeToFire.priority
                    ))
                    collector.collect(EngineEvent.RuleFired(
                        ruleName = nodeToFire.ruleName,
                        inputFact = inputFact,
                        outputFacts = addedOutputs,
                        priority = nodeToFire.priority
                    ))
                }
            }
        }

        // Run async fallback producers
        if (fallback.isNotEmpty()) {
            executeFallbackProducersAsync(fallback)
        }
    }

    private suspend fun executeFallbackProducersAsync(producers: List<InternalFactProducer<*, *>>) {
        val processedFacts = mutableMapOf<String, MutableSet<Any>>()
        var newFactsProduced: Boolean

        do {
            newFactsProduced = false
            iterations++

            // Check iteration limit to prevent infinite loops
            if (iterations > config.maxIterations) {
                throw MaxIterationsExceededException(iterations, config.maxIterations)
            }

            // Check for possible runaway execution (heuristic warning)
            if (!runawayWarningEmitted && iterations > 100) {
                val expectedActivations = iterations * producers.size * 2
                if (ruleActivations > expectedActivations) {
                    warnings.add(
                        "Possible runaway rule execution detected: $iterations iterations with " +
                        "$ruleActivations rule activations. Consider adding more specific conditions " +
                        "to limit rule triggering."
                    )
                    runawayWarningEmitted = true
                }
            }

            for (rule in producers) {
                val guard = rule.guard
                if (guard != null && !guard.allows(context)) {
                    if (rule.name !in skippedRules) {
                        skippedRules[rule.name] = guard.description
                        collector.collect(EngineEvent.RuleSkipped(rule.name, guard.description))
                    }
                    continue
                }

                val activations = tryFireFallbackProducerAsyncWithTracing(rule, processedFacts)
                for ((inputFact, outputs) in activations) {
                    val addedOutputs = mutableListOf<Any>()
                    for (output in outputs) {
                        if (workingMemory.add(output)) {
                            derivedFacts.add(output)
                            ruleActivations++
                            addedOutputs.add(output)
                            newFactsProduced = true
                            collector.collect(EngineEvent.FactInserted(output, isDerived = true))
                        }
                    }

                    // Record trace entry and emit event if outputs were added
                    if (addedOutputs.isNotEmpty()) {
                        traceEntries?.add(RuleActivation(
                            ruleName = rule.name,
                            inputFact = inputFact,
                            outputFacts = addedOutputs,
                            priority = rule.priority
                        ))
                        collector.collect(EngineEvent.RuleFired(
                            ruleName = rule.name,
                            inputFact = inputFact,
                            outputFacts = addedOutputs,
                            priority = rule.priority
                        ))
                    }
                }
            }
        } while (newFactsProduced)
    }

    /**
     * Fire async fallback producer and return input-output pairs for tracing.
     */
    @Suppress("UNCHECKED_CAST")
    private suspend fun <In : Any, Out : Any> tryFireFallbackProducerAsyncWithTracing(
        rule: InternalFactProducer<In, Out>,
        processedFacts: MutableMap<String, MutableSet<Any>>
    ): List<Pair<Any, List<Out>>> {
        val processedForRule = processedFacts.getOrPut(rule.name) { mutableSetOf() }
        val results = mutableListOf<Pair<Any, List<Out>>>()

        val matchingFacts = workingMemory.filterByType(rule.inputType)
            .filter { it !in processedForRule }

        for (fact in matchingFacts) {
            processedForRule.add(fact)

            if (rule.matchesAsync(fact as In)) {
                val output = rule.produceAsync(fact)
                if (!workingMemory.contains(output)) {
                    results.add(fact to listOf(output))
                }
            }
        }

        return results
    }

    @Suppress("UNCHECKED_CAST")
    private suspend fun evaluateValidationRulesAsync(): Verdict<Any> {
        val failures = mutableListOf<Failure<Any>>()

        for (rule in allValidationRules) {
            val guard = rule.guard
            if (guard != null && !guard.allows(context)) {
                if (rule.name !in skippedRules) {
                    skippedRules[rule.name] = guard.description
                    collector.collect(EngineEvent.RuleSkipped(rule.name, guard.description))
                }
                continue
            }

            val matchingFacts = workingMemory.filterByType(rule.inputType)

            for (fact in matchingFacts) {
                val typedRule = rule as InternalValidationRule<Any>

                if (typedRule.evaluateAsync(fact)) {
                    collector.collect(EngineEvent.ValidationPassed(rule.name, fact))
                } else {
                    val reason = typedRule.getFailureCause(fact)
                    failures.add(Failure(rule.name, reason))
                    collector.collect(EngineEvent.ValidationFailed(rule.name, fact, reason))
                }
            }
        }

        return if (failures.isEmpty()) {
            Verdict.Pass
        } else {
            Verdict.Fail(failures)
        }
    }
}
