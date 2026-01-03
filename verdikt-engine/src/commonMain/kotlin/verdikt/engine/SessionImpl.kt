package verdikt.engine

import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import verdikt.Failure
import verdikt.Verdict
import kotlin.reflect.KClass

/**
 * Implementation of [Session] with forward-chaining rule evaluation.
 */
internal class SessionImpl<Reason : Any>(
    private val productionRules: List<InternalProductionRule<*, *>>,
    private val validationRules: List<InternalValidationRule<*, Reason>>
) : Session<Reason> {

    // Working memory: all facts
    private val workingMemory = mutableSetOf<Any>()

    // Facts that have been processed by each rule (to avoid re-firing)
    // Key: rule name, Value: set of facts already processed
    private val processedFacts = mutableMapOf<String, MutableSet<Any>>()

    // Facts derived during execution
    private val derivedFacts = mutableSetOf<Any>()

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

    override fun fire(): EngineResult<Reason> {
        // Check for async rules
        val hasAsync = productionRules.any { it.isAsync } || validationRules.any { it.isAsync }
        if (hasAsync) {
            error("Engine contains async rules. Use fireAsync() instead.")
        }

        // Forward chaining loop
        var newFactsProduced: Boolean

        do {
            newFactsProduced = false
            iterations++

            // Try each production rule
            for (rule in productionRules) {
                val newFacts = tryFireRule(rule)
                if (newFacts.isNotEmpty()) {
                    workingMemory.addAll(newFacts)
                    derivedFacts.addAll(newFacts)
                    newFactsProduced = true
                }
            }
        } while (newFactsProduced)

        // Collect validation results
        val verdict = evaluateValidationRules()

        return EngineResult(
            facts = workingMemory.toSet(),
            derived = derivedFacts.toSet(),
            verdict = verdict,
            ruleActivations = ruleActivations,
            iterations = iterations
        )
    }

    override suspend fun fireAsync(): EngineResult<Reason> = coroutineScope {
        // Forward chaining loop
        var newFactsProduced: Boolean

        do {
            newFactsProduced = false
            iterations++

            // Try each production rule
            for (rule in productionRules) {
                val newFacts = tryFireRuleAsync(rule)
                if (newFacts.isNotEmpty()) {
                    workingMemory.addAll(newFacts)
                    derivedFacts.addAll(newFacts)
                    newFactsProduced = true
                }
            }
        } while (newFactsProduced)

        // Collect validation results
        val verdict = evaluateValidationRulesAsync()

        EngineResult(
            facts = workingMemory.toSet(),
            derived = derivedFacts.toSet(),
            verdict = verdict,
            ruleActivations = ruleActivations,
            iterations = iterations
        )
    }

    @Suppress("UNCHECKED_CAST")
    private fun <In : Any, Out : Any> tryFireRule(
        rule: InternalProductionRule<In, Out>
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
    private suspend fun <In : Any, Out : Any> tryFireRuleAsync(
        rule: InternalProductionRule<In, Out>
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
    private fun evaluateValidationRules(): Verdict<Reason> {
        val failures = mutableListOf<Failure<Reason>>()

        for (rule in validationRules) {
            val matchingFacts = workingMemory.filter { rule.inputType.isInstance(it) }

            for (fact in matchingFacts) {
                val typedFact = fact as Any
                val typedRule = rule as InternalValidationRule<Any, Reason>

                if (!typedRule.evaluate(typedFact)) {
                    failures.add(Failure(rule.name, typedRule.getFailureReason(typedFact)))
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
    private suspend fun evaluateValidationRulesAsync(): Verdict<Reason> = coroutineScope {
        val failures = mutableListOf<Failure<Reason>>()

        for (rule in validationRules) {
            val matchingFacts = workingMemory.filter { rule.inputType.isInstance(it) }

            for (fact in matchingFacts) {
                val typedFact = fact as Any
                val typedRule = rule as InternalValidationRule<Any, Reason>

                if (!typedRule.evaluateAsync(typedFact)) {
                    failures.add(Failure(rule.name, typedRule.getFailureReason(typedFact)))
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
