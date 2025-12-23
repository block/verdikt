package verdikt.test

import verdikt.Failure
import verdikt.Verdict
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.test.fail

/**
 * Asserts that this verdict represents a passing result.
 */
public fun Verdict<*>.assertPass(message: String? = null) {
    assertTrue(passed, message ?: "Expected verdict to pass but it failed: ${failureMessages()}")
}

/**
 * Asserts that this verdict represents a failing result.
 */
public fun Verdict<*>.assertFail(message: String? = null) {
    assertTrue(!passed, message ?: "Expected verdict to fail but it passed")
}

/**
 * Asserts that this verdict fails and allows detailed assertions on the failures.
 */
public fun Verdict<*>.assertFail(block: FailuresAssertion.() -> Unit) {
    assertFail()
    val failures = (this as Verdict.Fail<*>).failures
    FailuresAssertion(failures).apply(block)
}

/**
 * DSL for asserting on a list of failures.
 */
public class FailuresAssertion(private val failures: List<Failure<*>>) {

    /**
     * Asserts the exact number of failures.
     */
    public fun hasCount(expected: Int) {
        assertEquals(expected, failures.size, "Expected $expected failures but got ${failures.size}: ${failures.map { it.ruleName }}")
    }

    /**
     * Asserts that a failure exists for the given rule name.
     */
    public fun hasRule(ruleName: String) {
        assertTrue(
            failures.any { it.ruleName == ruleName },
            "Expected failure for rule '$ruleName' but only found: ${failures.map { it.ruleName }}"
        )
    }

    /**
     * Asserts that a failure exists for the given rule name and allows further assertions.
     */
    public fun hasRule(ruleName: String, block: FailureAssertion.() -> Unit) {
        val failure = failures.find { it.ruleName == ruleName }
            ?: fail("Expected failure for rule '$ruleName' but only found: ${failures.map { it.ruleName }}")
        FailureAssertion(failure).apply(block)
    }

    /**
     * Asserts that only the specified rules failed (no more, no less).
     */
    public fun hasOnlyRules(vararg ruleNames: String) {
        val expected = ruleNames.toSet()
        val actual = failures.map { it.ruleName }.toSet()
        assertEquals(expected, actual, "Expected only rules $expected to fail but got $actual")
    }

    /**
     * Asserts that any failure contains the given substring in its message.
     * Note: This converts the reason to String for comparison.
     */
    public fun anyMessageContains(substring: String) {
        assertTrue(
            failures.any { it.reason.toString().contains(substring) },
            "Expected any failure message to contain '$substring' but messages were: ${failures.map { it.reason }}"
        )
    }

    /**
     * Asserts on each failure in order.
     */
    public fun failures(block: FailuresListAssertion.() -> Unit) {
        FailuresListAssertion(failures).apply(block)
    }
}

/**
 * DSL for asserting on failures in order.
 */
public class FailuresListAssertion(private val failures: List<Failure<*>>) {
    private var index = 0

    /**
     * Asserts on the next failure in the list.
     */
    public fun failure(block: FailureAssertion.() -> Unit) {
        if (index >= failures.size) {
            fail("Expected more failures but only got ${failures.size}")
        }
        FailureAssertion(failures[index]).apply(block)
        index++
    }
}

/**
 * DSL for asserting on a single failure.
 */
public class FailureAssertion(private val failure: Failure<*>) {

    /**
     * Asserts the rule name matches exactly.
     */
    public fun ruleName(expected: String) {
        assertEquals(expected, failure.ruleName, "Expected rule name '$expected' but got '${failure.ruleName}'")
    }

    /**
     * Asserts the failure reason matches exactly.
     * Note: Compares using equals(), works with any type.
     */
    public fun reason(expected: Any?) {
        assertEquals(expected, failure.reason, "Expected reason '$expected' but got '${failure.reason}'")
    }

    /**
     * Asserts the failure message (reason as String) matches exactly.
     * Note: Converts reason to String for comparison.
     */
    public fun message(expected: String) {
        val reasonString = failure.reason.toString()
        assertEquals(expected, reasonString, "Expected message '$expected' but got '$reasonString'")
    }

    /**
     * Asserts the failure message contains the given substring.
     * Note: Converts reason to String for comparison.
     */
    public fun messageContains(substring: String) {
        val reasonString = failure.reason.toString()
        assertTrue(
            reasonString.contains(substring),
            "Expected message to contain '$substring' but was '$reasonString'"
        )
    }

    /**
     * Asserts the failure message starts with the given prefix.
     * Note: Converts reason to String for comparison.
     */
    public fun messageStartsWith(prefix: String) {
        val reasonString = failure.reason.toString()
        assertTrue(
            reasonString.startsWith(prefix),
            "Expected message to start with '$prefix' but was '$reasonString'"
        )
    }

    /**
     * Asserts the failure message ends with the given suffix.
     * Note: Converts reason to String for comparison.
     */
    public fun messageEndsWith(suffix: String) {
        val reasonString = failure.reason.toString()
        assertTrue(
            reasonString.endsWith(suffix),
            "Expected message to end with '$suffix' but was '$reasonString'"
        )
    }

    /**
     * Asserts the failure message matches the given regex.
     * Note: Converts reason to String for comparison.
     */
    public fun messageMatches(regex: Regex) {
        val reasonString = failure.reason.toString()
        assertTrue(
            regex.matches(reasonString),
            "Expected message to match '$regex' but was '$reasonString'"
        )
    }
}

private fun Verdict<*>.failureMessages(): String {
    return if (this is Verdict.Fail<*>) {
        failures.joinToString("; ") { "${it.ruleName}: ${it.reason}" }
    } else {
        ""
    }
}
