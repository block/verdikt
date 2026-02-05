package verdikt.engine

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow

/**
 * Evaluates facts and returns a [Flow] of [EngineEvent]s.
 *
 * This is a convenience function that wraps [Engine.evaluate] with a Flow interface.
 * Events are emitted as they occur during evaluation, ending with [EngineEvent.Completed].
 *
 * Example:
 * ```kotlin
 * engine.evaluateAsFlow(facts).collect { event ->
 *     when (event) {
 *         is EngineEvent.RuleFired -> println("Rule fired: ${event.ruleName}")
 *         is EngineEvent.Completed -> println("Done: ${event.result.passed}")
 *         else -> {}
 *     }
 * }
 * ```
 *
 * ## Buffer Limitations
 *
 * This function uses a buffered channel (default size 64). If the collector processes
 * events slower than they are produced, events may be dropped once the buffer fills.
 * For most evaluations this is not an issue, but for very large evaluations or slow
 * collectors, use [Engine.evaluate] with an [EngineEventCollector] directly to
 * guarantee all events are delivered.
 *
 * @param facts The facts to evaluate
 * @param context Optional context for guard evaluation
 * @return A Flow that emits events during evaluation and completes after [EngineEvent.Completed]
 */
public fun Engine.evaluateAsFlow(
    facts: Collection<Any>,
    context: RuleContext = RuleContext.EMPTY
): Flow<EngineEvent> = callbackFlow {
    evaluate(facts, context) { event ->
        trySend(event)
    }
    close()
}

/**
 * Evaluates facts asynchronously and returns a [Flow] of [EngineEvent]s.
 *
 * This is a convenience function that wraps [Engine.evaluateAsync] with a Flow interface.
 * Events are emitted as they occur during evaluation, ending with [EngineEvent.Completed].
 *
 * Use this when the engine contains async rules.
 *
 * ## Buffer Limitations
 *
 * This function uses a buffered channel (default size 64). If the collector processes
 * events slower than they are produced, events may be dropped once the buffer fills.
 * For most evaluations this is not an issue, but for very large evaluations or slow
 * collectors, use [Engine.evaluateAsync] with an [EngineEventCollector] directly to
 * guarantee all events are delivered.
 *
 * @param facts The facts to evaluate
 * @param context Optional context for guard evaluation
 * @return A Flow that emits events during evaluation and completes after [EngineEvent.Completed]
 */
public fun Engine.evaluateAsyncAsFlow(
    facts: Collection<Any>,
    context: RuleContext = RuleContext.EMPTY
): Flow<EngineEvent> = callbackFlow {
    evaluateAsync(facts, context) { event ->
        trySend(event)
    }
    close()
}
