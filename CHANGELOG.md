# Changelog

All notable changes to this project will be documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.1.0/),
and this project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

## [Unreleased]

## [0.1.0] - 2026-01-07

Initial release of Verdikt - a type-safe, multiplatform rules engine for Kotlin.

### Added

#### Core Rule Engine (`verdikt-core`)
- `Rule<Fact, Cause>` interface for synchronous rule definitions
- `AsyncRule<Fact, Cause>` interface for rules with suspend functions (I/O, database lookups)
- `RuleSet<Fact, Cause>` interface for composing multiple rules with typed failure reasons
- `Verdict<Cause>` sealed interface with `Pass` and `Fail` results
- `Failure<Cause>` generic data class with rule name and typed failure reason

#### DSL (`verdikt-core`)
- `rules<Fact, Cause> { }` builder for creating rule sets with typed failure reasons
- `rule<Fact, Cause>(name) { }` builder for standalone rules
- `condition { }` for synchronous evaluation
- `asyncCondition { }` for suspend evaluation
- `onFailure { fact -> reason }` for dynamic failure reasons based on the fact
- `onFailure(reason)` for static failure reasons (supports any type - String, enum, custom classes)

#### Typed Failure Reasons (`verdikt-core`)
- `Failure<Cause>` - generic failure type with typed `reason` property
- `Verdict.Fail.failures` - list of `Failure<*>` objects
- `Verdict.Fail.messages` - formatted failure messages as strings

#### Rule Set Features (`verdikt-core`)
- `evaluate(fact)` for synchronous evaluation
- `evaluateAsync(fact)` for concurrent async evaluation
- `rules` property to access all rules in a set
- `names` property for rule name list
- `size` and `isEmpty` properties
- `include(ruleSet)` to compose rule sets
- `plus()` operator to combine rule sets
- `failedRules(verdict)` extension to get rules that failed
- `passedRules(verdict)` extension to get rules that passed

#### Observability (`verdikt-core`)
- `sideEffect { }` extension for logging, analytics, and metrics
- Works on both individual rules and rule sets

#### Production Rules Engine (`verdikt-engine`)
- `Engine` interface for forward-chaining production rules
- `engine { }` DSL for building engines
- `produce<In, Out>(name) { }` for fact producers that derive new facts
- `validate<Fact>(name) { }` for validation rules within engine
- `evaluate(facts)` and `evaluateAsync(facts)` for engine execution
- `EngineResult` with derived facts, validation verdict, and execution metadata
- `derivedOfType<T>()` to access derived facts by type
- Forward chaining with automatic fixpoint iteration

#### Engine Features (`verdikt-engine`)
- `Phase` support for ordered execution stages
- `phase(name) { }` DSL for grouping rules
- `Guard` interface for conditional rule execution
- `guard(description) { ctx -> }` DSL for context-based guards
- `RuleContext` type-safe key-value context
- `ContextKey<T>` for type-safe context access
- `ruleContext { }` builder for creating contexts
- `priority` property for rule ordering within phases
- Async support with `asyncCondition { }` and `asyncOutput { }`
- Integration with `verdikt-core` rules via `addValidation(rule)`

#### Testing Utilities (`verdikt-test`)
- `assertPasses()` / `assertFails()` for rules
- `assertPasses()` / `assertFails()` for rule sets
- `assertRuleFails()` / `assertRuleFailsOnly()` for targeted assertions
- Async variants for all assertions
- Fluent assertion DSL with `message()`, `messageContains()`, `hasRule()`, `hasCount()`, `reason()`

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
