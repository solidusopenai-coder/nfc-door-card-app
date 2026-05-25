package com.victor.ncfdoorcard

import android.nfc.cardemulation.HostApduService
import android.os.Bundle
import android.util.Log

/**
 * NFC 卡片模擬服務
 * 當手機被用作門禁卡時，回應讀卡器嘅 APDU 命令
 */
class NfcCardEmulationService : HostApduService() {
    companion object {
        private const val TAG = "NfcCardEmulSvc"
        
        // ISO 7816-4 SELECT APDU (選擇 MF/根目錄)
        private val SELECT_MF_APDU = byteArrayOf(
            0xFF.toByte(), 0xA4.toByte(), 0x04.toByte(), 0x00.toByte(),
            0x02.toByte(), 0x3F.toByte(), 0x00.toByte()
        )
        
        // UID（模擬用，實際讀取時會用真實卡片嘅 UID）
        private var simulatedUid: ByteArray? = null
    }

    override fun processCommandApdu(commandApdu: ByteArray?, extras: Bundle?): ByteArray? {
        if (commandApdu == null) {
            return ErrorResponse()
        }

        Log.d(TAG, "Received APDU: ${commandApdu.toHexString()}")

        // 如果係 SELECT MF 命令，返回成功響應
        if (commandApdu.contentEquals(SELECT_MF_APDU)) {
            val response = byteArrayOf(
                0x90.toByte(), 0x00.toByte() // SW1=90, SW2=00 (Success)
            )
            return response
        }

        // 其他命令返回未處理
        Log.w(TAG, "Unhandled APDU: ${commandApdu.toHexString()}")
        return null // null = 不回應，由系統處理
    }

    override fun onDeactivated(reason: Int) {
        Log.d(TAG, "Card emulation deactivated: $reason")
    }

    /** ByteArray 轉 hex */
    private fun ByteArray.toHexString(): String {
        return joinToString(" ") { "%02X".format(it) }
    }

    companion object {
        fun setSimulatedUid(uid: ByteArray?) {
            simulatedUid = uid
        }
        
        fun getSimulatedUid(): ByteArray? = simulatedUid
        
        private fun ErrorResponse(): ByteArray {
            return byteArrayOf(0x6A.toByte(), 0x80.toByte()) // SW1=6A, SW2=80 (Wrong parameters)
        }
    }
}
