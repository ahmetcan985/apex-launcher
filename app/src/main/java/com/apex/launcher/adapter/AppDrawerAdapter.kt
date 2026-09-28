package com.apex.launcher.adapter

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.apex.launcher.R
import com.apex.launcher.model.AppInfo
import java.util.Locale

class AppDrawerAdapter(
    private val onAppClick: (AppInfo) -> Unit
) : RecyclerView.Adapter<AppDrawerAdapter.AppViewHolder>() {

    private var allApps: List<AppInfo> = emptyList()
    private var filteredApps: List<AppInfo> = emptyList()

    fun submitList(apps: List<AppInfo>) {
        allApps = apps
        filteredApps = apps
        notifyDataSetChanged()
    }

    fun filter(query: String) {
        val trimmed = query.trim().lowercase(Locale.getDefault())
        filteredApps = if (trimmed.isEmpty()) {
            allApps
        } else {
            allApps.filter { it.label.lowercase(Locale.getDefault()).contains(trimmed) }
        }
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): AppViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_app, parent, false)
        return AppViewHolder(view)
    }

    override fun onBindViewHolder(holder: AppViewHolder, position: Int) {
        holder.bind(filteredApps[position])
    }

    override fun getItemCount(): Int = filteredApps.size

    inner class AppViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val ivIcon: ImageView = itemView.findViewById(R.id.ivAppIcon)
        private val tvName: TextView = itemView.findViewById(R.id.tvAppName)

        fun bind(app: AppInfo) {
            ivIcon.setImageDrawable(app.icon)
            tvName.text = app.label
            itemView.setOnClickListener {
                onAppClick(app)
            }
        }
    }
}
