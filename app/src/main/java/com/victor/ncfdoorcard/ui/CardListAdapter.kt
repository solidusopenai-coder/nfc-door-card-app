package com.victor.ncfdoorcard.ui

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.victor.ncfdoorcard.R
import com.victor.ncfdoorcard.data.CardEntity

/** RecyclerView Adapter for card list */
class CardListAdapter(
    private val onItemClick: (CardEntity) -> Unit,
    private val onDeleteClick: (CardEntity) -> Unit
) : ListAdapter<CardEntity, CardListAdapter.CardViewHolder>(CardDiffCallback()) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): CardViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_card, parent, false)
        return CardViewHolder(view)
    }

    override fun onBindViewHolder(holder: CardViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    inner class CardViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val tvCardName: TextView = itemView.findViewById(R.id.tvCardName)
        private val tvCardUid: TextView = itemView.findViewById(R.id.tvCardUid)
        private val tvCardType: TextView = itemView.findViewById(R.id.tvCardType)

        fun bind(card: CardEntity) {
            tvCardName.text = card.name + if (card.isActive) " ⭐" else ""
            tvCardUid.text = "UID: ${card.uid}"
            tvCardType.text = card.cardType
            
            itemView.setOnClickListener { onItemClick(card) }
            
            // Long press to delete
            itemView.setOnLongClickListener {
                onDeleteClick(card)
                true
            }
        }
    }

    class CardDiffCallback : DiffUtil.ItemCallback<CardEntity>() {
        override fun areItemsTheSame(oldItem: CardEntity, newItem: CardEntity): Boolean {
            return oldItem.id == newItem.id
        }

        override fun areContentsTheSame(oldItem: CardEntity, newItem: CardEntity): Boolean {
            return oldItem == newItem
        }
    }
}
