package verdikt.engine

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

data class Item(val name: String, val price: Double)
data class Discount(val itemName: String, val amount: Double)

class EngineEventCollectorTest {

    @Test
    fun collectorReceivesFactInsertedEventsForInitialFacts() {
        val events = mutableListOf<EngineEvent>()

        val engine = engine {
            validate<Item>("has-name") {
                condition { it.name.isNotBlank() }
                onFailure { "Name required" }
            }
        }

        engine.evaluate(listOf(Item("Widget", 10.0))) { event ->
            events.add(event)
        }

        val insertedEvents = events.filterIsInstance<EngineEvent.FactInserted>()
        assertEquals(1, insertedEvents.size)
        assertEquals(Item("Widget", 10.0), insertedEvents[0].fact)
        assertEquals(false, insertedEvents[0].isDerived)
    }

    @Test
    fun collectorReceivesRuleFiredEvents() {
        val events = mutableListOf<EngineEvent>()

        val engine = engine {
            produce<Item, Discount>("discount-rule") {
                condition { it.price > 5.0 }
                output { Discount(it.name, it.price * 0.1) }
            }
        }

        engine.evaluate(listOf(Item("Widget", 10.0))) { event ->
            events.add(event)
        }

        val ruleFiredEvents = events.filterIsInstance<EngineEvent.RuleFired>()
        assertEquals(1, ruleFiredEvents.size)
        assertEquals("discount-rule", ruleFiredEvents[0].ruleName)
        assertEquals(Item("Widget", 10.0), ruleFiredEvents[0].inputFact)
        assertEquals(listOf(Discount("Widget", 1.0)), ruleFiredEvents[0].outputFacts)
    }

    @Test
    fun collectorReceivesFactInsertedEventsForDerivedFacts() {
        val events = mutableListOf<EngineEvent>()

        val engine = engine {
            produce<Item, Discount>("discount-rule") {
                condition { true }
                output { Discount(it.name, it.price * 0.1) }
            }
        }

        engine.evaluate(listOf(Item("Widget", 10.0))) { event ->
            events.add(event)
        }

        val derivedInserts = events.filterIsInstance<EngineEvent.FactInserted>()
            .filter { it.isDerived }
        assertEquals(1, derivedInserts.size)
        assertEquals(Discount("Widget", 1.0), derivedInserts[0].fact)
    }

    @Test
    fun collectorReceivesRuleSkippedEventsForGuards() {
        val events = mutableListOf<EngineEvent>()

        val engine = engine {
            produce<Item, Discount>("guarded-rule") {
                guard("Must be premium mode") { false }
                condition { true }
                output { Discount(it.name, it.price * 0.2) }
            }
        }

        engine.evaluate(listOf(Item("Widget", 10.0))) { event ->
            events.add(event)
        }

        val skippedEvents = events.filterIsInstance<EngineEvent.RuleSkipped>()
        assertEquals(1, skippedEvents.size)
        assertEquals("guarded-rule", skippedEvents[0].ruleName)
        assertEquals("Must be premium mode", skippedEvents[0].guardDescription)
    }

    @Test
    fun collectorReceivesValidationPassedEvents() {
        val events = mutableListOf<EngineEvent>()

        val engine = engine {
            validate<Item>("has-name") {
                condition { it.name.isNotBlank() }
                onFailure { "Name required" }
            }
        }

        engine.evaluate(listOf(Item("Widget", 10.0))) { event ->
            events.add(event)
        }

        val passedEvents = events.filterIsInstance<EngineEvent.ValidationPassed>()
        assertEquals(1, passedEvents.size)
        assertEquals("has-name", passedEvents[0].ruleName)
        assertEquals(Item("Widget", 10.0), passedEvents[0].fact)
    }

    @Test
    fun collectorReceivesValidationFailedEvents() {
        val events = mutableListOf<EngineEvent>()

        val engine = engine {
            validate<Item>("positive-price") {
                condition { it.price > 0 }
                onFailure { item -> "Price ${item.price} must be positive" }
            }
        }

        engine.evaluate(listOf(Item("Widget", -5.0))) { event ->
            events.add(event)
        }

        val failedEvents = events.filterIsInstance<EngineEvent.ValidationFailed>()
        assertEquals(1, failedEvents.size)
        assertEquals("positive-price", failedEvents[0].ruleName)
        assertEquals(Item("Widget", -5.0), failedEvents[0].fact)
        assertEquals("Price -5.0 must be positive", failedEvents[0].reason)
    }

    @Test
    fun collectorReceivesCompletedEvent() {
        val events = mutableListOf<EngineEvent>()

        val engine = engine {
            validate<Item>("has-name") {
                condition { it.name.isNotBlank() }
                onFailure { "Name required" }
            }
        }

        engine.evaluate(listOf(Item("Widget", 10.0))) { event ->
            events.add(event)
        }

        val completedEvents = events.filterIsInstance<EngineEvent.Completed>()
        assertEquals(1, completedEvents.size)
        assertTrue(completedEvents[0].result.passed)
    }

    @Test
    fun completedEventIsAlwaysLast() {
        val events = mutableListOf<EngineEvent>()

        val engine = engine {
            produce<Item, Discount>("discount") {
                condition { true }
                output { Discount(it.name, 1.0) }
            }
            validate<Item>("valid") {
                condition { true }
                onFailure { "never" }
            }
        }

        engine.evaluate(listOf(Item("A", 1.0), Item("B", 2.0))) { event ->
            events.add(event)
        }

        assertTrue(events.isNotEmpty())
        assertTrue(events.last() is EngineEvent.Completed)
    }

    @Test
    fun compositeCollectorDispatchesToAllCollectors() {
        val events1 = mutableListOf<EngineEvent>()
        val events2 = mutableListOf<EngineEvent>()

        val composite = CompositeCollector(
            EngineEventCollector { events1.add(it) },
            EngineEventCollector { events2.add(it) }
        )

        val engine = engine {
            validate<Item>("test") {
                condition { true }
                onFailure { "never" }
            }
        }

        engine.evaluate(listOf(Item("Widget", 10.0)), collector = composite)

        assertEquals(events1, events2)
        assertTrue(events1.isNotEmpty())
    }

    @Test
    fun emptyCollectorDoesNothing() {
        // Should not throw
        val engine = engine {
            produce<Item, Discount>("rule") {
                condition { true }
                output { Discount(it.name, 1.0) }
            }
        }

        val result = engine.evaluate(listOf(Item("Widget", 10.0)))
        assertTrue(result.passed)
    }
}
