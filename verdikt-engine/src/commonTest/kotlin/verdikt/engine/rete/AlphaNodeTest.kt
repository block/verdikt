package verdikt.engine.rete

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class AlphaNodeTest {

    // Test fact types
    data class Customer(val id: String, val totalSpend: Double)
    data class Order(val id: String, val amount: Double)

    @Test
    fun factsMatchingTypePassThrough() {
        val node = AlphaNode(
            id = "test-alpha",
            inputType = Customer::class,
            condition = { true }
        )

        val customer = Customer("1", 1000.0)
        val result = node.activate(customer)

        assertTrue(result)
        assertTrue(node.memory.contains(customer))
        assertEquals(1, node.memory.size())
    }

    @Test
    fun factsNotMatchingTypeAreRejected() {
        val node = AlphaNode(
            id = "test-alpha",
            inputType = Customer::class,
            condition = { true }
        )

        val order = Order("o1", 100.0)
        val result = node.activate(order)

        assertFalse(result)
        assertEquals(0, node.memory.size())
    }

    @Test
    fun conditionFilteringWorks() {
        val node = AlphaNode(
            id = "high-spender",
            inputType = Customer::class,
            condition = { it.totalSpend > 1000 }
        )

        val highSpender = Customer("1", 5000.0)
        val lowSpender = Customer("2", 500.0)

        assertTrue(node.activate(highSpender))
        assertFalse(node.activate(lowSpender))

        assertEquals(1, node.memory.size())
        assertTrue(node.memory.contains(highSpender))
        assertFalse(node.memory.contains(lowSpender))
    }

    @Test
    fun memoryStoresPassingTokens() {
        val node = AlphaNode(
            id = "test-alpha",
            inputType = Customer::class,
            condition = { true }
        )

        val customer1 = Customer("1", 1000.0)
        val customer2 = Customer("2", 2000.0)

        node.activate(customer1)
        node.activate(customer2)

        assertEquals(2, node.memory.size())
        val facts = node.memory.allFacts()
        assertTrue(customer1 in facts)
        assertTrue(customer2 in facts)
    }

    @Test
    fun duplicateFactsNotReprocessed() {
        var conditionCallCount = 0
        val node = AlphaNode(
            id = "test-alpha",
            inputType = Customer::class,
            condition = {
                conditionCallCount++
                true
            }
        )

        val customer = Customer("1", 1000.0)

        assertTrue(node.activate(customer))
        assertFalse(node.activate(customer))  // Duplicate

        assertEquals(1, conditionCallCount)
        assertEquals(1, node.memory.size())
    }

    @Test
    fun propagatesToSuccessors() {
        val node = AlphaNode(
            id = "test-alpha",
            inputType = Customer::class,
            condition = { true }
        )

        val receivedTokens = mutableListOf<Token<*>>()
        val successor = object : ReteNode {
            override val id = "successor"
            override val successors = mutableListOf<ReteNode>()
            override fun leftActivate(token: Token<*>) {
                receivedTokens.add(token)
            }
        }
        node.successors.add(successor)

        val customer = Customer("1", 1000.0)
        node.activate(customer)

        assertEquals(1, receivedTokens.size)
        assertEquals(customer, receivedTokens[0].fact)
    }

    @Test
    fun multipleSuccessorsAllReceiveTokens() {
        val node = AlphaNode(
            id = "test-alpha",
            inputType = Customer::class,
            condition = { true }
        )

        val received1 = mutableListOf<Token<*>>()
        val received2 = mutableListOf<Token<*>>()

        node.successors.add(object : ReteNode {
            override val id = "s1"
            override val successors = mutableListOf<ReteNode>()
            override fun leftActivate(token: Token<*>) { received1.add(token) }
        })
        node.successors.add(object : ReteNode {
            override val id = "s2"
            override val successors = mutableListOf<ReteNode>()
            override fun leftActivate(token: Token<*>) { received2.add(token) }
        })

        val customer = Customer("1", 1000.0)
        node.activate(customer)

        assertEquals(1, received1.size)
        assertEquals(1, received2.size)
    }

    @Test
    fun leftActivateCallsActivate() {
        val node = AlphaNode(
            id = "test-alpha",
            inputType = Customer::class,
            condition = { true }
        )

        val customer = Customer("1", 1000.0)
        val token = Token(customer)

        node.leftActivate(token)

        assertEquals(1, node.memory.size())
        assertTrue(node.memory.contains(customer))
    }
}
