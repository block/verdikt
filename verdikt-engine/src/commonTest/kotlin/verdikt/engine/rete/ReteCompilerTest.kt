package verdikt.engine.rete

import verdikt.engine.InternalFactProducer
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ReteCompilerTest {

    @Test
    fun compilationProducesCorrectNodeTypes() {
        val producer = InternalFactProducer<String, Int>(
            name = "string-length",
            description = "Computes string length",
            priority = 0,
            guard = null,
            inputType = String::class,
            condition = { true },
            asyncCondition = null,
            outputFn = { it.length },
            asyncOutputFn = null
        )

        val compiler = ReteCompiler()
        val result = compiler.compile(listOf(producer))

        val network = result.network
        assertEquals(1, network.alphaNodes.size)
        assertTrue(network.alphaNodes.containsKey(String::class))
        assertEquals(1, network.alphaNodes[String::class]!!.size)
        assertEquals(1, network.outputNodes.size)
        assertEquals("string-length", network.outputNodes[0].ruleName)
        assertTrue(network.betaNodes.isEmpty())
        assertTrue(result.fallbackProducers.isEmpty())
    }

    @Test
    fun multipleRulesForSameTypeShareAlphaNodeGroup() {
        val producer1 = InternalFactProducer<String, Int>(
            name = "rule-1",
            description = "",
            priority = 10,
            guard = null,
            inputType = String::class,
            condition = { it.length > 3 },
            asyncCondition = null,
            outputFn = { it.length },
            asyncOutputFn = null
        )
        val producer2 = InternalFactProducer<String, Int>(
            name = "rule-2",
            description = "",
            priority = 5,
            guard = null,
            inputType = String::class,
            condition = { it.startsWith("A") },
            asyncCondition = null,
            outputFn = { it.length * 2 },
            asyncOutputFn = null
        )

        val compiler = ReteCompiler()
        val result = compiler.compile(listOf(producer1, producer2))

        val network = result.network

        // Both rules are for String type, so they should be grouped under String::class
        assertEquals(1, network.alphaNodes.size)
        assertEquals(2, network.alphaNodes[String::class]!!.size)
        assertEquals(2, network.outputNodes.size)
    }

    @Test
    fun conditionsPropagateCorrectlyThroughAlphaNodes() {
        val producer = InternalFactProducer<String, Int>(
            name = "long-strings",
            description = "",
            priority = 0,
            guard = null,
            inputType = String::class,
            condition = { it.length > 5 },
            asyncCondition = null,
            outputFn = { it.length },
            asyncOutputFn = null
        )

        val compiler = ReteCompiler()
        val result = compiler.compile(listOf(producer))
        val network = result.network

        // Activate a fact that should pass the condition
        assertTrue(network.activate("longstring"))

        // Activate a fact that should NOT pass the condition
        val shortResult = network.activate("hi")
        // "hi" has length 2, condition requires > 5, so it should not pass
        assertTrue(!shortResult || network.outputNodes[0].pendingCount() == 1)

        // The output node should have been activated for the long string
        assertTrue(network.outputNodes[0].hasPendingActivations())
    }

    @Test
    fun alphaNodeConnectsToOutputNode() {
        val producer = InternalFactProducer<String, Int>(
            name = "test-rule",
            description = "",
            priority = 0,
            guard = null,
            inputType = String::class,
            condition = { true },
            asyncCondition = null,
            outputFn = { it.length },
            asyncOutputFn = null
        )

        val compiler = ReteCompiler()
        val result = compiler.compile(listOf(producer))
        val network = result.network

        val alphaNode = network.alphaNodes[String::class]!!.first()
        assertEquals(1, alphaNode.successors.size)
        assertTrue(alphaNode.successors[0] is OutputNode<*>)
    }

    @Test
    fun outputNodesAreWiredToNetwork() {
        val producer = InternalFactProducer<String, Int>(
            name = "test-rule",
            description = "",
            priority = 0,
            guard = null,
            inputType = String::class,
            condition = { true },
            asyncCondition = null,
            outputFn = { it.length },
            asyncOutputFn = null
        )

        val compiler = ReteCompiler()
        val result = compiler.compile(listOf(producer))
        val network = result.network

        // Output nodes should be wired to the network for pending activation counting
        network.activate("hello")
        assertTrue(network.hasPendingActivations())
        assertEquals(1, network.pendingActivationCount)
    }

    @Test
    fun asyncProducersFallBackToLinearScan() {
        val asyncProducer = InternalFactProducer<String, Int>(
            name = "async-rule",
            description = "",
            priority = 0,
            guard = null,
            inputType = String::class,
            condition = { true },
            asyncCondition = { true },
            outputFn = { it.length },
            asyncOutputFn = { it.length }
        )

        val compiler = ReteCompiler()
        val result = compiler.compile(listOf(asyncProducer))

        assertTrue(result.network.alphaNodes.isEmpty())
        assertTrue(result.network.outputNodes.isEmpty())
        assertEquals(1, result.fallbackProducers.size)
        assertEquals("async-rule", result.fallbackProducers[0].name)
    }

    @Test
    fun mixedSyncAndAsyncProducersCompileCorrectly() {
        val syncProducer = InternalFactProducer<String, Int>(
            name = "sync-rule",
            description = "",
            priority = 0,
            guard = null,
            inputType = String::class,
            condition = { true },
            asyncCondition = null,
            outputFn = { it.length },
            asyncOutputFn = null
        )
        val asyncProducer = InternalFactProducer<String, Int>(
            name = "async-rule",
            description = "",
            priority = 0,
            guard = null,
            inputType = String::class,
            condition = { true },
            asyncCondition = { true },
            outputFn = { it.length },
            asyncOutputFn = { it.length }
        )

        val compiler = ReteCompiler()
        val result = compiler.compile(listOf(syncProducer, asyncProducer))

        assertEquals(1, result.network.alphaNodes.size)
        assertEquals(1, result.network.outputNodes.size)
        assertEquals("sync-rule", result.network.outputNodes[0].ruleName)
        assertEquals(1, result.fallbackProducers.size)
        assertEquals("async-rule", result.fallbackProducers[0].name)
    }

    @Test
    fun emptyProducerListCompilesToEmptyNetwork() {
        val compiler = ReteCompiler()
        val result = compiler.compile(emptyList())

        assertTrue(result.network.alphaNodes.isEmpty())
        assertTrue(result.network.betaNodes.isEmpty())
        assertTrue(result.network.outputNodes.isEmpty())
        assertTrue(result.fallbackProducers.isEmpty())
    }

    @Test
    fun outputNodeProducerExecutesCorrectly() {
        val producer = InternalFactProducer<String, Int>(
            name = "test-rule",
            description = "",
            priority = 0,
            guard = null,
            inputType = String::class,
            condition = { true },
            asyncCondition = null,
            outputFn = { it.length },
            asyncOutputFn = null
        )

        val compiler = ReteCompiler()
        val result = compiler.compile(listOf(producer))
        val network = result.network

        network.activate("hello")

        val outputNode = network.outputNodes[0]
        val outputs = outputNode.firePending()
        assertEquals(1, outputs.size)
        assertEquals(5, outputs[0])
    }

    @Test
    fun priorityIsPreservedInOutputNodes() {
        val highPriority = InternalFactProducer<String, Int>(
            name = "high",
            description = "",
            priority = 100,
            guard = null,
            inputType = String::class,
            condition = { true },
            asyncCondition = null,
            outputFn = { 1 },
            asyncOutputFn = null
        )
        val lowPriority = InternalFactProducer<String, Int>(
            name = "low",
            description = "",
            priority = 1,
            guard = null,
            inputType = String::class,
            condition = { true },
            asyncCondition = null,
            outputFn = { 2 },
            asyncOutputFn = null
        )

        val compiler = ReteCompiler()
        val result = compiler.compile(listOf(highPriority, lowPriority))

        assertEquals(100, result.network.outputNodes[0].priority)
        assertEquals(1, result.network.outputNodes[1].priority)
    }

    @Test
    fun differentInputTypesGetSeparateAlphaNodes() {
        val stringProducer = InternalFactProducer<String, Int>(
            name = "string-rule",
            description = "",
            priority = 0,
            guard = null,
            inputType = String::class,
            condition = { true },
            asyncCondition = null,
            outputFn = { it.length },
            asyncOutputFn = null
        )
        val intProducer = InternalFactProducer<Int, String>(
            name = "int-rule",
            description = "",
            priority = 0,
            guard = null,
            inputType = Int::class,
            condition = { true },
            asyncCondition = null,
            outputFn = { it.toString() },
            asyncOutputFn = null
        )

        val compiler = ReteCompiler()
        val result = compiler.compile(listOf(stringProducer, intProducer))

        assertEquals(2, result.network.alphaNodes.size)
        assertTrue(result.network.alphaNodes.containsKey(String::class))
        assertTrue(result.network.alphaNodes.containsKey(Int::class))
        assertEquals(2, result.network.outputNodes.size)
    }
}
