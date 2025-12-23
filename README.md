# Verdikt

A type-safe, multiplatform rules engine for Kotlin.

Verdikt provides a clean DSL for defining business rules that evaluate facts and return structured verdicts. It supports synchronous and asynchronous rules, accumulated failures, composable rule sets, and comprehensive testing utilities.

## Installation

```kotlin
// build.gradle.kts
dependencies {
    implementation("xyz.block:verdikt:0.1.0")

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

### Core Types

| Type | Description |
|------|-------------|
| `Rule<Fact, Reason>` | Interface for synchronous rules with typed failure reasons |
| `AsyncRule<Fact, Reason>` | Interface for async rules (I/O operations) with typed failure reasons |
| `RuleSet<Fact, Reason>` | Interface for organizing rules with typed failure reasons |
| `Verdict<Reason>` | Sealed interface: `Pass` or `Fail<Reason>` with typed failures |
| `Failure<Reason>` | Structured failure with rule name and typed reason |

### DSL Functions

| Function | Description |
|----------|-------------|
| `rules<Fact, Reason> { }` | Creates a rule set with typed failure reasons |
| `rule<Fact, Reason>(name) { }` | Creates a standalone `Rule<Fact, Reason>` |

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

## License

Apache License 2.0 - see [LICENSE](LICENSE)
