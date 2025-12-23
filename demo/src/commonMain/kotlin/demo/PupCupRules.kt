package demo

import kotlinx.coroutines.delay
import verdikt.AsyncRule
import verdikt.Rule
import verdikt.RuleSet
import verdikt.Verdict
import verdikt.failedRules
import verdikt.passedRules
import verdikt.rules
import verdikt.sideEffect

// Constants
const val PRICE_PER_SCOOP = 2.50
val availableFlavors = setOf("vanilla", "peanut butter", "bacon", "chicken", "cheese")
private val naughtyList = setOf("badboy", "trouble", "bitey", "growler")

// Rules defined separately
object DogHasNameTag : Rule<PupCupOrder, String> {
    override val name = "name-tag"
    override val description = "Dog must wear a name tag"
    override fun evaluate(fact: PupCupOrder) = fact.dogName.isNotBlank()
    override fun failureReason(fact: PupCupOrder) = description
}

object FlavorIsAvailable : Rule<PupCupOrder, String> {
    override val name = "flavor-available"

    override fun evaluate(fact: PupCupOrder) =
        fact.requestedFlavor.lowercase() in availableFlavors

    override fun failureReason(fact: PupCupOrder) =
        "Sorry pup, we don't have '${fact.requestedFlavor}'. Try: ${availableFlavors.joinToString(", ")}"
}

object ReasonableScoopCount : Rule<PupCupOrder, String> {
    override val name = "scoop-limit"

    override fun evaluate(fact: PupCupOrder) = fact.scoops in 1..3

    override fun failureReason(fact: PupCupOrder) = when {
        fact.scoops < 1 -> "You need at least 1 scoop, silly pup!"
        else -> "${fact.scoops} scoops?! That's too many even for a hungry dog. Max 3 scoops."
    }
}

object DogCanAffordIt : Rule<PupCupOrder, String> {
    override val name = "can-afford"

    override fun evaluate(fact: PupCupOrder): Boolean {
        val total = fact.scoops * PRICE_PER_SCOOP
        return fact.moneyInCollar >= total
    }

    override fun failureReason(fact: PupCupOrder): String {
        val total = fact.scoops * PRICE_PER_SCOOP
        val shortage = total - fact.moneyInCollar
        return "That's $${formatMoney(total)} but you only have $${formatMoney(fact.moneyInCollar)}. You're $${formatMoney(shortage)} short!"
    }
}

object GoodBoyCheck : Rule<PupCupOrder, String> {
    override val name = "good-boy"
    override val description = "Must do a trick to prove you're a good boy/girl"

    override fun evaluate(fact: PupCupOrder) = fact.didTrick

    override fun failureReason(fact: PupCupOrder) =
        "Do a trick first! Sit, shake, roll over... something!"
}

// Async rule that simulates checking a "naughty list" database
object NaughtyListCheck : AsyncRule<PupCupOrder, String> {
    override val name = "naughty-list"
    override val description = "Dog must not be on the naughty list"

    override suspend fun evaluate(fact: PupCupOrder): Boolean {
        // Simulate a database lookup
        delay(500)
        return fact.dogName.lowercase() !in naughtyList
    }

    override fun failureReason(fact: PupCupOrder) =
        "Sorry ${fact.dogName}, you're on the naughty list! No pup cup for you."
}

// RuleSet with side effect for logging
val PupCupRules: RuleSet<PupCupOrder, String> = rules<PupCupOrder, String> {
    add(DogHasNameTag)
    add(FlavorIsAvailable)
    add(ReasonableScoopCount)
    add(DogCanAffordIt)
    add(GoodBoyCheck)
    add(NaughtyListCheck)
}.sideEffect { order, verdict ->
    val passed = this.passedRules(verdict)
    val failed = this.failedRules(verdict)

    println("[PupCupRules] Order for '${order.dogName}': ${order.requestedFlavor} x${order.scoops}")
    println("[PupCupRules]   Result: ${if (verdict.passed) "APPROVED" else "REJECTED"}")
    println("[PupCupRules]   Passed (${passed.size}): ${passed.joinToString(", ") { it.name }}")
    if (verdict is Verdict.Fail) {
        println("[PupCupRules]   Failed (${failed.size}): ${failed.joinToString(", ") { it.name }}")
        verdict.failures.forEach { failure ->
            println("[PupCupRules]     - ${failure.ruleName}: ${failure.reason}")
        }
    }
}

fun formatMoney(amount: Double): String {
    val cents = (amount * 100).toLong()
    val dollars = cents / 100
    val remainder = (cents % 100).toString().padStart(2, '0')
    return "$dollars.$remainder"
}
