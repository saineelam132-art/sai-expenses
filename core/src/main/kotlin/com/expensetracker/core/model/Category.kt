package com.expensetracker.core.model

/**
 * Sectors used for auto-categorization and budgeting.
 * UNCATEGORIZED means the engine could not confidently pick one — surfaced to the
 * user for manual review instead of being guessed silently wrong.
 */
enum class Category(val displayName: String) {
    FOOD_DINING("Food & Dining"),
    GROCERIES("Groceries"),
    CLOTHES_SHOPPING("Clothes & Shopping"),
    TRAVEL_TRANSPORT("Travel/Transport"),
    BILLS_UTILITIES("Bills & Utilities"),
    ENTERTAINMENT("Entertainment"),
    HEALTH("Health"),
    RENT_EMI("Rent/EMI"),
    SUBSCRIPTIONS("Subscriptions"),
    EDUCATION("Education"),
    INVESTMENTS("Investments"),
    MISCELLANEOUS("Miscellaneous"),
    UNCATEGORIZED("Uncategorized"),
}
