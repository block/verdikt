package demo

data class PupCupOrder(
    val dogName: String,
    val moneyInCollar: Double,
    val requestedFlavor: String,
    val scoops: Int,
    val didTrick: Boolean
)
