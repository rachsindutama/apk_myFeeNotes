package com.example.data.db

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.example.data.model.Transaction
import kotlinx.coroutines.flow.Flow

@Dao
interface TransactionDao {
    @Query("SELECT * FROM transactions WHERE userId = :userId ORDER BY date DESC")
    fun getTransactionsByUser(userId: String): Flow<List<Transaction>>

    @Query("SELECT * FROM transactions WHERE userId = :userId AND isSynced = 0")
    suspend fun getUnsyncedTransactions(userId: String): List<Transaction>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertTransaction(transaction: Transaction)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertTransactions(transactions: List<Transaction>)

    @Query("UPDATE transactions SET isSynced = 1 WHERE userId = :userId")
    suspend fun markAllAsSynced(userId: String)

    @Delete
    suspend fun deleteTransaction(transaction: Transaction)

    @Query("DELETE FROM transactions WHERE userId = :userId")
    suspend fun clearUserTransactions(userId: String)
}
