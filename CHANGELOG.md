# Changelog

All notable changes to this project will be documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.1.0/),
and this project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

## [Unreleased]

### Changed

- **Breaking:** Renamed `verdikt` artifact to `verdikt-core` for consistency with `verdikt-engine` and `verdikt-test`
  - Update your dependencies: `xyz.block:verdikt` → `xyz.block:verdikt-core`
- **Breaking:** Removed `Cause` type parameter from engine layer - each validation rule now infers its own failure type from `onFailure`
  - `Engine<Cause>` → `Engine`
  - `Session<Cause>` → `Session`
  - `EngineResult<Cause>` → `EngineResult` (verdict is now `Verdict<*>`)
  - `engine<String> { }` → `engine { }`
- **Breaking:** Simplified Engine API - replaced `session()` with `evaluate()`
  - `Session` is now internal (implementation detail)
  - Old: `val session = engine.session(); session.insert(facts); val result = session.fire()`
  - New: `val result = engine.evaluate(listOf(facts))`
  - For async: `val result = engine.evaluateAsync(listOf(facts))`
  - Context for guards: `engine.evaluate(listOf(facts), context = myContext)`

## [0.1.0] - 2024-12-31

### Added

#### Core Rule Engine
- `Rule<Fact>` interface for synchronous rule definitions
- `AsyncRule<Fact>` interface for rules with suspend functions (I/O, database lookups)
- `RuleSet<Fact, Reason>` interface for composing multiple rules with typed failure reasons
- `Verdict<Reason>` sealed interface with `Pass` and `Fail` results
- `Failure<Reason>` generic data class with rule name and typed failure reason

#### DSL
- `rules<Fact, Reason> { }` builder for creating rule sets with typed failure reasons
- `rule<Fact, Reason>(name) { }` builder for standalone rules
- `condition { }` for synchronous evaluation
- `asyncCondition { }` for suspend evaluation
- `onFailure { fact -> reason }` for dynamic failure reasons based on the fact
- `onFailure(reason)` for static failure reasons (supports any type - String, enum, custom classes)

#### Typed Failure Reasons
- `Failure<Reason>` - generic failure type with typed `reason` property
- `Verdict.Fail.failures` - list of `Failure<*>` objects
- `Verdict.Fail.messages` - formatted failure messages as strings

#### Rule Set Features
- `evaluate(fact)` for synchronous evaluation
- `evaluateAsync(fact)` for concurrent async evaluation
- `rules` property to access all rules in a set
- `names` property for rule name list
- `size` and `isEmpty` properties
- `include(ruleSet)` to compose rule sets
- `plus()` operator to combine rule sets
- `failedRules(verdict)` extension to get rules that failed
- `passedRules(verdict)` extension to get rules that passed

#### Observability
- `sideEffect { }` extension for logging, analytics, and metrics
- Works on both individual rules and rule sets

#### Testing Utilities (`verdikt-test` module)
- `assertPasses()` / `assertFails()` for rules
- `assertPasses()` / `assertFails()` for rule sets
- `assertRuleFails()` / `assertRuleFailsOnly()` for targeted assertions
- Async variants for all assertions
- Fluent assertion DSL with `messageContains()`, `hasRule()`, `hasCount()`, `reason()`

#### Platform Support
- JVM (11+)
- Android
- iOS (arm64, x64, simulatorArm64)
- macOS (arm64, x64)
- Linux (x64)
- JavaScript (browser, Node.js)

### Notes

- Initial release of Verdikt
- Pre-1.0 API may have breaking changes in future versions

[Unreleased]: https://github.com/block/verdikt/compare/v0.1.0...HEAD
[0.1.0]: https://github.com/block/verdikt/releases/tag/v0.1.0
