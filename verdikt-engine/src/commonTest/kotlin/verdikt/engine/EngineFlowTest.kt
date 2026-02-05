package verdikt.engine

import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class EngineFlowTest {

    data class TestFact(val value: Int)
    data class DerivedFact(val doubled: Int)

    @Test
    fun evaluateAsFlowEmitsEvents() = runTest {
        val engine = engine {
            produce<TestFact, DerivedFact>("double") {
                condition { true }
                output { DerivedFact(it.value * 2) }
            }
        }

        val events = engine.evaluateAsFlow(listOf(TestFact(5))).toList()

        // Should have: FactInserted(initial), FactInserted(derived), RuleFired, Completed
        assertTrue(events.any { it is EngineEvent.FactInserted && !it.isDerived })
        assertTrue(events.any { it is EngineEvent.FactInserted && it.isDerived })
        assertTrue(events.any { it is EngineEvent.RuleFired })
        assertTrue(events.any { it is EngineEvent.Completed })
    }

    @Test
    fun evaluateAsFlowEndsWithCompleted() = runTest {
        val engine = engine {
            validate<TestFact>("positive") {
                condition { it.value > 0 }
                onFailure { "Must be positive" }
            }
        }

        val events = engine.evaluateAsFlow(listOf(TestFact(1))).toList()

        assertTrue(events.isNotEmpty())
        assertTrue(events.last() is EngineEvent.Completed)
    }

    @Test
    fun evaluateAsFlowReturnsResultInCompletedEvent() = runTest {
        val engine = engine {
            produce<TestFact, DerivedFact>("double") {
                condition { true }
                output { DerivedFact(it.value * 2) }
            }
        }

        val events = engine.evaluateAsFlow(listOf(TestFact(5))).toList()
        val completed = events.filterIsInstance<EngineEvent.Completed>().single()

        assertTrue(completed.result.passed)
        assertEquals(1, completed.result.derivedOfType<DerivedFact>().size)
        assertEquals(DerivedFact(10), completed.result.derivedOfType<DerivedFact>().first())
    }

    @Test
    fun evaluateAsyncAsFlowEmitsEvents() = runTest {
        val engine = engine {
            produce<TestFact, DerivedFact>("async-double") {
                asyncCondition { true }
                asyncOutput { DerivedFact(it.value * 2) }
            }
        }

        val events = engine.evaluateAsyncAsFlow(listOf(TestFact(5))).toList()

        // Should have: FactInserted(initial), FactInserted(derived), RuleFired, Completed
        assertTrue(events.any { it is EngineEvent.FactInserted && !it.isDerived })
        assertTrue(events.any { it is EngineEvent.FactInserted && it.isDerived })
        assertTrue(events.any { it is EngineEvent.RuleFired })
        assertTrue(events.any { it is EngineEvent.Completed })

        val completed = events.filterIsInstance<EngineEvent.Completed>().single()
        assertTrue(completed.result.passed)
        assertEquals(DerivedFact(10), completed.result.derivedOfType<DerivedFact>().first())
    }
}
