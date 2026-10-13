package com.example.data.repository

import com.example.data.db.TransactionDao
import com.example.data.model.Transaction
import kotlinx.coroutines.flow.Flow

class TransactionRepository(private val transactionDao: TransactionDao) {
    fun getTransactionsByUser(userId: String): Flow<List<Transaction>> =
        transactionDao.getTransactionsByUser(userId)

    suspend fun getUnsyncedTransactions(userId: String): List<Transaction> =
        transactionDao.getUnsyncedTransactions(userId)

    suspend fun insertTransaction(transaction: Transaction) =
        transactionDao.insertTransaction(transaction)

    suspend fun insertTransactions(transactions: List<Transaction>) =
        transactionDao.insertTransactions(transactions)

    suspend fun markAllAsSynced(userId: String) =
        transactionDao.markAllAsSynced(userId)

    suspend fun deleteTransaction(transaction: Transaction) =
        transactionDao.deleteTransaction(transaction)

    suspend fun clearUserTransactions(userId: String) =
        transactionDao.clearUserTransactions(userId)
}
