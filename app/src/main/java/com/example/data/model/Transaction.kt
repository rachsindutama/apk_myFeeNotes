package com.example.data.model

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "transactions")
data class Transaction(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val userId: String,
    val amount: Double,
    val type: String, // "INCOME" or "EXPENSE"
    val category: String,
    val date: Long, // timestamp
    val note: String,
    val isSynced: Boolean = false
)
