package com.rouddy.twophonesupporter.ui

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.rouddy.twophonesupporter.databinding.ItemSettingBinding

class SettingAdapter(private val listener: Listener) : RecyclerView.Adapter<SettingAdapter.SettingViewHolder>() {

    interface Listener {
        fun onSettingClick(settings: SettingActivity.Settings)
    }

    inner class SettingViewHolder(val binding: ItemSettingBinding) : RecyclerView.ViewHolder(binding.root) {
        fun setSetting(settings: SettingActivity.Settings) {
            binding.titleView.text = settings.text
            binding.root.setOnClickListener {
                listener.onSettingClick(settings)
            }
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): SettingViewHolder {
        val binding = ItemSettingBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return SettingViewHolder(binding)
    }

    override fun onBindViewHolder(holder: SettingViewHolder, position: Int) {
        holder.setSetting(SettingActivity.Settings.values()[position])
    }

    override fun getItemCount(): Int {
        return SettingActivity.Settings.values().size
    }
}