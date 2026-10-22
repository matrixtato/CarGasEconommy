package com.example.cargaseconommy.shared.database

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface FuelDao {
    @Query("SELECT * FROM fuel_entries ORDER BY timestamp DESC")
    fun getAllEntries(): Flow<List<FuelEntry>>

    @Query("SELECT * FROM fuel_entries ORDER BY timestamp DESC LIMIT 1")
    suspend fun getLastEntry(): FuelEntry?

    @Insert
    suspend fun insertEntry(entry: FuelEntry)

    @Query("DELETE FROM fuel_entries")
    suspend fun deleteAll()
}
