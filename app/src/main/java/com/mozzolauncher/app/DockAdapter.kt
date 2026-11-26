package com.mozzolauncher.app

import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.ImageView
import androidx.recyclerview.widget.RecyclerView
import java.util.Collections

class DockAdapter(
    items: List<AppInfo>,
    private var iconSizeDp: Int,
    private val onClick: (AppInfo) -> Unit,
    private val onLongClick: (AppInfo) -> Unit
) : RecyclerView.Adapter<DockAdapter.VH>() {

    private val data = items.toMutableList()

    class VH(val box: FrameLayout, val img: ImageView, val dot: View) : RecyclerView.ViewHolder(box)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
        val d = parent.resources.displayMetrics.density
        val pad = (6 * d).toInt()
        val box = FrameLayout(parent.context).apply {
            isFocusable = true
            isClickable = true
            isLongClickable = true
            setPadding(pad, pad, pad, pad)
            layoutParams = RecyclerView.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
            setOnFocusChangeListener { v, hasFocus ->
                v.scaleX = if (hasFocus) 1.15f else 1.0f
                v.scaleY = if (hasFocus) 1.15f else 1.0f
            }
        }
        val img = ImageView(parent.context).apply {
            scaleType = ImageView.ScaleType.FIT_CENTER
        }
        val dot = View(parent.context).apply {
            setBackgroundColor(0xFF60A5FA.toInt())
        }
        val dotLp = FrameLayout.LayoutParams((5 * d).toInt(), (5 * d).toInt()).apply {
            gravity = Gravity.START or Gravity.CENTER_VERTICAL
        }
        box.addView(img)
        box.addView(dot, dotLp)
        return VH(box, img, dot)
    }

    override fun onBindViewHolder(holder: VH, position: Int) {
        val app = data[position]
        val d = holder.itemView.resources.displayMetrics.density
        val px = (iconSizeDp * d).toInt()
        holder.img.layoutParams = FrameLayout.LayoutParams(px, px).apply {
            gravity = Gravity.CENTER
            leftMargin = (4 * d).toInt()
        }
        holder.img.setImageDrawable(app.icon)
        holder.dot.visibility = if (app.isPinned) View.VISIBLE else View.INVISIBLE
        holder.box.setOnClickListener { onClick(app) }
        holder.box.setOnLongClickListener { onLongClick(app); true }
    }

    override fun getItemCount(): Int = data.size

    fun updateData(newItems: List<AppInfo>, newSizeDp: Int) {
        iconSizeDp = newSizeDp
        data.clear()
        data.addAll(newItems)
        notifyDataSetChanged()
    }

    fun moveItem(from: Int, to: Int) {
        if (from in data.indices && to in data.indices) {
            Collections.swap(data, from, to)
            notifyItemMoved(from, to)
        }
    }
}
