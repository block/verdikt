package demo

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import verdikt.Failure
import verdikt.Verdict

@Composable
fun App() {
    var dogName by remember { mutableStateOf("") }
    var money by remember { mutableStateOf("") }
    var flavor by remember { mutableStateOf("") }
    var scoops by remember { mutableStateOf("1") }
    var didTrick by remember { mutableStateOf(false) }
    var validationResult by remember { mutableStateOf<Verdict<*>?>(null) }
    var submittedOrder by remember { mutableStateOf<PupCupOrder?>(null) }
    var isLoading by remember { mutableStateOf(false) }

    val coroutineScope = rememberCoroutineScope()

    MaterialTheme {
        Surface(
            modifier = Modifier.fillMaxSize(),
            color = MaterialTheme.colorScheme.background
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(16.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Header()
                Spacer(modifier = Modifier.height(24.dp))
                OrderForm(
                    dogName = dogName,
                    onDogNameChange = { dogName = it },
                    money = money,
                    onMoneyChange = { money = it.filter { c -> c.isDigit() || c == '.' } },
                    flavor = flavor,
                    onFlavorChange = { flavor = it },
                    scoops = scoops,
                    onScoopsChange = { scoops = it.filter { c -> c.isDigit() } },
                    didTrick = didTrick,
                    onDidTrickChange = { didTrick = it }
                )
                Spacer(modifier = Modifier.height(24.dp))
                Button(
                    onClick = {
                        val order = PupCupOrder(
                            dogName = dogName,
                            moneyInCollar = money.toDoubleOrNull() ?: 0.0,
                            requestedFlavor = flavor,
                            scoops = scoops.toIntOrNull() ?: 0,
                            didTrick = didTrick
                        )
                        submittedOrder = order
                        validationResult = null
                        isLoading = true

                        // Evaluate all rules asynchronously using Verdikt!
                        // The naughty list check simulates a database lookup.
                        coroutineScope.launch {
                            validationResult = PupCupRules.evaluateAsync(order)
                            isLoading = false
                        }
                    },
                    enabled = !isLoading,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    if (isLoading) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(20.dp),
                            strokeWidth = 2.dp,
                            color = MaterialTheme.colorScheme.onPrimary
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Checking...")
                    } else {
                        Text("Order Pup Cup!")
                    }
                }
                Spacer(modifier = Modifier.height(24.dp))
                ValidationResultCard(validationResult, submittedOrder)
                Spacer(modifier = Modifier.height(24.dp))
                RulesInfoCard()
                Spacer(modifier = Modifier.height(16.dp))
                Text(
                    text = "Powered by Verdikt",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun Header() {
    Text(
        text = "Pup Cup Cart",
        style = MaterialTheme.typography.headlineMedium,
        fontWeight = FontWeight.Bold
    )
    Spacer(modifier = Modifier.height(8.dp))
    Text(
        text = "Help your dog order a pup cup!",
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant
    )
    Text(
        text = "Price: $${formatMoney(PRICE_PER_SCOOP)} per scoop",
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant
    )
}

@Composable
private fun OrderForm(
    dogName: String,
    onDogNameChange: (String) -> Unit,
    money: String,
    onMoneyChange: (String) -> Unit,
    flavor: String,
    onFlavorChange: (String) -> Unit,
    scoops: String,
    onScoopsChange: (String) -> Unit,
    didTrick: Boolean,
    onDidTrickChange: (Boolean) -> Unit
) {
    OutlinedTextField(
        value = dogName,
        onValueChange = onDogNameChange,
        label = { Text("Dog's Name (on collar tag)") },
        placeholder = { Text("Biscuit, Max, Luna...") },
        modifier = Modifier.fillMaxWidth(),
        singleLine = true
    )
    Spacer(modifier = Modifier.height(12.dp))
    OutlinedTextField(
        value = money,
        onValueChange = onMoneyChange,
        label = { Text("Money in Collar Pouch ($)") },
        placeholder = { Text("5.00") },
        modifier = Modifier.fillMaxWidth(),
        singleLine = true
    )
    Spacer(modifier = Modifier.height(12.dp))
    OutlinedTextField(
        value = flavor,
        onValueChange = onFlavorChange,
        label = { Text("Flavor") },
        placeholder = { Text("peanut butter, bacon, vanilla...") },
        modifier = Modifier.fillMaxWidth(),
        singleLine = true,
        supportingText = {
            Text("Available: ${availableFlavors.joinToString(", ")}")
        }
    )
    Spacer(modifier = Modifier.height(12.dp))
    OutlinedTextField(
        value = scoops,
        onValueChange = onScoopsChange,
        label = { Text("Number of Scoops") },
        modifier = Modifier.fillMaxWidth(),
        singleLine = true
    )
    Spacer(modifier = Modifier.height(12.dp))
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Checkbox(
            checked = didTrick,
            onCheckedChange = onDidTrickChange
        )
        Spacer(modifier = Modifier.width(8.dp))
        Column {
            Text("Did a trick for the vendor")
            Text(
                text = "(sit, shake, roll over, etc.)",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun ValidationResultCard(result: Verdict<*>?, submittedOrder: PupCupOrder?) {
    if (result == null) return

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = if (result.passed) Color(0xFFE8F5E9) else Color(0xFFFFF3E0)
        )
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            if (result.passed && submittedOrder != null) {
                OrderApprovedContent(submittedOrder)
            } else if (result is Verdict.Fail<*>) {
                OrderRejectedContent(result.failures)
            }
        }
    }
}

@Composable
private fun OrderApprovedContent(order: PupCupOrder) {
    Text(
        text = "Order Approved!",
        style = MaterialTheme.typography.titleMedium,
        fontWeight = FontWeight.Bold,
        color = Color(0xFF2E7D32)
    )
    Spacer(modifier = Modifier.height(8.dp))
    Text(
        text = "Here's your ${order.requestedFlavor} pup cup, ${order.dogName}! Enjoy!",
        style = MaterialTheme.typography.bodyMedium
    )
    Text(
        text = "Total: $${formatMoney(order.scoops * PRICE_PER_SCOOP)}",
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant
    )
}

@Composable
private fun OrderRejectedContent(failures: List<Failure<*>>) {
    Text(
        text = "Not So Fast, Pup!",
        style = MaterialTheme.typography.titleMedium,
        fontWeight = FontWeight.Bold,
        color = Color(0xFFE65100)
    )
    Spacer(modifier = Modifier.height(12.dp))
    failures.forEach { failure ->
        Row(modifier = Modifier.padding(vertical = 4.dp)) {
            Text(
                text = failure.reason.toString(),
                style = MaterialTheme.typography.bodyMedium
            )
        }
    }
}

@Composable
private fun RulesInfoCard() {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        )
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = "Cart Rules",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold
            )
            Spacer(modifier = Modifier.height(8.dp))
            listOf(
                "Dog must have a name tag",
                "Flavor must be available",
                "1-3 scoops only",
                "Must have enough money",
                "Must do a trick first",
                "Must not be on the naughty list"
            ).forEach { rule ->
                Text(
                    text = "- $rule",
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.padding(vertical = 2.dp)
                )
            }
        }
    }
}
