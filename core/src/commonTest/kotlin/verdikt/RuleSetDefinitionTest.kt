package verdikt

import kotlinx.coroutines.delay
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.time.Duration.Companion.milliseconds

class RuleSetDefinitionTest {

    data class Person(val name: String, val score: Int)

    // Rules defined separately
    object ScoreCheck : Rule<Person, String> {
        override val name = "score-check"
        override val description = "Must have score of 100 or higher"
        override fun evaluate(fact: Person) = fact.score >= 100
        override fun failureReason(fact: Person) = "Score ${fact.score} is below minimum 100"
    }

    object NameCheck : Rule<Person, String> {
        override val name = "name-check"
        override val description = "Name must not be blank"
        override fun evaluate(fact: Person) = fact.name.isNotBlank()
        override fun failureReason(fact: Person) = description
    }

    // RuleSets created with DSL
    val personRules = rules<Person, String> {
        add(ScoreCheck)
        add(NameCheck)
    }

    val asyncPersonRules = rules<Person, String> {
        rule("async-score-check") {
            asyncCondition {
                delay(10.milliseconds)
                it.score >= 100
            }
            onFailure { person -> "Score ${person.score} is below minimum 100" }
        }
    }

    @Test
    fun ruleSetEvaluateExtensionWorks() {
        val result = personRules.evaluate(Person("Alice", 150))
        assertIs<Verdict.Pass>(result)
    }

    @Test
    fun ruleSetEvaluateReturnsFailures() {
        val result = personRules.evaluate(Person("", 50))
        assertIs<Verdict.Fail<String>>(result)
        assertEquals(2, result.failures.size)
        assertEquals("score-check", result.failures[0].ruleName)
        assertEquals("name-check", result.failures[1].ruleName)
    }

    @Test
    fun ruleSetEvaluateAsyncExtensionWorks() = runTest {
        val result = asyncPersonRules.evaluateAsync(Person("Alice", 150))
        assertIs<Verdict.Pass>(result)
    }

    @Test
    fun ruleSetEvaluateAsyncReturnsFailures() = runTest {
        val result = asyncPersonRules.evaluateAsync(Person("Alice", 50))
        assertIs<Verdict.Fail<String>>(result)
        assertEquals("Score 50 is below minimum 100", result.failures[0].reason)
    }

    @Test
    fun ruleSetCanAccessRules() {
        assertEquals("score-check", ScoreCheck.name)
        assertEquals("name-check", NameCheck.name)
    }

    @Test
    fun ruleSetSizeIsAccessible() {
        assertEquals(2, personRules.size)
    }

    @Test
    fun configurableRulesWork() {
        fun configurableRules(minScore: Int) = rules<Person, String> {
            rule("configurable-score-check") {
                condition { it.score >= minScore }
                onFailure { person -> "Score ${person.score} is below minimum $minScore" }
            }
        }

        val rules150 = configurableRules(150)
        val rules100 = configurableRules(100)

        val person = Person("Alice", 120)

        assertIs<Verdict.Fail<String>>(rules150.evaluate(person))
        assertIs<Verdict.Pass>(rules100.evaluate(person))
    }
}
