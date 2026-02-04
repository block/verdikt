# Verdikt

A type-safe, multiplatform rules engine for Kotlin.

Verdikt provides a clean DSL for defining business rules that evaluate facts and return structured verdicts. It supports synchronous and asynchronous rules, accumulated failures, composable rule sets, and comprehensive testing utilities.

## Installation

```kotlin
// build.gradle.kts
dependencies {
    implementation("xyz.block:verdikt-core:0.1.0")

    // Optional: Forward-chaining production rules engine
    implementation("xyz.block:verdikt-engine:0.1.0")

    // Optional: Testing utilities
    testImplementation("xyz.block:verdikt-test:0.1.0")
}
```

## Quick Start

```kotlin
import verdikt.*

// Define a fact
data class Player(val name: String, val score: Int)

// Create rules inline
val playerRules = rules<Player, String> {
    rule("score-check") {
        description = "Score must be at least 100"
        condition { it.score >= 100 }
        onFailure { player -> "Score ${player.score} is below minimum 100" }
    }

    rule("name-check") {
        description = "Name is required"
        condition { it.name.isNotBlank() }
    }
}

// Evaluate
val verdict = playerRules.evaluate(Player("Alice", 150))
when (verdict) {
    is Verdict.Pass -> println("All rules passed!")
    is Verdict.Fail -> verdict.failures.forEach { println(it) }
}
```

## Stability

Verdikt is currently pre-1.0 (version 0.1.0). The core API is stable and well-tested, but breaking changes may occur before version 1.0. All changes are documented in [CHANGELOG.md](CHANGELOG.md).

## Core Concepts

### Rules

Rules are reusable validation logic. Define them as objects for reusability:

```kotlin
object ScoreRule : Rule<Player, String> {
    override val name = "score-check"
    override val description = "Score must be at least 100"

    override fun evaluate(fact: Player) = fact.score >= 100

    override fun failureReason(fact: Player) =
        "Score ${fact.score} is below minimum 100"
}
```

Or inline with the DSL:

```kotlin
val scoreRule = rule<Player, String>("score-check") {
    description = "Score must be at least 100"
    condition { it.score >= 100 }
    onFailure { player -> "Score ${player.score} is below minimum 100" }
}
```

### Rule Sets

Group related rules into reusable sets:

```kotlin
// Define reusable rules
object ScoreCheck : Rule<Player, String> {
    override val name = "score-check"
    override val description = "Score must be at least 100"
    override fun evaluate(fact: Player) = fact.score >= 100
    override fun failureReason(fact: Player) =
        "Score ${fact.score} is below minimum 100"
}

object NameCheck : Rule<Player, String> {
    override val name = "name-check"
    override val description = "Name is required"
    override fun evaluate(fact: Player) = fact.name.isNotBlank()
    override fun failureReason(fact: Player) = description
}

// Create a rule set
val playerRules = rules<Player, String> {
    add(ScoreCheck)
    add(NameCheck)
}

// Usage
val verdict = playerRules.evaluate(player)
```

Compose rule sets:

```kotlin
val allRules = rules<Player, String> {
    include(PlayerRules)
    include(TeamRules)

    rule("custom") {
        condition { it.isActive }
    }
}
```

### Verdicts

Rule evaluation returns a `Verdict<Reason>`:

```kotlin
sealed interface Verdict<out Reason : Any> {
    data object Pass : Verdict<Nothing>
    data class Fail<out Reason : Any>(val failures: List<Failure<Reason>>) : Verdict<Reason>
}
```

Handle results fluently:

```kotlin
rules.evaluate(fact).handle(
    onPass = { println("All rules passed!") },
    onFail = { failures -> failures.forEach { println(it) } }
)
```

Or use pattern matching / convenience properties:

```kotlin
// Pattern matching
when (val verdict = rules.evaluate(fact)) {
    is Verdict.Pass -> handleSuccess()
    is Verdict.Fail -> handleFailures(verdict.failures)
}

// Convenience properties
if (verdict.passed) { ... }
if (verdict.failed) { ... }

// Query failures
verdict.failureCount              // Number of failures (0 if passed)
verdict.failedRuleNames           // Names of failed rules
verdict.hasFailure("rule-name")   // Check if specific rule failed
verdict.failuresMatching { it.reason is MyError }  // Filter failures
```

