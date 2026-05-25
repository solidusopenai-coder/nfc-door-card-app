package com.victor.ncfdoorcard

import android.nfc.NfcAdapter
import android.nfc.Tag
import android.nfc.tech.*
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * NFC 讀卡服務 — 負責讀取門禁卡資料
 * 支援 MIFARE Classic / Ultralight / FeliCa / ISO14443-B
 */
class NfcReaderService {
    companion object {
        private const val TAG = "NfcReaderService"
    }

    /**
     * 讀取 NFC Tag 嘅資料
     * @return CardData? 如果讀取成功返回卡片數據，失敗返回 null
     */
    suspend fun readTag(tag: Tag): CardData? = withContext(Dispatchers.IO) {
        try {
            val techList = tag.techList.map { it to Class.forName(it).kotlin }
            
            // 嘗試 MIFARE Classic（最常見嘅門禁卡類型）
            MifareClassic.get(tag)?.use { mfc ->
                Log.d(TAG, "Detected: MIFARE Classic (${mfc.type})")
                return@withContext readMifareClassic(mfc, tag)
            }

            // 嘗試 MIFARE Ultralight（部分新式門禁卡）
            MifareUltralight.get(tag)?.use { mu ->
                Log.d(TAG, "Detected: MIFARE Ultralight")
                return@withContext readMifareUltralight(mu, tag)
            }

            // 嘗試 FeliCa（日本/部分港式大廈）
            Felica.get(tag)?.use { felica ->
                Log.d(TAG, "Detected: FeliCa")
                return@withContext readFelica(felica, tag)
            }

            // 嘗試 ISO Dep（ISO 14443-4）
            IsoDep.get(tag)?.use { isoDep ->
                Log.d(TAG, "Detected: ISO 14443-4 (IsoDep)")
                return@withContext readIsoDep(isoDep, tag)
            }

            Log.w(TAG, "Unsupported card type")
            null

        } catch (e: Exception) {
            Log.e(TAG, "Error reading tag", e)
            null
        }
    }

    /** 讀取 MIFARE Classic 卡片 */
    private fun readMifareClassic(mfc: MifareClassic, tag: Tag): CardData? {
        try {
            mfc.connect()
            
            val uid = bytesToHex(tag.id)
            val type = when (mfc.type) {
                MifareClassic.TYPE_CLASSIC -> "MIFARE Classic 1K"
                MifareClassic.TYPE_PLUS -> "MIFARE Classic Plus"
                MifareClassic.TYPE_PRO -> "MIFARE Classic Pro"
                else -> "MIFARE Classic Unknown"
            }

            // 嘗試用常見密碼驗證區塊（MIFARE Classic 默認密碼）
            val defaultKeys = listOf(
                byteArrayOf(0xFF.toByte(), 0xFF.toByte(), 0xFF.toByte(), 0xFF.toByte(), 0xFF.toByte(), 0xFF.toByte()),
                byteArrayOf(0x00, 0x00, 0x00, 0x00, 0x00, 0x00),
                byteArrayOf(0xA0, 0xA1, 0xA2, 0xA3, 0xA4, 0xA5),
                byteArrayOf(0xB0, 0xB1, 0xB2, 0xB3, 0xB4, 0xB5),
            )

            val readableBlocks = mutableListOf<String>()
            val totalBlocks = mfc.blockCount
            
            // 讀取前 16 個區塊（通常包含 UID 同基本資料）
            for (block in 0 until minOf(totalBlocks, 16)) {
                defaultKeys.forEachIndexed { keyIndex, key ->
                    try {
                        if (mfc.authenticateSectorWithKeyA(block * 4, key)) {
                            val data = mfc.readBlock(block)
                            readableBlocks.add("Block $block: ${bytesToHex(data)}")
                            return@forEachIndexed // 认证成功，跳到下一個區塊
                        }
                    } catch (e: Exception) { /* 密碼錯誤，試下一個 */ }
                }
            }

            val dataBlocks = readableBlocks.joinToString("\n")
            
            CardData(
                uid = uid,
                cardType = "MIFARE Classic",
                name = "門禁卡 ${uid.takeLast(4)}",
                dataBlocks = dataBlocks,
                extraInfo = "類型: $type | 區塊數: $totalBlocks"
            )

        } catch (e: Exception) {
            Log.e(TAG, "Error reading MIFARE Classic", e)
            null
        }
    }

