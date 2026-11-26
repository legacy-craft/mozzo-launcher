package com.mozzolauncher.app

import android.text.TextUtils
import android.view.Gravity
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView

class DrawerAdapter(
    items: List<AppInfo>,
    private var iconSizeDp: Int,
    private val onClick: (AppInfo) -> Unit,
    private val onLongClick: (AppInfo) -> Unit
) : RecyclerView.Adapter<DrawerAdapter.VH>() {

    private val data = items.toMutableList()

    class VH(val root: LinearLayout, val img: ImageView, val txt: TextView) : RecyclerView.ViewHolder(root)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
        val d = parent.resources.displayMetrics.density
        val pad = (8 * d).toInt()
        val root = LinearLayout(parent.context).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
            isFocusable = true
            isClickable = true
            isLongClickable = true
            setPadding(pad, pad, pad, pad)
            layoutParams = RecyclerView.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
            setOnFocusChangeListener { v, hasFocus ->
                v.setBackgroundColor(if (hasFocus) 0x333B82F6 else 0x00000000)
                v.scaleX = if (hasFocus) 1.06f else 1.0f
                v.scaleY = if (hasFocus) 1.06f else 1.0f
            }
        }
        val img = ImageView(parent.context).apply {
            scaleType = ImageView.ScaleType.FIT_CENTER
        }
        val txt = TextView(parent.context).apply {
            gravity = Gravity.CENTER
            maxLines = 2
            ellipsize = TextUtils.TruncateAt.END
            textSize = 12f
            setTextColor(0xFFF1F5F9.toInt())
        }
        val txtLp = LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT
        ).apply { topMargin = (6 * d).toInt() }

        root.addView(img)
        root.addView(txt, txtLp)
        return VH(root, img, txt)
    }

    override fun onBindViewHolder(holder: VH, position: Int) {
        val app = data[position]
        val d = holder.itemView.resources.displayMetrics.density
        val px = (iconSizeDp * d).toInt()
        holder.img.layoutParams = LinearLayout.LayoutParams(px, px)
        holder.img.setImageDrawable(app.icon)
        holder.txt.text = if (app.isPinned) "★ ${app.label}" else app.label
        holder.root.setOnClickListener { onClick(app) }
        holder.root.setOnLongClickListener { onLongClick(app); true }
    }

    override fun getItemCount(): Int = data.size

    fun updateData(newItems: List<AppInfo>, newSizeDp: Int) {
        iconSizeDp = newSizeDp
        data.clear()
        data.addAll(newItems)
        notifyDataSetChanged()
    }
}