### Typed Failure Reasons

Failure reasons can be any type, not just strings. The `Reason` type is specified as the second type parameter on `RuleSet`:

```kotlin
// Define typed error reasons
enum class EligibilityReason {
    LOW_SCORE,
    NO_CREDIT_HISTORY,
    INSUFFICIENT_BALANCE
}

// Use typed reasons in rules - Reason type is EligibilityReason
val eligibilityRules = rules<Applicant, EligibilityReason> {
    rule("score-check") {
        condition { it.score >= 100 }
        onFailure(EligibilityReason.LOW_SCORE)
    }

    rule("credit-check") {
        condition { it.creditScore != null }
        onFailure(EligibilityReason.NO_CREDIT_HISTORY)
    }
}

// Access typed reasons - no casting needed!
val verdict = eligibilityRules.evaluate(applicant)
if (verdict is Verdict.Fail<EligibilityReason>) {
    verdict.failures.forEach { failure ->
        // failure.reason is typed as EligibilityReason
        println("${failure.ruleName}: ${failure.reason}")
    }
}
```

For string-based failure messages (the common case):

```kotlin
val playerRules = rules<Player, String> {
    rule("score-check") {
        condition { it.score >= 100 }
        onFailure { player -> "Score ${player.score} is below minimum 100" }
    }
}
```

You can also use typed reasons with object rules:

```kotlin
object ScoreCheck : Rule<Applicant, EligibilityReason> {
    override val name = "score-check"
    override fun evaluate(fact: Applicant) = fact.score >= 100
    override fun failureReason(fact: Applicant) = EligibilityReason.LOW_SCORE
}
```

## Async Rules

For rules that perform I/O operations (database queries, API calls):

```kotlin
class CreditCheckRule(
    private val creditService: CreditService
) : AsyncRule<User, String> {
    override val name = "credit-check"
    override val description = "Must have good credit standing"

    override suspend fun evaluate(fact: User) =
        creditService.checkStanding(fact.id)

    override fun failureReason(fact: User) =
        "Credit check failed for user ${fact.id}"
}

// Add to rule set
val userRules = rules<User, String> {
    add(CreditCheckRule(creditService))

    // Or inline
    rule("balance-check") {
        asyncCondition { userService.getBalance(it.id) >= 0 }
    }
}

// Evaluate asynchronously - runs concurrently, results in order
val verdict = userRules.evaluateAsync(user)
```

## Side Effects

Add observability without affecting rule evaluation:

```kotlin
val observedRules = myRules.sideEffect { fact, verdict ->
    logger.info("Evaluated $fact: $verdict")
    metrics.record(verdict)
}
```

## Production Rules Engine

The `verdikt-engine` module provides a forward-chaining production rules engine for complex scenarios where rules can derive new facts:

```kotlin
import verdikt.engine.*

// Define fact types
data class Customer(val id: String, val totalSpend: Double)
data class VipStatus(val customerId: String, val tier: String)
data class Discount(val customerId: String, val percent: Int)
data class Order(val customerId: String, val amount: Double)

// Create an engine with fact producers and validation rules
val pricingEngine = engine {
    // Fact producers derive new facts from existing ones
    produce<Customer, VipStatus>("vip-check") {
        description = "Customers who spent over 10k are VIPs"
        condition { it.totalSpend > 10_000 }
        output { customer -> VipStatus(customer.id, "gold") }
    }

    // Rules can chain - VipStatus triggers discount
    produce<VipStatus, Discount>("vip-discount") {
        condition { it.tier == "gold" }
        output { vip -> Discount(vip.customerId, 20) }
    }

    // Validation rules check facts without producing new ones
    validate<Order>("minimum-order") {
        description = "Order must be at least $10"
        condition { it.amount >= 10.0 }
        onFailure { order -> "Order amount ${order.amount} is below minimum" }
    }
}

// Evaluate - facts are derived automatically via forward chaining
val result = pricingEngine.evaluate(listOf(customer, order))

// Access derived facts
val vipStatus = result.derivedOfType<VipStatus>().firstOrNull()
val discount = result.derivedOfType<Discount>().firstOrNull()

// Check validation verdict
when (result.verdict) {
    is Verdict.Pass -> println("All validations passed")
    is Verdict.Fail -> println("Validation failed: ${result.verdict.messages}")
}
```

