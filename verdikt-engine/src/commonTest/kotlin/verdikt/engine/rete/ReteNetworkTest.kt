package verdikt.engine.rete

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ReteNetworkTest {

    data class Customer(val id: String, val spend: Double)
    data class Order(val id: String, val amount: Double)

    // Polymorphic test types
    interface Named { val name: String }
    data class Employee(override val name: String, val dept: String) : Named
    data class Contractor(override val name: String, val agency: String) : Named

    // --- Activate with typed facts ---

    @Test
    fun activateRoutesToExactTypeAlphaNode() {
        val alphaNode = AlphaNode(
            id = "customer-alpha",
            inputType = Customer::class,
            condition = { true }
        )

        val network = ReteNetwork(
            alphaNodes = mapOf(Customer::class to listOf(alphaNode)),
            betaNodes = emptyList(),
            outputNodes = emptyList()
        )

        val result = network.activate(Customer("1", 1000.0))

        assertTrue(result)
        assertEquals(1, alphaNode.memory.size())
    }

    @Test
    fun activateReturnsFalseWhenNoMatchingAlphaNode() {
        val alphaNode = AlphaNode(
            id = "customer-alpha",
            inputType = Customer::class,
            condition = { true }
        )

        val network = ReteNetwork(
            alphaNodes = mapOf(Customer::class to listOf(alphaNode)),
            betaNodes = emptyList(),
            outputNodes = emptyList()
        )

        val result = network.activate(Order("o1", 50.0))

        assertFalse(result)
        assertEquals(0, alphaNode.memory.size())
    }

    @Test
    fun activateRoutesToMultipleAlphaNodesOfSameType() {
        val alpha1 = AlphaNode(
            id = "high-spend",
            inputType = Customer::class,
            condition = { it.spend > 500 }
        )
        val alpha2 = AlphaNode(
            id = "all-customers",
            inputType = Customer::class,
            condition = { true }
        )

        val network = ReteNetwork(
            alphaNodes = mapOf(Customer::class to listOf(alpha1, alpha2)),
            betaNodes = emptyList(),
            outputNodes = emptyList()
        )

        val customer = Customer("1", 1000.0)
        val result = network.activate(customer)

        assertTrue(result)
        assertEquals(1, alpha1.memory.size())
        assertEquals(1, alpha2.memory.size())
    }

    @Test
    fun activateReturnsFalseWhenConditionRejects() {
        val alphaNode = AlphaNode(
            id = "high-spend",
            inputType = Customer::class,
            condition = { it.spend > 10_000 }
        )

        val network = ReteNetwork(
            alphaNodes = mapOf(Customer::class to listOf(alphaNode)),
            betaNodes = emptyList(),
            outputNodes = emptyList()
        )

        val result = network.activate(Customer("1", 500.0))

        assertFalse(result)
        assertEquals(0, alphaNode.memory.size())
    }

    // --- Polymorphic dispatch (subclass/interface matching) ---

    @Test
    fun polymorphicDispatchMatchesSubtype() {
        val namedAlpha = AlphaNode(
            id = "named-alpha",
            inputType = Named::class,
            condition = { true }
        )

        val network = ReteNetwork(
            alphaNodes = mapOf(Named::class to listOf(namedAlpha)),
            betaNodes = emptyList(),
            outputNodes = emptyList()
        )

        // Employee implements Named, should match via polymorphic dispatch
        val result = network.activate(Employee("Alice", "Engineering"))

        assertTrue(result)
        assertEquals(1, namedAlpha.memory.size())
    }

    @Test
    fun polymorphicDispatchMatchesMultipleSubtypes() {
        val namedAlpha = AlphaNode(
            id = "named-alpha",
            inputType = Named::class,
            condition = { true }
        )

        val network = ReteNetwork(
            alphaNodes = mapOf(Named::class to listOf(namedAlpha)),
            betaNodes = emptyList(),
            outputNodes = emptyList()
        )

        network.activate(Employee("Alice", "Engineering"))
        network.activate(Contractor("Bob", "Acme"))

        assertEquals(2, namedAlpha.memory.size())
    }

    @Test
    fun bothExactAndPolymorphicNodesActivated() {
        val employeeAlpha = AlphaNode(
            id = "employee-alpha",
            inputType = Employee::class,
            condition = { true }
        )
        val namedAlpha = AlphaNode(
            id = "named-alpha",
            inputType = Named::class,
            condition = { true }
        )

        val network = ReteNetwork(
            alphaNodes = mapOf(
                Employee::class to listOf(employeeAlpha),
                Named::class to listOf(namedAlpha)
            ),
            betaNodes = emptyList(),
            outputNodes = emptyList()
        )

        val employee = Employee("Alice", "Engineering")
        val result = network.activate(employee)

        assertTrue(result)
        // Both the exact Employee node and the polymorphic Named node should match
        assertEquals(1, employeeAlpha.memory.size())
        assertEquals(1, namedAlpha.memory.size())
    }

    @Test
    fun polymorphicCacheIsReusedForSameType() {
        val namedAlpha = AlphaNode(
            id = "named-alpha",
            inputType = Named::class,
            condition = { true }
        )

        val network = ReteNetwork(
            alphaNodes = mapOf(Named::class to listOf(namedAlpha)),
            betaNodes = emptyList(),
            outputNodes = emptyList()
        )

        // First call builds cache, second uses it
        network.activate(Employee("Alice", "Engineering"))
        network.activate(Employee("Bob", "Sales"))

        assertEquals(2, namedAlpha.memory.size())
    }

    // --- Pending activations ---

    @Test
    fun hasPendingActivationsReflectsOutputNodeState() {
        val alphaNode = AlphaNode(
            id = "customer-alpha",
            inputType = Customer::class,
            condition = { true }
        )
        val outputNode = OutputNode<String>(
            id = "output",
            ruleName = "test-rule",
            priority = 0,
            producer = { "result" }
        )
        alphaNode.successors.add(outputNode)

        val network = ReteNetwork(
            alphaNodes = mapOf(Customer::class to listOf(alphaNode)),
            betaNodes = emptyList(),
            outputNodes = listOf(outputNode)
        )
        outputNode.network = network

        assertFalse(network.hasPendingActivations())

        network.activate(Customer("1", 100.0))

        assertTrue(network.hasPendingActivations())
        assertEquals(1, network.pendingActivationCount)
    }

    @Test
    fun pendingActivationCountTracksMultipleNodes() {
        val alpha1 = AlphaNode(
            id = "alpha-1",
            inputType = Customer::class,
            condition = { true }
        )
        val alpha2 = AlphaNode(
            id = "alpha-2",
            inputType = Order::class,
            condition = { true }
        )
        val output1 = OutputNode<String>(
            id = "output-1",
            ruleName = "rule-1",
            priority = 0,
            producer = { "r1" }
        )
        val output2 = OutputNode<String>(
            id = "output-2",
            ruleName = "rule-2",
            priority = 0,
            producer = { "r2" }
        )
        alpha1.successors.add(output1)
        alpha2.successors.add(output2)

        val network = ReteNetwork(
            alphaNodes = mapOf(
                Customer::class to listOf(alpha1),
                Order::class to listOf(alpha2)
            ),
            betaNodes = emptyList(),
            outputNodes = listOf(output1, output2)
        )
        output1.network = network
        output2.network = network

        network.activate(Customer("1", 100.0))
        network.activate(Order("o1", 50.0))

        assertEquals(2, network.pendingActivationCount)
    }

    // --- Reset ---

    @Test
    fun resetClearsPendingActivationCount() {
        val network = ReteNetwork(
            alphaNodes = emptyMap(),
            betaNodes = emptyList(),
            outputNodes = emptyList()
        )

        network.incrementPendingActivations()
        network.incrementPendingActivations()
        assertEquals(2, network.pendingActivationCount)

        network.reset()

        assertEquals(0, network.pendingActivationCount)
        assertFalse(network.hasPendingActivations())
    }

    @Test
    fun resetClearsAlphaMemory() {
        val alphaNode = AlphaNode(
            id = "customer-alpha",
            inputType = Customer::class,
            condition = { true }
        )

        val network = ReteNetwork(
            alphaNodes = mapOf(Customer::class to listOf(alphaNode)),
            betaNodes = emptyList(),
            outputNodes = emptyList()
        )

        network.activate(Customer("1", 100.0))
        assertEquals(1, alphaNode.memory.size())

        network.reset()

        assertEquals(0, alphaNode.memory.size())
    }

    @Test
    fun resetClearsOutputNodeState() {
        val outputNode = OutputNode<String>(
            id = "output",
            ruleName = "test-rule",
            priority = 0,
            producer = { "result" }
        )

        val network = ReteNetwork(
            alphaNodes = emptyMap(),
            betaNodes = emptyList(),
            outputNodes = listOf(outputNode)
        )
        outputNode.network = network

        outputNode.leftActivateFact("fact")
        assertTrue(outputNode.hasPendingActivations())

        network.reset()

        assertFalse(outputNode.hasPendingActivations())
        assertEquals(0, outputNode.fireCount())
    }

    @Test
    fun resetClearsPolymorphicCacheAllowingRecomputation() {
        val namedAlpha = AlphaNode(
            id = "named-alpha",
            inputType = Named::class,
            condition = { true }
        )

        val network = ReteNetwork(
            alphaNodes = mapOf(Named::class to listOf(namedAlpha)),
            betaNodes = emptyList(),
            outputNodes = emptyList()
        )

        // Build polymorphic cache
        network.activate(Employee("Alice", "Engineering"))
        assertEquals(1, namedAlpha.memory.size())

        // Reset clears everything including cache
        network.reset()
        assertEquals(0, namedAlpha.memory.size())

        // Should still work after reset (cache rebuilt)
        network.activate(Employee("Bob", "Sales"))
        assertEquals(1, namedAlpha.memory.size())
    }

    // --- Stats ---

    @Test
    fun statsReportsCorrectNodeCounts() {
        val alpha1 = AlphaNode(
            id = "a1",
            inputType = Customer::class,
            condition = { true }
        )
        val alpha2 = AlphaNode(
            id = "a2",
            inputType = Order::class,
            condition = { true }
        )
        val output1 = OutputNode<String>(
            id = "o1",
            ruleName = "r1",
            priority = 0,
            producer = { "r" }
        )

        val network = ReteNetwork(
            alphaNodes = mapOf(
                Customer::class to listOf(alpha1),
                Order::class to listOf(alpha2)
            ),
            betaNodes = emptyList(),
            outputNodes = listOf(output1)
        )

        val stats = network.stats()

        assertEquals(2, stats.alphaNodeCount)
        assertEquals(0, stats.betaNodeCount)
        assertEquals(1, stats.outputNodeCount)
        assertEquals(0, stats.totalTokensInAlphaMemory)
    }

    @Test
    fun statsReportsCorrectTokenCount() {
        val alphaNode = AlphaNode(
            id = "a1",
            inputType = Customer::class,
            condition = { true }
        )

        val network = ReteNetwork(
            alphaNodes = mapOf(Customer::class to listOf(alphaNode)),
            betaNodes = emptyList(),
            outputNodes = emptyList()
        )

        network.activate(Customer("1", 100.0))
        network.activate(Customer("2", 200.0))

        val stats = network.stats()
        assertEquals(2, stats.totalTokensInAlphaMemory)
    }

    // --- Unknown types handled gracefully ---

    @Test
    fun unknownTypeReturnsNoActivation() {
        val network = ReteNetwork(
            alphaNodes = emptyMap(),
            betaNodes = emptyList(),
            outputNodes = emptyList()
        )

        val result = network.activate("completely-unknown-type")

        assertFalse(result)
    }

    @Test
    fun emptyNetworkHandlesActivationGracefully() {
        val network = ReteNetwork(
            alphaNodes = emptyMap(),
            betaNodes = emptyList(),
            outputNodes = emptyList()
        )

        assertFalse(network.activate(42))
        assertFalse(network.hasPendingActivations())
        assertEquals(0, network.pendingActivationCount)

        val stats = network.stats()
        assertEquals(0, stats.alphaNodeCount)
    }
}
