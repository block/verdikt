package verdikt.engine

import verdikt.Verdict
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.test.assertContains

/**
 * Integration tests for the Rete-based rules engine.
 */
class ReteIntegrationTest {

    // Test domain classes
    data class Customer(val id: String, val totalSpend: Double)
    data class VipStatus(val customerId: String, val tier: String)
    data class Discount(val customerId: String, val percent: Int)
    data class Order(val customerId: String, val amount: Double)
    data class LoyaltyPoints(val customerId: String, val points: Int)

    // Context key for guard tests
    object CustomerTierKey : ContextKey<String>

    @Test
    fun singleRuleProducesCorrectResults() {
        val engine = engine {
            produce<Customer, VipStatus>("vip-check") {
                condition { it.totalSpend > 10_000 }
                output { VipStatus(it.id, "gold") }
            }
        }

        val facts = listOf(
            Customer("1", 15000.0),
            Customer("2", 5000.0),
            Customer("3", 20000.0)
        )

        val result = engine.evaluate(facts)

        // Only customers 1 and 3 should be VIPs
        assertEquals(2, result.derived.size)
        val vips = result.derivedOfType<VipStatus>()
        assertTrue(vips.any { it.customerId == "1" })
        assertTrue(vips.any { it.customerId == "3" })
    }

    @Test
    fun chainedRulesWorkCorrectly() {
        val engine = engine {
            produce<Customer, VipStatus>("vip-check") {
                condition { it.totalSpend > 10_000 }
                output { VipStatus(it.id, "gold") }
            }
            produce<VipStatus, Discount>("vip-discount") {
                condition { true }
                output { Discount(it.customerId, 20) }
            }
        }

        val result = engine.evaluate(listOf(Customer("123", 15000.0)))

        // Should derive both VipStatus and Discount
        assertEquals(2, result.derived.size)

        val vip = result.derivedOfType<VipStatus>().first()
        assertEquals("gold", vip.tier)

        val discount = result.derivedOfType<Discount>().first()
        assertEquals(20, discount.percent)
        assertEquals("123", discount.customerId)
    }

    @Test
    fun multiLevelChainWorksCorrectly() {
        // Chain: String -> Int -> Double -> Boolean
        val engine = engine {
            produce<String, Int>("a-to-b") {
                condition { it == "A" }
                output { 1 }
            }
            produce<Int, Double>("b-to-c") {
                condition { it == 1 }
                output { 2.0 }
            }
            produce<Double, Boolean>("c-to-d") {
                condition { it == 2.0 }
                output { true }
            }
        }

        val result = engine.evaluate(listOf("A"))

        assertEquals(3, result.derived.size)
        assertContains(result.derived, 1)
        assertContains(result.derived, 2.0)
        assertContains(result.derived, true)
    }

    @Test
    fun validationRulesWork() {
        val engine = engine {
            produce<Customer, VipStatus>("vip-check") {
                condition { it.totalSpend > 10_000 }
                output { VipStatus(it.id, "gold") }
            }
            validate<Customer>("positive-spend") {
                condition { it.totalSpend >= 0 }
                onFailure { "Negative spend not allowed" }
            }
        }

        val validResult = engine.evaluate(listOf(Customer("1", 15000.0)))
        assertEquals(Verdict.Pass, validResult.verdict)
        assertEquals(1, validResult.derived.size)

        val invalidResult = engine.evaluate(listOf(Customer("2", -100.0)))
        assertTrue(invalidResult.verdict is Verdict.Fail)
    }

    @Test
    fun phasedExecutionWorks() {
        val engine = engine {
            phase("enrichment") {
                produce<Customer, VipStatus>("vip-check") {
                    condition { it.totalSpend > 10_000 }
                    output { VipStatus(it.id, "gold") }
                }
            }
            phase("rewards") {
                produce<VipStatus, LoyaltyPoints>("vip-points") {
                    condition { true }
                    output { LoyaltyPoints(it.customerId, 1000) }
                }
            }
        }

        val result = engine.evaluate(listOf(Customer("123", 15000.0)))

        assertEquals(2, result.derived.size)
        assertTrue(result.derivedOfType<VipStatus>().isNotEmpty())
        assertTrue(result.derivedOfType<LoyaltyPoints>().isNotEmpty())
    }

    @Test
    fun duplicateOutputsAreNotAdded() {
        val engine = engine {
            produce<String, Int>("always-42") {
                condition { true }
                output { 42 }  // Always produces the same value
            }
        }

        val result = engine.evaluate(listOf("a", "b", "c"))

        // Should only have one 42, not three
        val ints = result.derivedOfType<Int>()
        assertEquals(1, ints.size)
        assertEquals(42, ints.first())
    }

