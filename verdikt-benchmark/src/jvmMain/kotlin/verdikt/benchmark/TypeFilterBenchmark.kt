package verdikt.benchmark

import kotlinx.benchmark.*
import verdikt.engine.engine

/**
 * Benchmarks type filtering performance with varying fact counts.
 *
 * This tests the core performance bottleneck: filtering working memory by type.
 * With type-based indexing, this should be O(1) instead of O(n).
 */
@State(Scope.Benchmark)
@BenchmarkMode(Mode.Throughput)
@OutputTimeUnit(BenchmarkTimeUnit.MILLISECONDS)
open class TypeFilterBenchmark {

    // Fact types for benchmarking
    data class Customer(val id: String, val totalSpend: Double)
    data class Order(val id: String, val customerId: String, val amount: Double)
    data class Product(val id: String, val name: String, val price: Double)
    data class VipStatus(val customerId: String, val tier: String)
    data class Discount(val customerId: String, val percent: Int)

    @Param("100", "1000", "10000")
    var factCount: Int = 1000

    private lateinit var mixedFacts: List<Any>

    @Setup
    fun setup() {
        // Create a mix of different fact types (roughly equal distribution)
        val customers = (1..factCount / 3).map { Customer("c$it", it * 100.0) }
        val orders = (1..factCount / 3).map { Order("o$it", "c${it % (factCount / 3) + 1}", it * 10.0) }
        val products = (1..factCount / 3).map { Product("p$it", "Product $it", it * 5.0) }

        mixedFacts = (customers + orders + products).shuffled()
    }

    @Benchmark
    fun filterByCustomerType(): Int {
        val engine = engine {
            produce<Customer, VipStatus>("vip-check") {
                condition { it.totalSpend > 10_000 }
                output { customer -> VipStatus(customer.id, "gold") }
            }
        }
        val result = engine.evaluate(mixedFacts)
        return result.derived.size
    }

    @Benchmark
    fun filterByOrderType(): Int {
        val engine = engine {
            produce<Order, Discount>("bulk-discount") {
                condition { it.amount > 500 }
                output { order -> Discount(order.customerId, 10) }
            }
        }
        val result = engine.evaluate(mixedFacts)
        return result.derived.size
    }

    @Benchmark
    fun filterByMultipleTypes(): Int {
        val engine = engine {
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
        val result = engine.evaluate(mixedFacts)
        return result.derived.size
    }
}
