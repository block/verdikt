package verdikt.test

import kotlinx.coroutines.test.runTest
import verdikt.AsyncRule
import verdikt.rule
import verdikt.rules
import kotlin.test.Test
import kotlin.test.assertFailsWith

class AsyncAssertionsTest {

    data class Person(val name: String, val score: Int)

    object AsyncScoreCheck : AsyncRule<Person, String> {
        override val name = "async-score-check"
        override suspend fun evaluate(fact: Person) = fact.score >= 100
        override fun failureReason(fact: Person) = "Score ${fact.score} is below minimum 100"
    }

    private val asyncRule = rule<Person, String>("async-score-check") {
        asyncCondition { it.score >= 100 }
        onFailure { person -> "Score ${person.score} is below minimum 100" }
    }

    private val asyncRuleSet = rules<Person, String> {
        add(AsyncScoreCheck)
        rule("name-check") {
            condition { it.name.isNotBlank() }
            onFailure("Name cannot be blank")
        }
    }

    // ========================================================================
    // Rule async assertion tests
    // ========================================================================

    @Test
    fun ruleAssertPassesAsyncSucceeds() = runTest {
        asyncRule.assertPassesAsync(Person("Alice", 150))
    }

    @Test
    fun ruleAssertPassesAsyncFails() = runTest {
        assertFailsWith<AssertionError> {
            asyncRule.assertPassesAsync(Person("Bob", 50))
        }
    }

    @Test
    fun ruleAssertFailsAsyncSucceeds() = runTest {
        asyncRule.assertFailsAsync(Person("Bob", 50))
    }

    @Test
    fun ruleAssertFailsAsyncFails() = runTest {
        assertFailsWith<AssertionError> {
            asyncRule.assertFailsAsync(Person("Alice", 150))
        }
    }

    @Test
    fun ruleAssertFailsAsyncWithBlock() = runTest {
        asyncRule.assertFailsAsync(Person("Bob", 50)) {
            messageContains("below minimum")
        }
    }

    // ========================================================================
    // RuleSet async assertion tests
    // ========================================================================

    @Test
    fun ruleSetAssertPassesAsyncSucceeds() = runTest {
        asyncRuleSet.assertPassesAsync(Person("Alice", 150))
    }

    @Test
    fun ruleSetAssertPassesAsyncFails() = runTest {
        assertFailsWith<AssertionError> {
            asyncRuleSet.assertPassesAsync(Person("Bob", 50))
        }
    }

    @Test
    fun ruleSetAssertFailsAsyncSucceeds() = runTest {
        asyncRuleSet.assertFailsAsync(Person("Bob", 50))
    }

    @Test
    fun ruleSetAssertFailsAsyncFails() = runTest {
        assertFailsWith<AssertionError> {
            asyncRuleSet.assertFailsAsync(Person("Alice", 150))
        }
    }

    @Test
    fun ruleSetAssertFailsAsyncWithBlock() = runTest {
        asyncRuleSet.assertFailsAsync(Person("", 50)) {
            hasCount(2)
            hasRule("async-score-check")
            hasRule("name-check")
        }
    }

    @Test
    fun ruleSetAssertAsyncRuleFailsOnly() = runTest {
        asyncRuleSet.assertAsyncRuleFailsOnly(AsyncScoreCheck, fact = Person("Alice", 50))
    }
}
