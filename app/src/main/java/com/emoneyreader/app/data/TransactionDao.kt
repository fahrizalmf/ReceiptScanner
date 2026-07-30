package com.emoneyreader.app.data

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface TransactionDao {

    @Query("SELECT * FROM transaction_history ORDER BY timestamp DESC")
    fun getAll(): Flow<List<TransactionHistory>>

    @Query("""
        SELECT * FROM transaction_history 
        WHERE timestamp BETWEEN :startMillis AND :endMillis 
        ORDER BY timestamp DESC
    """)
    suspend fun getByPeriod(startMillis: Long, endMillis: Long): List<TransactionHistory>

    @Insert
    suspend fun insert(transaction: TransactionHistory): Long

    @Delete
    suspend fun delete(transaction: TransactionHistory)
}
