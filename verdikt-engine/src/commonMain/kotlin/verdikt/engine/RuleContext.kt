package verdikt.engine

/**
 * Type-safe key for storing and retrieving values from a [RuleContext].
 *
 * Define keys as objects for type safety:
 * ```
 * object CustomerTier : ContextKey<String>
 * object IsPremium : ContextKey<Boolean>
 * ```
 *
 * @param T The type of value this key stores
 */
public interface ContextKey<T>

/**
 * A type-safe key-value store for rule evaluation context.
 *
 * Used by guards to conditionally skip rules based on runtime context
 * that isn't part of the fact being evaluated.
 *
 * Example:
 * ```
 * // Define keys
 * object CustomerTier : ContextKey<String>
 * object OrderSource : ContextKey<String>
 *
 * // Create context
 * val context = ruleContext {
 *     set(CustomerTier, "gold")
 *     set(OrderSource, "online")
 * }
 *
 * // Access values
 * val tier = context[CustomerTier]  // "gold"
 * val source = context[OrderSource]  // "online"
 * ```
 */
public interface RuleContext {
    /**
     * Gets the value for the given key, or null if not present.
     */
    public operator fun <T> get(key: ContextKey<T>): T?

    /**
     * Returns true if the context contains the given key.
     */
    public operator fun contains(key: ContextKey<*>): Boolean

    /**
     * Gets the value for the given key, or the default if not present.
     */
    public fun <T> getOrDefault(key: ContextKey<T>, default: T): T

    public companion object {
        /**
         * An empty context with no values.
         */
        public val EMPTY: RuleContext = EmptyRuleContext
    }
}

/**
 * Builder for creating a [RuleContext].
 */
@EngineDsl
public class RuleContextBuilder @PublishedApi internal constructor() {
    private val values = mutableMapOf<ContextKey<*>, Any?>()

    /**
     * Sets a value for the given key.
     */
    public fun <T> set(key: ContextKey<T>, value: T) {
        values[key] = value
    }

    @PublishedApi
    internal fun build(): RuleContext = MapRuleContext(values.toMap())
}

/**
 * Creates a [RuleContext] with the given values.
 *
 * Example:
 * ```
 * val context = ruleContext {
 *     set(CustomerTier, "gold")
 *     set(OrderSource, "online")
 * }
 * ```
 */
public inline fun ruleContext(block: RuleContextBuilder.() -> Unit): RuleContext {
    return RuleContextBuilder().apply(block).build()
}

// Internal implementations

internal object EmptyRuleContext : RuleContext {
    override fun <T> get(key: ContextKey<T>): T? = null
    override fun contains(key: ContextKey<*>): Boolean = false
    override fun <T> getOrDefault(key: ContextKey<T>, default: T): T = default
}

internal class MapRuleContext(
    private val values: Map<ContextKey<*>, Any?>
) : RuleContext {
    @Suppress("UNCHECKED_CAST")
    override fun <T> get(key: ContextKey<T>): T? = values[key] as T?

    override fun contains(key: ContextKey<*>): Boolean = key in values

    override fun <T> getOrDefault(key: ContextKey<T>, default: T): T = get(key) ?: default
}
