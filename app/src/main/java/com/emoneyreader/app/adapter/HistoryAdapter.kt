package com.emoneyreader.app.adapter

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.emoneyreader.app.data.TransactionHistory
import com.emoneyreader.app.databinding.ItemHistoryBinding
import java.text.NumberFormat
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class HistoryAdapter(
    private var items: List<TransactionHistory>,
    private val onDeleteClick: (TransactionHistory) -> Unit
) : RecyclerView.Adapter<HistoryAdapter.ViewHolder>() {

    private val dateFormat = SimpleDateFormat("dd MMM yyyy, HH:mm", Locale("id", "ID"))
    private val currencyFormat = NumberFormat.getCurrencyInstance(Locale("id", "ID"))

    inner class ViewHolder(val binding: ItemHistoryBinding) : RecyclerView.ViewHolder(binding.root)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val binding = ItemHistoryBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return ViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val item = items[position]
        holder.binding.tvTollGate.text = item.tollGateName
        holder.binding.tvDateTime.text = dateFormat.format(Date(item.timestamp))
        holder.binding.tvNominal.text = currencyFormat.format(item.nominal)
        holder.binding.tvCardUid.text = if (!item.cardUid.isNullOrBlank())
            "Kartu: ${item.cardUid}" else "Kartu: (input manual)"

        holder.binding.btnDelete.setOnClickListener { onDeleteClick(item) }
    }

    override fun getItemCount(): Int = items.size

    fun updateData(newItems: List<TransactionHistory>) {
        items = newItems
        notifyDataSetChanged()
    }
}
