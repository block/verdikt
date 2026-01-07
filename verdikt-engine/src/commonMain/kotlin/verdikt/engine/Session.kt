package verdikt.engine

import kotlin.reflect.KClass

/**
 * Internal session representing working memory for rule evaluation.
 *
 * Facts are inserted into the session, then rules are executed via [fire] or [fireAsync].
 * The engine iterates (forward chaining) until no new facts are produced, then
 * evaluates validation rules.
 *
 * Sessions are insert-only: facts cannot be retracted once inserted.
 * Sessions are not thread-safe; use one session per thread/coroutine.
 *
 * This is an internal implementation detail. Use [Engine.evaluate] instead.
 */
internal interface Session {
    /**
     * The evaluation context for this session.
     * Used by guards to conditionally skip rules based on runtime context.
     */
    val context: RuleContext

    /**
     * Inserts one or more facts into working memory.
     *
     * Facts can be of any type. Production rules will be matched against facts
     * based on their declared input types.
     *
     * @param facts The facts to insert
     */
    fun insert(vararg facts: Any)

    /**
     * Inserts a collection of facts into working memory.
     *
     * @param facts The facts to insert
     */
    fun insertAll(facts: Iterable<Any>)

    /**
     * Executes the engine synchronously.
     *
     * 1. Iterates through production rules until no new facts are produced (fixpoint)
     * 2. Evaluates all validation rules against working memory
     * 3. Returns the combined result
     *
     * @return The result containing all facts, derived facts, validation verdict, and stats
     * @throws IllegalStateException if the engine contains async rules (use [fireAsync] instead)
     */
    fun fire(): EngineResult

    /**
     * Executes the engine asynchronously.
     *
     * Use this when the engine contains rules with async conditions or outputs.
     * Async rules are executed concurrently where possible.
     *
     * @return The result containing all facts, derived facts, validation verdict, and stats
     */
    suspend fun fireAsync(): EngineResult

    /**
     * Gets all facts currently in working memory.
     *
     * @return An immutable set of all facts
     */
    fun getAllFacts(): Set<Any>

    /**
     * Gets facts of a specific type from working memory.
     *
     * @param T The fact type to retrieve
     * @return An immutable set of facts of the specified type
     */
    fun <T : Any> getFacts(type: KClass<T>): Set<T>
}

/**
 * Gets facts of a specific type from working memory.
 *
 * Reified version for convenience.
 */
internal inline fun <reified T : Any> Session.getFacts(): Set<T> = getFacts(T::class)
