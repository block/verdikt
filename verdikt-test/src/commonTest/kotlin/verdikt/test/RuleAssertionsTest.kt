package verdikt.test

import verdikt.rule
import kotlin.test.Test
import kotlin.test.assertFailsWith

class RuleAssertionsTest {

    data class Person(val name: String, val score: Int)

    private val scoreRule = rule<Person, String>("score-check") {
        description = "Must have score of 100 or higher"
        condition { it.score >= 100 }
        onFailure { person -> "Score ${person.score} is below minimum 100" }
    }

    private val nameRule = rule<Person, String>("name-check") {
        condition { it.name.isNotBlank() }
        onFailure("Name cannot be blank")
    }

    // ========================================================================
    // assertPasses tests
    // ========================================================================

    @Test
    fun assertPassesSucceedsWhenRulePasses() {
        scoreRule.assertPasses(Person("Alice", 150))
    }

    @Test
    fun assertPassesFailsWhenRuleFails() {
        assertFailsWith<AssertionError> {
            scoreRule.assertPasses(Person("Bob", 50))
        }
    }

    @Test
    fun assertPassesWithCustomMessage() {
        val error = assertFailsWith<AssertionError> {
            scoreRule.assertPasses(Person("Bob", 50), "Custom message")
        }
        kotlin.test.assertEquals("Custom message", error.message)
    }

    // ========================================================================
    // assertFails tests
    // ========================================================================

    @Test
    fun assertFailsSucceedsWhenRuleFails() {
        scoreRule.assertFails(Person("Bob", 50))
    }

    @Test
    fun assertFailsFailsWhenRulePasses() {
        assertFailsWith<AssertionError> {
            scoreRule.assertFails(Person("Alice", 150))
        }
    }

    @Test
    fun assertFailsWithCustomMessage() {
        val error = assertFailsWith<AssertionError> {
            scoreRule.assertFails(Person("Alice", 150), "Custom message")
        }
        kotlin.test.assertEquals("Custom message", error.message)
    }

    // ========================================================================
    // assertFails with block tests
    // ========================================================================

    @Test
    fun assertFailsWithBlockSucceedsWithCorrectMessage() {
        scoreRule.assertFails(Person("Bob", 50)) {
            message("Score 50 is below minimum 100")
        }
    }

    @Test
    fun assertFailsWithBlockMessageContains() {
        scoreRule.assertFails(Person("Bob", 50)) {
            messageContains("below minimum")
        }
    }

    @Test
    fun assertFailsWithBlockMessageStartsWith() {
        scoreRule.assertFails(Person("Bob", 50)) {
            messageStartsWith("Score 50")
        }
    }

    @Test
    fun assertFailsWithBlockMessageEndsWith() {
        scoreRule.assertFails(Person("Bob", 50)) {
            messageEndsWith("minimum 100")
        }
    }

    @Test
    fun assertFailsWithBlockRuleName() {
        scoreRule.assertFails(Person("Bob", 50)) {
            ruleName("score-check")
        }
    }

    @Test
    fun assertFailsWithBlockFailsOnWrongMessage() {
        assertFailsWith<AssertionError> {
            scoreRule.assertFails(Person("Bob", 50)) {
                message("Wrong message")
            }
        }
    }

    @Test
    fun assertFailsWithBlockFailsOnWrongMessageContains() {
        assertFailsWith<AssertionError> {
            scoreRule.assertFails(Person("Bob", 50)) {
                messageContains("not in message")
            }
        }
    }

    @Test
    fun assertFailsWithBlockFailsWhenRulePasses() {
        assertFailsWith<AssertionError> {
            scoreRule.assertFails(Person("Alice", 150)) {
                messageContains("anything")
            }
        }
    }

    @Test
    fun assertFailsWithBlockMessageMatches() {
        scoreRule.assertFails(Person("Bob", 50)) {
            messageMatches(Regex("Score \\d+ is below minimum \\d+"))
        }
    }
}
