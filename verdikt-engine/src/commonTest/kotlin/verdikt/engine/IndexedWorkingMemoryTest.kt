package verdikt.engine

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class IndexedWorkingMemoryTest {

    // Test fact types
    data class Customer(val id: String, val name: String)
    data class Order(val id: String, val customerId: String, val amount: Double)
    data class Product(val id: String, val name: String)
    interface Nameable { val name: String }
    data class NamedEntity(override val name: String) : Nameable

    @Test
    fun addAndRetrieveSingleFact() {
        val memory = IndexedWorkingMemory()
        val customer = Customer("1", "Alice")

        assertTrue(memory.add(customer))
        assertTrue(memory.contains(customer))
        assertEquals(1, memory.size)
    }

    @Test
    fun addDuplicateFactReturnsFalse() {
        val memory = IndexedWorkingMemory()
        val customer = Customer("1", "Alice")

        assertTrue(memory.add(customer))
        assertFalse(memory.add(customer)) // Duplicate
        assertEquals(1, memory.size)
    }

    @Test
    fun addAllAddsMultipleFacts() {
        val memory = IndexedWorkingMemory()
        val customers = listOf(
            Customer("1", "Alice"),
            Customer("2", "Bob"),
            Customer("3", "Charlie")
        )

        memory.addAll(customers)
        assertEquals(3, memory.size)
        customers.forEach { assertTrue(memory.contains(it)) }
    }

    @Test
    fun allReturnsAllFacts() {
        val memory = IndexedWorkingMemory()
        val customer = Customer("1", "Alice")
        val order = Order("o1", "1", 100.0)

        memory.add(customer)
        memory.add(order)

        val allFacts = memory.all()
        assertEquals(2, allFacts.size)
        assertTrue(customer in allFacts)
        assertTrue(order in allFacts)
    }

    @Test
    fun ofTypeReturnsFactsOfExactType() {
        val memory = IndexedWorkingMemory()
        val customer1 = Customer("1", "Alice")
        val customer2 = Customer("2", "Bob")
        val order = Order("o1", "1", 100.0)
        val product = Product("p1", "Widget")

        memory.add(customer1)
        memory.add(customer2)
        memory.add(order)
        memory.add(product)

        val customers = memory.ofType(Customer::class)
        assertEquals(2, customers.size)
        assertTrue(customer1 in customers)
        assertTrue(customer2 in customers)
        assertFalse(order in customers.map { it as Any })
    }

    @Test
    fun ofTypeReturnsEmptySetForMissingType() {
        val memory = IndexedWorkingMemory()
        val customer = Customer("1", "Alice")

        memory.add(customer)

        val orders = memory.ofType(Order::class)
        assertTrue(orders.isEmpty())
    }

    @Test
    fun hasAnyReturnsTrueWhenTypeExists() {
        val memory = IndexedWorkingMemory()
        val customer = Customer("1", "Alice")

        memory.add(customer)

        assertTrue(memory.hasAny(Customer::class))
        assertFalse(memory.hasAny(Order::class))
    }

    @Test
    fun filterByTypeReturnsFactsAsListUsingInstanceCheck() {
        val memory = IndexedWorkingMemory()
        val customer1 = Customer("1", "Alice")
        val customer2 = Customer("2", "Bob")
        val order = Order("o1", "1", 100.0)

        memory.add(customer1)
        memory.add(customer2)
        memory.add(order)

        val customers = memory.filterByType(Customer::class)
        assertEquals(2, customers.size)
        assertTrue(customer1 in customers)
        assertTrue(customer2 in customers)
    }

    @Test
    fun containsChecksFact() {
        val memory = IndexedWorkingMemory()
        val customer = Customer("1", "Alice")
        val other = Customer("2", "Bob")

        memory.add(customer)

        assertTrue(memory.contains(customer))
        assertFalse(memory.contains(other))
    }

    @Test
    fun isEmptyWorksCorrectly() {
        val memory = IndexedWorkingMemory()

        assertTrue(memory.isEmpty())

        memory.add(Customer("1", "Alice"))

        assertFalse(memory.isEmpty())
    }

    @Test
    fun mixedTypesAreStoredCorrectly() {
        val memory = IndexedWorkingMemory()

        val customer = Customer("1", "Alice")
        val order = Order("o1", "1", 100.0)
        val product = Product("p1", "Widget")

        memory.add(customer)
        memory.add(order)
        memory.add(product)

        assertEquals(1, memory.ofType(Customer::class).size)
        assertEquals(1, memory.ofType(Order::class).size)
        assertEquals(1, memory.ofType(Product::class).size)
        assertEquals(3, memory.all().size)
    }

    @Test
    fun interfaceQueryFallsBackToLinearScan() {
        val memory = IndexedWorkingMemory()
        val named = NamedEntity("Entity")
        val customer = Customer("1", "Alice")

        memory.add(named)
        memory.add(customer)

        // Querying by interface should find the implementor
        val nameables = memory.ofType(Nameable::class)
        assertEquals(1, nameables.size)
        assertTrue(named in nameables)
    }
}
