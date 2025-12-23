package verdikt.test

import kotlinx.coroutines.delay
import kotlinx.coroutines.test.runTest
import verdikt.AsyncRule
import verdikt.Rule
import verdikt.RuleSet
import verdikt.rules
import kotlin.test.Test
import kotlin.test.assertFailsWith
import kotlin.time.Duration.Companion.milliseconds

class RuleDefinitionAssertionsTest {

    data class Person(val name: String, val score: Int)

    // ========================================================================
    // Rule tests
    // ========================================================================

    object ScoreRule : Rule<Person, String> {
        override val name = "score-check"
        override val description = "Must have score of 100 or higher"
        override fun evaluate(fact: Person) = fact.score >= 100
        override fun failureReason(fact: Person) = "Score ${fact.score} is below minimum 100"
    }

    object NameRule : Rule<Person, String> {
        override val name = "name-check"
        override fun evaluate(fact: Person) = fact.name.isNotBlank()
        override fun failureReason(fact: Person) = "Name cannot be blank"
    }

    @Test
    fun ruleAssertPassesSucceeds() {
        ScoreRule.assertPasses(Person("Alice", 150))
    }

    @Test
    fun ruleAssertPassesFails() {
        assertFailsWith<AssertionError> {
            ScoreRule.assertPasses(Person("Bob", 50))
        }
    }

    @Test
    fun ruleAssertFailsSucceeds() {
        ScoreRule.assertFails(Person("Bob", 50))
    }

    @Test
    fun ruleAssertFailsFails() {
        assertFailsWith<AssertionError> {
            ScoreRule.assertFails(Person("Alice", 150))
        }
    }

    @Test
    fun ruleAssertFailsWithBlock() {
        ScoreRule.assertFails(Person("Bob", 50)) {
            messageContains("below minimum")
        }
    }

    // ========================================================================
    // AsyncRule tests
    // ========================================================================

    object AsyncScoreRule : AsyncRule<Person, String> {
        override val name = "async-score-check"
        override val description = "Async score check"
        override suspend fun evaluate(fact: Person): Boolean {
            delay(1.milliseconds)
            return fact.score >= 100
        }
        override fun failureReason(fact: Person) = "Score ${fact.score} is below minimum 100"
    }

    @Test
    fun asyncRuleAssertPassesSucceeds() = runTest {
        AsyncScoreRule.assertPasses(Person("Alice", 150))
    }

    @Test
    fun asyncRuleAssertPassesFails() = runTest {
        assertFailsWith<AssertionError> {
            AsyncScoreRule.assertPasses(Person("Bob", 50))
        }
    }

    @Test
    fun asyncRuleAssertFailsSucceeds() = runTest {
        AsyncScoreRule.assertFails(Person("Bob", 50))
    }

    @Test
    fun asyncRuleAssertFailsFails() = runTest {
        assertFailsWith<AssertionError> {
            AsyncScoreRule.assertFails(Person("Alice", 150))
        }
    }

    @Test
    fun asyncRuleAssertFailsWithBlock() = runTest {
        AsyncScoreRule.assertFails(Person("Bob", 50)) {
            messageContains("below minimum")
        }
    }

    // ========================================================================
    // RuleSet tests
    // ========================================================================

    val personRules = rules<Person, String> {
        add(ScoreRule)
        add(NameRule)
    }

    @Test
    fun ruleSetAssertPassesSucceeds() {
        personRules.assertPasses(Person("Alice", 150))
    }

    @Test
    fun ruleSetAssertPassesFails() {
        assertFailsWith<AssertionError> {
            personRules.assertPasses(Person("Alice", 50))
        }
    }

    @Test
    fun ruleSetAssertFailsSucceeds() {
        personRules.assertFails(Person("Alice", 50))
    }

    @Test
    fun ruleSetAssertFailsFails() {
        assertFailsWith<AssertionError> {
            personRules.assertFails(Person("Alice", 150))
        }
    }

    @Test
    fun ruleSetAssertFailsWithBlock() {
        personRules.assertFails(Person("", 50)) {
            hasCount(2)
            hasRule("score-check")
            hasRule("name-check")
        }
    }

    @Test
    fun ruleSetAssertFailsOnly() {
        personRules.assertRuleFailsOnly(ScoreRule, fact = Person("Alice", 50))
    }

    @Test
    fun ruleSetAssertRuleFails() {
        personRules.assertRuleFails(ScoreRule, Person("Alice", 50))
    }

    @Test
    fun ruleSetAssertRuleFailsWithBlock() {
        personRules.assertRuleFails(ScoreRule, Person("Alice", 50)) {
            messageContains("below minimum")
        }
    }
}
