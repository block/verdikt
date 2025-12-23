package verdikt

import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertIs
import kotlin.test.assertTrue

class RuleBuilderTest {

    data class Person(val name: String, val score: Int)

    @Test
    fun validRuleBuildsSuccessfully() {
        val personRule = rule<Person, String>("score-check") {
            description = "Must have score of 100 or higher"
            condition { it.score >= 100 }
        }

        assertEquals("score-check", personRule.name)
        assertEquals("Must have score of 100 or higher", personRule.description)
    }

    @Test
    fun missingConditionThrows() {
        assertFailsWith<IllegalArgumentException> {
            rule<Person, String>("no-condition") {
                description = "This has no condition"
            }
        }
    }

    @Test
    fun cannotSetBothConditionAndAsyncCondition() {
        assertFailsWith<IllegalArgumentException> {
            rule<Person, String>("both-conditions") {
                condition { it.score >= 100 }
                asyncCondition { true }
            }
        }
    }

    @Test
    fun cannotSetAsyncConditionAfterCondition() {
        assertFailsWith<IllegalArgumentException> {
            rule<Person, String>("condition-first") {
                condition { it.score >= 100 }
                asyncCondition { true }
            }
        }
    }

    @Test
    fun cannotSetConditionAfterAsyncCondition() {
        assertFailsWith<IllegalArgumentException> {
            rule<Person, String>("async-first") {
                asyncCondition { true }
                condition { it.score >= 100 }
            }
        }
    }

    @Test
    fun descriptionIsOptional() {
        val personRule = rule<Person, String>("score-check") {
            condition { it.score >= 100 }
        }

        assertEquals("", personRule.description)
    }

    @Test
    fun onFailureIsOptional() {
        val personRule = rule<Person, String>("score-check") {
            condition { it.score >= 100 }
        }

        // Use RuleSet to get Verdict
        val ruleSet = rules<Person, String> { add(personRule) }
        val result = ruleSet.evaluate(Person("Alice", 50))
        assertIs<Verdict.Fail<String>>(result)
        // Default message when no description
        assertEquals("Rule 'score-check' failed", result.failures[0].reason)
    }

    @Test
    fun defaultFailureMessageIncludesDescriptionWhenProvided() {
        val personRule = rule<Person, String>("score-check") {
            description = "Must have score of 100 or higher"
            condition { it.score >= 100 }
        }

        val ruleSet = rules<Person, String> { add(personRule) }
        val result = ruleSet.evaluate(Person("Alice", 50))
        assertIs<Verdict.Fail<String>>(result)
        assertEquals("Must have score of 100 or higher", result.failures[0].reason)
    }

    @Test
    fun defaultFailureMessageUsesOnlyNameWhenNoDescription() {
        val personRule = rule<Person, String>("score-check") {
            condition { it.score >= 100 }
        }

        val ruleSet = rules<Person, String> { add(personRule) }
        val result = ruleSet.evaluate(Person("Alice", 50))
        assertIs<Verdict.Fail<String>>(result)
        assertEquals("Rule 'score-check' failed", result.failures[0].reason)
    }

    @Test
    fun customOnFailureOverridesDefaultMessage() {
        val personRule = rule<Person, String>("score-check") {
            description = "Must have score of 100 or higher"
            condition { it.score >= 100 }
            onFailure { fact -> "Score ${fact.score} is below minimum 100" }
        }

        val ruleSet = rules<Person, String> { add(personRule) }
        val result = ruleSet.evaluate(Person("Alice", 50))
        assertIs<Verdict.Fail<String>>(result)
        assertEquals("Score 50 is below minimum 100", result.failures[0].reason)
    }

    @Test
    fun staticOnFailureMessageWorks() {
        val personRule = rule<Person, String>("score-check") {
            condition { it.score >= 100 }
            onFailure("Must have score 100+")
        }

        val ruleSet = rules<Person, String> { add(personRule) }
        val result = ruleSet.evaluate(Person("Alice", 50))
        assertIs<Verdict.Fail<String>>(result)
        assertEquals("Must have score 100+", result.failures[0].reason)
    }

    @Test
    fun ruleEvaluateReturnsPassWhenConditionTrue() {
        val personRule = rule<Person, String>("score-check") {
            condition { it.score >= 100 }
        }

        val ruleSet = rules<Person, String> { add(personRule) }
        val result = ruleSet.evaluate(Person("Alice", 150))
        assertIs<Verdict.Pass>(result)
    }

