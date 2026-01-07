package verdikt.engine

import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import verdikt.Failure
import verdikt.Verdict
import kotlin.reflect.KClass

/**
 * Implementation of [Session] with forward-chaining rule evaluation.
 *
 * Execution model:
 * 1. Each phase's producers run to fixpoint (no new facts) before the next phase
 * 2. Within a phase, rules are sorted by priority (descending)
 * 3. After all phases complete, validation rules from all phases run
 */
internal class SessionImpl(
    phases: List<PhaseImpl>,
    override val context: RuleContext = RuleContext.EMPTY
) : Session {

    // Pre-process phases: sort fact producers by priority within each phase
    private val phases: List<ProcessedPhase> = phases.map { phase ->
        ProcessedPhase(
            name = phase.name,
            factProducers = phase.factProducers.sortedByDescending { it.priority },
            validationRules = phase.validationRules.sortedByDescending { it.priority }
        )
    }

    // Collect all validation rules from all phases (run after all production phases)
    private val allValidationRules: List<InternalValidationRule<*>> =
        this.phases.flatMap { it.validationRules }

    // Working memory: all facts
    private val workingMemory = mutableSetOf<Any>()

    // Facts that have been processed by each rule (to avoid re-firing)
    // Key: rule name, Value: set of facts already processed
    private val processedFacts = mutableMapOf<String, MutableSet<Any>>()

    // Facts derived during execution
    private val derivedFacts = mutableSetOf<Any>()

    // Rules that were skipped due to guard conditions
    private val skippedRules = mutableMapOf<String, String>()

    // Tracking
    private var ruleActivations = 0
    private var iterations = 0

    override fun insert(vararg facts: Any) {
        workingMemory.addAll(facts)
    }

    override fun insertAll(facts: Iterable<Any>) {
        workingMemory.addAll(facts)
    }

    override fun getAllFacts(): Set<Any> = workingMemory.toSet()

    @Suppress("UNCHECKED_CAST")
    override fun <T : Any> getFacts(type: KClass<T>): Set<T> =
        workingMemory.filter { type.isInstance(it) }.map { it as T }.toSet()

    override fun fire(): EngineResult {
        // Check for async rules
        val hasAsync = phases.any { phase ->
            phase.factProducers.any { it.isAsync } || phase.validationRules.any { it.isAsync }
        }
        if (hasAsync) {
            error("Engine contains async rules. Use fireAsync() instead.")
        }

        // Execute each phase in order
        for (phase in phases) {
            executePhase(phase)
        }

        // Collect validation results from all phases
        val verdict = evaluateValidationRules()

        return EngineResult(
            facts = workingMemory.toSet(),
            derived = derivedFacts.toSet(),
            verdict = verdict,
            skipped = skippedRules.toMap(),
            ruleActivations = ruleActivations,
            iterations = iterations
        )
    }

    /**
     * Execute a single phase's producers to fixpoint.
     */
    private fun executePhase(phase: ProcessedPhase) {
        var newFactsProduced: Boolean

        do {
            newFactsProduced = false
            iterations++

            // Try each fact producer (already sorted by priority)
            for (rule in phase.factProducers) {
                // Check guard
                val guard = rule.guard
                if (guard != null && !guard.allows(context)) {
                    skippedRules[rule.name] = guard.description
                    continue
                }

                val newFacts = tryFireFactProducer(rule)
                if (newFacts.isNotEmpty()) {
                    workingMemory.addAll(newFacts)
                    derivedFacts.addAll(newFacts)
                    newFactsProduced = true
                }
            }
        } while (newFactsProduced)
    }

    override suspend fun fireAsync(): EngineResult = coroutineScope {
        // Execute each phase in order
        for (phase in phases) {
            executePhaseAsync(phase)
        }

        // Collect validation results from all phases
        val verdict = evaluateValidationRulesAsync()

        EngineResult(
            facts = workingMemory.toSet(),
            derived = derivedFacts.toSet(),
            verdict = verdict,
            skipped = skippedRules.toMap(),
            ruleActivations = ruleActivations,
            iterations = iterations
        )
    }

    /**
     * Execute a single phase's producers to fixpoint (async version).
     */
    private suspend fun executePhaseAsync(phase: ProcessedPhase) {
        var newFactsProduced: Boolean

        do {
            newFactsProduced = false
            iterations++

            // Try each fact producer (already sorted by priority)
            for (rule in phase.factProducers) {
                // Check guard
                val guard = rule.guard
                if (guard != null && !guard.allows(context)) {
                    skippedRules[rule.name] = guard.description
                    continue
                }

                val newFacts = tryFireFactProducerAsync(rule)
                if (newFacts.isNotEmpty()) {
                    workingMemory.addAll(newFacts)
                    derivedFacts.addAll(newFacts)
                    newFactsProduced = true
                }
            }
        } while (newFactsProduced)
    }

    @Suppress("UNCHECKED_CAST")
    private fun <In : Any, Out : Any> tryFireFactProducer(
        rule: InternalFactProducer<In, Out>
    ): List<Out> {
        val processedForRule = processedFacts.getOrPut(rule.name) { mutableSetOf() }
        val produced = mutableListOf<Out>()

        // Find facts that match this rule's input type and haven't been processed
        val matchingFacts = workingMemory
            .filter { rule.inputType.isInstance(it) }
            .filter { it !in processedForRule }
            .map { it as In }

        for (fact in matchingFacts) {
            processedForRule.add(fact)

            if (rule.matches(fact)) {
                val output = rule.produce(fact)
                // Only add if not already in working memory (avoid duplicates)
                if (output !in workingMemory) {
                    produced.add(output)
                    ruleActivations++
                }
            }
        }

        return produced
    }

    @Suppress("UNCHECKED_CAST")
    private suspend fun <In : Any, Out : Any> tryFireFactProducerAsync(
        rule: InternalFactProducer<In, Out>
    ): List<Out> {
        val processedForRule = processedFacts.getOrPut(rule.name) { mutableSetOf() }
        val produced = mutableListOf<Out>()

        // Find facts that match this rule's input type and haven't been processed
        val matchingFacts = workingMemory
            .filter { rule.inputType.isInstance(it) }
            .filter { it !in processedForRule }
            .map { it as In }

        for (fact in matchingFacts) {
            processedForRule.add(fact)

            if (rule.matchesAsync(fact)) {
                val output = rule.produceAsync(fact)
                // Only add if not already in working memory (avoid duplicates)
                if (output !in workingMemory) {
                    produced.add(output)
                    ruleActivations++
                }
            }
        }

        return produced
    }

    @Suppress("UNCHECKED_CAST")
    private fun evaluateValidationRules(): Verdict<Any> {
        val failures = mutableListOf<Failure<Any>>()

        for (rule in allValidationRules) {
            // Check guard
            val guard = rule.guard
            if (guard != null && !guard.allows(context)) {
                skippedRules[rule.name] = guard.description
                continue
            }

            val matchingFacts = workingMemory.filter { rule.inputType.isInstance(it) }

            for (fact in matchingFacts) {
                val typedRule = rule as InternalValidationRule<Any>

                if (!typedRule.evaluate(fact)) {
                    failures.add(Failure(rule.name, typedRule.getFailureCause(fact)))
                }
            }
        }

        return if (failures.isEmpty()) {
            Verdict.Pass
        } else {
            Verdict.Fail(failures)
        }
    }

    @Suppress("UNCHECKED_CAST")
    private suspend fun evaluateValidationRulesAsync(): Verdict<Any> = coroutineScope {
        val failures = mutableListOf<Failure<Any>>()

        for (rule in allValidationRules) {
            // Check guard
            val guard = rule.guard
            if (guard != null && !guard.allows(context)) {
                skippedRules[rule.name] = guard.description
                continue
            }

            val matchingFacts = workingMemory.filter { rule.inputType.isInstance(it) }

            for (fact in matchingFacts) {
                val typedRule = rule as InternalValidationRule<Any>

                if (!typedRule.evaluateAsync(fact)) {
                    failures.add(Failure(rule.name, typedRule.getFailureCause(fact)))
                }
            }
        }

        if (failures.isEmpty()) {
            Verdict.Pass
        } else {
            Verdict.Fail(failures)
        }
    }
}

/**
 * Pre-processed phase with rules sorted by priority.
 */
private data class ProcessedPhase(
    val name: String,
    val factProducers: List<InternalFactProducer<*, *>>,
    val validationRules: List<InternalValidationRule<*>>
)
