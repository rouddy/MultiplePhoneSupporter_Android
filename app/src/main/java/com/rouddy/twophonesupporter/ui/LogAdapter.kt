package com.rouddy.twophonesupporter.ui

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.rouddy.twophonesupporter.databinding.ItemLogBinding

class LogAdapter : RecyclerView.Adapter<LogAdapter.LogViewHolder>() {

    inner class LogViewHolder(private val binding: ItemLogBinding) : RecyclerView.ViewHolder(binding.root) {

        fun setLog(log: String) {
            binding.logView.text = log
        }
    }

    private var logs = listOf<String>()

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): LogViewHolder {
        val binding = ItemLogBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return LogViewHolder(binding)
    }

    override fun onBindViewHolder(holder: LogViewHolder, position: Int) {
        holder.setLog(logs[position])
    }

    override fun getItemCount(): Int {
        return logs.size
    }

    fun setLogs(logs: List<String>) {
        this.logs = logs
        notifyDataSetChanged()
    }
}