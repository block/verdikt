package verdikt.engine.rete

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class OutputNodeTest {

    data class InputFact(val value: String)
    data class OutputFact(val result: String)

    private fun createOutputNode(
        id: String = "test-output",
        ruleName: String = "test-rule",
        priority: Int = 0,
        producer: (List<Any>) -> OutputFact? = { facts ->
            val input = facts.first() as InputFact
            OutputFact("produced-${input.value}")
        }
    ): OutputNode<OutputFact> = OutputNode(
        id = id,
        ruleName = ruleName,
        priority = priority,
        producer = producer
    )

    // --- Single-fact activation and firing ---

    @Test
    fun singleFactActivationAndFiring() {
        val node = createOutputNode()
        val input = InputFact("hello")

        node.leftActivateFact(input)

        assertTrue(node.hasPendingActivations())
        assertEquals(1, node.pendingCount())

        val results = node.firePending()

        assertEquals(1, results.size)
        assertEquals(OutputFact("produced-hello"), results.first())
        assertFalse(node.hasPendingActivations())
        assertEquals(0, node.pendingCount())
    }

    @Test
    fun multipleSingleFactActivations() {
        val node = createOutputNode()

        node.leftActivateFact(InputFact("a"))
        node.leftActivateFact(InputFact("b"))
        node.leftActivateFact(InputFact("c"))

        assertEquals(3, node.pendingCount())

        val results = node.firePending()

        assertEquals(3, results.size)
        assertEquals(OutputFact("produced-a"), results[0])
        assertEquals(OutputFact("produced-b"), results[1])
        assertEquals(OutputFact("produced-c"), results[2])
    }

    // --- Deduplication ---

    @Test
    fun duplicateSingleFactIsNotQueued() {
        val node = createOutputNode()
        val input = InputFact("same")

        node.leftActivateFact(input)
        node.leftActivateFact(input)

        assertEquals(1, node.pendingCount())
    }

    @Test
    fun hasFiredForReturnsTrueAfterActivation() {
        val node = createOutputNode()
        val input = InputFact("test")

        assertFalse(node.hasFiredFor(listOf(input)))

        node.leftActivateFact(input)

        assertTrue(node.hasFiredFor(listOf(input)))
    }

    @Test
    fun fireCountTracksTotalActivations() {
        val node = createOutputNode()

        assertEquals(0, node.fireCount())

        node.leftActivateFact(InputFact("a"))
        node.leftActivateFact(InputFact("b"))

        assertEquals(2, node.fireCount())
    }

    // --- Multi-fact activation (JoinedToken) ---

    @Test
    fun multiFactActivationViaJoinedToken() {
        val node = OutputNode<String>(
            id = "multi-output",
            ruleName = "multi-rule",
            priority = 0,
            producer = { facts ->
                facts.joinToString("-") { it.toString() }
            }
        )

        val joinedToken = JoinedToken(listOf("alpha", "beta"))
        node.leftActivate(joinedToken)

        assertTrue(node.hasPendingActivations())
        assertEquals(1, node.pendingCount())

        val results = node.firePending()

        assertEquals(1, results.size)
        assertEquals("alpha-beta", results.first())
    }

    @Test
    fun duplicateJoinedTokenIsNotQueued() {
        val node = OutputNode<String>(
            id = "multi-output",
            ruleName = "multi-rule",
            priority = 0,
            producer = { "output" }
        )

        val joinedToken = JoinedToken(listOf("a", "b"))
        node.leftActivate(joinedToken)
        node.leftActivate(joinedToken)

        assertEquals(1, node.pendingCount())
    }

    @Test
    fun hasFiredForMultiFacts() {
        val node = OutputNode<String>(
            id = "multi-output",
            ruleName = "multi-rule",
            priority = 0,
            producer = { "output" }
        )

        val facts = listOf("a", "b")
        assertFalse(node.hasFiredFor(facts))

        node.leftActivate(JoinedToken(facts))

        assertTrue(node.hasFiredFor(facts))
    }

    // --- Mixed single and multi-fact ---

    @Test
    fun mixedSingleAndMultiFactActivations() {
        var callCount = 0
        val node = OutputNode<String>(
            id = "mixed-output",
            ruleName = "mixed-rule",
            priority = 0,
            producer = { facts ->
                callCount++
                facts.joinToString(",")
            }
        )

        node.leftActivateFact("single-fact")
        node.leftActivate(JoinedToken(listOf("joined-a", "joined-b")))

        assertEquals(2, node.pendingCount())

        val results = node.firePending()

        assertEquals(2, results.size)
        assertEquals(2, callCount)
    }

    // --- firePendingWithInputs ---

    @Test
    fun firePendingWithInputsReturnsPairedResults() {
        val node = createOutputNode()
        val input = InputFact("test")

        node.leftActivateFact(input)

        val results = node.firePendingWithInputs()

        assertEquals(1, results.size)
        val (inputFacts, outputs) = results.first()
        assertEquals(listOf(input), inputFacts)
        assertEquals(listOf(OutputFact("produced-test")), outputs)
    }

    @Test
    fun firePendingWithInputsSkipsNullProducerOutputs() {
        val node = OutputNode<OutputFact>(
            id = "null-output",
            ruleName = "null-rule",
            priority = 0,
            producer = { _: List<Any> -> null }
        )

        val fact = "test-fact"
        node.leftActivateFact(fact)

        assertTrue(node.hasPendingActivations(), "Node should have pending activations")
        assertEquals(1, node.pendingCount(), "Pending count should be 1")

        val results = node.firePendingWithInputs()

        // Null producer outputs are skipped entirely — no allocation for no-ops
        assertTrue(results.isEmpty(), "Results should be empty when producer returns null")
    }

    @Test
    fun firePendingReturnsEmptyListWhenNothingPending() {
        val node = createOutputNode()

        val results = node.firePending()

        assertTrue(results.isEmpty())
    }

    @Test
    fun firePendingWithInputsReturnsEmptyListWhenNothingPending() {
        val node = createOutputNode()

        val results = node.firePendingWithInputs()

        assertTrue(results.isEmpty())
    }

    // --- Reset ---

    @Test
    fun resetClearsAllState() {
        val node = createOutputNode()

        node.leftActivateFact(InputFact("a"))
        node.leftActivateFact(InputFact("b"))

        assertTrue(node.hasPendingActivations())
        assertEquals(2, node.fireCount())

        node.reset()

        assertFalse(node.hasPendingActivations())
        assertEquals(0, node.pendingCount())
        assertEquals(0, node.fireCount())
        assertFalse(node.hasFiredFor(listOf(InputFact("a"))))
    }

    @Test
    fun resetAllowsSameFactToBeActivatedAgain() {
        val node = createOutputNode()
        val input = InputFact("reuse")

        node.leftActivateFact(input)
        node.firePending()

        // Same fact should be deduplicated before reset
        node.leftActivateFact(input)
        assertEquals(0, node.pendingCount())

        // After reset, the same fact can be activated again
        node.reset()
        node.leftActivateFact(input)
        assertEquals(1, node.pendingCount())
    }

    // --- clearPending ---

    @Test
    fun clearPendingDiscardsPendingWithoutFiring() {
        var producerCallCount = 0
        val node = OutputNode<String>(
            id = "clear-test",
            ruleName = "clear-rule",
            priority = 0,
            producer = {
                producerCallCount++
                "output"
            }
        )

        node.leftActivateFact("fact")
        assertTrue(node.hasPendingActivations())

        node.clearPending()

        assertFalse(node.hasPendingActivations())
        assertEquals(0, producerCallCount)
    }

    @Test
    fun clearPendingOnEmptyNodeIsNoOp() {
        val node = createOutputNode()

        // Should not throw
        node.clearPending()
        assertFalse(node.hasPendingActivations())
    }

    // --- isSkipped ---

    @Test
    fun skippedNodeIgnoresSingleFactActivation() {
        val node = createOutputNode()
        node.isSkipped = true

        node.leftActivateFact(InputFact("ignored"))

        assertFalse(node.hasPendingActivations())
        assertEquals(0, node.pendingCount())
    }

    @Test
    fun skippedNodeIgnoresJoinedTokenActivation() {
        val node = OutputNode<String>(
            id = "skip-test",
            ruleName = "skip-rule",
            priority = 0,
            producer = { "output" }
        )
        node.isSkipped = true

        node.leftActivate(JoinedToken(listOf("a", "b")))

        assertFalse(node.hasPendingActivations())
    }

    @Test
    fun resetClearsIsSkippedFlag() {
        val node = createOutputNode()
        node.isSkipped = true

        node.reset()

        assertFalse(node.isSkipped)
    }

    // --- leftActivate(Token) delegates to leftActivateFact ---

    @Test
    fun leftActivateTokenDelegatesToFactActivation() {
        val node = createOutputNode()
        val input = InputFact("via-token")

        node.leftActivate(Token(input))

        assertTrue(node.hasPendingActivations())
        assertEquals(1, node.pendingCount())

        val results = node.firePending()

        assertEquals(1, results.size)
        assertEquals(OutputFact("produced-via-token"), results.first())
    }

    // --- Network pending count integration ---

    @Test
    fun networkPendingCountIsUpdatedOnActivation() {
        val network = ReteNetwork(
            alphaNodes = emptyMap(),
            betaNodes = emptyList(),
            outputNodes = emptyList()
        )

        val node = createOutputNode()
        node.network = network

        assertEquals(0, network.pendingActivationCount)

        node.leftActivateFact(InputFact("a"))
        assertEquals(1, network.pendingActivationCount)

        node.leftActivateFact(InputFact("b"))
        assertEquals(2, network.pendingActivationCount)
    }

    @Test
    fun networkPendingCountIsDecrementedOnFire() {
        val network = ReteNetwork(
            alphaNodes = emptyMap(),
            betaNodes = emptyList(),
            outputNodes = emptyList()
        )

        val node = createOutputNode()
        node.network = network

        node.leftActivateFact(InputFact("a"))
        node.leftActivateFact(InputFact("b"))
        assertEquals(2, network.pendingActivationCount)

        node.firePending()
        assertEquals(0, network.pendingActivationCount)
    }

    @Test
    fun networkPendingCountIsDecrementedOnClear() {
        val network = ReteNetwork(
            alphaNodes = emptyMap(),
            betaNodes = emptyList(),
            outputNodes = emptyList()
        )

        val node = createOutputNode()
        node.network = network

        node.leftActivateFact(InputFact("a"))
        assertEquals(1, network.pendingActivationCount)

        node.clearPending()
        assertEquals(0, network.pendingActivationCount)
    }
}
