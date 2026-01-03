package verdikt.engine

import kotlin.reflect.KClass

/**
 * A production rule that matches facts of type [In] and produces facts of type [Out].
 *
 * Production rules are the core of the forward-chaining engine. When a fact matches
 * the rule's input type and satisfies its condition, the rule "fires" and produces
 * a new fact that is added to working memory.
 *
 * @param In The input fact type this rule matches
 * @param Out The output fact type this rule produces
 */
public interface ProductionRule<In : Any, Out : Any> {
    /** Unique identifier for this rule */
    public val name: String

    /** Human-readable description of what this rule does */
    public val description: String
        get() = ""

    /** The KClass of the input fact type this rule matches */
    public val inputType: KClass<In>

    /** Evaluates whether this rule should fire for the given fact */
    public fun matches(fact: In): Boolean

    /** Produces the output fact when this rule fires */
    public fun produce(fact: In): Out
}

/**
 * Async version of [ProductionRule] for rules that need I/O during matching or production.
 *
 * Use this when your rule needs to perform async operations like database queries
 * or API calls during condition evaluation or output production.
 *
 * @param In The input fact type this rule matches
 * @param Out The output fact type this rule produces
 */
public interface AsyncProductionRule<In : Any, Out : Any> {
    /** Unique identifier for this rule */
    public val name: String

    /** Human-readable description of what this rule does */
    public val description: String
        get() = ""

    /** The KClass of the input fact type this rule matches */
    public val inputType: KClass<In>

    /** Evaluates whether this rule should fire for the given fact (async) */
    public suspend fun matches(fact: In): Boolean

    /** Produces the output fact when this rule fires (async) */
    public suspend fun produce(fact: In): Out
}
