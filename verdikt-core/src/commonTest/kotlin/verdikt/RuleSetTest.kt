package verdikt

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertIs
import kotlin.test.assertTrue

class RuleSetTest {

    data class Person(val name: String, val score: Int, val email: String)

    @Test
    fun emptyRuleSetReturnsPass() {
        val ruleSet = rules<Person, String> { }
        val result = ruleSet.evaluate(Person("Alice", 150, "alice@example.com"))
        assertIs<Verdict.Pass>(result)
    }

    @Test
    fun singlePassingRuleReturnsPass() {
        val ruleSet = rules<Person, String> {
            rule("score-check") {
                condition { it.score >= 100 }
            }
        }
        val result = ruleSet.evaluate(Person("Alice", 150, "alice@example.com"))
        assertIs<Verdict.Pass>(result)
    }

    @Test
    fun singleFailingRuleReturnsFailWithFailure() {
        val ruleSet = rules<Person, String> {
            rule("score-check") {
                description = "Must have score of 100 or higher"
                condition { it.score >= 100 }
            }
        }
        val result = ruleSet.evaluate(Person("Alice", 50, "alice@example.com"))

        assertIs<Verdict.Fail<String>>(result)
        assertEquals(1, result.failures.size)
        assertEquals("score-check", result.failures[0].ruleName)
        assertEquals("Must have score of 100 or higher", result.failures[0].reason)
    }

    @Test
    fun multipleFailuresCollectedInOrder() {
        val ruleSet = rules<Person, String> {
            rule("score-check") {
                description = "Must have score of 100 or higher"
                condition { it.score >= 100 }
            }
            rule("email-check") {
                condition { "@" in it.email }
                onFailure { fact -> "Invalid email: ${fact.email}" }
            }
            rule("name-check") {
                description = "Name must not be blank"
                condition { it.name.isNotBlank() }
            }
        }

        val result = ruleSet.evaluate(Person("", 50, "invalid"))

        assertIs<Verdict.Fail<String>>(result)
        assertEquals(3, result.failures.size)
        assertEquals("score-check", result.failures[0].ruleName)
        assertEquals("email-check", result.failures[1].ruleName)
        assertEquals("name-check", result.failures[2].ruleName)
    }

    @Test
    fun ruleSetPlusCombinesRules() {
        val rules1 = rules<Person, String> {
            rule("rule1") { condition { true } }
        }
        val rules2 = rules<Person, String> {
            rule("rule2") { condition { true } }
        }

        val combined = rules1 + rules2

        assertEquals(2, combined.size)
        assertEquals(listOf("rule1", "rule2"), combined.names)
    }

    @Test
    fun includeAddsRulesFromAnotherSet() {
        val basicRules = rules<Person, String> {
            rule("score-check") {
                condition { it.score >= 100 }
            }
        }

        val extendedRules = rules<Person, String> {
            include(basicRules)
            rule("email-check") {
                condition { "@" in it.email }
            }
        }

        assertEquals(2, extendedRules.size)
        assertEquals(listOf("score-check", "email-check"), extendedRules.names)
    }

    @Test
    fun standaloneRuleCanBeAddedToMultipleSets() {
        val scoreRule = rule<Person, String>("score-check") {
            condition { it.score >= 100 }
        }

        val ruleSet1 = rules<Person, String> { add(scoreRule) }
        val ruleSet2 = rules<Person, String> { add(scoreRule) }

        assertEquals(1, ruleSet1.size)
        assertEquals(1, ruleSet2.size)
        assertEquals(listOf("score-check"), ruleSet1.names)
        assertEquals(listOf("score-check"), ruleSet2.names)
    }

    @Test
    fun evaluateThrowsIfRuleSetHasAsyncRules() {
        val ruleSet = rules<Person, String> {
            rule("async-rule") {
                asyncCondition { true }
            }
        }

        assertFailsWith<IllegalStateException> {
            ruleSet.evaluate(Person("Alice", 150, "alice@example.com"))
        }
    }

    @Test
    fun rulesExecuteInInsertionOrder() {
        val executionOrder = mutableListOf<String>()

        val ruleSet = rules<Person, String> {
            rule("first") {
                condition {
                    executionOrder.add("first")
                    true
                }
            }
            rule("second") {
                condition {
                    executionOrder.add("second")
                    true
                }
            }
            rule("third") {
                condition {
                    executionOrder.add("third")
                    true
                }
            }
        }

        ruleSet.evaluate(Person("Alice", 150, "alice@example.com"))

        assertEquals(listOf("first", "second", "third"), executionOrder)
    }

    @Test
    fun ruleSetSizeReturnsCorrectCount() {
        val ruleSet = rules<Person, String> {
            rule("rule1") { condition { true } }
            rule("rule2") { condition { true } }
            rule("rule3") { condition { true } }
        }

        assertEquals(3, ruleSet.size)
    }

    @Test
    fun ruleSetIsEmptyReturnsTrueForEmptySet() {
        val ruleSet = rules<Person, String> { }
        assertTrue(ruleSet.isEmpty)
    }

    @Test
    fun ruleSetNamesReturnsAllRuleNames() {
        val ruleSet = rules<Person, String> {
            rule("score-check") { condition { true } }
            rule("email-check") { condition { true } }
        }

        assertEquals(listOf("score-check", "email-check"), ruleSet.names)
    }

    @Test
    fun failureMessagesIncludeDynamicValues() {
        val ruleSet = rules<Person, String> {
            rule("score-check") {
                condition { it.score >= 100 }
                onFailure { fact -> "Score ${fact.score} is below minimum 100" }
            }
        }

        val result = ruleSet.evaluate(Person("Alice", 50, "alice@example.com"))

        assertIs<Verdict.Fail<String>>(result)
        assertEquals("Score 50 is below minimum 100", result.failures[0].reason)
    }

    @Test
    fun ruleSetAllowsDuplicateRuleNames() {
        val ruleSet = rules<Person, String> {
            rule("same-name") { condition { true } }
            rule("same-name") { condition { false } }
        }

        assertEquals(2, ruleSet.size)
        assertEquals(listOf("same-name", "same-name"), ruleSet.names)
    }
}
