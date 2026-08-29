package com.expensetracker.core.categorize

import com.expensetracker.core.model.Category

/**
 * Starter merchant-keyword -> category table. Ordered map: first substring match wins, so put
 * more specific keywords before generic ones (e.g. "amazon pay" style narrow terms before a
 * bare "amazon"). Fully user-editable at runtime via the Settings screen — this is just the
 * seed data for a fresh install.
 */
object DefaultKeywordRules {
    fun build(): LinkedHashMap<String, Category> = linkedMapOf(
        // Food & Dining
        "swiggy" to Category.FOOD_DINING,
        "zomato" to Category.FOOD_DINING,
        "dominos" to Category.FOOD_DINING,
        "mcdonald" to Category.FOOD_DINING,
        "kfc" to Category.FOOD_DINING,
        "starbucks" to Category.FOOD_DINING,
        "cafe" to Category.FOOD_DINING,
        "restaurant" to Category.FOOD_DINING,
        "eatsure" to Category.FOOD_DINING,
        "box8" to Category.FOOD_DINING,
        "dineout" to Category.FOOD_DINING,

        // Groceries
        "bigbasket" to Category.GROCERIES,
        "blinkit" to Category.GROCERIES,
        "zepto" to Category.GROCERIES,
        "grofers" to Category.GROCERIES,
        "dmart" to Category.GROCERIES,
        "jiomart" to Category.GROCERIES,
        "milkbasket" to Category.GROCERIES,
        "grocery" to Category.GROCERIES,
        "nature's basket" to Category.GROCERIES,

        // Clothes & Shopping
        "myntra" to Category.CLOTHES_SHOPPING,
        "ajio" to Category.CLOTHES_SHOPPING,
        "zara" to Category.CLOTHES_SHOPPING,
        "h&m" to Category.CLOTHES_SHOPPING,
        "lifestyle" to Category.CLOTHES_SHOPPING,
        "pantaloons" to Category.CLOTHES_SHOPPING,
        "levis" to Category.CLOTHES_SHOPPING,
        "westside" to Category.CLOTHES_SHOPPING,
        "shoppers stop" to Category.CLOTHES_SHOPPING,

        // Travel/Transport
        "uber" to Category.TRAVEL_TRANSPORT,
        "ola" to Category.TRAVEL_TRANSPORT,
        "rapido" to Category.TRAVEL_TRANSPORT,
        "irctc" to Category.TRAVEL_TRANSPORT,
        "redbus" to Category.TRAVEL_TRANSPORT,
        "indigo" to Category.TRAVEL_TRANSPORT,
        "spicejet" to Category.TRAVEL_TRANSPORT,
        "metro" to Category.TRAVEL_TRANSPORT,
        "petrol" to Category.TRAVEL_TRANSPORT,
        "fuel" to Category.TRAVEL_TRANSPORT,
        "hpcl" to Category.TRAVEL_TRANSPORT,
        "ioc" to Category.TRAVEL_TRANSPORT,
        "bpcl" to Category.TRAVEL_TRANSPORT,
        "fastag" to Category.TRAVEL_TRANSPORT,

        // Bills & Utilities
        "airtel" to Category.BILLS_UTILITIES,
        "jio" to Category.BILLS_UTILITIES,
        "vodafone" to Category.BILLS_UTILITIES,
        "vi" to Category.BILLS_UTILITIES,
        "bescom" to Category.BILLS_UTILITIES,
        "bses" to Category.BILLS_UTILITIES,
        "electricity" to Category.BILLS_UTILITIES,
        "broadband" to Category.BILLS_UTILITIES,
        "act fibernet" to Category.BILLS_UTILITIES,
        "gas agency" to Category.BILLS_UTILITIES,
        "water board" to Category.BILLS_UTILITIES,

        // Entertainment
        "pvr" to Category.ENTERTAINMENT,
        "inox" to Category.ENTERTAINMENT,
        "bookmyshow" to Category.ENTERTAINMENT,
        "cinema" to Category.ENTERTAINMENT,

        // Health
        "apollo" to Category.HEALTH,
        "pharmeasy" to Category.HEALTH,
        "practo" to Category.HEALTH,
        "1mg" to Category.HEALTH,
        "netmeds" to Category.HEALTH,
        "hospital" to Category.HEALTH,
        "clinic" to Category.HEALTH,
        "medplus" to Category.HEALTH,
        "pharmacy" to Category.HEALTH,

        // Rent/EMI
        "rent" to Category.RENT_EMI,
        "landlord" to Category.RENT_EMI,
        "emi" to Category.RENT_EMI,
        "housing finance" to Category.RENT_EMI,
        "bajaj finserv" to Category.RENT_EMI,
        "home loan" to Category.RENT_EMI,

        // Subscriptions
        "netflix" to Category.SUBSCRIPTIONS,
        "spotify" to Category.SUBSCRIPTIONS,
        "hotstar" to Category.SUBSCRIPTIONS,
        "prime video" to Category.SUBSCRIPTIONS,
        "youtube premium" to Category.SUBSCRIPTIONS,
        "sonyliv" to Category.SUBSCRIPTIONS,
        "zee5" to Category.SUBSCRIPTIONS,
        "icloud" to Category.SUBSCRIPTIONS,

        // Education
        "byju" to Category.EDUCATION,
        "udemy" to Category.EDUCATION,
        "coursera" to Category.EDUCATION,
        "unacademy" to Category.EDUCATION,
        "vedantu" to Category.EDUCATION,
        "tuition" to Category.EDUCATION,

        // Miscellaneous — deliberately broad/ambiguous merchants land here by default;
        // the user can re-map any of them to a more specific category once and it's remembered.
        "amazon" to Category.MISCELLANEOUS,
        "flipkart" to Category.MISCELLANEOUS,
        "atm" to Category.MISCELLANEOUS,
        "cash withdrawal" to Category.MISCELLANEOUS,
    )
}
