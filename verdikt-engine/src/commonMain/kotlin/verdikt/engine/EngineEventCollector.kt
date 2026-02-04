package verdikt.engine

/**
 * Collector for engine evaluation events.
 *
 * Implement this interface to observe events as they occur during engine evaluation.
 * The collector is called synchronously on the evaluation thread.
 *
 * ## Usage
 *
 * As a lambda (SAM conversion):
 * ```kotlin
 * engine.evaluate(facts) { event ->
 *     when (event) {
 *         is EngineEvent.RuleFired -> logger.info("Rule fired: ${event.ruleName}")
 *         else -> {}
 *     }
 * }
 * ```
 *
 * As a reusable object:
 * ```kotlin
 * object MetricsCollector : EngineEventCollector {
 *     override fun collect(event: EngineEvent) {
 *         if (event is EngineEvent.RuleFired) {
 *             metrics.counter("rule.fired", "rule" to event.ruleName).increment()
 *         }
 *     }
 * }
 *
 * engine.evaluate(facts, collector = MetricsCollector)
 * ```
 *
 * ## Thread Safety
 *
 * For synchronous evaluation ([Engine.evaluate]), the collector is called on the
 * calling thread. For asynchronous evaluation ([Engine.evaluateAsync]), the collector
 * may be called from coroutine dispatchers. Implementations should be thread-safe
 * if used with async evaluation.
 */
public fun interface EngineEventCollector {
    /**
     * Called when an event occurs during engine evaluation.
     *
     * @param event The event that occurred
     */
    public fun collect(event: EngineEvent)

    public companion object {
        /**
         * A no-op collector that discards all events.
         *
         * This is the default collector used when none is specified.
         */
        public val EMPTY: EngineEventCollector = EngineEventCollector { }
    }
}

/**
 * Combines multiple collectors into one.
 *
 * Events are dispatched to all collectors in order.
 *
 * Example:
 * ```kotlin
 * val combined = CompositeCollector(
 *     LoggingCollector(logger),
 *     MetricsCollector(registry)
 * )
 * engine.evaluate(facts, collector = combined)
 * ```
 */
public class CompositeCollector(
    private val collectors: List<EngineEventCollector>
) : EngineEventCollector {
    public constructor(vararg collectors: EngineEventCollector) : this(collectors.toList())

    override fun collect(event: EngineEvent) {
        for (collector in collectors) {
            collector.collect(event)
        }
    }
}
