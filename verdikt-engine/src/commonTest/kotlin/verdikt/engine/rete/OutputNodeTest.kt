package verdikt.engine.rete

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class OutputNodeTest {

    @Test
    fun singleFactPendingAndFiring() {
        val node = OutputNode<String>(
            id = "test-output",
            ruleName = "test-rule",
            priority = 0,
            producer = { facts -> "produced-${facts.first()}" }
        )

        node.leftActivateFact("hello")

        assertTrue(node.hasPendingActivations())
        assertEquals(1, node.pendingCount())

        val results = node.firePending()
        assertEquals(listOf("produced-hello"), results)
        assertFalse(node.hasPendingActivations())
        assertEquals(0, node.pendingCount())
    }

    @Test
    fun multiFactPendingAndFiring() {
        val node = OutputNode<String>(
            id = "test-output",
            ruleName = "test-rule",
            priority = 0,
            producer = { facts -> facts.joinToString("-") }
        )

        val joinedToken = JoinedToken(listOf("a", "b"))
        node.leftActivate(joinedToken)

        assertTrue(node.hasPendingActivations())
        assertEquals(1, node.pendingCount())

        val results = node.firePending()
        assertEquals(1, results.size)
        assertEquals("a-b", results.first())
    }

    @Test
    fun resetClearsPendingState() {
        val node = OutputNode<String>(
            id = "test-output",
            ruleName = "test-rule",
            priority = 0,
            producer = { facts -> facts.first().toString() }
        )

        node.leftActivateFact("hello")
        node.leftActivate(JoinedToken(listOf("a", "b")))

        assertTrue(node.hasPendingActivations())
        assertEquals(2, node.pendingCount())

        node.reset()

        assertFalse(node.hasPendingActivations())
        assertEquals(0, node.pendingCount())
        assertEquals(0, node.fireCount())
    }

    @Test
    fun resetClearsFiredState() {
        val node = OutputNode<String>(
            id = "test-output",
            ruleName = "test-rule",
            priority = 0,
            producer = { facts -> facts.first().toString() }
        )

        node.leftActivateFact("hello")
        node.firePending()

        assertTrue(node.hasFiredFor(listOf("hello")))
        assertEquals(1, node.fireCount())

        node.reset()

        assertFalse(node.hasFiredFor(listOf("hello")))
        assertEquals(0, node.fireCount())
    }

    @Test
    fun firedForSingleDeduplication() {
        val network = ReteNetwork(emptyMap(), emptyList(), emptyList())
        val node = OutputNode<String>(
            id = "test-output",
            ruleName = "test-rule",
            priority = 0,
            producer = { facts -> "produced-${facts.first()}" }
        )
        node.network = network

        node.leftActivateFact("hello")
        node.leftActivateFact("hello") // duplicate

        assertEquals(1, node.pendingCount())
        assertEquals(1, network.pendingActivationCount)

        val results = node.firePending()
        assertEquals(1, results.size)
        assertEquals(0, network.pendingActivationCount)
    }

    @Test
    fun firedForMultiDeduplication() {
        val node = OutputNode<String>(
            id = "test-output",
            ruleName = "test-rule",
            priority = 0,
            producer = { facts -> facts.joinToString("-") }
        )

        val token1 = JoinedToken(listOf("a", "b"))
        val token2 = JoinedToken(listOf("a", "b")) // same facts

        node.leftActivate(token1)
        node.leftActivate(token2)

        assertEquals(1, node.pendingCount())
    }

    @Test
    fun firePendingWithInputsReturnsCorrectStructure() {
        val node = OutputNode<String>(
            id = "test-output",
            ruleName = "test-rule",
            priority = 0,
            producer = { facts -> "out-${facts.first()}" }
        )

        node.leftActivateFact("fact1")
        node.leftActivateFact("fact2")

        val results = node.firePendingWithInputs()

        assertEquals(2, results.size)

        val first = results[0]
        assertEquals(listOf("fact1"), first.first)
        assertEquals(listOf("out-fact1"), first.second)

        val second = results[1]
        assertEquals(listOf("fact2"), second.first)
        assertEquals(listOf("out-fact2"), second.second)
    }

    @Test
    fun firePendingWithInputsHandlesMultiFacts() {
        val node = OutputNode<String>(
            id = "test-output",
            ruleName = "test-rule",
            priority = 0,
            producer = { facts -> facts.joinToString("+") }
        )

        node.leftActivate(JoinedToken(listOf("x", "y")))

        val results = node.firePendingWithInputs()

        assertEquals(1, results.size)
        assertEquals(listOf("x", "y"), results[0].first)
        assertEquals(listOf("x+y"), results[0].second)
    }

    @Test
    fun producerReturningNullProducesNoOutput() {
        val node = OutputNode<String>(
            id = "test-output",
            ruleName = "test-rule",
            priority = 0,
            producer = { _ -> null }
        )

        node.leftActivateFact("hello")

        val results = node.firePendingWithInputs()

        assertTrue(results.isEmpty(), "Null producer output should be skipped entirely")
    }


    @Test
    fun networkPendingCountTrackedCorrectly() {
        val network = ReteNetwork(emptyMap(), emptyList(), emptyList())
        val node = OutputNode<String>(
            id = "test-output",
            ruleName = "test-rule",
            priority = 0,
            producer = { facts -> facts.first().toString() }
        )
        node.network = network

        assertEquals(0, network.pendingActivationCount)

        node.leftActivateFact("a")
        assertEquals(1, network.pendingActivationCount)

        node.leftActivateFact("b")
        assertEquals(2, network.pendingActivationCount)

        node.firePending()
        assertEquals(0, network.pendingActivationCount)
    }

    @Test
    fun clearPendingDiscardsWithoutFiring() {
        var producerCalled = false
        val network = ReteNetwork(emptyMap(), emptyList(), emptyList())
        val node = OutputNode<String>(
            id = "test-output",
            ruleName = "test-rule",
            priority = 0,
            producer = { _ ->
                producerCalled = true
                "output"
            }
        )
        node.network = network

        node.leftActivateFact("hello")
        assertEquals(1, network.pendingActivationCount)

        node.clearPending()

        assertFalse(producerCalled)
        assertFalse(node.hasPendingActivations())
        assertEquals(0, network.pendingActivationCount)
    }

    @Test
    fun isSkippedPreventsActivation() {
        val node = OutputNode<String>(
            id = "test-output",
            ruleName = "test-rule",
            priority = 0,
            producer = { facts -> facts.first().toString() }
        )
        node.isSkipped = true

        node.leftActivateFact("hello")
        node.leftActivate(JoinedToken(listOf("a", "b")))

        assertFalse(node.hasPendingActivations())
        assertEquals(0, node.pendingCount())
    }

    @Test
    fun leftActivateTokenDelegatesToFactActivation() {
        val node = OutputNode<String>(
            id = "test-output",
            ruleName = "test-rule",
            priority = 0,
            producer = { facts -> "out-${facts.first()}" }
        )

        val token = Token("hello")
        node.leftActivate(token)

        assertTrue(node.hasPendingActivations())
        assertEquals(1, node.pendingCount())

        val results = node.firePending()
        assertEquals(listOf("out-hello"), results)
    }

    @Test
    fun hasFiredForReturnsTrueAfterActivation() {
        val node = OutputNode<String>(
            id = "test-output",
            ruleName = "test-rule",
            priority = 0,
            producer = { facts -> facts.first().toString() }
        )

        node.leftActivateFact("hello")
        assertFalse(node.hasFiredFor(listOf("world")))
        assertTrue(node.hasFiredFor(listOf("hello")))

        node.firePending()
        assertTrue(node.hasFiredFor(listOf("hello")))
    }

    @Test
    fun hasFiredForMultiFactsWorks() {
        val node = OutputNode<String>(
            id = "test-output",
            ruleName = "test-rule",
            priority = 0,
            producer = { facts -> facts.joinToString("-") }
        )

        node.leftActivate(JoinedToken(listOf("a", "b")))
        assertTrue(node.hasFiredFor(listOf("a", "b")))
        assertFalse(node.hasFiredFor(listOf("a", "c")))
    }

    @Test
    fun firePendingReturnsEmptyWhenNothingPending() {
        val node = OutputNode<String>(
            id = "test-output",
            ruleName = "test-rule",
            priority = 0,
            producer = { facts -> facts.first().toString() }
        )

        assertEquals(emptyList(), node.firePending())
        assertEquals(emptyList(), node.firePendingWithInputs())
    }

    @Test
    fun fireCountAccumulatesAcrossMultipleFirings() {
        val node = OutputNode<String>(
            id = "test-output",
            ruleName = "test-rule",
            priority = 0,
            producer = { facts -> facts.first().toString() }
        )

        node.leftActivateFact("a")
        node.firePending()
        assertEquals(1, node.fireCount())

        node.leftActivateFact("b")
        node.firePending()
        assertEquals(2, node.fireCount())
    }
}
