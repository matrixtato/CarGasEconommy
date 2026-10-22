package com.example.cargaseconommy.shared.database

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "fuel_entries")
data class FuelEntry(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val timestamp: Long,
    val alcoholPrice: Double,
    val gasolinePrice: Double,
    val totalPaid: Double = 0.0, // Valor total do abastecimento (R$)
    val km: Double,
    val liters: Double,
    val fuelType: String, // "ALCOOL" ou "GASOLINA"
    val alcoholPercentage: Double
)
