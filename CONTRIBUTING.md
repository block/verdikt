# Contributing to Verdikt

Thank you for your interest in contributing to Verdikt! This document provides guidelines and instructions for contributing.

## Development Setup

### Prerequisites

- JDK 11 or higher
- Gradle 8.x (wrapper included)

### Building the Project

```bash
# Clone the repository
git clone https://github.com/block/verdikt.git
cd verdikt

# Build all modules
./gradlew build

# Run tests
./gradlew allTests
```

### Project Structure

```
verdikt/
├── verdikt-core/     # Core rules DSL and evaluation
├── verdikt-engine/   # Forward-chaining production rules engine
├── verdikt-test/     # Testing utilities and assertions
└── demo/             # Demo applications
```

## Running Tests

```bash
# Run all tests across all platforms
./gradlew allTests

# Run JVM tests only (faster)
./gradlew jvmTest

# Run tests for a specific module
./gradlew :verdikt-core:jvmTest
./gradlew :verdikt-engine:jvmTest
./gradlew :verdikt-test:jvmTest
```

## Code Style

- Follow [Kotlin coding conventions](https://kotlinlang.org/docs/coding-conventions.html)
- Use explicit API mode (all public declarations must have explicit visibility)
- Add KDoc to all public APIs with examples where helpful
- Keep line length under 120 characters

## Making Changes

### 1. Create a Branch

```bash
git checkout -b feature/your-feature-name
```

### 2. Make Your Changes

- Write tests for new functionality
- Update documentation as needed
- Follow existing code patterns

### 3. Verify Your Changes

```bash
# Run tests
./gradlew allTests

# Check API compatibility
./gradlew apiCheck

# If you added/changed public API, update the dump
./gradlew apiDump
```

### 4. Commit Your Changes

Write clear, concise commit messages:

```
Add support for rule composition with AND/OR operators

- Add `and()` and `or()` extension functions
- Update RuleSet to support composite rules
- Add tests for composite behavior
```

### 5. Submit a Pull Request

- Provide a clear description of the changes
- Reference any related issues
- Ensure CI passes

## API Guidelines

### Public API Design

- Use reified type parameters where possible for better ergonomics
- Prefer DSL builders over complex constructors
- Make implementation details internal
- Use sealed interfaces/classes for exhaustive when expressions

### Adding New Features

1. Discuss significant changes in an issue first
2. Start with the public API design
3. Implement with comprehensive tests
4. Add KDoc with examples
5. Update README if user-facing

### Breaking Changes

For pre-1.0 releases, breaking changes are acceptable but should be:
- Clearly documented in CHANGELOG.md
- Accompanied by migration guidance
- Discussed in the PR

## Testing Guidelines

### Test Structure

```kotlin
class MyFeatureTest {
    @Test
    fun `descriptive test name with backticks`() {
        // Given
        val input = ...

        // When
        val result = ...

        // Then
        assertEquals(expected, result)
    }
}
```

### What to Test

- Happy path scenarios
- Edge cases and error conditions
- Async behavior (use `runTest` from kotlinx-coroutines-test)
- Type safety (ensure generics work correctly)

## Questions?

- Open an issue for bugs or feature requests
- Start a discussion for questions or ideas

## License

By contributing, you agree that your contributions will be licensed under the Apache License 2.0.
