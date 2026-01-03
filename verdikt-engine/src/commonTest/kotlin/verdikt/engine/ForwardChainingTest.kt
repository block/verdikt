package verdikt.engine

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.test.assertContains

// Test domain classes
data class Customer(val id: String, val totalSpend: Double)
data class VipStatus(val customerId: String, val tier: String)
data class Discount(val customerId: String, val percent: Int)
data class Order(val customerId: String, val quantity: Int, val unitPrice: Double)
data class OrderTotal(val customerId: String, val total: Double)

class ForwardChainingTest {

    @Test
    fun singleProductionRuleDerivesFact() {
        val engine = engine<String> {
            produce<Customer, VipStatus>("vip-check") {
                condition { it.totalSpend > 10_000 }
                output { VipStatus(it.id, "gold") }
            }
        }

        val session = engine.session()
        session.insert(Customer("123", 15000.0))
        val result = session.fire()

        assertEquals(1, result.derived.size)
        val vip = result.derivedOfType<VipStatus>().first()
        assertEquals("123", vip.customerId)
        assertEquals("gold", vip.tier)
    }

    @Test
    fun productionRuleDoesNotFireWhenConditionIsFalse() {
        val engine = engine<String> {
            produce<Customer, VipStatus>("vip-check") {
                condition { it.totalSpend > 10_000 }
                output { VipStatus(it.id, "gold") }
            }
        }

        val session = engine.session()
        session.insert(Customer("123", 5000.0))
        val result = session.fire()

        assertEquals(0, result.derived.size)
        assertTrue(result.derivedOfType<VipStatus>().isEmpty())
    }

    @Test
    fun ruleChainingDerivedFactTriggersAnotherRule() {
        val engine = engine<String> {
            produce<Customer, VipStatus>("vip-check") {
                condition { it.totalSpend > 10_000 }
                output { VipStatus(it.id, "gold") }
            }
            produce<VipStatus, Discount>("vip-discount") {
                condition { true }
                output { Discount(it.customerId, 20) }
            }
        }

        val session = engine.session()
        session.insert(Customer("123", 15000.0))
        val result = session.fire()

        // Should derive both VipStatus and Discount
        assertEquals(2, result.derived.size)

        val vip = result.derivedOfType<VipStatus>().first()
        assertEquals("gold", vip.tier)

        val discount = result.derivedOfType<Discount>().first()
        assertEquals(20, discount.percent)
        assertEquals("123", discount.customerId)
    }

    @Test
    fun multipleInitialFactsAreAllProcessed() {
        val engine = engine<String> {
            produce<Customer, VipStatus>("vip-check") {
                condition { it.totalSpend > 10_000 }
                output { VipStatus(it.id, "gold") }
            }
        }

        val session = engine.session()
        session.insert(
            Customer("1", 15000.0),
            Customer("2", 20000.0),
            Customer("3", 5000.0)  // Below threshold
        )
        val result = session.fire()

        val vips = result.derivedOfType<VipStatus>()
        assertEquals(2, vips.size)
        assertTrue(vips.any { it.customerId == "1" })
        assertTrue(vips.any { it.customerId == "2" })
    }

    @Test
    fun sameRuleDoesNotFireTwiceForSameFact() {
        var fireCount = 0
        val engine = engine<String> {
            produce<Customer, VipStatus>("vip-check") {
                condition {
                    fireCount++
                    it.totalSpend > 10_000
                }
                output { VipStatus(it.id, "gold") }
            }
        }

        val session = engine.session()
        session.insert(Customer("123", 15000.0))
        session.fire()

        // Rule should only evaluate the customer once
        assertEquals(1, fireCount)
    }

    @Test
    fun multipleIterationsUntilFixpoint() {
        // Chain: A -> B -> C -> D
        val engine = engine<String> {
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

        val session = engine.session()
        session.insert("A")
        val result = session.fire()

        assertEquals(3, result.derived.size)
        assertContains(result.derived, 1)
        assertContains(result.derived, 2.0)
        assertContains(result.derived, true)
        assertTrue(result.iterations > 1)
    }

    @Test
    fun factsContainsBothInitialAndDerived() {
        val engine = engine<String> {
            produce<Customer, VipStatus>("vip-check") {
                condition { it.totalSpend > 10_000 }
                output { VipStatus(it.id, "gold") }
            }
        }

        val session = engine.session()
        val customer = Customer("123", 15000.0)
        session.insert(customer)
        val result = session.fire()

        // facts should contain both initial customer and derived VipStatus
        assertEquals(2, result.facts.size)
        assertContains(result.facts, customer)
        assertTrue(result.factsOfType<VipStatus>().isNotEmpty())
    }

    @Test
    fun duplicateOutputFactsAreNotAdded() {
        val engine = engine<String> {
            produce<String, Int>("always-42") {
                condition { true }
                output { 42 }  // Always produces the same value
            }
        }

        val session = engine.session()
        session.insert("a", "b", "c")
        val result = session.fire()

        // Should only have one 42, not three
        val ints = result.derivedOfType<Int>()
        assertEquals(1, ints.size)
        assertEquals(42, ints.first())
    }

    @Test
    fun rulesOnlyMatchTheirDeclaredInputType() {
        // Using data classes instead of primitives because Kotlin/JS doesn't
        // distinguish Int/Double at runtime (both are JS numbers)
        data class StringWrapper(val value: String)
        data class IntWrapper(val value: Int)
        data class DoubleWrapper(val value: Double)

        val engine = engine<String> {
            produce<StringWrapper, IntWrapper>("string-to-int") {
                condition { true }
                output { IntWrapper(it.value.length) }
            }
            produce<IntWrapper, DoubleWrapper>("int-to-double") {
                condition { true }
                output { DoubleWrapper(it.value.toDouble()) }
            }
        }

        val session = engine.session()
        session.insert(StringWrapper("hello"), IntWrapper(100))
        val result = session.fire()

        // StringWrapper rule should fire for "hello" -> IntWrapper(5)
        // IntWrapper rule should fire for 100 -> DoubleWrapper(100.0) and for 5 -> DoubleWrapper(5.0)
        val ints = result.derivedOfType<IntWrapper>()
        val doubles = result.derivedOfType<DoubleWrapper>()

        assertEquals(1, ints.size)  // "hello".length = 5
        assertEquals(2, doubles.size)  // 100.0 and 5.0
    }

    @Test
    fun insertAllWorksCorrectly() {
        val engine = engine<String> {
            produce<String, Int>("length") {
                condition { true }
                output { it.length }
            }
        }

        val session = engine.session()
        session.insertAll(listOf("a", "bb", "ccc"))
        val result = session.fire()

        val lengths = result.derivedOfType<Int>()
        assertEquals(setOf(1, 2, 3), lengths)
    }

    @Test
    fun ruleActivationsTracksNumberOfRuleFires() {
        val engine = engine<String> {
            produce<String, Int>("length") {
                condition { true }
                output { it.length }
            }
        }

        val session = engine.session()
        session.insert("a", "bb", "ccc")
        val result = session.fire()

        assertEquals(3, result.ruleActivations)
    }
}
