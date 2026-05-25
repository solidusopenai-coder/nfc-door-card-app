package com.victor.ncfdoorcard

import android.app.PendingIntent
import android.content.Intent
import android.content.IntentFilter
import android.nfc.NfcAdapter
import android.nfc.Tag
import android.os.Build
import android.os.Bundle
import android.os.VibrationEffect
import android.os.Vibrator
import android.provider.Settings
import android.util.Log
import android.view.Menu
import android.view.MenuItem
import android.widget.Button
import android.widget.RecyclerView
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.lifecycle.ViewModelProvider
import androidx.recyclerview.widget.LinearLayoutManager
import com.victor.ncfdoorcard.data.CardEntity
import com.victor.ncfdoorcard.ui.CardListAdapter
import com.victor.ncfdoorcard.ui.MainViewModel

class MainActivity : AppCompatActivity() {
    
    companion object {
        private const val TAG = "MainActivity"
    }

    private lateinit var nfcAdapter: NfcAdapter
    private lateinit var viewModel: MainViewModel
    private lateinit var readerService: NfcReaderService
    
    private lateinit var tvNfcStatus: TextView
    private lateinit var btnScanNfc: Button
    private lateinit var rvCardList: RecyclerView
    private lateinit var adapter: CardListAdapter

    // NFC PendingIntent for foreground dispatch
    private lateinit var nfcPendingIntent: PendingIntent
    private lateinit var nfcFilters: Array<IntentFilter>
    private lateinit var techLists: Array<Array<Class<*>>>

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        // Initialize NFC adapter
        nfcAdapter = NfcAdapter.getDefaultAdapter(this)
        
        if (nfcAdapter == null) {
            showNfcNotSupported()
            return
        }

        // Initialize ViewModel & Reader Service
        viewModel = ViewModelProvider(this)[MainViewModel::class.java]
        readerService = NfcReaderService()

