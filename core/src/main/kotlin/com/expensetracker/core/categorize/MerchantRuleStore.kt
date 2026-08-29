package com.expensetracker.core.categorize

import com.expensetracker.core.model.Category

/**
 * Persists merchant -> category corrections the user makes ("learn this merchant").
 * The app module backs this with Room; tests use [InMemoryMerchantRuleStore].
 */
interface MerchantRuleStore {
    fun getLearnedCategory(normalizedMerchant: String): Category?
    fun learn(normalizedMerchant: String, category: Category)
}

class InMemoryMerchantRuleStore : MerchantRuleStore {
    private val mappings = mutableMapOf<String, Category>()

    override fun getLearnedCategory(normalizedMerchant: String): Category? = mappings[normalizedMerchant]

    override fun learn(normalizedMerchant: String, category: Category) {
        mappings[normalizedMerchant] = category
    }
}