### Engine Features

**Phased Execution**: Group rules into ordered phases:

```kotlin
val engine = engine {
    phase("discounts") {
        produce<Order, Discount>("bulk-discount") {
            condition { it.quantity > 100 }
            output { Discount(it.id, 15) }
        }
    }

    phase("taxes") {
        produce<Order, Tax>("sales-tax") {
            condition { true }
            output { Tax(it.id, it.total * 0.08) }
        }
    }
}
```

**Guards**: Skip rules based on context:

```kotlin
produce<Order, Discount>("vip-discount") {
    guard("Customer must be VIP tier") { ctx ->
        ctx[CustomerTier] in listOf("gold", "platinum")
    }
    condition { it.subtotal > 100 }
    output { Discount(it.id, 10) }
}
```

**Async Support**: For rules that need I/O:

```kotlin
produce<Order, FraudScore>("fraud-check") {
    asyncCondition { fraudService.shouldCheck(it) }
    asyncOutput { order -> fraudService.score(order) }
}

// Use evaluateAsync for engines with async rules
val result = engine.evaluateAsync(facts)
```

### Configuration

The engine can be configured with `EngineConfig`:

```kotlin
// Default configuration
val engine = engine { ... }

// Custom configuration
val engine = engine(EngineConfig(maxIterations = 10_000, enableTracing = true)) { ... }
```

| Option | Default | Description |
|--------|---------|-------------|
| `maxIterations` | 1,000,000 | Maximum rule firing iterations before throwing `MaxIterationsExceededException`. Prevents infinite loops from runaway rules. |
| `enableTracing` | false | When true, records rule activations in `EngineResult.trace` for debugging. |

### Execution Tracing

Enable tracing to debug rule execution:

```kotlin
val engine = engine(EngineConfig(enableTracing = true)) {
    produce<Customer, VipStatus>("vip-check") {
        condition { it.totalSpend > 10_000 }
        output { VipStatus(it.id, "gold") }
    }
}

val result = engine.evaluate(listOf(customer))

// Inspect what rules fired
result.trace.forEach { activation ->
    println("${activation.ruleName}: ${activation.inputFact} -> ${activation.outputFacts}")
}
```

### Performance

The engine uses **type-based indexing** for efficient fact lookups. When querying facts by type
(e.g., `facts.ofType<Customer>()`), lookups are O(1) instead of O(n) linear scans.

**Algorithm Decision**: We evaluated Rete, TREAT, and LEAPS algorithms commonly used in production
rule systems. Type-based indexing was chosen because:

- Verdikt's API primarily uses single-type conditions (`produce<Customer, VipStatus>`)
- Simple implementation maintains multiplatform compatibility
- Provides 5-10x speedup for type-based lookups with minimal memory overhead
- Architecture supports future Rete-style enhancements if complex join optimization is needed

## Testing

The `verdikt-test` module provides assertion utilities:

```kotlin
import verdikt.test.*

@Test
fun `score rule passes for high scores`() {
    ScoreRule.assertPasses(Player("Alice", 150))
}

@Test
fun `score rule fails for low scores`() {
    ScoreRule.assertFails(Player("Bob", 50)) {
        message("Score 50 is below minimum 100")
    }
}

@Test
fun `rule set collects all failures`() {
    PlayerRules.assertFails(Player("", 50)) {
        hasCount(2)
        hasRule("score-check")
        hasRule("name-check")
    }
}
```

## Platform Support

Verdikt is built with Kotlin Multiplatform and supports:

- JVM
- Android
- iOS (arm64, x64, simulator)
- macOS (arm64, x64)
- Linux (x64)
- JavaScript (browser, Node.js)

