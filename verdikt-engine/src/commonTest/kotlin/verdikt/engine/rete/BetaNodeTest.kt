package verdikt.engine.rete

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class BetaNodeTest {

    // Test fact types
    data class Customer(val id: String, val name: String)
    data class Order(val id: String, val customerId: String, val amount: Double)

    @Test
    fun leftActivationJoinsWithRightMemory() {
        // Create alpha node for orders (right input)
        val orderAlpha = AlphaNode(
            id = "order-alpha",
            inputType = Order::class,
            condition = { true }
        )

        // Create beta node that joins Customer with Order on customerId
        val betaNode = BetaNode(
            id = "customer-order-join",
            rightInput = orderAlpha,
            joinCondition = { leftFacts, order ->
                val customer = leftFacts.first() as Customer
                customer.id == order.customerId
            }
        )

        // Add orders to right alpha memory first
        val order1 = Order("o1", "c1", 100.0)
        val order2 = Order("o2", "c2", 200.0)
        orderAlpha.activate(order1)
        orderAlpha.activate(order2)

        // Now left activate with customer
        val customer1 = Customer("c1", "Alice")
        val customerToken = Token(customer1)
        betaNode.leftActivate(customerToken)

        // Should have joined customer1 with order1 (matching customerId)
        assertEquals(1, betaNode.memory.size())
        val joins = betaNode.memory.all()
        val joined = joins.first()
        assertEquals(2, joined.size)
        assertEquals(customer1, joined[0])
        assertEquals(order1, joined[1])
    }

    @Test
    fun rightActivationJoinsWithLeftTokens() {
        val orderAlpha = AlphaNode(
            id = "order-alpha",
            inputType = Order::class,
            condition = { true }
        )

        val betaNode = BetaNode(
            id = "customer-order-join",
            rightInput = orderAlpha,
            joinCondition = { leftFacts, order ->
                val customer = leftFacts.first() as Customer
                customer.id == order.customerId
            }
        )

        // Add customer to left first
        val customer1 = Customer("c1", "Alice")
        betaNode.leftActivate(Token(customer1))

        // Now right activate with order
        val order1 = Order("o1", "c1", 100.0)
        orderAlpha.activate(order1)
        betaNode.rightActivate(Token(order1))

        assertEquals(1, betaNode.memory.size())
    }

    @Test
    fun joinConditionFilteringWorks() {
        val orderAlpha = AlphaNode(
            id = "order-alpha",
            inputType = Order::class,
            condition = { true }
        )

        val betaNode = BetaNode(
            id = "customer-order-join",
            rightInput = orderAlpha,
            joinCondition = { leftFacts, order ->
                val customer = leftFacts.first() as Customer
                customer.id == order.customerId
            }
        )

        // Add order for customer c2
        val order = Order("o1", "c2", 100.0)
        orderAlpha.activate(order)

        // Left activate with customer c1 (doesn't match)
        val customer1 = Customer("c1", "Alice")
        betaNode.leftActivate(Token(customer1))

        // No join should occur
        assertEquals(0, betaNode.memory.size())
    }

    @Test
    fun multipleMatchingJoins() {
        val orderAlpha = AlphaNode(
            id = "order-alpha",
            inputType = Order::class,
            condition = { true }
        )

        val betaNode = BetaNode(
            id = "customer-order-join",
            rightInput = orderAlpha,
            joinCondition = { leftFacts, order ->
                val customer = leftFacts.first() as Customer
                customer.id == order.customerId
            }
        )

        // Add multiple orders for same customer
        val order1 = Order("o1", "c1", 100.0)
        val order2 = Order("o2", "c1", 200.0)
        val order3 = Order("o3", "c2", 300.0)
        orderAlpha.activate(order1)
        orderAlpha.activate(order2)
        orderAlpha.activate(order3)

        // Left activate with customer c1
        val customer1 = Customer("c1", "Alice")
        betaNode.leftActivate(Token(customer1))

        // Should have 2 joins (c1 with o1 and o2)
        assertEquals(2, betaNode.memory.size())
    }

    @Test
    fun propagatesToSuccessors() {
        val orderAlpha = AlphaNode(
            id = "order-alpha",
            inputType = Order::class,
            condition = { true }
        )

        val receivedTokens = mutableListOf<JoinedToken>()

        val betaNode = BetaNode(
            id = "customer-order-join",
            rightInput = orderAlpha,
            joinCondition = { _, _ -> true }  // Always join
        )

        betaNode.successors.add(object : ReteNode {
            override val id = "successor"
            override val successors = mutableListOf<ReteNode>()
            override fun leftActivate(token: Token<*>) {}
            override fun leftActivate(token: JoinedToken) {
                receivedTokens.add(token)
            }
        })

        val order = Order("o1", "c1", 100.0)
        orderAlpha.activate(order)

        val customer = Customer("c1", "Alice")
        betaNode.leftActivate(Token(customer))

        assertEquals(1, receivedTokens.size)
        assertEquals(2, receivedTokens[0].size)
    }

    @Test
    fun joinedTokenPlusOperatorWorks() {
        val token = JoinedToken(listOf("a", "b"))
        val extended = token + "c"

        assertEquals(3, extended.size)
        assertEquals("a", extended[0])
        assertEquals("b", extended[1])
        assertEquals("c", extended[2])
    }
}
