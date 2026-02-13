package verdikt.engine

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class MaxIterationsTest {

    // A fact type that chains back into itself to cause runaway execution
    data class Counter(val value: Int)

    @Test
    fun runawayRuleChainTriggersMaxIterationsExceededException() {
        // Create an engine where a rule produces a new Counter each time,
        // causing infinite forward chaining
        val engine = engine(EngineConfig(maxIterations = 50)) {
            produce<Counter, Counter>("increment") {
                condition { it.value < 1000 }
                output { Counter(it.value + 1) }
            }
        }

        val exception = assertFailsWith<MaxIterationsExceededException> {
            engine.evaluate(listOf(Counter(0)))
        }

        assertEquals(50, exception.maxIterations)
        assertTrue(exception.iterations > 50)
        assertTrue(exception.message!!.contains("maximum iterations"))
    }

    @Test
    fun customMaxIterationsValueIsRespected() {
        val engine = engine(EngineConfig(maxIterations = 10)) {
            produce<Counter, Counter>("increment") {
                condition { it.value < 1000 }
                output { Counter(it.value + 1) }
            }
        }

        val exception = assertFailsWith<MaxIterationsExceededException> {
            engine.evaluate(listOf(Counter(0)))
        }

        assertEquals(10, exception.maxIterations)
    }

    @Test
    fun largeMaxIterationsAllowsDeepChains() {
        // A chain that terminates after exactly 20 derivations (Counter 1 through 20)
        val engine = engine(EngineConfig(maxIterations = 1000)) {
            produce<Counter, Counter>("increment") {
                condition { it.value < 20 }
                output { Counter(it.value + 1) }
            }
        }

        val result = engine.evaluate(listOf(Counter(0)))

        // Should complete successfully (20 new Counter facts)
        assertEquals(20, result.derived.size)
        // All Counter values from 1 to 20 should be present
        val values = result.derivedOfType<Counter>().map { it.value }.toSet()
        assertEquals((1..20).toSet(), values)
    }

    @Test
    fun normalExecutionDoesNotTriggerMaxIterations() {
        val engine = engine(EngineConfig(maxIterations = 100)) {
            produce<String, Int>("length") {
                condition { true }
                output { it.length }
            }
        }

        val result = engine.evaluate(listOf("hi", "world"))

        // Should complete without hitting iteration limit ("hi"->2, "world"->5 = 2 distinct ints)
        assertEquals(2, result.derived.size)
        assertTrue(result.iterations < 100)
    }

    @Test
    fun maxIterationsConfigRequiresPositiveValue() {
        assertFailsWith<IllegalArgumentException> {
            EngineConfig(maxIterations = 0)
        }

        assertFailsWith<IllegalArgumentException> {
            EngineConfig(maxIterations = -1)
        }
    }

    @Test
    fun defaultMaxIterationsIsHighEnough() {
        assertEquals(1_000_000, EngineConfig.DEFAULT_MAX_ITERATIONS)
        assertEquals(1_000_000, EngineConfig.DEFAULT.maxIterations)
    }

    @Test
    fun maxIterationsExceptionContainsDescriptiveMessage() {
        val exception = MaxIterationsExceededException(iterations = 101, maxIterations = 100)

        assertTrue(exception.message!!.contains("100"))
        assertTrue(exception.message!!.contains("maximum iterations"))
        assertTrue(exception.message!!.contains("runaway"))
        assertEquals(101, exception.iterations)
        assertEquals(100, exception.maxIterations)
    }
}
