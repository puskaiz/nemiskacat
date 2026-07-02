package hu.deposoft.webshop.domain.checkout;

/** One weight band: applies while total weight <= maxWeightGrams (edge inclusive). */
public record WeightBand(int maxWeightGrams, long grossHuf) {
}
