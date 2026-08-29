package com.expensetracker.core.categorize

import com.expensetracker.core.model.Category

enum class MatchSource { LEARNED, KEYWORD, NONE }

data class CategorizationResult(
    val category: Category,
    val confident: Boolean,
    val matchSource: MatchSource,
    val matchedTerm: String? = null,
)

/**
 * Categorizes a transaction by merchant name using, in priority order:
 *  1. A learned exact merchant -> category mapping (from a past user correction).
 *  2. A substring match against the editable keyword table.
 *  3. Category.UNCATEGORIZED, flagged unconfident, if neither matches — callers should surface
 *     this for manual review rather than guessing.
 *
 * [keywordRules] is a mutable, ordered map so callers (Settings screen) can add/remove/reorder
 * rules at runtime; iteration order determines precedence when multiple keywords could match.
 */
class CategoryEngine(
    private val keywordRules: LinkedHashMap<String, Category> = DefaultKeywordRules.build(),
    private val ruleStore: MerchantRuleStore = InMemoryMerchantRuleStore(),
) {
    fun categorize(merchant: String?): CategorizationResult {
        val key = normalize(merchant) ?: return CategorizationResult(Category.UNCATEGORIZED, false, MatchSource.NONE)

        ruleStore.getLearnedCategory(key)?.let {
            return CategorizationResult(it, true, MatchSource.LEARNED, key)
        }

        for ((keyword, category) in keywordRules) {
            if (matchesKeyword(key, keyword)) {
                return CategorizationResult(category, true, MatchSource.KEYWORD, keyword)
            }
        }

        return CategorizationResult(Category.UNCATEGORIZED, false, MatchSource.NONE)
    }

    /**
     * Multi-word keywords (e.g. "cash withdrawal") match as a substring; single-word keywords
     * (e.g. "vi", "rent", "emi") must match a whole word in the merchant string, otherwise short
     * telecom/brand keywords would false-positive inside unrelated words (e.g. "rent" inside
     * "parent", "vi" inside "service").
     */
    private fun matchesKeyword(key: String, keyword: String): Boolean {
        val kw = keyword.trim()
        return if (kw.contains(" ")) {
            key.contains(kw)
        } else {
            key.split(" ").any { it == kw }
        }
    }

    /** Call when the user corrects a transaction's category — remembered for this merchant going forward. */
    fun correctCategory(merchant: String, category: Category) {
        val key = normalize(merchant) ?: return
        ruleStore.learn(key, category)
    }

    fun addKeywordRule(keyword: String, category: Category) {
        val normalized = normalize(keyword) ?: return
        keywordRules[normalized] = category
    }

    fun removeKeywordRule(keyword: String) {
        keywordRules.remove(normalize(keyword))
    }

    fun currentKeywordRules(): Map<String, Category> = keywordRules.toMap()

    companion object {
        fun normalize(merchant: String?): String? {
            if (merchant.isNullOrBlank()) return null
            return merchant
                .lowercase()
                .replace(Regex("""[^a-z0-9&' ]"""), " ")
                .replace(Regex("""\s+"""), " ")
                .trim()
                .takeIf { it.isNotBlank() }
        }
    }
}
