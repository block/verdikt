package verdikt.engine

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class EngineTest {

    @Test
    fun emptyEngineHasNoRules() {
        val engine = engine {}

        assertEquals(0, engine.size)
        assertTrue(engine.factProducerNames.isEmpty())
        assertTrue(engine.validationRuleNames.isEmpty())
        assertFalse(engine.hasAsyncRules)
    }

    @Test
    fun engineTracksFactProducerNames() {
        val engine = engine {
            produce<String, Int>("rule-1") {
                condition { true }
                output { it.length }
            }
            produce<Int, Double>("rule-2") {
                condition { true }
                output { it.toDouble() }
            }
        }

        assertEquals(2, engine.size)
        assertEquals(listOf("rule-1", "rule-2"), engine.factProducerNames)
        assertTrue(engine.validationRuleNames.isEmpty())
    }

    @Test
    fun engineTracksValidationRuleNames() {
        val engine = engine {
            validate<String>("val-1") {
                condition { it.isNotBlank() }
                onFailure { "Cannot be blank" }
            }
            validate<Int>("val-2") {
                condition { it > 0 }
                onFailure { "Must be positive" }
            }
        }

        assertEquals(2, engine.size)
        assertTrue(engine.factProducerNames.isEmpty())
        assertEquals(listOf("val-1", "val-2"), engine.validationRuleNames)
    }

    @Test
    fun engineCanHaveBothFactProducersAndValidationRules() {
        val engine = engine {
            produce<String, Int>("prod") {
                condition { true }
                output { it.length }
            }
            validate<Int>("val") {
                condition { it > 0 }
                onFailure { "Must be positive" }
            }
        }

        assertEquals(2, engine.size)
        assertEquals(listOf("prod"), engine.factProducerNames)
        assertEquals(listOf("val"), engine.validationRuleNames)
    }

    @Test
    fun eachEvaluationIsIndependent() {
        val engine = engine {
            produce<String, Int>("length") {
                condition { true }
                output { it.length }
            }
        }

        val result1 = engine.evaluate(listOf("hello"))
        val result2 = engine.evaluate(listOf("world", "!"))

        // Each evaluation is independent - derived facts don't leak between calls
        assertEquals(setOf(5), result1.derivedOfType<Int>())
        assertEquals(setOf(5, 1), result2.derivedOfType<Int>())
    }
}
