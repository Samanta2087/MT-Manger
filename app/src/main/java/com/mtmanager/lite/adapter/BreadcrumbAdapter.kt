package com.mtmanager.lite.adapter

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.mtmanager.lite.R
import java.io.File

class BreadcrumbAdapter(
    private val onCrumbClick: (File) -> Unit
) : RecyclerView.Adapter<BreadcrumbAdapter.CrumbViewHolder>() {

    private val crumbs = mutableListOf<File>()

    inner class CrumbViewHolder(v: View) : RecyclerView.ViewHolder(v) {
        val tvCrumb: TextView     = v.findViewById(R.id.tvCrumb)
        val tvSeparator: TextView = v.findViewById(R.id.tvSeparator)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): CrumbViewHolder {
        val v = LayoutInflater.from(parent.context).inflate(R.layout.item_breadcrumb, parent, false)
        return CrumbViewHolder(v)
    }

    override fun onBindViewHolder(holder: CrumbViewHolder, position: Int) {
        val file = crumbs[position]
        holder.tvCrumb.text = if (position == 0) "/ Storage" else file.name
        holder.tvSeparator.visibility = if (position == crumbs.lastIndex) View.GONE else View.VISIBLE
        val isLast = position == crumbs.lastIndex
        holder.tvCrumb.alpha = if (isLast) 1.0f else 0.6f
        holder.tvCrumb.setOnClickListener { onCrumbClick(file) }
    }

    override fun getItemCount() = crumbs.size

    fun updatePath(path: File) {
        crumbs.clear()
        val parts = mutableListOf<File>()
        var current: File? = path
        while (current != null) {
            parts.add(0, current)
            current = current.parentFile
        }
        crumbs.addAll(parts)
        notifyDataSetChanged()
    }
}
