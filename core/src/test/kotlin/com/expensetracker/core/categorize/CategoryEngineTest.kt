package com.expensetracker.core.categorize

import com.expensetracker.core.model.Category
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CategoryEngineTest {

    @Test
    fun `known merchant keyword maps to expected sector`() {
        val engine = CategoryEngine()
        assertEquals(Category.FOOD_DINING, engine.categorize("SWIGGY*ORDER123").category)
        assertEquals(Category.FOOD_DINING, engine.categorize("swiggy@okhdfcbank").category)
        assertEquals(Category.CLOTHES_SHOPPING, engine.categorize("MYNTRA DESIGNS PVT LTD").category)
        assertEquals(Category.TRAVEL_TRANSPORT, engine.categorize("UBER INDIA SYSTEMS").category)
        assertEquals(Category.GROCERIES, engine.categorize("BIGBASKET.COM").category)
    }

    @Test
    fun `unknown merchant is uncategorized and not confident`() {
        val engine = CategoryEngine()
        val result = engine.categorize("Some Random Shop 42")
        assertEquals(Category.UNCATEGORIZED, result.category)
        assertFalse(result.confident)
    }

    @Test
    fun `unresolved merchant VPAs with no human-readable name are flagged for review not guessed`() {
        val engine = CategoryEngine()
        // Real examples: no readable merchant name, and none should accidentally collide with an
        // existing keyword substring (e.g. "grofers1paytm" must NOT match the "grofers" keyword —
        // whole-word matching, not substring, is what prevents that false positive).
        for (vpa in listOf("paytm.s1dm4e1@pty", "BHARATPE.90068516197@fbpe", "grofers1paytm@hdfcbank")) {
            val result = engine.categorize(vpa)
            assertEquals("$vpa should be uncategorized", Category.UNCATEGORIZED, result.category)
            assertFalse("$vpa should not be confident", result.confident)
        }
    }

    @Test
    fun `learning a VPA once is remembered for future transactions`() {
        val engine = CategoryEngine()
        engine.correctCategory("grofers1paytm@hdfcbank", Category.GROCERIES)
        assertEquals(Category.GROCERIES, engine.categorize("grofers1paytm@hdfcbank").category)
    }

    @Test
    fun `null merchant is uncategorized`() {
        val engine = CategoryEngine()
        val result = engine.categorize(null)
        assertEquals(Category.UNCATEGORIZED, result.category)
        assertFalse(result.confident)
    }

    @Test
    fun `user correction is remembered for future transactions`() {
        val engine = CategoryEngine()
        val before = engine.categorize("Local Kirana Store")
        assertEquals(Category.UNCATEGORIZED, before.category)

        engine.correctCategory("Local Kirana Store", Category.GROCERIES)

        val after = engine.categorize("Local Kirana Store")
        assertEquals(Category.GROCERIES, after.category)
        assertTrue(after.confident)
        assertEquals(MatchSource.LEARNED, after.matchSource)
    }

    @Test
    fun `learned mapping takes priority over keyword table`() {
        val engine = CategoryEngine()
        // amazon defaults to Miscellaneous, but the user can override it for their own habits.
        engine.correctCategory("Amazon", Category.CLOTHES_SHOPPING)
        assertEquals(Category.CLOTHES_SHOPPING, engine.categorize("Amazon").category)
    }

    @Test
    fun `short single-word keyword does not match inside unrelated words`() {
        val engine = CategoryEngine()
        // "rent" is a keyword for Rent/EMI but must not match inside "Parent" or "Different".
        val result = engine.categorize("Different Traders Pvt Ltd")
        assertEquals(Category.UNCATEGORIZED, result.category)
    }

    @Test
    fun `custom keyword rule can be added at runtime`() {
        val engine = CategoryEngine()
        engine.addKeywordRule("mycompanycanteen", Category.FOOD_DINING)
        assertEquals(Category.FOOD_DINING, engine.categorize("MyCompanyCanteen").category)
    }

    @Test
    fun `keyword rule can be removed`() {
        val engine = CategoryEngine()
        engine.removeKeywordRule("swiggy")
        assertEquals(Category.UNCATEGORIZED, engine.categorize("Swiggy").category)
    }
}