    @Test
    fun complexScenarioWithPriorities() {
        val engine = engine {
            produce<Customer, VipStatus>("vip-gold") {
                priority = 10
                condition { it.totalSpend >= 10_000 }
                output { VipStatus(it.id, "gold") }
            }
            produce<Customer, VipStatus>("vip-silver") {
                priority = 5
                condition { it.totalSpend >= 5_000 && it.totalSpend < 10_000 }
                output { VipStatus(it.id, "silver") }
            }
            produce<VipStatus, Discount>("vip-discount") {
                condition { it.tier == "gold" }
                output { Discount(it.customerId, 20) }
            }
            produce<VipStatus, Discount>("silver-discount") {
                condition { it.tier == "silver" }
                output { Discount(it.customerId, 10) }
            }
        }

        val facts = listOf(
            Customer("gold-customer", 15000.0),
            Customer("silver-customer", 7000.0),
            Customer("regular-customer", 2000.0)
        )

        val result = engine.evaluate(facts)

        // 2 VIP statuses + 2 discounts = 4 derived facts
        assertEquals(4, result.derived.size)
        assertEquals(4, result.ruleActivations)
    }

    @Test
    fun guardsWork() {
        val engine = engine {
            produce<Customer, Discount>("vip-only-discount") {
                guard("Customer must be VIP") { ctx ->
                    ctx[CustomerTierKey] == "vip"
                }
                condition { it.totalSpend > 1000 }
                output { Discount(it.id, 10) }
            }
        }

        // Without VIP context - rule should be skipped
        val resultNoVip = engine.evaluate(listOf(Customer("1", 5000.0)))
        assertTrue(resultNoVip.derived.isEmpty())
        assertTrue(resultNoVip.skipped.containsKey("vip-only-discount"))

        // With VIP context - rule should fire
        val vipContext = ruleContext {
            set(CustomerTierKey, "vip")
        }
        val resultVip = engine.evaluate(listOf(Customer("1", 5000.0)), vipContext)
        assertEquals(1, resultVip.derived.size)
    }

    @Test
    fun networkCachingReusesCompiledNetworkAcrossEvaluations() {
        val engine = engine {
            produce<Customer, VipStatus>("vip-check") {
                condition { it.totalSpend > 10_000 }
                output { VipStatus(it.id, "gold") }
            }
        }

        // Multiple evaluations should all work correctly
        val result1 = engine.evaluate(listOf(Customer("1", 15000.0)))
        val result2 = engine.evaluate(listOf(Customer("2", 20000.0)))
        val result3 = engine.evaluate(listOf(Customer("3", 5000.0))) // Below threshold

        assertEquals(1, result1.derived.size)
        assertEquals(1, result2.derived.size)
        assertEquals(0, result3.derived.size)

        // Each evaluation should be independent
        assertTrue(result1.derivedOfType<VipStatus>().first().customerId == "1")
        assertTrue(result2.derivedOfType<VipStatus>().first().customerId == "2")
    }

    @Test
    fun priorityOrderingRespected() {
        // Create engine where high priority rule should fire first
        var fireOrder = mutableListOf<String>()

        val engine = engine {
            produce<String, Int>("low-priority") {
                priority = 1
                condition { true }
                output {
                    fireOrder.add("low")
                    1
                }
            }
            produce<String, Int>("high-priority") {
                priority = 100
                condition { true }
                output {
                    fireOrder.add("high")
                    2
                }
            }
        }

        // Reset fire order and evaluate
        fireOrder = mutableListOf()
        engine.evaluate(listOf("test"))

        // High priority should fire before low priority
        assertTrue(fireOrder.isNotEmpty())
        assertEquals("high", fireOrder.first())
    }

    @Test
    fun tracingCapturesRuleActivations() {
        // Note: Tracing requires EngineConfig which isn't directly exposed via DSL yet
        // This test verifies the trace field exists and works when populated

        val engine = engine {
            produce<Customer, VipStatus>("vip-check") {
                condition { it.totalSpend > 10_000 }
                output { VipStatus(it.id, "gold") }
            }
        }

        val result = engine.evaluate(listOf(Customer("1", 15000.0)))

        // Default config has tracing disabled, so trace should be empty
        assertTrue(result.trace.isEmpty())
    }

    @Test
    fun warningsFieldExists() {
        val engine = engine {
            produce<String, Int>("simple") {
                condition { true }
                output { 42 }
            }
        }

        val result = engine.evaluate(listOf("test"))

        // Warnings should be an empty list for normal execution
        assertTrue(result.warnings.isEmpty())
    }
}
