package verdikt

import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

class SideEffectTest {

    data class Person(val name: String, val score: Int)

    // ========================================================================
    // Rule.sideEffect tests
    // ========================================================================

    @Test
    fun ruleSideEffectIsCalledOnPass() {
        var called = false
        var capturedPassed: Boolean? = null

        val rule = rule<Person, String>("score-check") {
            condition { it.score >= 100 }
        }.sideEffect { fact, passed ->
            called = true
            capturedPassed = passed
        }

        rule.evaluate(Person("Alice", 150))

        assertTrue(called, "Side effect should be called")
        assertEquals(true, capturedPassed)
    }

    @Test
    fun ruleSideEffectIsCalledOnFail() {
        var called = false
        var capturedPassed: Boolean? = null

        val rule = rule<Person, String>("score-check") {
            condition { it.score >= 100 }
        }.sideEffect { fact, passed ->
            called = true
            capturedPassed = passed
        }

        rule.evaluate(Person("Bob", 50))

        assertTrue(called, "Side effect should be called")
        assertEquals(false, capturedPassed)
    }

    @Test
    fun ruleSideEffectReceivesFact() {
        var capturedFact: Person? = null

        val rule = rule<Person, String>("score-check") {
            condition { it.score >= 100 }
        }.sideEffect { fact, passed ->
            capturedFact = fact
        }

        val person = Person("Alice", 150)
        rule.evaluate(person)

        assertEquals(person, capturedFact)
    }

    @Test
    fun ruleSideEffectHasAccessToRuleName() {
        var capturedName: String? = null

        val rule = rule<Person, String>("score-check") {
            condition { it.score >= 100 }
        }.sideEffect { fact, passed ->
            capturedName = this.name
        }

        rule.evaluate(Person("Alice", 150))

        assertEquals("score-check", capturedName)
    }

    @Test
    fun multipleSideEffectsAreChained() {
        val calls = mutableListOf<String>()

        val rule = rule<Person, String>("score-check") {
            condition { it.score >= 100 }
        }.sideEffect { fact, passed ->
            calls.add("first")
        }.sideEffect { fact, passed ->
            calls.add("second")
        }.sideEffect { fact, passed ->
            calls.add("third")
        }

        rule.evaluate(Person("Alice", 150))

        assertEquals(listOf("first", "second", "third"), calls)
    }

    @Test
    fun ruleSideEffectDoesNotAffectResult() {
        val rule = rule<Person, String>("score-check") {
            condition { it.score >= 100 }
            onFailure("Score too low")
        }.sideEffect { fact, passed ->
            // Side effect does nothing to result
        }

        // Use RuleSet to get Verdict
        val ruleSet = rules<Person, String> { add(rule) }

        val passResult = ruleSet.evaluate(Person("Alice", 150))
        assertIs<Verdict.Pass>(passResult)

        val failResult = ruleSet.evaluate(Person("Bob", 50))
        assertIs<Verdict.Fail<String>>(failResult)
        assertEquals("Score too low", failResult.failures[0].reason)
    }

    @Test
    fun asyncRuleSideEffectIsCalledOnPass() = runTest {
        var called = false
        var capturedPassed: Boolean? = null

        val rule = rule<Person, String>("async-check") {
            asyncCondition { it.score >= 100 }
        }.sideEffect { fact, passed ->
            called = true
            capturedPassed = passed
        }

        // Use RuleSet to evaluate async rules
        val ruleSet = rules<Person, String> { add(rule) }
        ruleSet.evaluateAsync(Person("Alice", 150))

        assertTrue(called, "Side effect should be called")
        assertEquals(true, capturedPassed)
    }

    @Test
    fun asyncRuleSideEffectIsCalledOnFail() = runTest {
        var called = false
        var capturedPassed: Boolean? = null

        val rule = rule<Person, String>("async-check") {
            asyncCondition { it.score >= 100 }
        }.sideEffect { fact, passed ->
            called = true
            capturedPassed = passed
        }

        // Use RuleSet to evaluate async rules
        val ruleSet = rules<Person, String> { add(rule) }
        ruleSet.evaluateAsync(Person("Bob", 50))

        assertTrue(called, "Side effect should be called")
        assertEquals(false, capturedPassed)
    }

    // ========================================================================
    // RuleSet.sideEffect tests
    // ========================================================================

    @Test
    fun ruleSetSideEffectIsCalledOnPass() {
        var called = false
        var capturedVerdict: Verdict<String>? = null

        val ruleSet = rules<Person, String> {
            rule("score-check") {
                condition { it.score >= 100 }
            }
        }.sideEffect { fact, verdict ->
            called = true
            capturedVerdict = verdict
        }

        ruleSet.evaluate(Person("Alice", 150))

        assertTrue(called, "Side effect should be called")
        assertIs<Verdict.Pass>(capturedVerdict)
    }

