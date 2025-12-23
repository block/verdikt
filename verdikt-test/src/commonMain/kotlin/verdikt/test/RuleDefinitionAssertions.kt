package verdikt.test

import verdikt.AsyncRule
import verdikt.Failure
import kotlin.test.assertTrue
import kotlin.test.fail

// ============================================================================
// AsyncRule assertions
// ============================================================================

/**
 * Asserts that this async rule passes for the given fact.
 */
public suspend fun <Fact> AsyncRule<Fact, *>.assertPasses(fact: Fact, message: String? = null) {
    val result = evaluate(fact)
    assertTrue(
        result,
        message ?: "Expected rule '${name}' to pass but it failed"
    )
}

/**
 * Asserts that this async rule fails for the given fact.
 */
public suspend fun <Fact> AsyncRule<Fact, *>.assertFails(fact: Fact, message: String? = null) {
    val result = evaluate(fact)
    assertTrue(
        !result,
        message ?: "Expected rule '${name}' to fail but it passed"
    )
}

/**
 * Asserts that this async rule fails and allows assertions on the failure reason.
 */
public suspend fun <Fact> AsyncRule<Fact, *>.assertFails(fact: Fact, block: FailureAssertion.() -> Unit) {
    val result = evaluate(fact)
    if (result) {
        fail("Expected rule '${name}' to fail but it passed")
    }
    val failure = Failure(name, failureReason(fact))
    FailureAssertion(failure).apply(block)
}
