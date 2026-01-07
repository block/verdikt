package verdikt

import kotlinx.coroutines.delay
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.milliseconds

class RuleDefinitionTest {

    data class Person(val name: String, val score: Int, val email: String)

    // Example of a simple rule as an object
    object ScoreRule : Rule<Person, String> {
        override val name = "score-check"
        override val description = "Must have score of 100 or higher"

        override fun evaluate(fact: Person): Boolean = fact.score >= 100

        override fun failureReason(fact: Person) = "Score ${fact.score} is below minimum 100"
    }

    // Example of a rule as a class with dependencies
    class EmailDomainRule(private val allowedDomains: Set<String>) : Rule<Person, String> {
        override val name = "email-domain-check"
        override val description = "Email must be from allowed domain"

        override fun evaluate(fact: Person): Boolean {
            val domain = fact.email.substringAfter("@", "")
            return domain in allowedDomains
        }

        override fun failureReason(fact: Person): String {
            val domain = fact.email.substringAfter("@", "")
            return "Domain '$domain' is not in allowed list: $allowedDomains"
        }
    }

    // Example of a rule using description as failure reason
    object NameNotBlankRule : Rule<Person, String> {
        override val name = "name-not-blank"
        override val description = "Name must not be blank"

        override fun evaluate(fact: Person) = fact.name.isNotBlank()
        override fun failureReason(fact: Person) = description
    }

    // Example of rule with simple failure message
    object HasEmailRule : Rule<Person, String> {
        override val name = "has-email"

        override fun evaluate(fact: Person) = fact.email.isNotEmpty()
        override fun failureReason(fact: Person) = "Rule '$name' failed"
    }

    @Test
    fun ruleObjectCanBeAddedToRuleSet() {
        val ruleSet = rules<Person, String> {
            add(ScoreRule)
        }

        assertEquals(1, ruleSet.size)
        assertEquals(listOf("score-check"), ruleSet.names)
    }

    @Test
    fun ruleEvaluatesCorrectly() {
        val ruleSet = rules<Person, String> {
            add(ScoreRule)
        }

        val passResult = ruleSet.evaluate(Person("Alice", 150, "alice@example.com"))
        assertIs<Verdict.Pass>(passResult)

        val failResult = ruleSet.evaluate(Person("Bob", 50, "bob@example.com"))
        assertIs<Verdict.Fail<String>>(failResult)
        assertEquals("Score 50 is below minimum 100", failResult.failures[0].reason)
    }

    @Test
    fun ruleClassWithDependenciesWorks() {
        val rule = EmailDomainRule(setOf("company.com", "partner.org"))

        val ruleSet = rules<Person, String> {
            add(rule)
        }

        val passResult = ruleSet.evaluate(Person("Alice", 150, "alice@company.com"))
        assertIs<Verdict.Pass>(passResult)

        val failResult = ruleSet.evaluate(Person("Bob", 150, "bob@gmail.com"))
        assertIs<Verdict.Fail<String>>(failResult)
        assertTrue((failResult.failures[0].reason as String).contains("gmail.com"))
    }

    @Test
    fun ruleDefaultFailureMessageUsesDescription() {
        val ruleSet = rules<Person, String> {
            add(NameNotBlankRule)
        }

        val result = ruleSet.evaluate(Person("", 150, "test@example.com"))
        assertIs<Verdict.Fail<String>>(result)
        assertEquals("Name must not be blank", result.failures[0].reason)
    }

    @Test
    fun ruleDefaultFailureMessageWithoutDescription() {
        val ruleSet = rules<Person, String> {
            add(HasEmailRule)
        }

        val result = ruleSet.evaluate(Person("Alice", 150, ""))
        assertIs<Verdict.Fail<String>>(result)
        assertEquals("Rule 'has-email' failed", result.failures[0].reason)
    }

    @Test
    fun multipleRulesCanBeCombined() {
        val ruleSet = rules<Person, String> {
            add(ScoreRule)
            add(NameNotBlankRule)
            add(EmailDomainRule(setOf("example.com")))
        }

        assertEquals(3, ruleSet.size)

        val result = ruleSet.evaluate(Person("", 50, "test@gmail.com"))
        assertIs<Verdict.Fail<String>>(result)
        assertEquals(3, result.failures.size)
    }

