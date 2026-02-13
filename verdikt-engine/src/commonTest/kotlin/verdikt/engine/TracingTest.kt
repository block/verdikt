package verdikt.engine

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class TracingTest {

    data class Customer(val id: String, val spend: Double)
    data class VipStatus(val customerId: String, val tier: String)
    data class Discount(val customerId: String, val percent: Int)

    @Test
    fun tracingDisabledByDefaultProducesEmptyTrace() {
        val engine = engine {
            produce<Customer, VipStatus>("vip-check") {
                condition { it.spend > 10_000 }
                output { VipStatus(it.id, "gold") }
            }
        }

        val result = engine.evaluate(listOf(Customer("1", 15000.0)))

        assertTrue(result.trace.isEmpty())
        // Rule should still fire normally
        assertEquals(1, result.derived.size)
    }

    @Test
    fun tracingEnabledCollectsTraceEntries() {
        val engine = engine(EngineConfig(enableTracing = true)) {
            produce<Customer, VipStatus>("vip-check") {
                condition { it.spend > 10_000 }
                output { VipStatus(it.id, "gold") }
            }
        }

        val result = engine.evaluate(listOf(Customer("1", 15000.0)))

        assertTrue(result.trace.isNotEmpty())
        assertEquals(1, result.trace.size)
    }

    @Test
    fun traceEntryContainsCorrectRuleName() {
        val engine = engine(EngineConfig(enableTracing = true)) {
            produce<Customer, VipStatus>("vip-check") {
                condition { it.spend > 10_000 }
                output { VipStatus(it.id, "gold") }
            }
        }

        val result = engine.evaluate(listOf(Customer("1", 15000.0)))
        val entry = result.trace.first()

        assertEquals("vip-check", entry.ruleName)
    }

    @Test
    fun traceEntryContainsInputFact() {
        val engine = engine(EngineConfig(enableTracing = true)) {
            produce<Customer, VipStatus>("vip-check") {
                condition { it.spend > 10_000 }
                output { VipStatus(it.id, "gold") }
            }
        }

        val customer = Customer("1", 15000.0)
        val result = engine.evaluate(listOf(customer))
        val entry = result.trace.first()

        assertEquals(customer, entry.inputFact)
    }

    @Test
    fun traceEntryContainsOutputFacts() {
        val engine = engine(EngineConfig(enableTracing = true)) {
            produce<Customer, VipStatus>("vip-check") {
                condition { it.spend > 10_000 }
                output { VipStatus(it.id, "gold") }
            }
        }

        val result = engine.evaluate(listOf(Customer("1", 15000.0)))
        val entry = result.trace.first()

        assertEquals(1, entry.outputFacts.size)
        assertEquals(VipStatus("1", "gold"), entry.outputFacts.first())
    }

    @Test
    fun traceEntryContainsPriority() {
        val engine = engine(EngineConfig(enableTracing = true)) {
            produce<Customer, VipStatus>("vip-check") {
                priority = 42
                condition { it.spend > 10_000 }
                output { VipStatus(it.id, "gold") }
            }
        }

        val result = engine.evaluate(listOf(Customer("1", 15000.0)))
        val entry = result.trace.first()

        assertEquals(42, entry.priority)
    }

    @Test
    fun tracingCapturesMultipleRuleFirings() {
        val engine = engine(EngineConfig(enableTracing = true)) {
            produce<Customer, VipStatus>("vip-check") {
                condition { it.spend > 10_000 }
                output { VipStatus(it.id, "gold") }
            }
        }

        val result = engine.evaluate(listOf(
            Customer("1", 15000.0),
            Customer("2", 20000.0)
        ))

        assertEquals(2, result.trace.size)
        val ruleNames = result.trace.map { it.ruleName }.toSet()
        assertEquals(setOf("vip-check"), ruleNames)
    }

    @Test
    fun tracingCapturesChainedRuleFirings() {
        val engine = engine(EngineConfig(enableTracing = true)) {
            produce<Customer, VipStatus>("vip-check") {
                condition { it.spend > 10_000 }
                output { VipStatus(it.id, "gold") }
            }
            produce<VipStatus, Discount>("vip-discount") {
                condition { true }
                output { Discount(it.customerId, 20) }
            }
        }

        val result = engine.evaluate(listOf(Customer("1", 15000.0)))

        assertEquals(2, result.trace.size)

        val ruleNames = result.trace.map { it.ruleName }
        assertTrue("vip-check" in ruleNames)
        assertTrue("vip-discount" in ruleNames)
    }

    @Test
    fun traceIsEmptyWhenNoRulesFired() {
        val engine = engine(EngineConfig(enableTracing = true)) {
            produce<Customer, VipStatus>("vip-check") {
                condition { it.spend > 10_000 }
                output { VipStatus(it.id, "gold") }
            }
        }

        // Low spender will not trigger any rules
        val result = engine.evaluate(listOf(Customer("1", 500.0)))

        assertTrue(result.trace.isEmpty())
        assertTrue(result.derived.isEmpty())
    }

    @Test
    fun tracingDoesNotAffectEngineResults() {
        val engineWithTracing = engine(EngineConfig(enableTracing = true)) {
            produce<Customer, VipStatus>("vip-check") {
                condition { it.spend > 10_000 }
                output { VipStatus(it.id, "gold") }
            }
        }

        val engineWithoutTracing = engine(EngineConfig(enableTracing = false)) {
            produce<Customer, VipStatus>("vip-check") {
                condition { it.spend > 10_000 }
                output { VipStatus(it.id, "gold") }
            }
        }

        val facts = listOf(Customer("1", 15000.0), Customer("2", 5000.0))

        val resultWith = engineWithTracing.evaluate(facts)
        val resultWithout = engineWithoutTracing.evaluate(facts)

        // Same derived facts regardless of tracing
        assertEquals(resultWith.derived, resultWithout.derived)
        assertEquals(resultWith.ruleActivations, resultWithout.ruleActivations)

        // But only tracing-enabled has trace entries
        assertTrue(resultWith.trace.isNotEmpty())
        assertTrue(resultWithout.trace.isEmpty())
    }

    @Test
    fun multipleEvaluationsWithTracingAreIndependent() {
        val engine = engine(EngineConfig(enableTracing = true)) {
            produce<Customer, VipStatus>("vip-check") {
                condition { it.spend > 10_000 }
                output { VipStatus(it.id, "gold") }
            }
        }

        val result1 = engine.evaluate(listOf(Customer("1", 15000.0)))
        val result2 = engine.evaluate(listOf(Customer("2", 20000.0)))

        // Each result should have its own trace, not accumulated
        assertEquals(1, result1.trace.size)
        assertEquals(1, result2.trace.size)
        assertEquals(Customer("1", 15000.0), result1.trace.first().inputFact)
        assertEquals(Customer("2", 20000.0), result2.trace.first().inputFact)
    }
}
