package com.noncey.android.ui.configs

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.noncey.android.data.ConfigCache
import com.noncey.android.databinding.ItemConfigBinding

class ConfigsAdapter(
    private val onActivate:    (ConfigCache.SmsConfig) -> Unit,
    private val onUnsubscribe: (ConfigCache.SmsConfig) -> Unit
) : ListAdapter<ConfigCache.SmsConfig, ConfigsAdapter.ViewHolder>(DIFF) {

    inner class ViewHolder(private val b: ItemConfigBinding) : RecyclerView.ViewHolder(b.root) {
        fun bind(config: ConfigCache.SmsConfig) {
            b.tvName.text = config.name

            // Activate / deactivate button
            if (config.isOwned) {
                b.btnActivate.visibility = View.VISIBLE
                b.btnActivate.text = if (config.activated) "Deactivate" else "Activate"
                b.btnActivate.setOnClickListener { onActivate(config) }
                b.btnUnsubscribe.visibility = View.GONE
            } else {
                // Subscribed public config
                b.btnActivate.visibility    = View.GONE
                b.btnUnsubscribe.visibility = View.VISIBLE
                b.btnUnsubscribe.setOnClickListener { onUnsubscribe(config) }
            }

            // Status chip
            b.tvStatus.text = if (config.activated) "Active" else "Inactive"
            b.tvStatus.setTextColor(
                b.root.context.getColor(
                    if (config.activated) android.R.color.holo_green_dark
                    else android.R.color.darker_gray
                )
            )

            // Matcher info summary
            b.tvMatcherInfo.text = if (config.matchers.isEmpty()) {
                "No matcher configured"
            } else {
                config.matchers.joinToString(" | ") { m ->
                    buildString {
                        if (m.sender_phone != null) append("From: ${m.sender_phone}")
                        if (m.body_pattern != null) {
                            if (isNotEmpty()) append(", ")
                            val typeLabel = if (m.body_match_type == "starts_with") "starts" else "~"
                            append("Body $typeLabel: ${m.body_pattern}")
                        }
                    }.ifEmpty { "Any" }
                }
            }
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) =
        ViewHolder(ItemConfigBinding.inflate(LayoutInflater.from(parent.context), parent, false))

    override fun onBindViewHolder(holder: ViewHolder, position: Int) =
        holder.bind(getItem(position))

    companion object {
        private val DIFF = object : DiffUtil.ItemCallback<ConfigCache.SmsConfig>() {
            override fun areItemsTheSame(a: ConfigCache.SmsConfig, b: ConfigCache.SmsConfig) =
                a.id == b.id
            override fun areContentsTheSame(a: ConfigCache.SmsConfig, b: ConfigCache.SmsConfig) =
                a == b
        }
    }
}
