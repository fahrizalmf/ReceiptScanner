package com.emoneyreader.app.data

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface TollGateDao {

    @Query("SELECT * FROM toll_gate ORDER BY name ASC")
    fun getAll(): Flow<List<TollGate>>

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insert(tollGate: TollGate): Long

    @Update
    suspend fun update(tollGate: TollGate)

    @Delete
    suspend fun delete(tollGate: TollGate)
}
