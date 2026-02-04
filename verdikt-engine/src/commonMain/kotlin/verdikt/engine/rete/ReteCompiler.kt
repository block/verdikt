package verdikt.engine.rete

import verdikt.engine.InternalFactProducer
import kotlin.reflect.KClass

/**
 * Compiles fact producers into a Rete network.
 *
 * The compiler creates:
 * 1. Alpha nodes for type filtering and condition testing
 * 2. Beta nodes for multi-fact joins (future work - not yet implemented)
 * 3. Output nodes to produce new facts
 *
 * ## Current Implementation
 *
 * Currently, only single-fact rules are compiled to Rete. The compilation creates:
 * - One alpha node per rule (for type filtering + condition testing)
 * - One output node per rule (for fact production)
 * - Direct alpha -> output connection (no beta network)
 *
 * ## Future Work: Beta Node Compilation
 *
 * TODO: Add support for compiling multi-fact conditions to beta nodes.
 * This would require:
 * 1. New DSL syntax for declaring join conditions (e.g., `conditionJoin<B> { a, b -> ... }`)
 * 2. Compilation logic to create beta nodes that join facts on matching keys
 * 3. Proper indexing in beta memory for efficient right-activation lookups
 */
internal class ReteCompiler {

    /**
     * Compile a list of fact producers into a Rete network.
     *
     * @param producers The fact producers to compile
     * @return A compiled ReteNetwork and list of producers that need fallback
     */
    fun compile(producers: List<InternalFactProducer<*, *>>): CompilationResult {
        val alphaNodes = mutableMapOf<KClass<*>, MutableList<AlphaNode<*>>>()
        val betaNodes = mutableListOf<BetaNode<*>>()
        val outputNodes = mutableListOf<OutputNode<*>>()
        val fallbackProducers = mutableListOf<InternalFactProducer<*, *>>()

        for (producer in producers) {
            // Async producers need fallback for now
            if (producer.isAsync) {
                fallbackProducers.add(producer)
                continue
            }

            // Compile to Rete network
            compileProducer(producer, alphaNodes, outputNodes)
        }

        val network = ReteNetwork(alphaNodes, betaNodes, outputNodes)
        return CompilationResult(network, fallbackProducers)
    }

    @Suppress("UNCHECKED_CAST")
    private fun compileProducer(
        producer: InternalFactProducer<*, *>,
        alphaNodes: MutableMap<KClass<*>, MutableList<AlphaNode<*>>>,
        outputNodes: MutableList<OutputNode<*>>
    ) {
        val inputType = producer.inputType as KClass<Any>

        // Create alpha node with condition
        val alphaNode = AlphaNode(
            id = "alpha-${producer.name}",
            inputType = inputType,
            condition = { fact: Any ->
                @Suppress("UNCHECKED_CAST")
                (producer as InternalFactProducer<Any, Any>).matches(fact)
            }
        )

        // Register alpha node by type
        alphaNodes.getOrPut(inputType) { mutableListOf() }.add(alphaNode)

        // Create output node
        val outputNode = OutputNode<Any>(
            id = "output-${producer.name}",
            ruleName = producer.name,
            priority = producer.priority,
            producer = { facts ->
                val inputFact = facts.first()
                @Suppress("UNCHECKED_CAST")
                (producer as InternalFactProducer<Any, Any>).produce(inputFact)
            }
        )

        // Connect alpha -> output
        alphaNode.successors.add(outputNode)
        outputNodes.add(outputNode)
    }
}

/**
 * Result of compiling fact producers to a Rete network.
 *
 * @property network The compiled Rete network
 * @property fallbackProducers Producers that couldn't be compiled (need naive execution)
 */
internal data class CompilationResult(
    val network: ReteNetwork,
    val fallbackProducers: List<InternalFactProducer<*, *>>
)
