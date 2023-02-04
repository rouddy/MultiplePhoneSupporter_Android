package com.rouddy.twophonesupporter.ui

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.rouddy.twophonesupporter.databinding.ItemSettingBinding

class SettingAdapter(private val listener: Listener) : RecyclerView.Adapter<SettingAdapter.SettingViewHolder>() {

    enum class Settings(val text: String) {
        Connection("Connection"),
        Credit("Credit"),
    }

    interface Listener {
        fun onSettingClick(settings: Settings)
    }

    inner class SettingViewHolder(val binding: ItemSettingBinding) : RecyclerView.ViewHolder(binding.root) {
        fun setSetting(settings: Settings) {
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
        holder.setSetting(Settings.values()[position])
    }

    override fun getItemCount(): Int {
        return Settings.values().size
    }
}