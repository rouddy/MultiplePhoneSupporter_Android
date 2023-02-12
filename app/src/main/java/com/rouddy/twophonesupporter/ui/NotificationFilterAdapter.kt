package com.rouddy.twophonesupporter.ui

import android.graphics.drawable.Drawable
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.rouddy.twophonesupporter.databinding.ItemNotificationFilterBinding

class NotificationFilterAdapter(
    val listener: Listener,
) : RecyclerView.Adapter<NotificationFilterAdapter.NotificationFilterViewHolder>() {

    interface Listener {
        fun onChecked(packageName: String, enabled: Boolean)
    }

    data class Application(
        val packageName: String,
        val appName: String,
        val drawable: Drawable,
        val installed: Boolean,
    )

    inner class NotificationFilterViewHolder(val binding: ItemNotificationFilterBinding) : RecyclerView.ViewHolder(binding.root) {

        fun setApplication(application: Application) {
            binding.iconView.setImageDrawable(application.drawable)
            binding.titleView.text = application.appName
            binding.isInstalledView.visibility = if (application.installed) View.VISIBLE else View.GONE
            binding.checkBox.isChecked = checkedApplications.contains(application.packageName)
            binding.root.setOnClickListener {
                binding.checkBox.isChecked.let {
                    listener.onChecked(application.packageName, it)
                }
            }
        }
    }

    private var applicationPackages = listOf<Application>()
    private var checkedApplications: Set<String> = setOf()

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): NotificationFilterViewHolder {
        val binding = ItemNotificationFilterBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return NotificationFilterViewHolder(binding)
    }

    override fun onBindViewHolder(holder: NotificationFilterViewHolder, position: Int) {
        holder.setApplication(applicationPackages[position])
    }

    override fun getItemCount(): Int {
        return applicationPackages.size
    }

    fun setApplications(list: List<Application>) {
        applicationPackages = list
            .filter {
                !it.appName.matches("[a-z]+\\.[a-zA-Z.]+".toRegex())
            }
            .sortedByNonAlphabetFirst {
                it.appName
            }
        notifyDataSetChanged()
    }

    fun setFilters(set: Set<String>) {
        checkedApplications = set
        notifyDataSetChanged()
    }
}

inline fun <T> Iterable<T>.sortedByNonAlphabetFirst(crossinline selector: (T) -> String?): List<T> {
    return sortedWith { left, right ->
        val leftString = selector(left)
        val rightString = selector(right)
        return@sortedWith if (leftString != null && rightString != null) {
            leftString.compareWithChars(rightString) { leftChar, rightChar ->
                val leftAlphabetOrDigit = leftChar.isAlphabetOrDigit()
                if (leftAlphabetOrDigit == rightChar.isAlphabetOrDigit()) {
                    leftChar.compareTo(rightChar)
                } else if (leftAlphabetOrDigit) {
                    1
                } else {
                    -1
                }
            }
        } else if (leftString == null && rightString == null) {
            0
        } else if (leftString == null) {
            -1
        } else {
            1
        }
    }
}

fun String.compareWithChars(compared: String, comparator: Comparator<Char>): Int {
    val leftChars = toList()
    val rightChars = compared.toList()
    val minLength = Math.min(leftChars.size, rightChars.size)
    for (index in 0 until minLength) {
        comparator.compare(leftChars[index], rightChars[index])
            .also { if (it != 0) return it }
    }
    return leftChars.size - rightChars.size
}

fun Char.isAlphabetOrDigit(): Boolean {
    return (this in 'a'..'z') || (this in 'A'..'Z') || (this in '0'..'9')
}
