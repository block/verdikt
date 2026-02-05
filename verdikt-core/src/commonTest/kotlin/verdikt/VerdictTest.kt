package verdikt

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue

class VerdictTest {

    @Test
    fun passPlusPassEqualsPass() {
        val result = Verdict.Pass + Verdict.Pass
        assertIs<Verdict.Pass>(result)
    }

    @Test
    fun passPlusFailEqualsFail() {
        val failure = Failure("rule1", "reason1")
        val result = Verdict.Pass + Verdict.Fail(failure)

        assertIs<Verdict.Fail<String>>(result)
        assertEquals(1, result.failures.size)
        assertEquals(failure, result.failures[0])
    }

    @Test
    fun failPlusPassEqualsFail() {
        val failure = Failure("rule1", "reason1")
        val result = Verdict.Fail(failure) + Verdict.Pass

        assertIs<Verdict.Fail<String>>(result)
        assertEquals(1, result.failures.size)
        assertEquals(failure, result.failures[0])
    }

    @Test
    fun failPlusFailCombinesFailures() {
        val failure1 = Failure("rule1", "reason1")
        val failure2 = Failure("rule2", "reason2")
        val result = Verdict.Fail(failure1) + Verdict.Fail(failure2)

        assertIs<Verdict.Fail<String>>(result)
        assertEquals(2, result.failures.size)
        assertEquals(failure1, result.failures[0])
        assertEquals(failure2, result.failures[1])
    }

    @Test
    fun passedPropertyReturnsTrueForPass() {
        assertTrue(Verdict.Pass.passed)
        assertFalse(Verdict.Pass.failed)
    }

    @Test
    fun failedPropertyReturnsTrueForFail() {
        val fail = Verdict.Fail(Failure("rule", "reason"))
        assertTrue(fail.failed)
        assertFalse(fail.passed)
    }

    @Test
    fun messagesReturnsFormattedStrings() {
        val fail = Verdict.Fail(
            listOf(
                Failure("rule1", "reason1"),
                Failure("rule2", "reason2")
            )
        )

        val messages = fail.messages
        assertEquals(2, messages.size)
        assertEquals("Rule 'rule1' failed: reason1", messages[0])
        assertEquals("Rule 'rule2' failed: reason2", messages[1])
    }

    @Test
    fun failuresProvidesStructuredAccess() {
        val failure1 = Failure("rule1", "reason1")
        val failure2 = Failure("rule2", "reason2")
        val fail = Verdict.Fail(listOf(failure1, failure2))

        assertEquals(2, fail.failures.size)
        assertEquals("rule1", fail.failures[0].ruleName)
        assertEquals("reason1", fail.failures[0].reason)
        assertEquals("rule2", fail.failures[1].ruleName)
        assertEquals("reason2", fail.failures[1].reason)
    }

    @Test
    fun singleFailureConstructorWorks() {
        val failure = Failure("rule1", "reason1")
        val fail = Verdict.Fail(failure)

        assertEquals(1, fail.failures.size)
        assertEquals(failure, fail.failures[0])
    }

    @Test
    fun combiningMultipleFailuresPreservesOrder() {
        val fail1 = Verdict.Fail(listOf(Failure("a", "1"), Failure("b", "2")))
        val fail2 = Verdict.Fail(listOf(Failure("c", "3")))
        val result = fail1 + fail2

        assertIs<Verdict.Fail<String>>(result)
        assertEquals(3, result.failures.size)
        assertEquals("a", result.failures[0].ruleName)
        assertEquals("b", result.failures[1].ruleName)
        assertEquals("c", result.failures[2].ruleName)
    }

    @Test
    fun failureCountReturnsZeroForPass() {
        assertEquals(0, Verdict.Pass.failureCount)
    }

    @Test
    fun failureCountReturnsCountForFail() {
        val fail = Verdict.Fail(listOf(Failure("a", "1"), Failure("b", "2")))
        assertEquals(2, fail.failureCount)
    }

    @Test
    fun failedRuleNamesReturnsEmptyListForPass() {
        assertEquals(emptyList(), Verdict.Pass.failedRuleNames)
    }

    @Test
    fun failedRuleNamesReturnsRuleNamesForFail() {
        val fail = Verdict.Fail(listOf(Failure("rule1", "reason1"), Failure("rule2", "reason2")))
        assertEquals(listOf("rule1", "rule2"), fail.failedRuleNames)
    }

    @Test
    fun hasFailureReturnsFalseForPass() {
        assertFalse(Verdict.Pass.hasFailure("any-rule"))
    }

    @Test
    fun hasFailureReturnsTrueWhenRuleMatches() {
        val fail = Verdict.Fail(listOf(Failure("rule1", "reason1"), Failure("rule2", "reason2")))
        assertTrue(fail.hasFailure("rule1"))
        assertTrue(fail.hasFailure("rule2"))
    }

    @Test
    fun hasFailureReturnsFalseWhenRuleDoesNotMatch() {
        val fail = Verdict.Fail(listOf(Failure("rule1", "reason1")))
        assertFalse(fail.hasFailure("rule2"))
    }

    @Test
    fun failuresMatchingReturnsEmptyListForPass() {
        val result = Verdict.Pass.failuresMatching { true }
        assertEquals(emptyList(), result)
    }

    @Test
    fun failuresMatchingFiltersFailures() {
        val fail = Verdict.Fail(
            listOf(
                Failure("rule1", "error"),
                Failure("rule2", "warning"),
                Failure("rule3", "error")
            )
        )
        val errors = fail.failuresMatching { it.reason == "error" }
        assertEquals(2, errors.size)
        assertEquals("rule1", errors[0].ruleName)
        assertEquals("rule3", errors[1].ruleName)
    }
}