    @Test
    fun ruleEvaluateReturnsFailWhenConditionFalse() {
        val personRule = rule<Person, String>("score-check") {
            condition { it.score >= 100 }
        }

        val ruleSet = rules<Person, String> { add(personRule) }
        val result = ruleSet.evaluate(Person("Alice", 50))
        assertIs<Verdict.Fail<String>>(result)
    }

    @Test
    fun asyncRuleCanBeAddedToRuleSet() {
        val asyncRule = rule<Person, String>("async-check") {
            asyncCondition { true }
        }

        val ruleSet = rules<Person, String> { add(asyncRule) }
        assertEquals(1, ruleSet.size)
        assertEquals(listOf("async-check"), ruleSet.names)
    }

    @Test
    fun rulesDslCreatesRuleSet() {
        val ruleSet = rules<Person, String> {
            rule("score-check") {
                condition { it.score >= 100 }
            }
            rule("name-check") {
                condition { it.name.isNotBlank() }
            }
        }

        assertEquals(2, ruleSet.size)
        assertEquals(listOf("score-check", "name-check"), ruleSet.names)
    }

    @Test
    fun standaloneRuleCanBeAddedToRuleSet() {
        val scoreRule = rule<Person, String>("score-check") {
            condition { it.score >= 100 }
        }

        val ruleSet = rules<Person, String> {
            add(scoreRule)
        }

        assertEquals(1, ruleSet.size)
        assertEquals(listOf("score-check"), ruleSet.names)
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
            rule("name-check") {
                condition { it.name.isNotBlank() }
            }
        }

        assertEquals(2, extendedRules.size)
        assertEquals(listOf("score-check", "name-check"), extendedRules.names)
    }

    @Test
    fun ruleSetPlusOperatorCombinesRuleSets() {
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
    fun ruleEvaluateAsyncWorksForAsyncRule() = runTest {
        val asyncRule = rule<Person, String>("async-score-check") {
            asyncCondition { it.score >= 100 }
            onFailure { fact -> "Score ${fact.score} is below minimum 100" }
        }

        val ruleSet = rules<Person, String> { add(asyncRule) }

        val passResult = ruleSet.evaluateAsync(Person("Alice", 150))
        assertIs<Verdict.Pass>(passResult)

        val failResult = ruleSet.evaluateAsync(Person("Bob", 50))
        assertIs<Verdict.Fail<String>>(failResult)
        assertEquals("Score 50 is below minimum 100", failResult.failures[0].reason)
    }

    @Test
    fun ruleEvaluateAsyncWorksForSyncRule() = runTest {
        val syncRule = rule<Person, String>("sync-score-check") {
            condition { it.score >= 100 }
        }

        val ruleSet = rules<Person, String> { add(syncRule) }
        val result = ruleSet.evaluateAsync(Person("Alice", 150))
        assertIs<Verdict.Pass>(result)
    }

    @Test
    fun emptyRuleNameIsAllowed() {
        val emptyNameRule = rule<Person, String>("") {
            condition { it.score >= 100 }
        }

        assertEquals("", emptyNameRule.name)

        val ruleSet = rules<Person, String> { add(emptyNameRule) }
        val result = ruleSet.evaluate(Person("Alice", 50))
        assertIs<Verdict.Fail<String>>(result)
        assertEquals("", result.failures[0].ruleName)
    }

    @Test
    fun blankRuleNameIsAllowed() {
        val blankNameRule = rule<Person, String>("   ") {
            condition { it.score >= 100 }
        }

        assertEquals("   ", blankNameRule.name)
    }

    @Test
    fun ruleDirectEvaluateReturnsBooleanTrue() {
        val personRule = rule<Person, String>("score-check") {
            condition { it.score >= 100 }
        }

        val passed = personRule.evaluate(Person("Alice", 150))
        assertEquals(true, passed)
    }

    @Test
    fun ruleDirectEvaluateReturnsBooleanFalse() {
        val personRule = rule<Person, String>("score-check") {
            condition { it.score >= 100 }
        }

        val passed = personRule.evaluate(Person("Bob", 50))
        assertEquals(false, passed)
    }
}