    @Test
    fun ruleCanBeMixedWithDslRules() {
        val ruleSet = rules<Person, String> {
            add(ScoreRule)
            rule("custom-check") {
                condition { it.name.length <= 50 }
                onFailure("Name too long")
            }
        }

        assertEquals(2, ruleSet.size)
        assertEquals(listOf("score-check", "custom-check"), ruleSet.names)
    }

    @Test
    fun ruleInterfaceEvaluatesDirectly() {
        // Rule interface has evaluate() returning Boolean
        val person = Person("Alice", 50, "test@example.com")
        val passed = ScoreRule.evaluate(person)
        assertEquals(false, passed)

        // And we can get the failure reason
        val reason = ScoreRule.failureReason(person)
        assertEquals("Score 50 is below minimum 100", reason)
    }

    // Async rule tests

    class SlowCheckRule(private val delayMs: Long) : AsyncRule<Person, String> {
        override val name = "slow-check"
        override val description = "Simulated slow check"

        override suspend fun evaluate(fact: Person): Boolean {
            delay(delayMs.milliseconds)
            return fact.score >= 100
        }

        override fun failureReason(fact: Person) = "Slow check failed for ${fact.name}"
    }

    @Test
    fun asyncRuleCanBeAddedToRuleSet() {
        val ruleSet = rules<Person, String> {
            add(SlowCheckRule(10))
        }

        assertEquals(1, ruleSet.size)
        assertEquals(listOf("slow-check"), ruleSet.names)
    }

    @Test
    fun asyncRuleEvaluatesCorrectly() = runTest {
        val ruleSet = rules<Person, String> {
            add(SlowCheckRule(10))
        }

        val passResult = ruleSet.evaluateAsync(Person("Alice", 150, "test@example.com"))
        assertIs<Verdict.Pass>(passResult)

        val failResult = ruleSet.evaluateAsync(Person("Bob", 50, "test@example.com"))
        assertIs<Verdict.Fail<String>>(failResult)
        assertEquals("Slow check failed for Bob", failResult.failures[0].reason)
    }

    @Test
    fun asyncRuleCanBeMixedWithSyncRules() = runTest {
        val ruleSet = rules<Person, String> {
            add(ScoreRule)  // sync Rule
            add(SlowCheckRule(10))  // async AsyncRule
        }

        assertEquals(2, ruleSet.size)

        val result = ruleSet.evaluateAsync(Person("Alice", 150, "test@example.com"))
        assertIs<Verdict.Pass>(result)
    }

    @Test
    fun asyncRuleInterfaceEvaluatesDirectly() = runTest {
        // AsyncRule interface has suspend evaluate() returning Boolean
        val asyncRule = SlowCheckRule(10)
        val person = Person("Alice", 50, "test@example.com")

        val passed = asyncRule.evaluate(person)
        assertEquals(false, passed)

        val reason = asyncRule.failureReason(person)
        assertEquals("Slow check failed for Alice", reason)
    }

    @Test
    fun asyncRuleDefaultFailureMessageUsesDescription() = runTest {
        val asyncWithDescription = object : AsyncRule<Person, String> {
            override val name = "async-with-desc"
            override val description = "Async rule description"
            override suspend fun evaluate(fact: Person): Boolean {
                delay(1.milliseconds)
                return false
            }
            override fun failureReason(fact: Person) = description
        }

        val ruleSet = rules<Person, String> {
            add(asyncWithDescription)
        }

        val result = ruleSet.evaluateAsync(Person("Alice", 150, "test@example.com"))
        assertIs<Verdict.Fail<String>>(result)
        assertEquals("Async rule description", result.failures[0].reason)
    }

    @Test
    fun asyncRuleDefaultFailureMessageWithoutDescription() = runTest {
        val asyncNoDescription = object : AsyncRule<Person, String> {
            override val name = "async-no-desc"
            override suspend fun evaluate(fact: Person): Boolean {
                delay(1.milliseconds)
                return false
            }
            override fun failureReason(fact: Person) = "Rule '$name' failed"
        }

        val ruleSet = rules<Person, String> {
            add(asyncNoDescription)
        }

        val result = ruleSet.evaluateAsync(Person("Alice", 150, "test@example.com"))
        assertIs<Verdict.Fail<String>>(result)
        assertEquals("Rule 'async-no-desc' failed", result.failures[0].reason)
    }
}
