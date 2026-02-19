package verdikt.engine.rete

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ReteNetworkTest {

    // Test domain classes
    data class Animal(val name: String, val legs: Int)
    data class Bird(val name: String, val canFly: Boolean)

    // Polymorphic test types
    interface Shape { val area: Double }
    data class Circle(val radius: Double) : Shape {
        override val area: Double get() = 3.14159 * radius * radius
    }
    data class Rectangle(val width: Double, val height: Double) : Shape {
        override val area: Double get() = width * height
    }

    @Test
    fun activateWithTypedFactRoutesToCorrectAlphaNode() {
        val animalAlpha = AlphaNode<Animal>(
            id = "animal-alpha",
            inputType = Animal::class,
            condition = { it.legs == 4 }
        )

        val birdAlpha = AlphaNode<Bird>(
            id = "bird-alpha",
            inputType = Bird::class,
            condition = { it.canFly }
        )

        val network = ReteNetwork(
            alphaNodes = mapOf(
                Animal::class to listOf(animalAlpha),
                Bird::class to listOf(birdAlpha)
            ),
            betaNodes = emptyList(),
            outputNodes = emptyList()
        )

        val dog = Animal("Dog", 4)
        val eagle = Bird("Eagle", true)
        val penguin = Bird("Penguin", false)

        assertTrue(network.activate(dog))
        assertTrue(network.activate(eagle))
        assertFalse(network.activate(penguin)) // Condition fails

        assertEquals(1, animalAlpha.memory.size())
        assertEquals(1, birdAlpha.memory.size())
    }

    @Test
    fun polymorphicDispatchMatchesSubclassToSupertypeRule() {
        val shapeAlpha = AlphaNode<Shape>(
            id = "shape-alpha",
            inputType = Shape::class,
            condition = { it.area > 10.0 }
        )

        val network = ReteNetwork(
            alphaNodes = mapOf(
                Shape::class to listOf(shapeAlpha)
            ),
            betaNodes = emptyList(),
            outputNodes = emptyList()
        )

        val bigCircle = Circle(5.0) // area ~78.5
        val smallCircle = Circle(1.0) // area ~3.14

        // Circle::class != Shape::class, so exact-match misses; polymorphic dispatch catches it
        assertTrue(network.activate(bigCircle))
        assertFalse(network.activate(smallCircle)) // Condition fails (area too small)

        assertEquals(1, shapeAlpha.memory.size())
    }

    @Test
    fun polymorphicCacheIsReusedForSameType() {
        val shapeAlpha = AlphaNode<Shape>(
            id = "shape-alpha",
            inputType = Shape::class,
            condition = { true }
        )

        val network = ReteNetwork(
            alphaNodes = mapOf(
                Shape::class to listOf(shapeAlpha)
            ),
            betaNodes = emptyList(),
            outputNodes = emptyList()
        )

        val circle1 = Circle(1.0)
        val circle2 = Circle(2.0)

        network.activate(circle1)
        network.activate(circle2)

        // Both circles should be accepted
        assertEquals(2, shapeAlpha.memory.size())
    }

    @Test
    fun resetClearsAllState() {
        val animalAlpha = AlphaNode<Animal>(
            id = "animal-alpha",
            inputType = Animal::class,
            condition = { true }
        )

        val outputNode = OutputNode<String>(
            id = "test-output",
            ruleName = "test-rule",
            priority = 0,
            producer = { facts -> "result-${facts.first()}" }
        )

        animalAlpha.successors.add(outputNode)

        val network = ReteNetwork(
            alphaNodes = mapOf(Animal::class to listOf(animalAlpha)),
            betaNodes = emptyList(),
            outputNodes = listOf(outputNode)
        )
        outputNode.network = network

        // Activate a fact
        network.activate(Animal("Cat", 4))

        assertTrue(animalAlpha.memory.size() > 0)
        assertTrue(network.hasPendingActivations())

        // Reset clears everything
        network.reset()

        assertEquals(0, animalAlpha.memory.size())
        assertEquals(0, network.pendingActivationCount)
        assertFalse(network.hasPendingActivations())
    }

    @Test
    fun pendingActivationCountAccuracy() {
        val outputNode1 = OutputNode<String>(
            id = "output-1",
            ruleName = "rule-1",
            priority = 0,
            producer = { facts -> facts.first().toString() }
        )
        val outputNode2 = OutputNode<String>(
            id = "output-2",
            ruleName = "rule-2",
            priority = 0,
            producer = { facts -> facts.first().toString() }
        )

        val animalAlpha = AlphaNode<Animal>(
            id = "animal-alpha",
            inputType = Animal::class,
            condition = { true }
        )
        animalAlpha.successors.add(outputNode1)
        animalAlpha.successors.add(outputNode2)

        val network = ReteNetwork(
            alphaNodes = mapOf(Animal::class to listOf(animalAlpha)),
            betaNodes = emptyList(),
            outputNodes = listOf(outputNode1, outputNode2)
        )
        outputNode1.network = network
        outputNode2.network = network

        assertEquals(0, network.pendingActivationCount)

        network.activate(Animal("Dog", 4))
        assertEquals(2, network.pendingActivationCount) // One per output node

        outputNode1.firePending()
        assertEquals(1, network.pendingActivationCount)

        outputNode2.firePending()
        assertEquals(0, network.pendingActivationCount)
    }

    @Test
    fun activateWithUnmatchedTypeReturnsFalse() {
        val animalAlpha = AlphaNode<Animal>(
            id = "animal-alpha",
            inputType = Animal::class,
            condition = { true }
        )

        val network = ReteNetwork(
            alphaNodes = mapOf(Animal::class to listOf(animalAlpha)),
            betaNodes = emptyList(),
            outputNodes = emptyList()
        )

        // String doesn't match Animal and isn't a supertype/subtype
        assertFalse(network.activate("unrelated-type"))
        assertEquals(0, animalAlpha.memory.size())
    }

    @Test
    fun activateWithEmptyNetworkReturnsFalse() {
        val network = ReteNetwork(
            alphaNodes = emptyMap(),
            betaNodes = emptyList(),
            outputNodes = emptyList()
        )

        assertFalse(network.activate("anything"))
    }

    @Test
    fun statsReturnsCorrectCounts() {
        val alpha1 = AlphaNode<Animal>(
            id = "alpha-1",
            inputType = Animal::class,
            condition = { true }
        )
        val alpha2 = AlphaNode<Bird>(
            id = "alpha-2",
            inputType = Bird::class,
            condition = { true }
        )
        val output = OutputNode<String>(
            id = "output-1",
            ruleName = "rule-1",
            priority = 0,
            producer = { "result" }
        )

        val network = ReteNetwork(
            alphaNodes = mapOf(
                Animal::class to listOf(alpha1),
                Bird::class to listOf(alpha2)
            ),
            betaNodes = emptyList(),
            outputNodes = listOf(output)
        )

        val stats = network.stats()
        assertEquals(2, stats.alphaNodeCount)
        assertEquals(0, stats.betaNodeCount)
        assertEquals(1, stats.outputNodeCount)
        assertEquals(0, stats.totalTokensInAlphaMemory)
    }

    @Test
    fun statsReflectsMemoryAfterActivation() {
        val animalAlpha = AlphaNode<Animal>(
            id = "animal-alpha",
            inputType = Animal::class,
            condition = { true }
        )

        val network = ReteNetwork(
            alphaNodes = mapOf(Animal::class to listOf(animalAlpha)),
            betaNodes = emptyList(),
            outputNodes = emptyList()
        )

        network.activate(Animal("Dog", 4))
        network.activate(Animal("Cat", 4))

        val stats = network.stats()
        assertEquals(2, stats.totalTokensInAlphaMemory)
    }

    @Test
    fun multipleAlphaNodesForSameTypeAllReceiveFacts() {
        val alpha1 = AlphaNode<Animal>(
            id = "alpha-legs-4",
            inputType = Animal::class,
            condition = { it.legs == 4 }
        )
        val alpha2 = AlphaNode<Animal>(
            id = "alpha-name-d",
            inputType = Animal::class,
            condition = { it.name.startsWith("D") }
        )

        val network = ReteNetwork(
            alphaNodes = mapOf(Animal::class to listOf(alpha1, alpha2)),
            betaNodes = emptyList(),
            outputNodes = emptyList()
        )

        val dog = Animal("Dog", 4) // Matches both
        val duck = Animal("Duck", 2) // Only matches alpha2

        assertTrue(network.activate(dog))
        assertTrue(network.activate(duck))

        assertEquals(1, alpha1.memory.size()) // Only Dog
        assertEquals(2, alpha2.memory.size()) // Dog and Duck
    }

    @Test
    fun resetClearsAlphaMemoryWhilePreservingPolymorphicCache() {
        val shapeAlpha = AlphaNode<Shape>(
            id = "shape-alpha",
            inputType = Shape::class,
            condition = { true }
        )

        val network = ReteNetwork(
            alphaNodes = mapOf(Shape::class to listOf(shapeAlpha)),
            betaNodes = emptyList(),
            outputNodes = emptyList()
        )

        // First activation builds polymorphic cache
        network.activate(Circle(1.0))
        assertEquals(1, shapeAlpha.memory.size())

        // Reset clears alpha memory but preserves polymorphic cache
        network.reset()
        assertEquals(0, shapeAlpha.memory.size())

        // Activation still works after reset (polymorphic cache preserved)
        network.activate(Circle(2.0))
        assertEquals(1, shapeAlpha.memory.size())
    }
}