        setupViews()
        setupNfcForegroundDispatch()
        observeCards()
    }

    private fun setupViews() {
        tvNfcStatus = findViewById(R.id.tvNfcStatus)
        btnScanNfc = findViewById(R.id.btnScanNfc)
        rvCardList = findViewById(R.id.rvCardList)

        // Setup RecyclerView
        adapter = CardListAdapter(
            onItemClick = { card -> showCardDetails(card) },
            onDeleteClick = { card -> confirmDelete(card) }
        )
        rvCardList.layoutManager = LinearLayoutManager(this)
        rvCardList.adapter = adapter

        // Scan button
        btnScanNfc.setOnClickListener {
            if (!nfcAdapter.isEnabled) {
                Toast.makeText(this, R.string.nfc_disabled, Toast.LENGTH_LONG).show()
                startActivity(Intent(Settings.ACTION_NFC_SETTINGS))
            } else {
                enableForegroundDispatch()
                tvNfcStatus.text = getString(R.string.nfc_reading)
                btnScanNfc.isEnabled = false
            }
        }

        // Check NFC status on startup
        updateNfcStatus()
    }

    private fun setupNfcForegroundDispatch() {
        nfcPendingIntent = PendingIntent.getActivity(
            this, 0,
            Intent(this, javaClass).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),
            PendingIntent.FLAG_MUTABLE
        )

        // NFC intent filters
        nfcFilters = arrayOf(
            IntentFilter(NfcAdapter.ACTION_TAG_DISCOVERED)
        )

        // Tech lists to filter
        techLists = arrayOf(
            arrayOf(
                android.nfc.tech.MifareClassic::class.java,
                android.nfc.tech.MifareUltralight::class.java,
                android.nfc.tech.Felica::class.java,
                android.nfc.tech.IsoDep::class.java,
                android.nfc.tech.Ndef::class.java,
                android.nfc.tech.NdefFormatable::class.java
            )
        )
    }

    private fun enableForegroundDispatch() {
        nfcAdapter.enableForegroundDispatch(
            this,
            nfcPendingIntent,
            nfcFilters,
            techLists
        )
    }

    private fun disableForegroundDispatch() {
        try {
            nfcAdapter.disableForegroundDispatch(this)
        } catch (e: Exception) {
            Log.w(TAG, "Error disabling foreground dispatch", e)
        }
    }

    override fun onResume() {
        super.onResume()
        updateNfcStatus()
        
        // If NFC is enabled and we're in scan mode, enable foreground dispatch
        if (nfcAdapter?.isEnabled == true && btnScanNfc.isEnabled.not()) {
            enableForegroundDispatch()
        }
    }

    override fun onPause() {
        super.onPause()
        disableForegroundDispatch()
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        
        if (intent.action == NfcAdapter.ACTION_TAG_DISCOVERED) {
            val tag: Tag? = intent.getParcelableExtra(NfcAdapter.EXTRA_TAG)
            if (tag != null) {
                readNfcTag(tag)
            }
        }
    }

    private fun readNfcTag(tag: Tag) {
        // Vibrate to confirm NFC detected
        vibrate()
        
        viewModel.allCards.value?.let { cards ->
            Log.d(TAG, "Reading tag. Current cards count: ${cards.size}")
        }

        // Use the reader service to extract card data
        val cardData = runBlocking {
            readerService.readTag(tag)
        }

        if (cardData != null) {
            Log.d(TAG, "Card read successfully: ${cardData.uid} (${cardData.cardType})")
            
            // Check if card already exists by UID
            val existingCard = viewModel.allCards.value?.find { it.uid == cardData.uid }
            
            if (existingCard != null) {
                // Update last used time
                viewModel.updateLastUsed(existingCard.id)
                Toast.makeText(this, "卡片已更新 — ${cardData.name}", Toast.LENGTH_SHORT).show()
            } else {
                // Save new card
                val newCard = CardEntity(
                    uid = cardData.uid,
                    cardType = cardData.cardType,
                    name = cardData.name,
                    dataBlocks = cardData.dataBlocks
                )
                viewModel.addCard(newCard)
                Toast.makeText(this, R.string.toast_card_saved, Toast.LENGTH_SHORT).show()
            }

        } else {
            Log.w(TAG, "Failed to read tag")
            tvNfcStatus.text = getString(R.string.nfc_error)
        }

        // Reset scan mode after a delay
        resetScanMode()
    }

    private fun observeCards() {
        viewModel.allCards.observe(this) { cards ->
            adapter.submitList(cards)
            
            val emptyState = findViewById<TextView>(R.id.tvEmptyState)
            if (cards.isEmpty()) {
                rvCardList.visibility = android.view.View.GONE
                emptyState?.visibility = android.view.View.VISIBLE
            } else {
                rvCardList.visibility = android.view.View.VISIBLE
                emptyState?.visibility = android.view.View.GONE
            }

            // Update active card display
            updateActiveCardDisplay()
        }

        viewModel.activeCard.observe(this) { card ->
            updateActiveCardDisplay()
        }
    }

    private fun updateActiveCardDisplay() {
        val layoutActive = findViewById<android.widget.LinearLayout>(R.id.layoutActiveCard)
        val tvActiveName = findViewById<TextView>(R.id.tvActiveCardName)
        
        viewModel.activeCard.value?.let { card ->
            layoutActive?.visibility = android.view.View.VISIBLE
            tvActiveName?.text = "${card.name} (${card.uid.takeLast(6)})"
        } ?: run {
            layoutActive?.visibility = android.view.View.GONE
        }
    }

    private fun updateNfcStatus() {
        if (nfcAdapter == null) {
            tvNfcStatus.text = getString(R.string.nfc_not_supported)
            btnScanNfc.isEnabled = false
        } else if (!nfcAdapter!!.isEnabled) {
            tvNfcStatus.text = getString(R.string.nfc_disabled)
            btnScanNfc.isEnabled = true
        } else {
            tvNfcStatus.text = getString(R.string.main_scan_hint)
            btnScanNfc.isEnabled = true
        }
    }

    private fun showNfcNotSupported() {
        tvNfcStatus.text = getString(R.string.nfc_not_supported)
        btnScanNfc.isEnabled = false
    }

    private fun resetScanMode() {
        runOnUiThread {
            disableForegroundDispatch()
            tvNfcStatus.text = getString(R.string.main_scan_hint)
            btnScanNfc.isEnabled = true
        }
    }

    private fun showCardDetails(card: CardEntity) {
        val dialog = AlertDialog.Builder(this)
            .setTitle("卡片詳情")
            .setMessage(
                "名稱：${card.name}\n" +
                "UID：${card.uid}\n" +
                "類型：${card.cardType}\n" +
                "資料區塊：\n${card.dataBlocks.take(200)}\n" +
                (if (card.extraInfo.isNotEmpty()) "\n額外信息：${card.extraInfo}" else "")
            )
            .setPositiveButton("關閉") { d, _ -> d.dismiss() }
            .setNeutralButton("重命名") { _, _ -> renameCard(card) }
            .setNegativeButton(if (card.isActive) "停用" else "設為活躍卡") { _, _ ->
                viewModel.setActiveCard(card.id)
            }
            .create()

        dialog.show()
    }

    private fun renameCard(card: CardEntity) {
        val input = android.widget.EditText(this).apply {
            setText(card.name)
            hint = "輸入新名稱"
        }

        AlertDialog.Builder(this)
            .setTitle("重新命名卡片")
            .setView(input)
            .setPositiveButton("確定") { _, _ ->
                val newName = input.text.toString().trim()
                if (newName.isNotEmpty()) {
                    viewModel.updateCardName(card.id, newName)
                    Toast.makeText(this, "已更新名稱", Toast.LENGTH_SHORT).show()
                }
            }
            .setNegativeButton("取消", null)
            .show()
    }

    private fun confirmDelete(card: CardEntity) {
        AlertDialog.Builder(this)
            .setTitle(R.string.main_delete_confirm)
            .setMessage("${card.name} (${card.uid})")
            .setPositiveButton("刪除") { _, _ ->
                viewModel.deleteCard(card)
                Toast.makeText(this, R.string.toast_card_deleted, Toast.LENGTH_SHORT).show()
            }
            .setNegativeButton("取消", null)
            .show()
    }

    private fun vibrate() {
        val vibrator = getSystemService(VIBRATOR_SERVICE) as Vibrator
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            vibrator.vibrate(VibrationEffect.createOneShot(100, VibrationEffect.DEFAULT_AMPLITUDE))
        } else {
            @Suppress("DEPRECATION")
            vibrator.vibrate(100)
        }
    }

    override fun onCreateOptionsMenu(menu: Menu?): Boolean {
        menuInflater.inflate(R.menu.main_menu, menu)
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            R.id.action_about -> {
                AlertDialog.Builder(this)
                    .setTitle("關於")
                    .setMessage(
                        "NFC 門禁卡模擬 App\n" +
                        "版本：1.0\n\n" +
                        "功能：\n" +
                        "• 讀取門禁卡 UID 同資料\n" +
                        "• 儲存多張卡片\n" +
                        "• 模擬門禁卡開門\n\n" +
                        "支援類型：\n" +
                        "• MIFARE Classic/Ultralight\n" +
                        "• FeliCa\n" +
                        "• ISO 14443-A/B"
                    )
                    .setPositiveButton("確定", null)
                    .show()
                true
            }
            else -> super.onOptionsItemSelected(item)
        }
    }

    /** Simple coroutine scope for readNfcTag */
    private suspend inline fun <reified T> runBlocking(block: () -> T): T {
        return kotlinx.coroutines.runBlocking { block() }
    }
}
