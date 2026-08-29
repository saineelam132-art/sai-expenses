package com.expensetracker.app.data.repository

import com.expensetracker.app.data.db.KeywordRuleDao
import com.expensetracker.app.data.db.KeywordRuleEntity
import com.expensetracker.app.data.db.MerchantRuleDao
import com.expensetracker.app.data.db.MerchantRuleEntity
import com.expensetracker.core.categorize.CategoryEngine
import com.expensetracker.core.categorize.DefaultKeywordRules
import com.expensetracker.core.categorize.MerchantRuleStore
import com.expensetracker.core.model.Category
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import java.util.concurrent.ConcurrentHashMap

/**
 * Room-backed [MerchantRuleStore]. CategoryEngine calls these methods synchronously (from the
 * SMS/notification capture path, which needs an instant answer), so we keep a plain in-memory
 * cache and mirror writes to Room asynchronously rather than making the interface suspend.
 */
class RoomMerchantRuleStore(
    private val dao: MerchantRuleDao,
    private val scope: CoroutineScope,
) : MerchantRuleStore {
    private val cache = ConcurrentHashMap<String, Category>()

    suspend fun preload() {
        dao.getAllOnce().forEach { cache[it.normalizedMerchant] = it.category }
    }

    override fun getLearnedCategory(normalizedMerchant: String): Category? = cache[normalizedMerchant]

    override fun learn(normalizedMerchant: String, category: Category) {
        cache[normalizedMerchant] = category
        scope.launch { dao.upsert(MerchantRuleEntity(normalizedMerchant, category)) }
    }
}

/**
 * Loads the editable keyword table from Room, seeding it with [DefaultKeywordRules] on first run.
 * Row order isn't preserved by SQLite across restarts, so keyword-match precedence can shift
 * slightly after a reload — acceptable since overlapping keywords are rare and the user can
 * always fix a specific merchant with a one-off correction (which always takes priority).
 */
suspend fun loadOrSeedKeywordRules(dao: KeywordRuleDao): LinkedHashMap<String, Category> {
    if (dao.count() == 0) {
        DefaultKeywordRules.build().forEach { (keyword, category) ->
            dao.upsert(KeywordRuleEntity(keyword, category))
        }
    }
    val rules = LinkedHashMap<String, Category>()
    dao.getAllOnce().forEach { rules[it.keyword] = it.category }
    return rules
}

fun buildCategoryEngine(
    keywordRules: LinkedHashMap<String, Category>,
    merchantRuleStore: MerchantRuleStore,
): CategoryEngine = CategoryEngine(keywordRules, merchantRuleStore)
