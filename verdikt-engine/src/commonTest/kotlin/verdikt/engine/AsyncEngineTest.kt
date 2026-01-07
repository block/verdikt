package verdikt.engine

import kotlinx.coroutines.delay
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

data class User(val id: String, val email: String)
data class UserVerified(val userId: String)

class AsyncEngineTest {

    @Test
    fun asyncProductionRuleWorksWithFireAsync() = runTest {
        val engine = engine {
            produce<User, UserVerified>("verify-user") {
                asyncCondition { user ->
                    delay(10)  // Simulate async lookup
                    user.email.contains("@")
                }
                asyncOutput { user ->
                    delay(10)  // Simulate async operation
                    UserVerified(user.id)
                }
            }
        }

        val result = engine.evaluateAsync(listOf(User("123", "test@example.com")))

        assertEquals(1, result.derived.size)
        val verified = result.derivedOfType<UserVerified>().first()
        assertEquals("123", verified.userId)
    }

    @Test
    fun syncFireFailsWhenAsyncRulesPresent() {
        val engine = engine {
            produce<User, UserVerified>("verify-user") {
                asyncCondition { true }
                output { UserVerified(it.id) }
            }
        }

        val exception = assertFailsWith<IllegalStateException> {
            engine.evaluate(listOf(User("123", "test@example.com")))
        }
        assertTrue(exception.message?.contains("async") == true)
    }

    @Test
    fun asyncValidationRuleWorksWithFireAsync() = runTest {
        val engine = engine {
            validate<User>("email-valid") {
                asyncCondition { user ->
                    delay(10)  // Simulate async validation
                    user.email.contains("@")
                }
                onFailure { "Invalid email: ${it.email}" }
            }
        }

        val result = engine.evaluateAsync(listOf(User("123", "invalid-email")))

        assertTrue(result.failed)
        assertTrue((result.verdict as verdikt.Verdict.Fail).failures[0].reason.toString().contains("invalid-email"))
    }

    @Test
    fun mixedSyncAndAsyncRulesWorkWithFireAsync() = runTest {
        val engine = engine {
            // Sync production rule
            produce<String, Int>("length") {
                condition { true }
                output { it.length }
            }

            // Async validation rule
            validate<Int>("positive") {
                asyncCondition { value ->
                    delay(10)
                    value > 0
                }
                onFailure { _: Int -> "Must be positive" }
            }
        }

        val result = engine.evaluateAsync(listOf("hello"))

        // Production derived Int(5)
        assertTrue(result.derivedOfType<Int>().contains(5))
        // Validation passed (5 > 0)
        assertTrue(result.passed)
    }

    @Test
    fun hasAsyncRulesDetectsAsyncCondition() {
        val syncEngine = engine {
            produce<String, Int>("sync") {
                condition { true }
                output { 1 }
            }
        }

        val asyncEngine = engine {
            produce<String, Int>("async") {
                asyncCondition { true }
                output { 1 }
            }
        }

        assertTrue(!syncEngine.hasAsyncRules)
        assertTrue(asyncEngine.hasAsyncRules)
    }

    @Test
    fun hasAsyncRulesDetectsAsyncOutput() {
        val asyncOutputEngine = engine {
            produce<String, Int>("async-output") {
                condition { true }
                asyncOutput { 1 }
            }
        }

        assertTrue(asyncOutputEngine.hasAsyncRules)
    }

    @Test
    fun hasAsyncRulesDetectsAsyncValidation() {
        val asyncValidationEngine = engine {
            validate<String>("async-val") {
                asyncCondition { true }
                onFailure { _: String -> "failed" }
            }
        }

        assertTrue(asyncValidationEngine.hasAsyncRules)
    }
}
