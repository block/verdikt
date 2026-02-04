package verdikt.engine

/**
 * Configuration options for the rules engine.
 *
 * @property maxIterations Maximum number of iterations before throwing [MaxIterationsExceededException].
 *           This prevents infinite loops when rules produce facts that trigger more rules indefinitely.
 *           Default is 1,000,000 - high enough for normal workloads, low enough to catch infinite loops.
 * @property enableTracing When true, records rule activations in [EngineResult.trace].
 *           Useful for debugging but adds overhead. Default is false.
 */
public data class EngineConfig(
    val maxIterations: Int = DEFAULT_MAX_ITERATIONS,
    val enableTracing: Boolean = false
) {
    init {
        require(maxIterations > 0) { "maxIterations must be positive, was $maxIterations" }
    }

    public companion object {
        /**
         * Default maximum iterations. Set high enough that normal workloads won't hit it,
         * but low enough to catch true infinite loops in reasonable time.
         */
        public const val DEFAULT_MAX_ITERATIONS: Int = 1_000_000

        /**
         * Default configuration with sensible limits.
         */
        public val DEFAULT: EngineConfig = EngineConfig()
    }
}

/**
 * Exception thrown when rule execution exceeds the configured maximum iterations.
 *
 * This typically indicates runaway rule execution where rules are producing facts
 * that trigger more rules indefinitely. Consider:
 * - Adding more specific conditions to limit which facts trigger rules
 * - Using guards to conditionally enable rules
 * - Increasing maxIterations if the iteration count is expected
 *
 * @property iterations The number of iterations that were executed
 * @property maxIterations The configured maximum
 */
public class MaxIterationsExceededException(
    public val iterations: Int,
    public val maxIterations: Int
) : RuntimeException(
    "Rule execution exceeded maximum iterations ($maxIterations). " +
    "This may indicate runaway rule execution. " +
    "Consider adding more specific conditions or increasing maxIterations."
)
