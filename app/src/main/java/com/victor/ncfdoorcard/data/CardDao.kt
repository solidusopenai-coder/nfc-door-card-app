package com.victor.ncfdoorcard.data

import androidx.lifecycle.LiveData
import androidx.room.*

@Dao
interface CardDao {
    @Query("SELECT * FROM cards ORDER BY createdAt DESC")
    fun getAllCards(): LiveData<List<CardEntity>>

    @Query("SELECT * FROM cards WHERE isActive = 1 LIMIT 1")
    fun getActiveCard(): LiveData<CardEntity?>

    @Query("SELECT * FROM cards WHERE id = :id")
    suspend fun getCardById(id: Long): CardEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertCard(card: CardEntity): Long

    @Update
    suspend fun updateCard(card: CardEntity)

    @Delete
    suspend fun deleteCard(card: CardEntity)

    @Query("UPDATE cards SET isActive = 0 WHERE id != :excludeId")
    suspend fun deactivateAllExcept(excludeId: Long)
}
