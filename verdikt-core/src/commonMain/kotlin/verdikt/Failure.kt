package verdikt

/**
 * Represents a single rule failure with structured information.
 *
 * @param Cause The type of the failure reason
 * @property ruleName The name of the rule that failed
 * @property reason The failure reason - can be any type (String, enum, custom error class, etc.)
 */
public data class Failure<out Cause : Any>(
    val ruleName: String,
    val reason: Cause
) {
    /**
     * Returns a formatted failure message including the rule name.
     */
    override fun toString(): String = "Rule '$ruleName' failed: $reason"
}