    /** 讀取 MIFARE Ultralight 卡片 */
    private fun readMifareUltralight(mu: MifareUltralight, tag: Tag): CardData? {
        try {
            mu.connect()
            
            val uid = bytesToHex(tag.id)
            
            // Ultralight 只讀前 16 頁（每頁 4 bytes）
            val pages = mutableListOf<String>()
            for (page in 0 until minOf(16, mu.pageCount)) {
                val data = mu.readPages(page)
                pages.add("Page $page: ${bytesToHex(data)}")
            }

            CardData(
                uid = uid,
                cardType = "MIFARE Ultralight",
                name = "Ultralight 卡 ${uid.takeLast(4)}",
                dataBlocks = pages.joinToString("\n"),
                extraInfo = "頁數: ${mu.pageCount}"
            )

        } catch (e: Exception) {
            Log.e(TAG, "Error reading MIFARE Ultralight", e)
            null
        }
    }

    /** 讀取 FeliCa 卡片 */
    private fun readFelica(felica: Felica, tag: Tag): CardData? {
        try {
            felica.connect()
            
            val uid = bytesToHex(tag.id)
            
            // 讀取服務 ID 同數據
            val serviceIdList = felica.requestService(listOf(0xFFFE.toShort().toUByte().toInt().toShort()))
            val requestCode = felica.requestResponse()

            CardData(
                uid = uid,
                cardType = "FeliCa",
                name = "FeliCa 卡 ${uid.takeLast(4)}",
                dataBlocks = "Service IDs: ${serviceIdList.joinToString(", ")}\nRequest Code: ${requestCode.toHexString()}",
                extraInfo = "FeliCa 卡片"
            )

        } catch (e: Exception) {
            Log.e(TAG, "Error reading FeliCa", e)
            null
        }
    }

    /** 讀取 ISO 14443-4 卡片 */
    private fun readIsoDep(isoDep: IsoDep, tag: Tag): CardData? {
        try {
            isoDep.connect()
            
            val uid = bytesToHex(tag.id)
            val atqa = isoDep.atqa?.let { bytesToHex(it) } ?: "N/A"
            val sak = isoDep.sak.toString(16).padStart(2, '0')

            // 發送 SELECT AID 命令
            val selectCmd = byteArrayOf(
                0xFF.toByte(), 0xCA.toByte(), 0x00.toByte(), // APDU SELECT
                0x00.toByte(), // P1
                0x00.toByte(), // P2
                0x07.toByte()  // Le (expected response length)
            )
            
            val response = isoDep.transceive(selectCmd)

            CardData(
                uid = uid,
                cardType = "ISO 14443-4",
                name = "ISO14443-4 卡 ${uid.takeLast(4)}",
                dataBlocks = "ATQA: $atqa\nSAK: $sak\nResponse: ${bytesToHex(response)}",
                extraInfo = "ISO 14443-4 (IsoDep)"
            )

        } catch (e: Exception) {
            Log.e(TAG, "Error reading ISO Dep", e)
            null
        }
    }

    /** Byte array 轉 hex string */
    private fun bytesToHex(bytes: ByteArray): String {
        return bytes.joinToString("") { "%02X".format(it) }
    }

    /** ByteArray 轉 hex（小寫） */
    private fun ByteArray.toHexString(): String {
        return joinToString("") { "%02x".format(it) }
    }
}

/** NFC 讀取到嘅卡片數據 */
data class CardData(
    val uid: String,
    val cardType: String,
    val name: String,
    val dataBlocks: String,
    val extraInfo: String = ""
)
