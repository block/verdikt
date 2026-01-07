package verdikt.engine

import kotlin.reflect.KClass

/**
 * A fact producer that matches facts of type [In] and produces facts of type [Out].
 *
 * Fact producers are the core of the forward-chaining engine. When a fact matches
 * the fact producer's input type and satisfies its condition, it "fires" and produces
 * a new fact that is added to working memory.
 *
 * @param In The input fact type this fact producer matches
 * @param Out The output fact type this fact producer produces
 */
public interface FactProducer<In : Any, Out : Any> {
    /** Unique identifier for this fact producer */
    public val name: String

    /** Human-readable description of what this fact producer does */
    public val description: String
        get() = ""

    /**
     * Execution priority. Higher values run first within a phase.
     * Default is 0. Fact producers with the same priority run in definition order.
     */
    public val priority: Int
        get() = 0

    /**
     * Optional guard that must be satisfied for this fact producer to run.
     * If the guard is not satisfied, the fact producer is skipped.
     */
    public val guard: Guard?
        get() = null

    /** The KClass of the input fact type this fact producer matches */
    public val inputType: KClass<In>

    /** Evaluates whether this fact producer should fire for the given fact */
    public fun matches(fact: In): Boolean

    /** Produces the output fact when this fact producer fires */
    public fun produce(fact: In): Out
}

/**
 * Async version of [FactProducer] for fact producers that need I/O during matching or production.
 *
 * Use this when your fact producer needs to perform async operations like database queries
 * or API calls during condition evaluation or output production.
 *
 * @param In The input fact type this fact producer matches
 * @param Out The output fact type this fact producer produces
 */
public interface AsyncFactProducer<In : Any, Out : Any> {
    /** Unique identifier for this fact producer */
    public val name: String

    /** Human-readable description of what this fact producer does */
    public val description: String
        get() = ""

    /**
     * Execution priority. Higher values run first within a phase.
     * Default is 0. Fact producers with the same priority run in definition order.
     */
    public val priority: Int
        get() = 0

    /**
     * Optional guard that must be satisfied for this fact producer to run.
     * If the guard is not satisfied, the fact producer is skipped.
     */
    public val guard: Guard?
        get() = null

    /** The KClass of the input fact type this fact producer matches */
    public val inputType: KClass<In>

    /** Evaluates whether this fact producer should fire for the given fact (async) */
    public suspend fun matches(fact: In): Boolean

    /** Produces the output fact when this fact producer fires (async) */
    public suspend fun produce(fact: In): Out
}
