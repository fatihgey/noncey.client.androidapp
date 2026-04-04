package com.noncey.android.ui.sms

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.noncey.android.databinding.ItemSmsBinding
import java.text.SimpleDateFormat
import java.util.*

class SmsAdapter(
    private val onForward: (SmsItem) -> Unit
) : ListAdapter<SmsItem, SmsAdapter.ViewHolder>(DIFF) {

    inner class ViewHolder(private val b: ItemSmsBinding) : RecyclerView.ViewHolder(b.root) {
        fun bind(item: SmsItem) {
            b.tvSender.text  = item.sender
            b.tvBody.text    = item.body.take(120).replace('\n', ' ')
            b.tvDate.text    = DATE_FMT.format(Date(item.dateMs))
            b.btnForward.setOnClickListener { onForward(item) }
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) =
        ViewHolder(ItemSmsBinding.inflate(LayoutInflater.from(parent.context), parent, false))

    override fun onBindViewHolder(holder: ViewHolder, position: Int) =
        holder.bind(getItem(position))

    companion object {
        private val DATE_FMT = SimpleDateFormat("dd MMM HH:mm", Locale.getDefault())

        private val DIFF = object : DiffUtil.ItemCallback<SmsItem>() {
            override fun areItemsTheSame(a: SmsItem, b: SmsItem) = a.id == b.id
            override fun areContentsTheSame(a: SmsItem, b: SmsItem) = a == b
        }
    }
}
