package verdikt.benchmark

import kotlinx.benchmark.*
import verdikt.engine.Engine
import verdikt.engine.engine

/**
 * Benchmarks type filtering performance with varying fact counts.
 *
 * Each rule compiles to a RETE alpha node that receives only facts matching
 * its input type. This measures the cost of alpha node routing — inserting
 * facts into the network and dispatching them to the correct alpha memories.
 */
@State(Scope.Benchmark)
@BenchmarkMode(Mode.Throughput)
@OutputTimeUnit(BenchmarkTimeUnit.MILLISECONDS)
open class AlphaNodeRoutingBenchmark {

    // Fact types for benchmarking
    data class Customer(val id: String, val totalSpend: Double)
    data class Order(val id: String, val customerId: String, val amount: Double)
    data class Product(val id: String, val name: String, val price: Double)
    data class VipStatus(val customerId: String, val tier: String)
    data class Discount(val customerId: String, val percent: Int)

    @Param("100", "1000", "10000")
    var factCount: Int = 1000

    /** When true, engine is constructed once in setup; when false, constructed per evaluate. */
    @Param("true", "false")
    var reuseEngine: Boolean = true

    private lateinit var mixedFacts: List<Any>
    private lateinit var customerTypeEngine: Engine
    private lateinit var orderTypeEngine: Engine
    private lateinit var multipleTypesEngine: Engine

    private fun buildCustomerTypeEngine() = engine {
        produce<Customer, VipStatus>("vip-check") {
            condition { it.totalSpend > 10_000 }
            output { customer -> VipStatus(customer.id, "gold") }
        }
    }

    private fun buildOrderTypeEngine() = engine {
        produce<Order, Discount>("bulk-discount") {
            condition { it.amount > 500 }
            output { order -> Discount(order.customerId, 10) }
        }
    }

    private fun buildMultipleTypesEngine() = engine {
        produce<Customer, VipStatus>("vip-check") {
            condition { it.totalSpend > 10_000 }
            output { customer -> VipStatus(customer.id, "gold") }
        }
        produce<VipStatus, Discount>("vip-discount") {
            condition { it.tier == "gold" }
            output { vip -> Discount(vip.customerId, 20) }
        }
        produce<Order, Discount>("bulk-discount") {
            condition { it.amount > 500 }
            output { order -> Discount(order.customerId, 10) }
        }
    }

    @Setup
    fun setup() {
        val customers = (1..factCount / 3).map { Customer("c$it", it * 100.0) }
        val orders = (1..factCount / 3).map { Order("o$it", "c${it % (factCount / 3) + 1}", it * 10.0) }
        val products = (1..factCount / 3).map { Product("p$it", "Product $it", it * 5.0) }
        mixedFacts = (customers + orders + products).shuffled()

        customerTypeEngine = buildCustomerTypeEngine()
        orderTypeEngine = buildOrderTypeEngine()
        multipleTypesEngine = buildMultipleTypesEngine()
    }

    @Benchmark
    fun filterByCustomerType(): Int {
        val eng = if (reuseEngine) customerTypeEngine else buildCustomerTypeEngine()
        return eng.evaluate(mixedFacts).derived.size
    }

    @Benchmark
    fun filterByOrderType(): Int {
        val eng = if (reuseEngine) orderTypeEngine else buildOrderTypeEngine()
        return eng.evaluate(mixedFacts).derived.size
    }

    @Benchmark
    fun filterByMultipleTypes(): Int {
        val eng = if (reuseEngine) multipleTypesEngine else buildMultipleTypesEngine()
        return eng.evaluate(mixedFacts).derived.size
    }
}
