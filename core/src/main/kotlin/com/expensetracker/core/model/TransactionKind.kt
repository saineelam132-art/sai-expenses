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
    ;

    /** Kinds that involve a specific Friend contact (see ContactEntity / LedgerAccount RECEIVABLE/PAYABLE). */
    val requiresContact: Boolean get() = this in setOf(LENT, BORROWED, FRIEND_REPAID_ME, I_REPAID_FRIEND)

    /** Kinds that involve a specific loan (see LedgerAccount LOAN category). */
    val requiresLoan: Boolean get() = this in setOf(LOAN_DISBURSED, LOAN_REPAYMENT)
}
