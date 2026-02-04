package verdikt.benchmark

import kotlinx.benchmark.*
import verdikt.engine.engine

/**
 * Benchmarks rule chaining performance with varying chain depths.
 *
 * Tests forward-chaining where one rule's output triggers the next.
 * Type-based indexing improves each iteration of the fixpoint loop.
 */
@State(Scope.Benchmark)
@BenchmarkMode(Mode.Throughput)
@OutputTimeUnit(BenchmarkTimeUnit.MILLISECONDS)
open class RuleChainingBenchmark {

    // Chain of derived facts: Level0 -> Level1 -> Level2 -> ... -> LevelN
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

    private lateinit var initialFacts: List<Level0>

    @Setup
    fun setup() {
        initialFacts = (1..factCount).map { Level0("id$it", it) }
    }

    @Benchmark
    fun chainDepth3(): Int {
        val engine = engine {
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
        val result = engine.evaluate(initialFacts)
        return result.derived.size
    }

    @Benchmark
    fun chainDepth5(): Int {
        val engine = engine {
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
        val result = engine.evaluate(initialFacts)
        return result.derived.size
    }

    @Benchmark
    fun chainDepth10(): Int {
        val engine = engine {
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
        val result = engine.evaluate(initialFacts)
        return result.derived.size
    }
}
