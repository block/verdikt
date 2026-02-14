package verdikt.engine

import verdikt.Verdict
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertIs
import kotlin.test.assertTrue

/**
 * Tests for error paths, builder validation, and guard behavior on validation rules.
 */
class EngineErrorPathTest {

    // -- Builder validation tests --

    @Test
    fun maxIterationsOfOneEdgeCase() {
        // maxIterations = 1 means only 1 iteration allowed
        val engine = engine(EngineConfig(maxIterations = 1)) {
            produce<String, Int>("length") {
                condition { true }
                output { it.length }
            }
        }

        // A single-step rule should succeed within 1 iteration
        val result = engine.evaluate(listOf("hello"))
        assertEquals(1, result.derived.size)
    }

    @Test
    fun emptyEngineEvaluationProducesNoResults() {
        val engine = engine {}

        val result = engine.evaluate(emptyList())

        assertTrue(result.facts.isEmpty())
        assertTrue(result.derived.isEmpty())
        assertIs<Verdict.Pass>(result.verdict)
        assertEquals(0, result.ruleActivations)
    }

    @Test
    fun emptyEngineWithFactsStillPasses() {
        val engine = engine {}

        val result = engine.evaluate(listOf("hello", 42))

        assertEquals(2, result.facts.size)
        assertTrue(result.derived.isEmpty())
        assertIs<Verdict.Pass>(result.verdict)
    }

    @Test
    fun factProducerRequiresCondition() {
        assertFailsWith<IllegalArgumentException> {
            engine {
                produce<String, Int>("missing-condition") {
                    output { it.length }
                }
            }
        }
    }

    @Test
    fun factProducerRequiresOutput() {
        assertFailsWith<IllegalArgumentException> {
            engine {
                produce<String, Int>("missing-output") {
                    condition { true }
                }
            }
        }
    }

    @Test
    fun validationRuleRequiresCondition() {
        assertFailsWith<IllegalArgumentException> {
            engine {
                validate<String>("missing-condition") {
                    onFailure { "fail" }
                }
            }
        }
    }

    @Test
    fun cannotSetBothConditionAndAsyncCondition() {
        assertFailsWith<IllegalArgumentException> {
            engine {
                produce<String, Int>("dual-condition") {
                    condition { true }
                    asyncCondition { true }
                    output { it.length }
                }
            }
        }
    }

    @Test
    fun cannotSetBothOutputAndAsyncOutput() {
        assertFailsWith<IllegalArgumentException> {
            engine {
                produce<String, Int>("dual-output") {
                    condition { true }
                    output { it.length }
                    asyncOutput { it.length }
                }
            }
        }
    }

    // -- Guard on validation rules tests --

    object PremiumKey : ContextKey<Boolean>

    @Test
    fun guardOnValidationRuleSkipsWhenGuardFails() {
        val engine = engine {
            validate<String>("premium-only-check") {
                guard("Requires premium") { ctx -> ctx[PremiumKey] == true }
                condition { it.length > 3 }
                onFailure { "String too short: $it" }
            }
        }

        // Without premium context - validation rule should be skipped
        val result = engine.evaluate(listOf("hi"))

        assertIs<Verdict.Pass>(result.verdict) // Skipped means no failure
        assertTrue(result.skipped.containsKey("premium-only-check"))
        assertEquals("Requires premium", result.skipped["premium-only-check"])
    }

    @Test
    fun guardOnValidationRuleFiresWhenGuardPasses() {
        val engine = engine {
            validate<String>("premium-only-check") {
                guard("Requires premium") { ctx -> ctx[PremiumKey] == true }
                condition { it.length > 3 }
                onFailure { "String too short: $it" }
            }
        }

        val premiumContext = ruleContext {
            set(PremiumKey, true)
        }

        // "hi" has length 2, condition requires > 3, should fail
        val result = engine.evaluate(listOf("hi"), premiumContext)

        assertTrue(result.failed)
        assertTrue(result.skipped.isEmpty())
    }

    @Test
    fun guardOnValidationRulePassesWhenConditionMet() {
        val engine = engine {
            validate<String>("premium-only-check") {
                guard("Requires premium") { ctx -> ctx[PremiumKey] == true }
                condition { it.length > 3 }
                onFailure { "String too short: $it" }
            }
        }

        val premiumContext = ruleContext {
            set(PremiumKey, true)
        }

        // "hello" has length 5, condition requires > 3, should pass
        val result = engine.evaluate(listOf("hello"), premiumContext)

        assertTrue(result.passed)
        assertTrue(result.skipped.isEmpty())
    }

    @Test
    fun guardOnProductionRuleSkipsAndDoesNotProduce() {
        val engine = engine {
            produce<String, Int>("premium-length") {
                guard("Requires premium") { ctx -> ctx[PremiumKey] == true }
                condition { true }
                output { it.length }
            }
        }

        val result = engine.evaluate(listOf("hello"))

        assertTrue(result.derived.isEmpty())
        assertTrue(result.skipped.containsKey("premium-length"))
    }

    @Test
    fun mixedGuardedAndUnguardedValidationRules() {
        val engine = engine {
            validate<Int>("always-positive") {
                condition { it > 0 }
                onFailure { "Must be positive" }
            }
            validate<Int>("premium-even-check") {
                guard("Requires premium") { ctx -> ctx[PremiumKey] == true }
                condition { it % 2 == 0 }
                onFailure { "Must be even (premium)" }
            }
        }

        // Without premium: only always-positive runs
        val result = engine.evaluate(listOf(-1))

        assertTrue(result.failed)
        val failures = (result.verdict as Verdict.Fail).failures
        assertEquals(1, failures.size)
        assertEquals("always-positive", failures[0].ruleName)
        assertTrue(result.skipped.containsKey("premium-even-check"))
    }

    @Test
    fun guardOnValidationRuleMultipleFactsAllEvaluated() {
        val engine = engine {
            validate<String>("premium-length-check") {
                guard("Requires premium") { ctx -> ctx[PremiumKey] == true }
                condition { it.length > 2 }
                onFailure { "Too short: $it" }
            }
        }

        val premiumContext = ruleContext {
            set(PremiumKey, true)
        }

        // "hi" fails (length 2), "abc" passes (length 3), "x" fails (length 1)
        val result = engine.evaluate(listOf("hi", "abc", "x"), premiumContext)

        assertTrue(result.failed)
        val failures = (result.verdict as Verdict.Fail).failures
        assertEquals(2, failures.size)
    }

    @Test
    fun guardOnValidationRuleWithContextSwitch() {
        val engine = engine {
            validate<String>("guarded-rule") {
                guard("Requires premium") { ctx -> ctx[PremiumKey] == true }
                condition { it.isNotEmpty() }
                onFailure { "Empty string" }
            }
        }

        // First eval: no premium, rule skipped
        val result1 = engine.evaluate(listOf(""))
        assertTrue(result1.passed) // Skipped, so passes
        assertTrue(result1.skipped.containsKey("guarded-rule"))

        // Second eval: with premium, rule fires and fails on empty string
        val premiumContext = ruleContext { set(PremiumKey, true) }
        val result2 = engine.evaluate(listOf(""), premiumContext)
        assertTrue(result2.failed)
        assertTrue(result2.skipped.isEmpty())
    }
}
