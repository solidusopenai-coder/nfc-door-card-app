package com.victor.ncfdoorcard.data

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * 門禁卡數據實體
 * 儲存卡片嘅 UID、類型同資料區塊
 */
@Entity(tableName = "cards")
data class CardEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    
    /** 卡片 UID (唯一標識) */
    val uid: String,
    
    /** 卡片類型: MIFARE Classic / Ultralight / FeliCa / ISO14443-B */
    val cardType: String,
    
    /** 卡片名稱（用戶自定義） */
    var name: String = "未命名卡片",
    
    /** 是否為活躍卡（用於模擬） */
    var isActive: Boolean = false,
    
    /** 完整資料區塊（hex string） */
    val dataBlocks: String = "",
    
    /** 額外信息 */
    val extraInfo: String = "",
    
    /** 創建時間 */
    val createdAt: Long = System.currentTimeMillis(),
    
    /** 最後使用時間 */
    val lastUsedAt: Long = 0L
)

/** NFC 卡片類型枚舉 */
enum class NfcCardType(val displayName: String, val techClass: String) {
    MIFARE_CLASSIC("MIFARE Classic", "android.nfc.tech.MifareClassic"),
    MIFARE_ULTRALIGHT("MIFARE Ultralight", "android.nfc.tech.MifareUltralight"),
    FELICA("FeliCa", "android.nfc.tech.Felica"),
    ISO_14443_B("ISO 14443-B", "android.nfc.tech.IsoDep"),
    UNKNOWN("未知類型", "")
}
