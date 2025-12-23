package verdikt

/**
 * A rule that evaluates a fact of type Fact and produces failure reasons of type Reason.
 *
 * Implement this interface to create reusable rules as classes or objects:
 *
 * ```
 * object NameLengthRule : Rule<Person, String> {
 *     override val name = "name-length-check"
 *     override val description = "Name must be at least 3 characters"
 *
 *     override fun evaluate(fact: Person) = fact.name.length >= 3
 *
 *     override fun failureReason(fact: Person) = "Name '${fact.name}' is too short (minimum 3 characters)"
 * }
 *
 * // Use in a rule set
 * val rules = rules<Person, String> {
 *     add(NameLengthRule)
 * }
 * ```
 *
 * For async rules that perform I/O, implement [AsyncRule] instead.
 *
 * @param Fact The type of fact this rule evaluates
 * @param Reason The type of failure reason this rule produces
 */
public interface Rule<Fact, out Reason : Any> {
    /**
     * Unique identifier for this rule.
     */
    public val name: String

    /**
     * Human-readable description of what this rule checks.
     */
    public val description: String
        get() = ""

    /**
     * Evaluates whether the fact passes this rule.
     * @return true if the rule passes, false if it fails
     */
    public fun evaluate(fact: Fact): Boolean

    /**
     * Generates the failure reason when this rule fails.
     *
     * For dynamic reasons that include fact values:
     * ```
     * override fun failureReason(fact: Person) = "Name '${fact.name}' is too short"
     * ```
     *
     * For typed error codes:
     * ```
     * override fun failureReason(fact: Person) = IneligibilityReason.NAME_TOO_SHORT
     * ```
     */
    public fun failureReason(fact: Fact): Reason
}

/**
 * An async rule that evaluates a fact of type Fact and produces failure reasons of type Reason.
 *
 * Implement this interface when your rule needs to perform I/O operations
 * like database queries or API calls:
 *
 * ```
 * class CreditCheckRule(
 *     private val creditService: CreditService
 * ) : AsyncRule<User, String> {
 *     override val name = "credit-check"
 *     override val description = "Must have good credit standing"
 *
 *     override suspend fun evaluate(fact: User) =
 *         creditService.checkStanding(fact.id)
 *
 *     override fun failureReason(fact: User) =
 *         "Credit check failed for user ${fact.id}"
 * }
 *
 * // Use in a rule set
 * val rules = rules<User, String> {
 *     add(CreditCheckRule(creditService))
 * }
 * ```
 *
 * @param Fact The type of fact this rule evaluates
 * @param Reason The type of failure reason this rule produces
 */
public interface AsyncRule<Fact, out Reason : Any> {
    /**
     * Unique identifier for this rule.
     */
    public val name: String

    /**
     * Human-readable description of what this rule checks.
     */
    public val description: String
        get() = ""

    /**
     * Evaluates whether the fact passes this rule asynchronously.
     * @return true if the rule passes, false if it fails
     */
    public suspend fun evaluate(fact: Fact): Boolean

    /**
     * Generates the failure reason when this rule fails.
     */
    public fun failureReason(fact: Fact): Reason
}

/**
 * Internal implementation of a rule using lambdas.
 * Created by the DSL and by converting [Rule] and [AsyncRule] instances.
 */
internal class InternalRule<Fact, Reason : Any>(
    override val name: String,
    override val description: String,
    private val condition: (Fact) -> Boolean,
    private val asyncCondition: (suspend (Fact) -> Boolean)?,
    private val failureReasonFn: (Fact) -> Reason
) : Rule<Fact, Reason> {
    /**
     * Returns true if this rule uses an async condition.
     */
    val isAsync: Boolean get() = asyncCondition != null

    override fun evaluate(fact: Fact): Boolean = condition(fact)

    override fun failureReason(fact: Fact): Reason = failureReasonFn(fact)

    /**
     * Evaluates this rule, returning a Verdict.
     */
    fun evaluateToVerdict(fact: Fact): Verdict<Reason> {
        val passed = condition(fact)
        return if (passed) {
            Verdict.Pass
        } else {
            Verdict.Fail(listOf(Failure(name, failureReasonFn(fact))))
        }
    }

    /**
     * Evaluates this rule asynchronously, returning a Verdict.
     */
    suspend fun evaluateToVerdictAsync(fact: Fact): Verdict<Reason> {
        val passed = asyncCondition?.invoke(fact) ?: condition(fact)
        return if (passed) {
            Verdict.Pass
        } else {
            Verdict.Fail(listOf(Failure(name, failureReasonFn(fact))))
        }
    }
}

/**
 * Converts a [Rule] to an [InternalRule] for use in rule sets.
 */
@Suppress("UNCHECKED_CAST")
internal fun <Fact, Reason : Any> Rule<Fact, Reason>.toInternalRule(): InternalRule<Fact, Reason> = when (this) {
    is InternalRule<*, *> -> this as InternalRule<Fact, Reason>
    else -> InternalRule(
        name = name,
        description = description,
        condition = ::evaluate,
        asyncCondition = null,
        failureReasonFn = ::failureReason
    )
}

/**
 * Converts an [AsyncRule] to an [InternalRule] for use in rule sets.
 */
internal fun <Fact, Reason : Any> AsyncRule<Fact, Reason>.toInternalRule(): InternalRule<Fact, Reason> = InternalRule(
    name = name,
    description = description,
    condition = { error("Async rule '$name' must be evaluated with evaluateAsync()") },
    asyncCondition = ::evaluate,
    failureReasonFn = ::failureReason
)
