package com.expensetracker.core.model

/**
 * Spending sectors, in the order they're offered in the categorize picker.
 *
 * UNCATEGORIZED ("Untagged") means the engine could not confidently pick one — surfaced to the
 * user for review instead of being guessed silently wrong. Note there is deliberately no EMI
 * sector: a loan repayment is an accounting *kind* ([TransactionKind.LOAN_REPAYMENT]), which
 * moves the loan's balance, not a spending sector.
 */
enum class Category(val displayName: String) {
    FOOD_DINING("Food & Dining"),
    GROCERIES("Groceries"),
    CLOTHES_SHOPPING("Clothes & Shopping"),
    TRAVEL_TRANSPORT("Travel & Transport"),
    ENTERTAINMENT("Entertainment"),
    HEALTH("Health"),
    RENT("Rent"),
    EDUCATION("Education"),
    BILLS_UTILITIES("Bills & Utilities"),
    SUBSCRIPTIONS("Subscriptions"),
    INVESTMENTS("Investments"),
    MISCELLANEOUS("Miscellaneous"),
    UNCATEGORIZED("Untagged"),
}
