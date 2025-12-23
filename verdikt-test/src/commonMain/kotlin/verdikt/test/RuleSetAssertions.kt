package verdikt.test

import verdikt.AsyncRule
import verdikt.Rule
import verdikt.RuleSet
import verdikt.Verdict
import kotlin.test.assertTrue
import kotlin.test.fail

/**
 * Asserts that all rules in this rule collection pass for the given fact.
 */
public fun <Fact> RuleSet<Fact, *>.assertPasses(fact: Fact, message: String? = null) {
    val result = evaluate(fact)
    assertTrue(
        result.passed,
        message ?: "Expected all rules to pass but some failed: ${result.failureMessages()}"
    )
}

/**
 * Asserts that at least one rule in this rule collection fails for the given fact.
 */
public fun <Fact> RuleSet<Fact, *>.assertFails(fact: Fact, message: String? = null) {
    val result = evaluate(fact)
    assertTrue(
        !result.passed,
        message ?: "Expected at least one rule to fail but all passed"
    )
}

/**
 * Asserts that rules fail and allows detailed assertions on the failures.
 */
public fun <Fact> RuleSet<Fact, *>.assertFails(fact: Fact, block: FailuresAssertion.() -> Unit) {
    val result = evaluate(fact)
    if (result.passed) {
        fail("Expected at least one rule to fail but all passed")
    }
    val failures = (result as Verdict.Fail<*>).failures
    FailuresAssertion(failures).apply(block)
}

/**
 * Asserts that only the specified rules fail (and all others pass).
 */
public fun <Fact> RuleSet<Fact, *>.assertRuleFailsOnly(vararg rules: Rule<Fact, *>, fact: Fact) {
    val result = evaluate(fact)
    val ruleNames = rules.map { it.name }
    if (result.passed) {
        fail("Expected rules $ruleNames to fail but all passed")
    }
    val failures = (result as Verdict.Fail<*>).failures
    val expected = ruleNames.toSet()
    val actual = failures.map { it.ruleName }.toSet()

    if (expected != actual) {
        val missing = expected - actual
        val extra = actual - expected
        val message = buildString {
            append("Expected only rules $expected to fail but got $actual.")
            if (missing.isNotEmpty()) append(" Missing: $missing.")
            if (extra.isNotEmpty()) append(" Unexpected: $extra.")
        }
        fail(message)
    }
}

/**
 * Asserts that a specific rule fails (others may pass or fail).
 */
public fun <Fact> RuleSet<Fact, *>.assertRuleFails(rule: Rule<Fact, *>, fact: Fact) {
    val result = evaluate(fact)
    if (result.passed) {
        fail("Expected rule '${rule.name}' to fail but all rules passed")
    }
    val failures = (result as Verdict.Fail<*>).failures
    assertTrue(
        failures.any { it.ruleName == rule.name },
        "Expected rule '${rule.name}' to fail but it passed. Failed rules: ${failures.map { it.ruleName }}"
    )
}

/**
 * Asserts that a specific rule fails and allows assertions on its failure.
 */
public fun <Fact> RuleSet<Fact, *>.assertRuleFails(rule: Rule<Fact, *>, fact: Fact, block: FailureAssertion.() -> Unit) {
    val result = evaluate(fact)
    if (result.passed) {
        fail("Expected rule '${rule.name}' to fail but all rules passed")
    }
    val failures = (result as Verdict.Fail<*>).failures
    val failure = failures.find { it.ruleName == rule.name }
        ?: fail("Expected rule '${rule.name}' to fail but it passed. Failed rules: ${failures.map { it.ruleName }}")
    FailureAssertion(failure).apply(block)
}

/**
 * Asserts that all rules pass (async version).
 */
public suspend fun <Fact> RuleSet<Fact, *>.assertPassesAsync(fact: Fact, message: String? = null) {
    val result = evaluateAsync(fact)
    assertTrue(
        result.passed,
        message ?: "Expected all rules to pass but some failed: ${result.failureMessages()}"
    )
}

/**
 * Asserts that at least one rule fails (async version).
 */
public suspend fun <Fact> RuleSet<Fact, *>.assertFailsAsync(fact: Fact, message: String? = null) {
    val result = evaluateAsync(fact)
    assertTrue(
        !result.passed,
        message ?: "Expected at least one rule to fail but all passed"
    )
}

/**
 * Asserts that rules fail and allows detailed assertions (async version).
 */
public suspend fun <Fact> RuleSet<Fact, *>.assertFailsAsync(fact: Fact, block: FailuresAssertion.() -> Unit) {
    val result = evaluateAsync(fact)
    if (result.passed) {
        fail("Expected at least one rule to fail but all passed")
    }
    val failures = (result as Verdict.Fail<*>).failures
    FailuresAssertion(failures).apply(block)
}

/**
 * Asserts that only the specified async rules fail (and all others pass).
 */
public suspend fun <Fact> RuleSet<Fact, *>.assertAsyncRuleFailsOnly(vararg rules: AsyncRule<Fact, *>, fact: Fact) {
    val result = evaluateAsync(fact)
    val ruleNames = rules.map { it.name }
    if (result.passed) {
        fail("Expected rules $ruleNames to fail but all passed")
    }
    val failures = (result as Verdict.Fail<*>).failures
    val expected = ruleNames.toSet()
    val actual = failures.map { it.ruleName }.toSet()

    if (expected != actual) {
        val missing = expected - actual
        val extra = actual - expected
        val message = buildString {
            append("Expected only rules $expected to fail but got $actual.")
            if (missing.isNotEmpty()) append(" Missing: $missing.")
            if (extra.isNotEmpty()) append(" Unexpected: $extra.")
        }
        fail(message)
    }
}

/**
 * Asserts that a specific async rule fails (others may pass or fail).
 */
public suspend fun <Fact> RuleSet<Fact, *>.assertAsyncRuleFails(rule: AsyncRule<Fact, *>, fact: Fact) {
    val result = evaluateAsync(fact)
    if (result.passed) {
        fail("Expected rule '${rule.name}' to fail but all rules passed")
    }
    val failures = (result as Verdict.Fail<*>).failures
    assertTrue(
        failures.any { it.ruleName == rule.name },
        "Expected rule '${rule.name}' to fail but it passed. Failed rules: ${failures.map { it.ruleName }}"
    )
}

/**
 * Asserts that a specific async rule fails and allows assertions on its failure.
 */
public suspend fun <Fact> RuleSet<Fact, *>.assertAsyncRuleFails(rule: AsyncRule<Fact, *>, fact: Fact, block: FailureAssertion.() -> Unit) {
    val result = evaluateAsync(fact)
    if (result.passed) {
        fail("Expected rule '${rule.name}' to fail but all rules passed")
    }
    val failures = (result as Verdict.Fail<*>).failures
    val failure = failures.find { it.ruleName == rule.name }
        ?: fail("Expected rule '${rule.name}' to fail but it passed. Failed rules: ${failures.map { it.ruleName }}")
    FailureAssertion(failure).apply(block)
}

private fun Verdict<*>.failureMessages(): String {
    return if (this is Verdict.Fail<*>) {
        failures.joinToString("; ") { "${it.ruleName}: ${it.reason}" }
    } else {
        ""
    }
}
