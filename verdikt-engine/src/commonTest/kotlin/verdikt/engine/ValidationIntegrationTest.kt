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
        val engine = engine {
            validate<Product>("positive-price") {
                condition { it.price > 0 }
                onFailure { _: Product -> "Price must be positive" }
            }
            validate<Product>("positive-quantity") {
                condition { it.quantity > 0 }
                onFailure { _: Product -> "Quantity must be positive" }
            }
        }

        val result = engine.evaluate(listOf(Product("Widget", 10.0, 5)))

        assertTrue(result.passed)
        assertFalse(result.failed)
        assertIs<Verdict.Pass>(result.verdict)
    }

    @Test
    fun validationFailsWhenAnyRuleFails() {
        val engine = engine {
            validate<Product>("positive-price") {
                condition { it.price > 0 }
                onFailure { _: Product -> "Price must be positive" }
            }
        }

        val result = engine.evaluate(listOf(Product("Widget", -5.0, 1)))

        assertFalse(result.passed)
        assertTrue(result.failed)
        assertIs<Verdict.Fail<*>>(result.verdict)
        assertEquals(1, (result.verdict as Verdict.Fail).failures.size)
        assertEquals("positive-price", (result.verdict as Verdict.Fail).failures[0].ruleName)
        assertEquals("Price must be positive", (result.verdict as Verdict.Fail).failures[0].reason)
    }

    @Test
    fun validationCollectsAllFailures() {
        val engine = engine {
            validate<Product>("positive-price") {
                condition { it.price > 0 }
                onFailure { _: Product -> "Price must be positive" }
            }
            validate<Product>("positive-quantity") {
                condition { it.quantity > 0 }
                onFailure { _: Product -> "Quantity must be positive" }
            }
            validate<Product>("has-name") {
                condition { it.name.isNotBlank() }
                onFailure { _: Product -> "Name is required" }
            }
        }

        val result = engine.evaluate(listOf(Product("", -5.0, 0)))  // Fails all three

        val failures = (result.verdict as Verdict.Fail).failures
        assertEquals(3, failures.size)
        assertTrue(failures.any { it.ruleName == "positive-price" })
        assertTrue(failures.any { it.ruleName == "positive-quantity" })
        assertTrue(failures.any { it.ruleName == "has-name" })
    }

    @Test
    fun validationRunsAfterProductionRulesReachFixpoint() {
        val engine = engine {
            // Production: calculate cart total
            produce<CartItem, CartTotal>("calculate-total") {
                condition { true }
                output { CartTotal(it.quantity * 10.0) }  // Assume $10 per item
            }

            // Validation: check the derived total
            validate<CartTotal>("max-order") {
                condition { it.total <= 100.0 }
                onFailure { total: CartTotal -> "Order total ${total.total} exceeds max of 100" }
            }
        }

        val result = engine.evaluate(listOf(CartItem("Widget", 15)))  // Total will be $150

        // Production should have derived CartTotal
        val total = result.derivedOfType<CartTotal>().first()
        assertEquals(150.0, total.total)

        // Validation should fail on the derived fact
        assertTrue(result.failed)
        val failure = (result.verdict as Verdict.Fail).failures[0]
        assertEquals("max-order", failure.ruleName)
        assertTrue(failure.reason.toString().contains("150"))
    }

    @Test
    fun validationRunsOnBothInitialAndDerivedFacts() {
        val engine = engine {
            produce<Int, String>("int-to-string") {
                condition { true }
                output { it.toString() }
            }

            validate<String>("not-empty") {
                condition { it.isNotEmpty() }
                onFailure { _: String -> "String cannot be empty" }
            }
        }

        val result = engine.evaluate(listOf(42, "hello"))  // Insert both Int and String

        // Should derive "42" from the Int
        assertTrue(result.derivedOfType<String>().contains("42"))

        // Validation should run on both "hello" (initial) and "42" (derived)
        // Both should pass
        assertTrue(result.passed)
    }

    @Test
    fun staticFailureReasonWorks() {
        val engine = engine {
            validate<Int>("must-be-positive") {
                condition { it > 0 }
                onFailure { _: Int -> "Value must be positive" }  // Static reason via lambda
            }
        }

        val result = engine.evaluate(listOf(-5))

        val failure = (result.verdict as Verdict.Fail).failures[0]
        assertEquals("Value must be positive", failure.reason)
    }

    @Test
    fun dynamicFailureReasonIncludesFactDetails() {
        val engine = engine {
            validate<Product>("price-range") {
                condition { it.price in 1.0..1000.0 }
                onFailure { product: Product -> "Price ${product.price} is outside valid range 1-1000" }
            }
        }

        val result = engine.evaluate(listOf(Product("Expensive", 5000.0, 1)))

        val failure = (result.verdict as Verdict.Fail).failures[0]
        assertTrue(failure.reason.toString().contains("5000"))
    }

    @Test
    fun validationOnlyRunsOnMatchingFactTypes() {
        val engine = engine {
            validate<String>("string-not-empty") {
                condition { it.isNotEmpty() }
                onFailure { _: String -> "String cannot be empty" }
            }
            validate<Int>("int-positive") {
                condition { it > 0 }
                onFailure { _: Int -> "Int must be positive" }
            }
        }

        val result = engine.evaluate(listOf("hello", 42))

        // Both valid, so should pass
        assertTrue(result.passed)

        // Now test with invalid of each type
        val result2 = engine.evaluate(listOf("", -1))

        assertEquals(2, (result2.verdict as Verdict.Fail).failures.size)
    }

    @Test
    fun descriptionIsUsedAsDefaultFailureReasonWhenOnFailureNotSet() {
        val engine = engine {
            validate<Int>("positive") {
                description = "Value must be positive"
                condition { it > 0 }
                onFailure { _: Int -> description }  // Use description as failure reason
            }
        }

        val result = engine.evaluate(listOf(-1))

        val failure = (result.verdict as Verdict.Fail).failures[0]
        assertEquals("Value must be positive", failure.reason)
    }

    @Test
    fun failuresOfTypeReturnsEmptyListForPass() {
        val engine = engine {
            validate<Int>("positive") {
                condition { it > 0 }
                onFailure { _: Int -> "Must be positive" }
            }
        }

        val result = engine.evaluate(listOf(1))

        assertTrue(result.passed)
        assertEquals(emptyList(), result.failuresOfType<String>())
    }

    @Test
    fun failuresOfTypeFiltersFailuresByCauseType() {
        // Define typed error classes
        data class PriceError(val price: Double)
        data class QuantityError(val quantity: Int)

        val engine = engine {
            validate<Product>("positive-price") {
                condition { it.price > 0 }
                onFailure { product -> PriceError(product.price) }
            }
            validate<Product>("positive-quantity") {
                condition { it.quantity > 0 }
                onFailure { product -> QuantityError(product.quantity) }
            }
        }

        val result = engine.evaluate(listOf(Product("Widget", -5.0, -2)))

        assertTrue(result.failed)

        // Get only PriceError failures
        val priceErrors = result.failuresOfType<PriceError>()
        assertEquals(1, priceErrors.size)
        assertEquals(-5.0, priceErrors[0].reason.price)

        // Get only QuantityError failures
        val quantityErrors = result.failuresOfType<QuantityError>()
        assertEquals(1, quantityErrors.size)
        assertEquals(-2, quantityErrors[0].reason.quantity)

        // Get non-existent type returns empty
        val stringErrors = result.failuresOfType<String>()
        assertEquals(emptyList(), stringErrors)
    }
}
