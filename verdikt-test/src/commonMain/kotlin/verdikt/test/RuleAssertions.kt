package verdikt.test

import verdikt.Failure
import verdikt.Rule
import verdikt.Verdict
import verdikt.rules
import kotlin.test.assertTrue
import kotlin.test.fail

/**
 * Asserts that this rule passes for the given fact.
 */
public fun <Fact> Rule<Fact, *>.assertPasses(fact: Fact, message: String? = null) {
    val result = evaluate(fact)
    assertTrue(
        result,
        message ?: "Expected rule '$name' to pass but it failed: ${failureReason(fact)}"
    )
}

/**
 * Asserts that this rule fails for the given fact.
 */
public fun <Fact> Rule<Fact, *>.assertFails(fact: Fact, message: String? = null) {
    val result = evaluate(fact)
    assertTrue(
        !result,
        message ?: "Expected rule '$name' to fail but it passed"
    )
}

/**
 * Asserts that this rule fails for the given fact and allows detailed assertions on the failure.
 */
public fun <Fact> Rule<Fact, *>.assertFails(fact: Fact, block: FailureAssertion.() -> Unit) {
    val result = evaluate(fact)
    if (result) {
        fail("Expected rule '$name' to fail but it passed")
    }
    val failure = Failure(name, failureReason(fact))
    FailureAssertion(failure).apply(block)
}

/**
 * Asserts that this rule passes for the given fact (async version).
 *
 * Use this for rules created with asyncCondition in the DSL.
 */
public suspend fun <Fact, Cause : Any> Rule<Fact, Cause>.assertPassesAsync(fact: Fact, message: String? = null) {
    val ruleSet = rules<Fact, Cause> { add(this@assertPassesAsync) }
    val result = ruleSet.evaluateAsync(fact)
    assertTrue(
        result.passed,
        message ?: "Expected rule '$name' to pass but it failed: ${result.failureMessages()}"
    )
}

/**
 * Asserts that this rule fails for the given fact (async version).
 *
 * Use this for rules created with asyncCondition in the DSL.
 */
public suspend fun <Fact, Cause : Any> Rule<Fact, Cause>.assertFailsAsync(fact: Fact, message: String? = null) {
    val ruleSet = rules<Fact, Cause> { add(this@assertFailsAsync) }
    val result = ruleSet.evaluateAsync(fact)
    assertTrue(
        !result.passed,
        message ?: "Expected rule '$name' to fail but it passed"
    )
}

/**
 * Asserts that this rule fails for the given fact and allows detailed assertions (async version).
 *
 * Use this for rules created with asyncCondition in the DSL.
 */
public suspend fun <Fact, Cause : Any> Rule<Fact, Cause>.assertFailsAsync(fact: Fact, block: FailureAssertion.() -> Unit) {
    val ruleSet = rules<Fact, Cause> { add(this@assertFailsAsync) }
    val result = ruleSet.evaluateAsync(fact)
    if (result.passed) {
        fail("Expected rule '$name' to fail but it passed")
    }
    val failure = (result as Verdict.Fail<*>).failures.first()
    FailureAssertion(failure).apply(block)
}

private fun Verdict<*>.failureMessages(): String {
    return if (this is Verdict.Fail<*>) {
        failures.joinToString("; ") { it.reason.toString() }
    } else {
        ""
    }
}
