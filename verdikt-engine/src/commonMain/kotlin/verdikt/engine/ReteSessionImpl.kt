package verdikt.engine

import verdikt.Failure
import verdikt.Verdict
import verdikt.engine.rete.CompilationResult
import verdikt.engine.rete.OutputNode
import verdikt.engine.rete.ReteNetwork
import kotlin.reflect.KClass

/**
 * Session implementation using Rete network for optimized execution.
 *
 * This implementation uses pre-compiled Rete networks from [EngineImpl],
 * providing significant performance improvements for:
 * - Large numbers of facts (avoid re-scanning all facts each iteration)
 * - Chained rules (incremental propagation through network)
 * - Repeated evaluations (network compiled per session in each evaluate() call)
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

    // Skip event allocation when collector is EMPTY (Bottleneck 7)
    private val collectEvents = collector !== EngineEventCollector.EMPTY

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
                if (collectEvents) collector.collect(EngineEvent.FactInserted(fact, isDerived = false))
            }
        }
    }

    override fun insertAll(facts: Iterable<Any>) {
        for (fact in facts) {
            if (workingMemory.add(fact)) {
                if (collectEvents) collector.collect(EngineEvent.FactInserted(fact, isDerived = false))
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

        if (collectEvents) collector.collect(EngineEvent.Completed(result))
        return result
    }

    /**
     * Find the highest-priority non-skipped output node with pending activations.
     * Output nodes are already in priority-descending order from compilation.
     */
    private fun findNextFirableNode(network: ReteNetwork): OutputNode<*>? {
        for (node in network.outputNodes) {
            if (!node.isSkipped && node.hasPendingActivations()) {
                return node
            }
        }
        return null
    }

    /**
     * Execute the RETE firing loop: reset network, apply guards, activate facts,
     * and fire pending activations in priority order until stable.
     *
     * This is the shared core of both [executePhase] and [executePhaseAsync].
     * The only divergence is how fallback producers are executed (sync vs async).
     */
    private fun executeReteLoop(
        network: ReteNetwork,
        phase: ProcessedPhase
    ) {
        // Reset network state from any previous evaluation using this network
        network.reset()

        // Pre-build guard lookup map: O(1) per node instead of O(N) linear scan
        val producersByName = HashMap<String, InternalFactProducer<*, *>>(phase.factProducers.size)
        for (producer in phase.factProducers) {
            producersByName[producer.name] = producer
        }

        // Mark skipped output nodes directly via flag (avoids Set lookup in hot loop)
        for (outputNode in network.outputNodes) {
            val producer = producersByName[outputNode.ruleName]
            val guard = producer?.guard
            if (guard != null && !guard.allows(context)) {
                if (outputNode.ruleName !in skippedRules) {
                    skippedRules[outputNode.ruleName] = guard.description
                    if (collectEvents) collector.collect(EngineEvent.RuleSkipped(outputNode.ruleName, guard.description))
                }
                outputNode.isSkipped = true
            }
        }

        // Activate initial facts through Rete network (queues activations)
        val initialFacts = workingMemory.snapshot()
        for (fact in initialFacts) {
            network.activate(fact)
        }

        // Output nodes are already in priority-descending order from compilation.
        // Linear scan to find highest-priority node with pending activations (replaces filter+sort).
        while (network.hasPendingActivations()) {
            iterations++

            if (iterations > config.maxIterations) {
                throw MaxIterationsExceededException(iterations, config.maxIterations)
            }

            // Find highest-priority non-skipped node with pending activations (linear scan)
            val nodeToFire = findNextFirableNode(network)

            if (nodeToFire == null) {
                // Only skipped nodes have pending activations - clear them without firing
                for (node in network.outputNodes) {
                    if (node.isSkipped && node.hasPendingActivations()) {
                        node.clearPending()
                    }
                }
                break
            }

            val activationsWithOutputs = nodeToFire.firePendingWithInputs()

            for ((inputFacts, outputs) in activationsWithOutputs) {
                // Optimization: avoid mutableListOf allocation for the common single-output case.
                // firstAdded/extraAdded tracks outputs without eagerly creating a list.
                var firstAdded: Any? = null
                var extraAdded: MutableList<Any>? = null

                for (output in outputs) {
                    if (workingMemory.add(output)) {
                        derivedFacts.add(output)
                        ruleActivations++
                        if (firstAdded == null) {
                            firstAdded = output
                        } else {
                            if (extraAdded == null) extraAdded = mutableListOf()
                            extraAdded.add(output)
                        }
                        if (collectEvents) collector.collect(EngineEvent.FactInserted(output, isDerived = true))
                        network.activate(output)
                    }
                }

                if (firstAdded != null) {
                    val inputFact = inputFacts.first()
                    val addedOutputs = if (extraAdded != null) {
                        buildList { add(firstAdded); addAll(extraAdded) }
                    } else {
                        listOf(firstAdded)
                    }
                    traceEntries?.add(RuleActivation(
                        ruleName = nodeToFire.ruleName,
                        inputFact = inputFact,
                        outputFacts = addedOutputs,
                        priority = nodeToFire.priority
                    ))
                    if (collectEvents) {
                        collector.collect(EngineEvent.RuleFired(
                            ruleName = nodeToFire.ruleName,
                            inputFact = inputFact,
                            outputFacts = addedOutputs,
                            priority = nodeToFire.priority
                        ))
                    }
                }
            }
        }
    }

    private fun executePhase(
        network: ReteNetwork,
        phase: ProcessedPhase,
        fallback: List<InternalFactProducer<*, *>>
    ) {
        executeReteLoop(network, phase)
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
                        if (collectEvents) collector.collect(EngineEvent.RuleSkipped(rule.name, guard.description))
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
                            if (collectEvents) collector.collect(EngineEvent.FactInserted(output, isDerived = true))
                        }
                    }

                    if (addedOutputs.isNotEmpty()) {
                        traceEntries?.add(RuleActivation(
                            ruleName = rule.name,
                            inputFact = inputFact,
                            outputFacts = addedOutputs,
                            priority = rule.priority
                        ))
                        if (collectEvents) {
                            collector.collect(EngineEvent.RuleFired(
                                ruleName = rule.name,
                                inputFact = inputFact,
                                outputFacts = addedOutputs,
                                priority = rule.priority
                            ))
                        }
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

        val matchingFacts = workingMemory.ofType(rule.inputType).toList()
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
            val guard = rule.guard
            if (guard != null && !guard.allows(context)) {
                if (rule.name !in skippedRules) {
                    skippedRules[rule.name] = guard.description
                    if (collectEvents) collector.collect(EngineEvent.RuleSkipped(rule.name, guard.description))
                }
                continue
            }

            val matchingFacts = workingMemory.ofType(rule.inputType).toList()

            for (fact in matchingFacts) {
                val typedRule = rule as InternalValidationRule<Any>

                if (typedRule.evaluate(fact)) {
                    if (collectEvents) collector.collect(EngineEvent.ValidationPassed(rule.name, fact))
                } else {
                    val reason = typedRule.getFailureCause(fact)
                    failures.add(Failure(rule.name, reason))
                    if (collectEvents) collector.collect(EngineEvent.ValidationFailed(rule.name, fact, reason))
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

        if (collectEvents) collector.collect(EngineEvent.Completed(result))
        return result
    }

    private suspend fun executePhaseAsync(
        network: ReteNetwork,
        phase: ProcessedPhase,
        fallback: List<InternalFactProducer<*, *>>
    ) {
        executeReteLoop(network, phase)
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
                        if (collectEvents) collector.collect(EngineEvent.RuleSkipped(rule.name, guard.description))
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
                            if (collectEvents) collector.collect(EngineEvent.FactInserted(output, isDerived = true))
                        }
                    }

                    if (addedOutputs.isNotEmpty()) {
                        traceEntries?.add(RuleActivation(
                            ruleName = rule.name,
                            inputFact = inputFact,
                            outputFacts = addedOutputs,
                            priority = rule.priority
                        ))
                        if (collectEvents) {
                            collector.collect(EngineEvent.RuleFired(
                                ruleName = rule.name,
                                inputFact = inputFact,
                                outputFacts = addedOutputs,
                                priority = rule.priority
                            ))
                        }
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

        val matchingFacts = workingMemory.ofType(rule.inputType).toList()
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
                    if (collectEvents) collector.collect(EngineEvent.RuleSkipped(rule.name, guard.description))
                }
                continue
            }

            val matchingFacts = workingMemory.ofType(rule.inputType).toList()

            for (fact in matchingFacts) {
                val typedRule = rule as InternalValidationRule<Any>

                if (typedRule.evaluateAsync(fact)) {
                    if (collectEvents) collector.collect(EngineEvent.ValidationPassed(rule.name, fact))
                } else {
                    val reason = typedRule.getFailureCause(fact)
                    failures.add(Failure(rule.name, reason))
                    if (collectEvents) collector.collect(EngineEvent.ValidationFailed(rule.name, fact, reason))
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
