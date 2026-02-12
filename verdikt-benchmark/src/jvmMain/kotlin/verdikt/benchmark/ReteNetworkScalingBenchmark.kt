package verdikt.benchmark

import kotlinx.benchmark.*
import verdikt.engine.Engine
import verdikt.engine.engine

/**
 * Benchmarks how RETE network execution scales across key dimensions:
 * alpha fan-out (multiple rules consuming the same type), network breadth
 * (independent rules across many types), and priority-ordered firing.
 */
@State(Scope.Benchmark)
@BenchmarkMode(Mode.Throughput)
@OutputTimeUnit(BenchmarkTimeUnit.MILLISECONDS)
open class ReteNetworkScalingBenchmark {

    // --- Fact types ---

    data class EventA(val id: Int, val value: Int)
    data class EventB(val id: Int, val value: Int)
    data class EventC(val id: Int, val value: Int)
    data class EventD(val id: Int, val value: Int)
    data class EventE(val id: Int, val value: Int)

    data class ResultA(val id: Int)
    data class ResultB(val id: Int)
    data class ResultC(val id: Int)
    data class ResultD(val id: Int)
    data class ResultE(val id: Int)
    data class ResultF(val id: Int)
    data class ResultG(val id: Int)
    data class ResultH(val id: Int)
    data class ResultI(val id: Int)
    data class ResultJ(val id: Int)

    // --- Parameters ---

    @Param("100", "1000", "5000")
    var factCount: Int = 100

    /** When true, engine is constructed once in setup; when false, constructed per evaluate. */
    @Param("true", "false")
    var reuseEngine: Boolean = true

    private lateinit var eventsA: List<EventA>
    private lateinit var mixedEvents: List<Any>
    private lateinit var fanOut3Engine: Engine
    private lateinit var fanOut10Engine: Engine
    private lateinit var breadth5Engine: Engine
    private lateinit var priority5Engine: Engine

    // --- Engine builders ---

    private fun buildFanOut3Engine() = engine {
        produce<EventA, ResultA>("rule-1") {
            condition { it.value > 0 }
            output { ResultA(it.id) }
        }
        produce<EventA, ResultB>("rule-2") {
            condition { it.value > 0 }
            output { ResultB(it.id) }
        }
        produce<EventA, ResultC>("rule-3") {
            condition { it.value > 0 }
            output { ResultC(it.id) }
        }
    }

    private fun buildFanOut10Engine() = engine {
        produce<EventA, ResultA>("rule-1") {
            condition { it.value > 0 }
            output { ResultA(it.id) }
        }
        produce<EventA, ResultB>("rule-2") {
            condition { it.value > 0 }
            output { ResultB(it.id) }
        }
        produce<EventA, ResultC>("rule-3") {
            condition { it.value > 0 }
            output { ResultC(it.id) }
        }
        produce<EventA, ResultD>("rule-4") {
            condition { it.value > 0 }
            output { ResultD(it.id) }
        }
        produce<EventA, ResultE>("rule-5") {
            condition { it.value > 0 }
            output { ResultE(it.id) }
        }
        produce<EventA, ResultF>("rule-6") {
            condition { it.value > 0 }
            output { ResultF(it.id) }
        }
        produce<EventA, ResultG>("rule-7") {
            condition { it.value > 0 }
            output { ResultG(it.id) }
        }
        produce<EventA, ResultH>("rule-8") {
            condition { it.value > 0 }
            output { ResultH(it.id) }
        }
        produce<EventA, ResultI>("rule-9") {
            condition { it.value > 0 }
            output { ResultI(it.id) }
        }
        produce<EventA, ResultJ>("rule-10") {
            condition { it.value > 0 }
            output { ResultJ(it.id) }
        }
    }

    private fun buildBreadth5Engine() = engine {
        produce<EventA, ResultA>("route-a") {
            condition { it.value > 0 }
            output { ResultA(it.id) }
        }
        produce<EventB, ResultB>("route-b") {
            condition { it.value > 0 }
            output { ResultB(it.id) }
        }
        produce<EventC, ResultC>("route-c") {
            condition { it.value > 0 }
            output { ResultC(it.id) }
        }
        produce<EventD, ResultD>("route-d") {
            condition { it.value > 0 }
            output { ResultD(it.id) }
        }
        produce<EventE, ResultE>("route-e") {
            condition { it.value > 0 }
            output { ResultE(it.id) }
        }
    }

    private fun buildPriority5Engine() = engine {
        produce<EventA, ResultA>("prio-1") {
            priority = 50
            condition { it.value > 0 }
            output { ResultA(it.id) }
        }
        produce<EventA, ResultB>("prio-2") {
            priority = 40
            condition { it.value > 0 }
            output { ResultB(it.id) }
        }
        produce<EventA, ResultC>("prio-3") {
            priority = 30
            condition { it.value > 0 }
            output { ResultC(it.id) }
        }
        produce<EventA, ResultD>("prio-4") {
            priority = 20
            condition { it.value > 0 }
            output { ResultD(it.id) }
        }
        produce<EventA, ResultE>("prio-5") {
            priority = 10
            condition { it.value > 0 }
            output { ResultE(it.id) }
        }
    }

    @Setup
    fun setup() {
        eventsA = (1..factCount).map { EventA(it, it) }

        val perType = factCount / 5
        val a = (1..perType).map { EventA(it, it) }
        val b = (1..perType).map { EventB(it, it) }
        val c = (1..perType).map { EventC(it, it) }
        val d = (1..perType).map { EventD(it, it) }
        val e = (1..perType).map { EventE(it, it) }
        mixedEvents = (a + b + c + d + e).shuffled()

        fanOut3Engine = buildFanOut3Engine()
        fanOut10Engine = buildFanOut10Engine()
        breadth5Engine = buildBreadth5Engine()
        priority5Engine = buildPriority5Engine()
    }

    // --- Benchmarks ---

    /**
     * 3 alpha nodes consuming the same input type (baseline fan-out).
     */
    @Benchmark
    fun alphaFanOut3Rules(): Int {
        val eng = if (reuseEngine) fanOut3Engine else buildFanOut3Engine()
        return eng.evaluate(eventsA).derived.size
    }

    /**
     * 10 alpha nodes consuming the same input type (marginal cost of wider
     * per-type alpha lists).
     */
    @Benchmark
    fun alphaFanOut10Rules(): Int {
        val eng = if (reuseEngine) fanOut10Engine else buildFanOut10Engine()
        return eng.evaluate(eventsA).derived.size
    }

    /**
     * 5 independent rules across 5 shuffled types (type-routing efficiency).
     */
    @Benchmark
    fun networkBreadth5Types(): Int {
        val eng = if (reuseEngine) breadth5Engine else buildBreadth5Engine()
        return eng.evaluate(mixedEvents).derived.size
    }

    /**
     * 5 rules with distinct priorities on same type (priority sort overhead).
     */
    @Benchmark
    fun priorityOrderedFiring5Rules(): Int {
        val eng = if (reuseEngine) priority5Engine else buildPriority5Engine()
        return eng.evaluate(eventsA).derived.size
    }
}
