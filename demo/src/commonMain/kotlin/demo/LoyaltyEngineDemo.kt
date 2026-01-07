package demo

import verdikt.Verdict
import verdikt.engine.engine

/**
 * Demo of verdikt-engine: A loyalty program that uses forward-chaining rules.
 *
 * This demonstrates:
 * - Fact producers that derive new facts from existing ones
 * - Rule chaining (VipStatus triggers Discount)
 * - Validation rules that run after production rules
 * - The simplified evaluate() API
 */

// === Domain Models (Facts) ===

data class Customer(
    val id: String,
    val name: String,
    val totalSpend: Double
)

data class Order(
    val customerId: String,
    val items: List<String>,
    val subtotal: Double
)

// === Derived Facts (produced by the engine) ===

data class VipStatus(
    val customerId: String,
    val tier: String  // "gold", "silver", or "bronze"
)

data class Discount(
    val customerId: String,
    val percent: Int,
    val reason: String
)

data class OrderTotal(
    val customerId: String,
    val subtotal: Double,
    val discount: Double,
    val total: Double
)

// === The Engine ===

val LoyaltyEngine = engine {

    // Rule 1: Determine VIP status based on total spend
    produce<Customer, VipStatus>("vip-status") {
        description = "Assign VIP tier based on lifetime spend"
        condition { it.totalSpend >= 100 }  // Only VIP if spent $100+
        output { customer ->
            val tier = when {
                customer.totalSpend >= 1000 -> "gold"
                customer.totalSpend >= 500 -> "silver"
                else -> "bronze"
            }
            VipStatus(customer.id, tier)
        }
    }

    // Rule 2: Gold VIPs get 20% discount (chains from VipStatus)
    produce<VipStatus, Discount>("gold-discount") {
        description = "Gold members get 20% off"
        condition { it.tier == "gold" }
        output { Discount(it.customerId, 20, "Gold member discount") }
    }

    // Rule 3: Silver VIPs get 10% discount
    produce<VipStatus, Discount>("silver-discount") {
        description = "Silver members get 10% off"
        condition { it.tier == "silver" }
        output { Discount(it.customerId, 10, "Silver member discount") }
    }

    // Rule 4: Bronze VIPs get 5% discount
    produce<VipStatus, Discount>("bronze-discount") {
        description = "Bronze members get 5% off"
        condition { it.tier == "bronze" }
        output { Discount(it.customerId, 5, "Bronze member discount") }
    }

    // Validation: Minimum order amount
    validate<Order>("minimum-order") {
        description = "Orders must be at least $10"
        condition { it.subtotal >= 10.0 }
        onFailure { order -> "Order subtotal $${order.subtotal} is below minimum $10" }
    }
}

// === Demo Runner ===

fun runLoyaltyEngineDemo() {
    println("=== Loyalty Engine Demo ===\n")

    // Scenario 1: Gold customer with valid order
    println("--- Scenario 1: Gold Customer ---")
    val goldCustomer = Customer("c1", "Alice", 1500.0)
    val aliceOrder = Order("c1", listOf("Widget", "Gadget"), 50.0)

    val result1 = LoyaltyEngine.evaluate(listOf(goldCustomer, aliceOrder))

    println("Customer: ${goldCustomer.name} (lifetime spend: $${goldCustomer.totalSpend})")
    println("Order: $${aliceOrder.subtotal}")
    println("Derived facts:")
    result1.derived.forEach { println("  - $it") }
    println("Verdict: ${if (result1.passed) "PASSED" else "FAILED"}")
    println()

    // Scenario 2: New customer (no VIP status)
    println("--- Scenario 2: New Customer (No VIP) ---")
    val newCustomer = Customer("c2", "Bob", 50.0)  // Below VIP threshold
    val bobOrder = Order("c2", listOf("Gizmo"), 25.0)

    val result2 = LoyaltyEngine.evaluate(listOf(newCustomer, bobOrder))

    println("Customer: ${newCustomer.name} (lifetime spend: $${newCustomer.totalSpend})")
    println("Order: $${bobOrder.subtotal}")
    println("Derived facts: ${if (result2.derived.isEmpty()) "none" else ""}")
    result2.derived.forEach { println("  - $it") }
    println("Verdict: ${if (result2.passed) "PASSED" else "FAILED"}")
    println()

    // Scenario 3: Order below minimum
    println("--- Scenario 3: Order Below Minimum ---")
    val silverCustomer = Customer("c3", "Carol", 600.0)
    val smallOrder = Order("c3", listOf("Sticker"), 5.0)  // Below $10 minimum

    val result3 = LoyaltyEngine.evaluate(listOf(silverCustomer, smallOrder))

    println("Customer: ${silverCustomer.name} (lifetime spend: $${silverCustomer.totalSpend})")
    println("Order: $${smallOrder.subtotal}")
    println("Derived facts:")
    result3.derived.forEach { println("  - $it") }
    println("Verdict: ${if (result3.passed) "PASSED" else "FAILED"}")
    if (result3.verdict is Verdict.Fail) {
        (result3.verdict as Verdict.Fail).failures.forEach {
            println("  Failure: ${it.reason}")
        }
    }
    println()

    // Show engine stats
    println("--- Engine Info ---")
    println("Fact producers: ${LoyaltyEngine.factProducerNames}")
    println("Validation rules: ${LoyaltyEngine.validationRuleNames}")
}
