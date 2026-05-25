package com.victor.ncfdoorcard.ui

import androidx.lifecycle.*
import com.victor.ncfdoorcard.data.CardDao
import com.victor.ncfdoorcard.data.CardEntity
import kotlinx.coroutines.launch

/** Main ViewModel — 管理卡片列表同 NFC 狀態 */
class MainViewModel(application: android.app.Application) : AndroidViewModel(application) {
    
    private val database = com.victor.ncfdoorcard.data.CardDatabase.getDatabase(application)
    private val cardDao = database.cardDao()

    /** 所有卡片列表 */
    val allCards: LiveData<List<CardEntity>> = cardDao.getAllCards()
    
    /** 當前活躍卡 */
    val activeCard: LiveData<CardEntity?> = cardDao.getActiveCard()

    /** 新增卡片 */
    fun addCard(card: CardEntity) {
        viewModelScope.launch {
            // 先停用其他卡
            val existingActive = cardDao.getActiveCard().value
            if (existingActive != null && existingActive.id != card.id) {
                cardDao.deactivateAllExcept(0L)
            }
            cardDao.insertCard(card.copy(isActive = true))
        }
    }

    /** 刪除卡片 */
    fun deleteCard(card: CardEntity) {
        viewModelScope.launch {
            cardDao.deleteCard(card)
        }
    }

    /** 設置活躍卡 */
    fun setActiveCard(cardId: Long) {
        viewModelScope.launch {
            cardDao.deactivateAllExcept(cardId)
            val card = cardDao.getCardById(cardId)
            if (card != null) {
                cardDao.updateCard(card.copy(isActive = true))
            }
        }
    }

    /** 更新卡片名稱 */
    fun updateCardName(cardId: Long, newName: String) {
        viewModelScope.launch {
            val card = cardDao.getCardById(cardId)
            if (card != null) {
                cardDao.updateCard(card.copy(name = newName))
            }
        }
    }

    /** 更新卡片最後使用時間 */
    fun updateLastUsed(cardId: Long) {
        viewModelScope.launch {
            val card = cardDao.getCardById(cardId)
            if (card != null) {
                cardDao.updateCard(card.copy(lastUsedAt = System.currentTimeMillis()))
            }
        }
    }
}
