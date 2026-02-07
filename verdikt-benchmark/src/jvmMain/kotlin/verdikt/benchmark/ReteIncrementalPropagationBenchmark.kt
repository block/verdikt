package verdikt.benchmark

import kotlinx.benchmark.*
import verdikt.engine.engine

/**
 * Benchmarks RETE incremental propagation — derived facts flowing back
 * through the network and triggering downstream alpha nodes. Covers
 * diamond-shaped topologies, selectivity variation, and derived-fact fan-out.
 */
@State(Scope.Benchmark)
@BenchmarkMode(Mode.Throughput)
@OutputTimeUnit(BenchmarkTimeUnit.MILLISECONDS)
open class ReteIncrementalPropagationBenchmark {

    // --- Fact types ---

    data class RawEvent(val id: Int, val severity: Int)
    data class Alert(val id: Int, val severity: Int)
    data class Critical(val id: Int)
    data class Notification(val id: Int)
    data class Escalation(val id: Int)
    data class AuditLog(val id: Int, val message: String)

    data class Input(val id: Int, val value: Int)
    data class Intermediate(val id: Int, val value: Int)
    data class Output(val id: Int)

    data class Source(val id: Int)
    data class Derived(val id: Int)
    data class DownstreamA(val id: Int)
    data class DownstreamB(val id: Int)
    data class DownstreamC(val id: Int)

    // --- Parameters ---

    @Param("100", "1000", "5000")
    var factCount: Int = 100

    private lateinit var rawEvents: List<RawEvent>
    private lateinit var inputs: List<Input>
    private lateinit var sources: List<Source>

    @Setup
    fun setup() {
        // Severity 1-10, evenly distributed
        rawEvents = (1..factCount).map { RawEvent(it, (it % 10) + 1) }
        inputs = (1..factCount).map { Input(it, it) }
        sources = (1..factCount).map { Source(it) }
    }

    // --- Benchmarks ---

    /**
     * Multi-level selective propagation:
     * RawEvent -> Alert (severity > 5, ~50%)
     *   Alert -> Critical (severity > 8, ~20% of alerts)
     *   Alert -> Notification (all alerts)
     *   Alert -> Escalation (severity == 10, ~10% of alerts)
     * RawEvent -> AuditLog (all events)
     */
    @Benchmark
    fun diamondPropagation(): Int {
        val engine = engine {
            produce<RawEvent, Alert>("triage") {
                condition { it.severity > 5 }
                output { Alert(it.id, it.severity) }
            }
            produce<Alert, Critical>("critical-filter") {
                condition { it.severity > 8 }
                output { Critical(it.id) }
            }
            produce<Alert, Notification>("notify") {
                condition { true }
                output { Notification(it.id) }
            }
            produce<Alert, Escalation>("escalate") {
                condition { it.severity == 10 }
                output { Escalation(it.id) }
            }
            produce<RawEvent, AuditLog>("audit") {
                condition { true }
                output { AuditLog(it.id, "event-${it.id}") }
            }
        }
        val result = engine.evaluate(rawEvents)
        return result.derived.size
    }

    /**
     * 2-level chain with ~10% condition pass rate at each level.
     * Most facts are filtered out, revealing per-evaluation overhead.
     */
    @Benchmark
    fun lowSelectivityChain(): Int {
        val engine = engine {
            produce<Input, Intermediate>("level-1") {
                condition { it.value % 10 == 0 }
                output { Intermediate(it.id, it.value) }
            }
            produce<Intermediate, Output>("level-2") {
                condition { it.value % 100 == 0 }
                output { Output(it.id) }
            }
        }
        val result = engine.evaluate(inputs)
        return result.derived.size
    }

    /**
     * Same 2-level chain with ~90% condition pass rate at each level.
     * Most facts pass through, revealing per-fact propagation overhead at scale.
     */
    @Benchmark
    fun highSelectivityChain(): Int {
        val engine = engine {
            produce<Input, Intermediate>("level-1") {
                condition { it.value % 10 != 0 }
                output { Intermediate(it.id, it.value) }
            }
            produce<Intermediate, Output>("level-2") {
                condition { it.value % 10 != 0 }
                output { Output(it.id) }
            }
        }
        val result = engine.evaluate(inputs)
        return result.derived.size
    }

    /**
     * 1 derived type fanning out to 3 downstream rules.
     * Measures incremental propagation fan-out from a single derived type.
     */
    @Benchmark
    fun derivedFactFanOut(): Int {
        val engine = engine {
            produce<Source, Derived>("derive") {
                condition { true }
                output { Derived(it.id) }
            }
            produce<Derived, DownstreamA>("downstream-a") {
                condition { true }
                output { DownstreamA(it.id) }
            }
            produce<Derived, DownstreamB>("downstream-b") {
                condition { true }
                output { DownstreamB(it.id) }
            }
            produce<Derived, DownstreamC>("downstream-c") {
                condition { true }
                output { DownstreamC(it.id) }
            }
        }
        val result = engine.evaluate(sources)
        return result.derived.size
    }
}
