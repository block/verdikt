package verdikt.engine

import verdikt.Verdict
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue

// Test domain classes
data class Product(val name: String, val price: Double, val quantity: Int)
data class CartItem(val productName: String, val quantity: Int)
data class CartTotal(val total: Double)

class ValidationIntegrationTest {

    @Test
    fun validationPassesWhenAllRulesPass() {
        val engine = engine<String> {
            validate<Product>("positive-price") {
                condition { it.price > 0 }
                onFailure { "Price must be positive" }
            }
            validate<Product>("positive-quantity") {
                condition { it.quantity > 0 }
                onFailure { "Quantity must be positive" }
            }
        }

        val session = engine.session()
        session.insert(Product("Widget", 10.0, 5))
        val result = session.fire()

        assertTrue(result.passed)
        assertFalse(result.failed)
        assertIs<Verdict.Pass>(result.verdict)
    }

    @Test
    fun validationFailsWhenAnyRuleFails() {
        val engine = engine<String> {
            validate<Product>("positive-price") {
                condition { it.price > 0 }
                onFailure { "Price must be positive" }
            }
        }

        val session = engine.session()
        session.insert(Product("Widget", -5.0, 1))
        val result = session.fire()

        assertFalse(result.passed)
        assertTrue(result.failed)
        assertIs<Verdict.Fail<String>>(result.verdict)
        assertEquals(1, (result.verdict as Verdict.Fail).failures.size)
        assertEquals("positive-price", (result.verdict as Verdict.Fail).failures[0].ruleName)
        assertEquals("Price must be positive", (result.verdict as Verdict.Fail).failures[0].reason)
    }

    @Test
    fun validationCollectsAllFailures() {
        val engine = engine<String> {
            validate<Product>("positive-price") {
                condition { it.price > 0 }
                onFailure { "Price must be positive" }
            }
            validate<Product>("positive-quantity") {
                condition { it.quantity > 0 }
                onFailure { "Quantity must be positive" }
            }
            validate<Product>("has-name") {
                condition { it.name.isNotBlank() }
                onFailure { "Name is required" }
            }
        }

        val session = engine.session()
        session.insert(Product("", -5.0, 0))  // Fails all three
        val result = session.fire()

        val failures = (result.verdict as Verdict.Fail).failures
        assertEquals(3, failures.size)
        assertTrue(failures.any { it.ruleName == "positive-price" })
        assertTrue(failures.any { it.ruleName == "positive-quantity" })
        assertTrue(failures.any { it.ruleName == "has-name" })
    }

    @Test
    fun validationRunsAfterProductionRulesReachFixpoint() {
        val engine = engine<String> {
            // Production: calculate cart total
            produce<CartItem, CartTotal>("calculate-total") {
                condition { true }
                output { CartTotal(it.quantity * 10.0) }  // Assume $10 per item
            }

            // Validation: check the derived total
            validate<CartTotal>("max-order") {
                condition { it.total <= 100.0 }
                onFailure { total -> "Order total ${total.total} exceeds max of 100" }
            }
        }

        val session = engine.session()
        session.insert(CartItem("Widget", 15))  // Total will be $150
        val result = session.fire()

        // Production should have derived CartTotal
        val total = result.derivedOfType<CartTotal>().first()
        assertEquals(150.0, total.total)

        // Validation should fail on the derived fact
        assertTrue(result.failed)
        val failure = (result.verdict as Verdict.Fail).failures[0]
        assertEquals("max-order", failure.ruleName)
        assertTrue(failure.reason.contains("150"))
    }

    @Test
    fun validationRunsOnBothInitialAndDerivedFacts() {
        val engine = engine<String> {
            produce<Int, String>("int-to-string") {
                condition { true }
                output { it.toString() }
            }

            validate<String>("not-empty") {
                condition { it.isNotEmpty() }
                onFailure { "String cannot be empty" }
            }
        }

        val session = engine.session()
        session.insert(42, "hello")  // Insert both Int and String
        val result = session.fire()

        // Should derive "42" from the Int
        assertTrue(result.derivedOfType<String>().contains("42"))

        // Validation should run on both "hello" (initial) and "42" (derived)
        // Both should pass
        assertTrue(result.passed)
    }

    @Test
    fun staticFailureReasonWorks() {
        val engine = engine<String> {
            validate<Int>("must-be-positive") {
                condition { it > 0 }
                onFailure("Value must be positive")  // Static reason
            }
        }

        val session = engine.session()
        session.insert(-5)
        val result = session.fire()

        val failure = (result.verdict as Verdict.Fail).failures[0]
        assertEquals("Value must be positive", failure.reason)
    }

    @Test
    fun dynamicFailureReasonIncludesFactDetails() {
        val engine = engine<String> {
            validate<Product>("price-range") {
                condition { it.price in 1.0..1000.0 }
                onFailure { product -> "Price ${product.price} is outside valid range 1-1000" }
            }
        }

        val session = engine.session()
        session.insert(Product("Expensive", 5000.0, 1))
        val result = session.fire()

        val failure = (result.verdict as Verdict.Fail).failures[0]
        assertTrue(failure.reason.contains("5000"))
    }

    @Test
    fun validationOnlyRunsOnMatchingFactTypes() {
        val engine = engine<String> {
            validate<String>("string-not-empty") {
                condition { it.isNotEmpty() }
                onFailure { "String cannot be empty" }
            }
            validate<Int>("int-positive") {
                condition { it > 0 }
                onFailure { "Int must be positive" }
            }
        }

        val session = engine.session()
        session.insert("hello", 42)
        val result = session.fire()

        // Both valid, so should pass
        assertTrue(result.passed)

        // Now test with invalid of each type
        val session2 = engine.session()
        session2.insert("", -1)
        val result2 = session2.fire()

        assertEquals(2, (result2.verdict as Verdict.Fail).failures.size)
    }

    @Test
    fun descriptionIsUsedAsDefaultFailureReasonWhenOnFailureNotSet() {
        val engine = engine<String> {
            validate<Int>("positive") {
                description = "Value must be positive"
                condition { it > 0 }
                // No onFailure - should use description
            }
        }

        val session = engine.session()
        session.insert(-1)
        val result = session.fire()

        val failure = (result.verdict as Verdict.Fail).failures[0]
        assertEquals("Value must be positive", failure.reason)
    }
}
