package com.tracy.industry.base

import android.content.Context
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.databinding.DataBindingUtil
import androidx.databinding.ViewDataBinding
import androidx.recyclerview.widget.RecyclerView

/**
 * Desc:RecyclerView.Adapter的databinding方式的基类
 *
 * @author：Jing Yang
 * @date: 2018/6/7 16:50
 */
abstract class BaseBindingAdapterKt<V : ViewDataBinding, T> : RecyclerView.Adapter<RecyclerView.ViewHolder>() {

    var list: List<T>? = null

    var listenerClick: OnItemClickListener<T>? = null

    var listenerLongClick: OnItemLongClickListener<T>? = null

    lateinit var context: Context

    protected lateinit var currentLanguageIndex: String

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        context = parent.context
        val binding: V = onCreateBind(LayoutInflater.from(parent.context), parent)
        val holder = BindingHolder(binding.root)
        holder.itemView.setOnClickListener { v ->
            val position = holder.layoutPosition
            onClickItem(v, position, list!![position])
        }
        listenerLongClick?.let {
            holder.itemView.setOnLongClickListener { v ->
                val position = holder.layoutPosition
                it.onLongClickItem(v, position, list!![position])
                true
            }
        }
        return holder
    }

    abstract fun onCreateBind(inflater: LayoutInflater, parent: ViewGroup): V

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        val binding = getBindingFromHolder(holder)
        onBindItem(binding, position, list!![position])
        binding.executePendingBindings()
    }

    private fun getBindingFromHolder(holder: RecyclerView.ViewHolder): V {
        return DataBindingUtil.getBinding(holder.itemView)!!
    }

    protected abstract fun onBindItem(binding: V, position: Int, bean: T)

    open fun onClickItem(v: View, position: Int, bean: T) {
        listenerClick?.onClickItem(v, position, list!![position])
    }

    override fun getItemCount(): Int {
        return if (list == null) 0 else list!!.size
    }

    fun getItem(position: Int): T {
        return list!![position]
    }

    interface OnItemClickListener<T> {
        fun onClickItem(view: View, position: Int, data: T)
    }

    interface OnItemLongClickListener<T> {
        fun onLongClickItem(view: View, position: Int, data: T)
    }
}
