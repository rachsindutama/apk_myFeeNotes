package com.example.data.sync

import com.example.data.model.Transaction
import kotlinx.coroutines.delay

object CloudSyncManager {
    // In-memory mapping simulating the Cloud Server DB per user email.
    private val cloudDatabase = mutableMapOf<String, MutableList<Transaction>>()

    suspend fun syncData(userId: String, localUnsynced: List<Transaction>): SyncResult {
        // Simulate network delay
        delay(1500)

        try {
            val userCloudList = cloudDatabase.getOrPut(userId) { mutableListOf() }

            // Push process: Add all local unsynced transactions that don't exist under ID
            localUnsynced.forEach { localTx ->
                val alreadyExistsIdx = userCloudList.indexOfFirst { 
                    (localTx.id != 0L && it.id == localTx.id) || 
                    (it.note == localTx.note && it.amount == localTx.amount && it.date == localTx.date) 
                }
                if (alreadyExistsIdx != -1) {
                    userCloudList[alreadyExistsIdx] = localTx.copy(isSynced = true)
                } else {
                    userCloudList.add(localTx.copy(isSynced = true))
                }
            }

            // Pull process: Retrieve all items in the cloud for this user
            val allCloudItems = userCloudList.map { it.copy(isSynced = true) }

            return SyncResult.Success(
                pushedCount = localUnsynced.size,
                pulledItems = allCloudItems
            )
        } catch (e: Exception) {
            return SyncResult.Error(e.message ?: "Unknown sync error")
        }
    }

    // A helper to seed some initial dummy data in the Cloud if a user does list restore!
    fun seedCloudData(userId: String) {
        val userCloudList = cloudDatabase.getOrPut(userId) { mutableListOf() }
        if (userCloudList.isEmpty()) {
            val now = System.currentTimeMillis()
            userCloudList.add(Transaction(id = 10001, userId = userId, amount = 7500000.0, type = "INCOME", category = "Gaji", date = now - 5 * 24 * 3600 * 1000, note = "Gaji Bulanan Utama", isSynced = true))
            userCloudList.add(Transaction(id = 10002, userId = userId, amount = 120000.0, type = "EXPENSE", category = "Makanan", date = now - 4 * 24 * 3600 * 1000, note = "Makan Siang Rawon", isSynced = true))
            userCloudList.add(Transaction(id = 10003, userId = userId, amount = 50000.0, type = "EXPENSE", category = "Transportasi", date = now - 3 * 24 * 3600 * 1000, note = "Pengisian Ovo Gojek", isSynced = true))
            userCloudList.add(Transaction(id = 10004, userId = userId, amount = 1250000.0, type = "INCOME", category = "Investasi", date = now - 2 * 24 * 3600 * 1000, note = "Dividen Reksa Dana", isSynced = true))
            userCloudList.add(Transaction(id = 10005, userId = userId, amount = 350000.0, type = "EXPENSE", category = "Belanja", date = now - 1 * 24 * 3600 * 1000, note = "Belanja Bulanan Alfamart", isSynced = true))
        }
    }

    fun clearCloudData(userId: String) {
        cloudDatabase.remove(userId)
    }
}

sealed class SyncResult {
    data class Success(val pushedCount: Int, val pulledItems: List<Transaction>) : SyncResult()
    data class Error(val message: String) : SyncResult()
}
