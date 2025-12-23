package verdikt

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals

class FailureTest {

    @Test
    fun toStringFormatsAsRuleNameFailedReason() {
        val failure = Failure("score-check", "Must have score of 100 or higher")
        assertEquals("Rule 'score-check' failed: Must have score of 100 or higher", failure.toString())
    }

    @Test
    fun dataClassEqualityWorks() {
        val failure1 = Failure("rule1", "reason1")
        val failure2 = Failure("rule1", "reason1")
        val failure3 = Failure("rule2", "reason1")

        assertEquals(failure1, failure2)
        assertEquals(failure1.hashCode(), failure2.hashCode())
        assertNotEquals(failure1, failure3)
    }

    @Test
    fun copyCreatesModifiedInstance() {
        val original = Failure("rule1", "reason1")
        val copied = original.copy(reason = "new reason")

        assertEquals("rule1", copied.ruleName)
        assertEquals("new reason", copied.reason)
    }
}
