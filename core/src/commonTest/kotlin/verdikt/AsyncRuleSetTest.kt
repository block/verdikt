package verdikt

import kotlinx.coroutines.delay
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.measureTime

class AsyncRuleSetTest {

    data class Person(val id: String, val name: String, val score: Int)

    @Test
    fun asyncRulesRunConcurrently() = runTest {
        val ruleSet = rules<Person, String> {
            rule("slow-check-1") {
                asyncCondition {
                    delay(100.milliseconds)
                    true
                }
            }
            rule("slow-check-2") {
                asyncCondition {
                    delay(100.milliseconds)
                    true
                }
            }
        }

        val duration = measureTime {
            ruleSet.evaluateAsync(Person("1", "Alice", 150))
        }

        // If sequential, would take ~200ms. Concurrent should take ~100ms.
        // Allow some buffer for test overhead.
        assertTrue(duration < 180.milliseconds, "Expected concurrent execution but took $duration")
    }

    @Test
    fun evaluateAsyncWorksWithMixedSyncAsyncRules() = runTest {
        val ruleSet = rules<Person, String> {
            rule("sync-rule") {
                condition { it.score >= 100 }
            }
            rule("async-rule") {
                asyncCondition {
                    delay(10.milliseconds)
                    it.name.isNotBlank()
                }
            }
        }

        val result = ruleSet.evaluateAsync(Person("1", "Alice", 150))
        assertIs<Verdict.Pass>(result)
    }

    @Test
    fun resultsPreserveInsertionOrder() = runTest {
        val ruleSet = rules<Person, String> {
            rule("first") {
                asyncCondition {
                    delay(50.milliseconds) // Finishes later
                    false
                }
                onFailure("First failed")
            }
            rule("second") {
                asyncCondition {
                    delay(10.milliseconds) // Finishes earlier
                    false
                }
                onFailure("Second failed")
            }
            rule("third") {
                asyncCondition {
                    delay(30.milliseconds) // Finishes in middle
                    false
                }
                onFailure("Third failed")
            }
        }

        val result = ruleSet.evaluateAsync(Person("1", "Alice", 150))

        assertIs<Verdict.Fail<String>>(result)
        assertEquals(3, result.failures.size)
        // Results should be in definition order, not completion order
        assertEquals("first", result.failures[0].ruleName)
        assertEquals("second", result.failures[1].ruleName)
        assertEquals("third", result.failures[2].ruleName)
    }

    @Test
    fun failuresFromMultipleAsyncRulesAreCollected() = runTest {
        val ruleSet = rules<Person, String> {
            rule("score-check") {
                asyncCondition {
                    delay(10.milliseconds)
                    it.score >= 100
                }
                onFailure { person -> "Score ${person.score} is below minimum 100" }
            }
            rule("name-check") {
                asyncCondition {
                    delay(10.milliseconds)
                    it.name.length >= 3
                }
                onFailure { person -> "Name '${person.name}' is too short" }
            }
        }

        val result = ruleSet.evaluateAsync(Person("1", "Al", 50))

        assertIs<Verdict.Fail<String>>(result)
        assertEquals(2, result.failures.size)
        assertEquals("score-check", result.failures[0].ruleName)
        assertEquals("Score 50 is below minimum 100", result.failures[0].reason)
        assertEquals("name-check", result.failures[1].ruleName)
        assertEquals("Name 'Al' is too short", result.failures[1].reason)
    }

    @Test
    fun emptyRuleSetReturnsPassAsync() = runTest {
        val ruleSet = rules<Person, String> { }
        val result = ruleSet.evaluateAsync(Person("1", "Alice", 150))
        assertIs<Verdict.Pass>(result)
    }

    @Test
    fun allPassingAsyncRulesReturnPass() = runTest {
        val ruleSet = rules<Person, String> {
            rule("check1") {
                asyncCondition {
                    delay(10.milliseconds)
                    true
                }
            }
            rule("check2") {
                asyncCondition {
                    delay(10.milliseconds)
                    true
                }
            }
        }

        val result = ruleSet.evaluateAsync(Person("1", "Alice", 150))
        assertIs<Verdict.Pass>(result)
    }

    @Test
    fun syncOnlyRuleSetWorksWithEvaluateAsync() = runTest {
        val ruleSet = rules<Person, String> {
            rule("sync-check") {
                condition { it.score >= 100 }
            }
        }

        val result = ruleSet.evaluateAsync(Person("1", "Alice", 150))
        assertIs<Verdict.Pass>(result)
    }

    @Test
    fun asyncRuleWithDynamicFailureMessage() = runTest {
        val ruleSet = rules<Person, String> {
            rule("id-check") {
                asyncCondition {
                    delay(10.milliseconds)
                    it.id.startsWith("USER-")
                }
                onFailure { person -> "Invalid ID format: ${person.id}" }
            }
        }

        val result = ruleSet.evaluateAsync(Person("123", "Alice", 150))

        assertIs<Verdict.Fail<String>>(result)
        assertEquals("Invalid ID format: 123", result.failures[0].reason)
    }
}
