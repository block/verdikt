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
        val engine = engine<String> {
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

        val session = engine.session()
        session.insert(User("123", "test@example.com"))
        val result = session.fireAsync()

        assertEquals(1, result.derived.size)
        val verified = result.derivedOfType<UserVerified>().first()
        assertEquals("123", verified.userId)
    }

    @Test
    fun syncFireFailsWhenAsyncRulesPresent() {
        val engine = engine<String> {
            produce<User, UserVerified>("verify-user") {
                asyncCondition { true }
                output { UserVerified(it.id) }
            }
        }

        val session = engine.session()
        session.insert(User("123", "test@example.com"))

        val exception = assertFailsWith<IllegalStateException> {
            session.fire()
        }
        assertTrue(exception.message?.contains("async") == true)
    }

    @Test
    fun asyncValidationRuleWorksWithFireAsync() = runTest {
        val engine = engine<String> {
            validate<User>("email-valid") {
                asyncCondition { user ->
                    delay(10)  // Simulate async validation
                    user.email.contains("@")
                }
                onFailure { "Invalid email: ${it.email}" }
            }
        }

        val session = engine.session()
        session.insert(User("123", "invalid-email"))
        val result = session.fireAsync()

        assertTrue(result.failed)
        assertTrue((result.verdict as verdikt.Verdict.Fail).failures[0].reason.contains("invalid-email"))
    }

    @Test
    fun mixedSyncAndAsyncRulesWorkWithFireAsync() = runTest {
        val engine = engine<String> {
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
                onFailure { "Must be positive" }
            }
        }

        val session = engine.session()
        session.insert("hello")
        val result = session.fireAsync()

        // Production derived Int(5)
        assertTrue(result.derivedOfType<Int>().contains(5))
        // Validation passed (5 > 0)
        assertTrue(result.passed)
    }

    @Test
    fun hasAsyncRulesDetectsAsyncCondition() {
        val syncEngine = engine<String> {
            produce<String, Int>("sync") {
                condition { true }
                output { 1 }
            }
        }

        val asyncEngine = engine<String> {
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
        val asyncOutputEngine = engine<String> {
            produce<String, Int>("async-output") {
                condition { true }
                asyncOutput { 1 }
            }
        }

        assertTrue(asyncOutputEngine.hasAsyncRules)
    }

    @Test
    fun hasAsyncRulesDetectsAsyncValidation() {
        val asyncValidationEngine = engine<String> {
            validate<String>("async-val") {
                asyncCondition { true }
                onFailure("failed")
            }
        }

        assertTrue(asyncValidationEngine.hasAsyncRules)
    }
}
