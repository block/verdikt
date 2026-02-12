package verdikt.benchmark

import kotlinx.benchmark.*
import verdikt.engine.Engine
import verdikt.engine.engine

/**
 * Benchmarks rule chaining performance with varying chain depths.
 *
 * Tests forward-chaining where one rule's derived output is fed back into
 * the RETE network and propagated to downstream alpha nodes. Each chain
 * level adds an incremental propagation cycle through the network.
 */
@State(Scope.Benchmark)
@BenchmarkMode(Mode.Throughput)
@OutputTimeUnit(BenchmarkTimeUnit.MILLISECONDS)
open class PropagationDepthBenchmark {

    data class Level0(val id: String, val value: Int)
    data class Level1(val id: String, val value: Int)
    data class Level2(val id: String, val value: Int)
    data class Level3(val id: String, val value: Int)
    data class Level4(val id: String, val value: Int)
    data class Level5(val id: String, val value: Int)
    data class Level6(val id: String, val value: Int)
    data class Level7(val id: String, val value: Int)
    data class Level8(val id: String, val value: Int)
    data class Level9(val id: String, val value: Int)

    @Param("100", "500", "1000")
    var factCount: Int = 100

    /** When true, engine is constructed once in setup; when false, constructed per evaluate. */
    @Param("true", "false")
    var reuseEngine: Boolean = true

    private lateinit var initialFacts: List<Level0>
    private lateinit var depth3Engine: Engine
    private lateinit var depth5Engine: Engine
    private lateinit var depth10Engine: Engine

    private fun buildDepth3Engine() = engine {
        produce<Level0, Level1>("level-0-to-1") {
            condition { it.value > 0 }
            output { Level1(it.id, it.value + 1) }
        }
        produce<Level1, Level2>("level-1-to-2") {
            condition { it.value > 0 }
            output { Level2(it.id, it.value + 1) }
        }
        produce<Level2, Level3>("level-2-to-3") {
            condition { it.value > 0 }
            output { Level3(it.id, it.value + 1) }
        }
    }

    private fun buildDepth5Engine() = engine {
        produce<Level0, Level1>("level-0-to-1") {
            condition { it.value > 0 }
            output { Level1(it.id, it.value + 1) }
        }
        produce<Level1, Level2>("level-1-to-2") {
            condition { it.value > 0 }
            output { Level2(it.id, it.value + 1) }
        }
        produce<Level2, Level3>("level-2-to-3") {
            condition { it.value > 0 }
            output { Level3(it.id, it.value + 1) }
        }
        produce<Level3, Level4>("level-3-to-4") {
            condition { it.value > 0 }
            output { Level4(it.id, it.value + 1) }
        }
        produce<Level4, Level5>("level-4-to-5") {
            condition { it.value > 0 }
            output { Level5(it.id, it.value + 1) }
        }
    }

    private fun buildDepth10Engine() = engine {
        produce<Level0, Level1>("level-0-to-1") {
            condition { it.value > 0 }
            output { Level1(it.id, it.value + 1) }
        }
        produce<Level1, Level2>("level-1-to-2") {
            condition { it.value > 0 }
            output { Level2(it.id, it.value + 1) }
        }
        produce<Level2, Level3>("level-2-to-3") {
            condition { it.value > 0 }
            output { Level3(it.id, it.value + 1) }
        }
        produce<Level3, Level4>("level-3-to-4") {
            condition { it.value > 0 }
            output { Level4(it.id, it.value + 1) }
        }
        produce<Level4, Level5>("level-4-to-5") {
            condition { it.value > 0 }
            output { Level5(it.id, it.value + 1) }
        }
        produce<Level5, Level6>("level-5-to-6") {
            condition { it.value > 0 }
            output { Level6(it.id, it.value + 1) }
        }
        produce<Level6, Level7>("level-6-to-7") {
            condition { it.value > 0 }
            output { Level7(it.id, it.value + 1) }
        }
        produce<Level7, Level8>("level-7-to-8") {
            condition { it.value > 0 }
            output { Level8(it.id, it.value + 1) }
        }
        produce<Level8, Level9>("level-8-to-9") {
            condition { it.value > 0 }
            output { Level9(it.id, it.value + 1) }
        }
    }

    @Setup
    fun setup() {
        initialFacts = (1..factCount).map { Level0("id$it", it) }
        depth3Engine = buildDepth3Engine()
        depth5Engine = buildDepth5Engine()
        depth10Engine = buildDepth10Engine()
    }

    @Benchmark
    fun chainDepth3(): Int {
        val eng = if (reuseEngine) depth3Engine else buildDepth3Engine()
        return eng.evaluate(initialFacts).derived.size
    }

    @Benchmark
    fun chainDepth5(): Int {
        val eng = if (reuseEngine) depth5Engine else buildDepth5Engine()
        return eng.evaluate(initialFacts).derived.size
    }

    @Benchmark
    fun chainDepth10(): Int {
        val eng = if (reuseEngine) depth10Engine else buildDepth10Engine()
        return eng.evaluate(initialFacts).derived.size
    }
}
