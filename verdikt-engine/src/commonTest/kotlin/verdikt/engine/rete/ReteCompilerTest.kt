package verdikt.engine.rete

import verdikt.engine.InternalFactProducer
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ReteCompilerTest {

    data class Customer(val id: String, val spend: Double)
    data class Order(val id: String, val amount: Double)
    data class VipStatus(val customerId: String, val tier: String)
    data class Discount(val customerId: String, val percent: Int)

    private fun <In : Any, Out : Any> createProducer(
        name: String,
        inputType: kotlin.reflect.KClass<In>,
        priority: Int = 0,
        condition: (In) -> Boolean = { true },
        outputFn: (In) -> Out
    ): InternalFactProducer<In, Out> = InternalFactProducer(
        name = name,
        description = "",
        priority = priority,
        guard = null,
        inputType = inputType,
        condition = condition,
        asyncCondition = null,
        outputFn = outputFn,
        asyncOutputFn = null
    )

    // --- Basic compilation ---

    @Test
    fun compileSingleProducerCreatesAlphaAndOutputNode() {
        val compiler = ReteCompiler()
        val producer = createProducer(
            name = "vip-check",
            inputType = Customer::class,
            outputFn = { VipStatus(it.id, "gold") }
        )

        val result = compiler.compile(listOf(producer))
        val network = result.network

        assertEquals(1, network.alphaNodes.size)
        assertTrue(Customer::class in network.alphaNodes)
        assertEquals(1, network.alphaNodes[Customer::class]!!.size)
        assertEquals(1, network.outputNodes.size)
        assertEquals("vip-check", network.outputNodes.first().ruleName)
        assertTrue(result.fallbackProducers.isEmpty())
    }

    @Test
    fun compileMultipleProducersCreatesCorrectStructure() {
        val compiler = ReteCompiler()
        val producers = listOf(
            createProducer(
                name = "vip-check",
                inputType = Customer::class,
                outputFn = { VipStatus(it.id, "gold") }
            ),
            createProducer(
                name = "order-total",
                inputType = Order::class,
                outputFn = { Discount(it.id, 10) }
            )
        )

        val result = compiler.compile(producers)
        val network = result.network

        assertEquals(2, network.alphaNodes.size)
        assertTrue(Customer::class in network.alphaNodes)
        assertTrue(Order::class in network.alphaNodes)
        assertEquals(2, network.outputNodes.size)
    }

    @Test
    fun compileEmptyProducerListCreatesEmptyNetwork() {
        val compiler = ReteCompiler()
        val result = compiler.compile(emptyList())
        val network = result.network

        assertTrue(network.alphaNodes.isEmpty())
        assertTrue(network.outputNodes.isEmpty())
        assertTrue(network.betaNodes.isEmpty())
        assertTrue(result.fallbackProducers.isEmpty())
    }

    // --- Shared alpha nodes for same-type rules ---

    @Test
    fun sameInputTypeSharesAlphaNodesList() {
        val compiler = ReteCompiler()
        val producers = listOf(
            createProducer(
                name = "vip-gold",
                inputType = Customer::class,
                priority = 10,
                condition = { it.spend > 10_000 },
                outputFn = { VipStatus(it.id, "gold") }
            ),
            createProducer(
                name = "vip-silver",
                inputType = Customer::class,
                priority = 5,
                condition = { it.spend > 5_000 },
                outputFn = { VipStatus(it.id, "silver") }
            )
        )

        val result = compiler.compile(producers)
        val network = result.network

        // Both rules should have alpha nodes keyed under Customer::class
        assertEquals(1, network.alphaNodes.size)
        val customerAlphas = network.alphaNodes[Customer::class]!!
        assertEquals(2, customerAlphas.size)

        // Two output nodes
        assertEquals(2, network.outputNodes.size)
    }

    // --- Conditions propagate correctly ---

    @Test
    fun alphaNodeConditionFiltersCorrectly() {
        val compiler = ReteCompiler()
        val producer = createProducer(
            name = "high-spender",
            inputType = Customer::class,
            condition = { it.spend > 5_000 },
            outputFn = { VipStatus(it.id, "gold") }
        )

        val result = compiler.compile(listOf(producer))
        val network = result.network

        // High spender should pass through
        val activated1 = network.activate(Customer("1", 10_000.0))
        assertTrue(activated1)

        // Low spender should be rejected
        val activated2 = network.activate(Customer("2", 1_000.0))
        assertFalse(activated2)
    }

    @Test
    fun outputNodeProducesCorrectFact() {
        val compiler = ReteCompiler()
        val producer = createProducer(
            name = "vip-check",
            inputType = Customer::class,
            condition = { it.spend > 5_000 },
            outputFn = { VipStatus(it.id, "gold") }
        )

        val result = compiler.compile(listOf(producer))
        val network = result.network
        val outputNode = network.outputNodes.first()

        // Activate a customer
        network.activate(Customer("123", 10_000.0))

        // Fire and check output
        val outputs = outputNode.firePending()
        assertEquals(1, outputs.size)
        assertEquals(VipStatus("123", "gold"), outputs.first())
    }

    // --- Alpha to output wiring ---

    @Test
    fun alphaNodeIsWiredToOutputNode() {
        val compiler = ReteCompiler()
        val producer = createProducer(
            name = "test-rule",
            inputType = Customer::class,
            outputFn = { VipStatus(it.id, "gold") }
        )

        val result = compiler.compile(listOf(producer))
        val network = result.network
        val alphaNode = network.alphaNodes[Customer::class]!!.first()

        assertEquals(1, alphaNode.successors.size)
        assertTrue(alphaNode.successors.first() is OutputNode<*>)
    }

    // --- Output nodes wired to network ---

    @Test
    fun outputNodesAreWiredToNetwork() {
        val compiler = ReteCompiler()
        val producer = createProducer(
            name = "test-rule",
            inputType = Customer::class,
            outputFn = { VipStatus(it.id, "gold") }
        )

        val result = compiler.compile(listOf(producer))
        val network = result.network
        val outputNode = network.outputNodes.first()

        // After activating, the pending count on the network should reflect the output node
        network.activate(Customer("1", 100.0))
        assertTrue(network.hasPendingActivations())
        assertEquals(1, network.pendingActivationCount)
    }

    // --- Priority preservation ---

    @Test
    fun outputNodesPreservePriority() {
        val compiler = ReteCompiler()
        val producers = listOf(
            createProducer(
                name = "high",
                inputType = Customer::class,
                priority = 100,
                outputFn = { VipStatus(it.id, "gold") }
            ),
            createProducer(
                name = "low",
                inputType = Customer::class,
                priority = 1,
                outputFn = { VipStatus(it.id, "silver") }
            )
        )

        val result = compiler.compile(producers)
        val network = result.network

        assertEquals(100, network.outputNodes[0].priority)
        assertEquals(1, network.outputNodes[1].priority)
    }

    // --- Async producers are sent to fallback ---

    @Test
    fun asyncProducersGoToFallback() {
        val compiler = ReteCompiler()
        val asyncProducer = InternalFactProducer(
            name = "async-rule",
            description = "",
            priority = 0,
            guard = null,
            inputType = Customer::class,
            condition = { _: Customer -> error("Should not be called") },
            asyncCondition = { _: Customer -> true },
            outputFn = { _: Customer -> error("Should not be called") },
            asyncOutputFn = { c: Customer -> VipStatus(c.id, "gold") }
        )

        val syncProducer = createProducer(
            name = "sync-rule",
            inputType = Order::class,
            outputFn = { Discount(it.id, 10) }
        )

        val result = compiler.compile(listOf(asyncProducer, syncProducer))

        // Async producer should be in fallback
        assertEquals(1, result.fallbackProducers.size)
        assertEquals("async-rule", result.fallbackProducers.first().name)

        // Sync producer should be in the network
        assertEquals(1, result.network.outputNodes.size)
        assertEquals("sync-rule", result.network.outputNodes.first().ruleName)
    }
}