    @Test
    fun ruleSetSideEffectIsCalledOnFail() {
        var called = false
        var capturedVerdict: Verdict<String>? = null

        val ruleSet = rules<Person, String> {
            rule("score-check") {
                condition { it.score >= 100 }
            }
        }.sideEffect { fact, verdict ->
            called = true
            capturedVerdict = verdict
        }

        ruleSet.evaluate(Person("Bob", 50))

        assertTrue(called, "Side effect should be called")
        assertIs<Verdict.Fail<String>>(capturedVerdict)
    }

    @Test
    fun ruleSetSideEffectReceivesFact() {
        var capturedFact: Person? = null

        val ruleSet = rules<Person, String> {
            rule("score-check") {
                condition { it.score >= 100 }
            }
        }.sideEffect { fact, verdict ->
            capturedFact = fact
        }

        val person = Person("Alice", 150)
        ruleSet.evaluate(person)

        assertEquals(person, capturedFact)
    }

    @Test
    fun multipleRuleSetSideEffectsAreChained() {
        val calls = mutableListOf<String>()

        val ruleSet = rules<Person, String> {
            rule("score-check") {
                condition { it.score >= 100 }
            }
        }.sideEffect { fact, verdict ->
            calls.add("first")
        }.sideEffect { fact, verdict ->
            calls.add("second")
        }

        ruleSet.evaluate(Person("Alice", 150))

        assertEquals(listOf("first", "second"), calls)
    }

    @Test
    fun ruleSetSideEffectDoesNotAffectResult() {
        val ruleSet = rules<Person, String> {
            rule("score-check") {
                condition { it.score >= 100 }
                onFailure("Score too low")
            }
        }.sideEffect { fact, verdict ->
            // Side effect does nothing to result
        }

        val passResult = ruleSet.evaluate(Person("Alice", 150))
        assertIs<Verdict.Pass>(passResult)

        val failResult = ruleSet.evaluate(Person("Bob", 50))
        assertIs<Verdict.Fail<String>>(failResult)
        assertEquals("Score too low", failResult.failures[0].reason)
    }

    @Test
    fun asyncRuleSetSideEffectIsCalled() = runTest {
        var called = false
        var capturedVerdict: Verdict<String>? = null

        val ruleSet = rules<Person, String> {
            rule("async-check") {
                asyncCondition { it.score >= 100 }
            }
        }.sideEffect { fact, verdict ->
            called = true
            capturedVerdict = verdict
        }

        ruleSet.evaluateAsync(Person("Alice", 150))

        assertTrue(called, "Side effect should be called")
        assertIs<Verdict.Pass>(capturedVerdict)
    }

    @Test
    fun asyncRuleSetSideEffectIsCalledOnFail() = runTest {
        var called = false
        var capturedVerdict: Verdict<String>? = null

        val ruleSet = rules<Person, String> {
            rule("async-check") {
                asyncCondition { it.score >= 100 }
            }
        }.sideEffect { fact, verdict ->
            called = true
            capturedVerdict = verdict
        }

        ruleSet.evaluateAsync(Person("Bob", 50))

        assertTrue(called, "Side effect should be called")
        assertIs<Verdict.Fail<String>>(capturedVerdict)
    }

    @Test
    fun ruleSetSideEffectHasAccessToNames() {
        var capturedNames: List<String>? = null

        val ruleSet = rules<Person, String> {
            rule("score-check") {
                condition { it.score >= 100 }
            }
            rule("name-check") {
                condition { it.name.isNotBlank() }
            }
        }.sideEffect { fact, verdict ->
            capturedNames = this.names
        }

        ruleSet.evaluate(Person("Alice", 150))

        assertEquals(listOf("score-check", "name-check"), capturedNames)
    }

    @Test
    fun ruleSetSideEffectCanComputePassedAndFailedRules() {
        var passedRules: Set<String>? = null
        var failedRules: List<String>? = null

        val ruleSet = rules<Person, String> {
            rule("score-check") {
                condition { it.score >= 100 }
            }
            rule("name-check") {
                condition { it.name.isNotBlank() }
            }
        }.sideEffect { fact, verdict ->
            failedRules = if (verdict is Verdict.Fail<String>) verdict.failures.map { it.ruleName } else emptyList()
            passedRules = this.names.toSet() - failedRules!!.toSet()
        }

        // Bob has 50 (fails score-check) but has a name (passes name-check)
        ruleSet.evaluate(Person("Bob", 50))

        assertEquals(setOf("name-check"), passedRules)
        assertEquals(listOf("score-check"), failedRules)
    }
}
