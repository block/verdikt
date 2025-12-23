package verdikt.test

import verdikt.Rule
import verdikt.rules
import kotlin.test.Test
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class RuleSetAssertionsTest {

    data class Person(val name: String, val score: Int, val email: String)

    object ScoreCheck : Rule<Person, String> {
        override val name = "score-check"
        override fun evaluate(fact: Person) = fact.score >= 100
        override fun failureReason(fact: Person) = "Score ${fact.score} is below minimum 100"
    }

    object NameCheck : Rule<Person, String> {
        override val name = "name-check"
        override fun evaluate(fact: Person) = fact.name.isNotBlank()
        override fun failureReason(fact: Person) = "Name cannot be blank"
    }

    object EmailCheck : Rule<Person, String> {
        override val name = "email-check"
        override fun evaluate(fact: Person) = "@" in fact.email
        override fun failureReason(fact: Person) = "Email must contain @"
    }

    private val ruleSet = rules<Person, String> {
        add(ScoreCheck)
        add(NameCheck)
        add(EmailCheck)
    }

    // ========================================================================
    // assertPasses tests
    // ========================================================================

    @Test
    fun assertPassesSucceedsWhenAllRulesPass() {
        ruleSet.assertPasses(Person("Alice", 150, "alice@example.com"))
    }

    @Test
    fun assertPassesFailsWhenAnyRuleFails() {
        assertFailsWith<AssertionError> {
            ruleSet.assertPasses(Person("Alice", 50, "alice@example.com"))
        }
    }

    // ========================================================================
    // assertFails tests
    // ========================================================================

    @Test
    fun assertFailsSucceedsWhenAnyRuleFails() {
        ruleSet.assertFails(Person("Alice", 50, "alice@example.com"))
    }

    @Test
    fun assertFailsFailsWhenAllRulesPass() {
        assertFailsWith<AssertionError> {
            ruleSet.assertFails(Person("Alice", 150, "alice@example.com"))
        }
    }

    // ========================================================================
    // assertFails with block tests
    // ========================================================================

    @Test
    fun assertFailsWithBlockHasCount() {
        ruleSet.assertFails(Person("", 50, "invalid")) {
            hasCount(3)
        }
    }

    @Test
    fun assertFailsWithBlockHasCountFails() {
        assertFailsWith<AssertionError> {
            ruleSet.assertFails(Person("", 50, "invalid")) {
                hasCount(2)
            }
        }
    }

    @Test
    fun assertFailsWithBlockHasRule() {
        ruleSet.assertFails(Person("Alice", 50, "alice@example.com")) {
            hasRule("score-check")
        }
    }

    @Test
    fun assertFailsWithBlockHasRuleFails() {
        assertFailsWith<AssertionError> {
            ruleSet.assertFails(Person("Alice", 50, "alice@example.com")) {
                hasRule("name-check")  // name is not blank, so this rule passes
            }
        }
    }

    @Test
    fun assertFailsWithBlockHasRuleWithAssertion() {
        ruleSet.assertFails(Person("Alice", 50, "alice@example.com")) {
            hasRule("score-check") {
                messageContains("below minimum")
            }
        }
    }

    @Test
    fun assertFailsWithBlockHasOnlyRules() {
        ruleSet.assertFails(Person("Alice", 50, "invalid")) {
            hasOnlyRules("score-check", "email-check")
        }
    }

    @Test
    fun assertFailsWithBlockHasOnlyRulesFails() {
        assertFailsWith<AssertionError> {
            ruleSet.assertFails(Person("Alice", 50, "invalid")) {
                hasOnlyRules("score-check")  // email-check also fails
            }
        }
    }

    // ========================================================================
    // assertRuleFailsOnly tests
    // ========================================================================

    @Test
    fun assertFailsWithBlockAnyMessageContains() {
        ruleSet.assertFails(Person("", 50, "invalid")) {
            anyMessageContains("minimum")
        }
    }

    @Test
    fun assertFailsWithBlockFailuresInOrder() {
        ruleSet.assertFails(Person("", 50, "invalid")) {
            failures {
                failure {
                    ruleName("score-check")
                    messageContains("below minimum")
                }
                failure {
                    ruleName("name-check")
                }
                failure {
                    ruleName("email-check")
                }
            }
        }
    }

    // ========================================================================
    // assertRuleFailsOnly tests
    // ========================================================================

    @Test
    fun assertRuleFailsOnlySucceeds() {
        ruleSet.assertRuleFailsOnly(ScoreCheck, fact = Person("Alice", 50, "alice@example.com"))
    }

    @Test
    fun assertRuleFailsOnlyWithMultipleRules() {
        ruleSet.assertRuleFailsOnly(ScoreCheck, EmailCheck, fact = Person("Alice", 50, "invalid"))
    }

    @Test
    fun assertRuleFailsOnlyFailsWhenExtraRuleFails() {
        assertFailsWith<AssertionError> {
            ruleSet.assertRuleFailsOnly(ScoreCheck, fact = Person("Alice", 50, "invalid"))
        }
    }

    @Test
    fun assertRuleFailsOnlyFailsWhenExpectedRulePasses() {
        assertFailsWith<AssertionError> {
            ruleSet.assertRuleFailsOnly(ScoreCheck, NameCheck, fact = Person("Alice", 50, "alice@example.com"))
        }
    }

    // ========================================================================
    // assertRuleFails tests
    // ========================================================================

    @Test
    fun assertRuleFailsSucceeds() {
        ruleSet.assertRuleFails(ScoreCheck, Person("Alice", 50, "alice@example.com"))
    }

    @Test
    fun assertRuleFailsFailsWhenRulePasses() {
        assertFailsWith<AssertionError> {
            ruleSet.assertRuleFails(ScoreCheck, Person("Alice", 150, "alice@example.com"))
        }
    }

    @Test
    fun assertRuleFailsWithAssertion() {
        ruleSet.assertRuleFails(ScoreCheck, Person("Alice", 50, "alice@example.com")) {
            messageContains("50")
            messageContains("minimum 100")
        }
    }
}