## API Reference

### Core Types (verdikt-core)

| Type | Description |
|------|-------------|
| `Rule<Fact, Reason>` | Interface for synchronous rules with typed failure reasons |
| `AsyncRule<Fact, Reason>` | Interface for async rules (I/O operations) with typed failure reasons |
| `RuleSet<Fact, Reason>` | Interface for organizing rules with typed failure reasons |
| `Verdict<Reason>` | Sealed interface: `Pass` or `Fail<Reason>` with typed failures |
| `Failure<Reason>` | Structured failure with rule name and typed reason |

### Engine Types (verdikt-engine)

| Type | Description |
|------|-------------|
| `Engine` | Forward-chaining production rules engine |
| `EngineConfig` | Configuration for engine behavior (iteration limits, tracing) |
| `EngineResult` | Result containing derived facts, validation verdict, trace, and metadata |
| `RuleActivation` | Record of a rule firing (rule name, input fact, output facts, priority) |
| `FactProducer<In, Out>` | Interface for rules that produce new facts |
| `Phase` | Named execution phase grouping related rules |
| `Guard` | Conditional gate that can skip rules based on context |
| `RuleContext` | Type-safe key-value context for guards |
| `ContextKey<T>` | Type-safe key for context values |
| `MaxIterationsExceededException` | Thrown when rule execution exceeds configured limit |

### DSL Functions

| Function | Description |
|----------|-------------|
| `rules<Fact, Reason> { }` | Creates a rule set with typed failure reasons |
| `rule<Fact, Reason>(name) { }` | Creates a standalone `Rule<Fact, Reason>` |
| `engine(config?) { }` | Creates a forward-chaining production rules engine with optional config |
| `ruleContext { }` | Creates a type-safe context for guards |

### FactProducerBuilder (inside `produce<In, Out>`)

| Method | Description |
|--------|-------------|
| `condition { }` | Simple condition on the input fact |
| `asyncCondition { }` | Async condition for I/O operations |
| `output { }` | Produce a single output fact |
| `asyncOutput { }` | Async output for I/O operations |
| `guard(desc) { ctx -> }` | Skip rule based on context |
| `priority` | Execution priority (higher runs first) |

### RuleSetBuilder

| Method | Description |
|--------|-------------|
| `rule(name) { }` | Add inline rule (use `asyncCondition` for async) |
| `add(rule)` | Add a `Rule<Fact, Reason>` or `AsyncRule<Fact, Reason>` |
| `include(ruleSet)` | Include rules from another set |

### RuleSet Methods

| Method | Description |
|--------|-------------|
| `evaluate(fact)` | Evaluate synchronously |
| `evaluateAsync(fact)` | Evaluate asynchronously |
| `size` | Number of rules |
| `isEmpty` | True if no rules |
| `names` | List of rule names |
| `plus(other)` | Combine two rule sets |

### Verdict Properties and Extensions

| Member | Description |
|--------|-------------|
| `passed` | True if verdict is `Pass` |
| `failed` | True if verdict is `Fail` |
| `failureCount` | Number of failures (0 if passed) |
| `failedRuleNames` | List of failed rule names (empty if passed) |
| `hasFailure(ruleName)` | Check if a specific rule failed |
| `failuresMatching { }` | Filter failures by predicate |

### Verdict.Fail Properties

| Property | Description |
|----------|-------------|
| `failures` | List of `Failure<*>` objects |
| `messages` | Formatted failure messages as strings |

### Failure<Reason> Properties

| Member | Description |
|--------|-------------|
| `ruleName` | Name of the failed rule |
| `reason` | The failure reason (typed as `Reason`) |

### EngineResult Methods

| Method | Description |
|--------|-------------|
| `derivedOfType<T>()` | Get derived facts of a specific type |
| `factsOfType<T>()` | Get all facts of a specific type |
| `failuresOfType<T>()` | Get failures with a specific cause type |
| `passed` | True if all validations passed |
| `failed` | True if any validation failed |

## License

Apache License 2.0 - see [LICENSE](LICENSE)
