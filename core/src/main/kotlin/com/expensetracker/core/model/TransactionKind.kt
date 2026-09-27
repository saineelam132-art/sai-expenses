package com.expensetracker.core.model

/**
 * What a transaction *is* for accounting purposes — separate from [Category] (which sector it's
 * in) and from [TransactionType] (which is just the SMS's debit/credit direction). This is what
 * the ledger posting engine uses to decide which asset/liability accounts move and whether the
 * amount counts toward income/expense/investment totals at all.
 */
enum class TransactionKind(val displayName: String) {
    EXPENSE("Expense"),
    INCOME("Income"),
    SELF_TRANSFER("Self-Transfer"),
    INVESTMENT_BUY("Investment-Buy"),
    INVESTMENT_SELL("Investment-Sell"),
    LOAN_DISBURSED("Loan-Disbursed"),
    LOAN_REPAYMENT("Loan-Repayment"),
    LENT("Lent"),
    BORROWED("Borrowed"),
    FRIEND_REPAID_ME("Friend-Repaid-Me"),
    I_REPAID_FRIEND("I-Repaid-Friend"),
    CASH_WITHDRAWAL("Cash-Withdrawal"),
    CASH_DEPOSIT("Cash-Deposit"),

    /**
     * Spending physical cash. Distinct from [EXPENSE], which only moves a bank balance the bank
     * itself reports: this reduces cash on hand, the one balance nothing external tracks. It
     * still counts as spending everywhere totals are taken — the difference is which pot it
     * leaves, not whether it was spent.
     */
    CASH_SPEND("Cash Spend"),
    ;

    /** Kinds that count as money spent, for sector totals and outgoing cash flow. */
    val isSpending: Boolean get() = this == EXPENSE || this == CASH_SPEND

    /** Kinds that involve a specific Friend contact (see ContactEntity / LedgerAccount RECEIVABLE/PAYABLE). */
    val requiresContact: Boolean get() = this in setOf(LENT, BORROWED, FRIEND_REPAID_ME, I_REPAID_FRIEND)

    /** Kinds that involve a specific loan (see LedgerAccount LOAN category). */
    val requiresLoan: Boolean get() = this in setOf(LOAN_DISBURSED, LOAN_REPAYMENT)
}
