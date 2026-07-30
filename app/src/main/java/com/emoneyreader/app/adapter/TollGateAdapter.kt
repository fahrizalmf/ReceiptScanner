package com.emoneyreader.app.adapter

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.emoneyreader.app.data.TollGate
import com.emoneyreader.app.databinding.ItemTollGateBinding

class TollGateAdapter(
    private var items: List<TollGate>,
    private val onDeleteClick: (TollGate) -> Unit
) : RecyclerView.Adapter<TollGateAdapter.ViewHolder>() {

    inner class ViewHolder(val binding: ItemTollGateBinding) : RecyclerView.ViewHolder(binding.root)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val binding = ItemTollGateBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return ViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val item = items[position]
        holder.binding.tvName.text = item.name
        holder.binding.btnDelete.setOnClickListener { onDeleteClick(item) }
    }

    override fun getItemCount(): Int = items.size

    fun updateData(newItems: List<TollGate>) {
        items = newItems
        notifyDataSetChanged()
    }
}
