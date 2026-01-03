package verdikt.engine

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class EngineTest {

    @Test
    fun emptyEngineHasNoRules() {
        val engine = engine<String> {}

        assertEquals(0, engine.size)
        assertTrue(engine.productionRuleNames.isEmpty())
        assertTrue(engine.validationRuleNames.isEmpty())
        assertFalse(engine.hasAsyncRules)
    }

    @Test
    fun engineTracksProductionRuleNames() {
        val engine = engine<String> {
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
        assertEquals(listOf("rule-1", "rule-2"), engine.productionRuleNames)
        assertTrue(engine.validationRuleNames.isEmpty())
    }

    @Test
    fun engineTracksValidationRuleNames() {
        val engine = engine<String> {
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
        assertTrue(engine.productionRuleNames.isEmpty())
        assertEquals(listOf("val-1", "val-2"), engine.validationRuleNames)
    }

    @Test
    fun engineCanHaveBothProductionAndValidationRules() {
        val engine = engine<String> {
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
        assertEquals(listOf("prod"), engine.productionRuleNames)
        assertEquals(listOf("val"), engine.validationRuleNames)
    }

    @Test
    fun eachSessionIsIndependent() {
        val engine = engine<String> {
            produce<String, Int>("length") {
                condition { true }
                output { it.length }
            }
        }

        val session1 = engine.session()
        session1.insert("hello")

        val session2 = engine.session()
        session2.insert("world", "!")

        assertEquals(setOf("hello"), session1.getAllFacts())
        assertEquals(setOf("world", "!"), session2.getAllFacts())
    }
}
