package com.tracy.industry.ui.theme

import android.app.Activity
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.tracy.industry.base.MyApplication
import com.tracy.industry.widget.Tip

/**
 * Desc: Context相关的内联
 * @author：Jing Yang
 * @date: 2021/7/20 11:26
 */

fun Activity.showMessage(msg: String) {
    Tip.showShort(msg)
}

fun Activity.postMessage(msg: String) {
    runOnUiThread { Tip.showShort(msg) }
}

fun <VM: AndroidViewModel> FragmentActivity.generateViewModel(vm: Class<VM>): VM {
    val factory = ViewModelProvider.AndroidViewModelFactory.getInstance(MyApplication.instance)
    return ViewModelProvider(this, factory).get(vm)
}

fun Fragment.showMessage(msg: String) {
    Tip.showShort(msg)
}

fun Fragment.postMessage(msg: String) {
    requireActivity().runOnUiThread { Tip.showShort(msg) }
}

fun <VM: AndroidViewModel> Fragment.generateViewModel(vm: Class<VM>): VM {
    val factory = ViewModelProvider.AndroidViewModelFactory.getInstance(MyApplication.instance)
    return ViewModelProvider(this, factory).get(vm)
}

fun <VM: AndroidViewModel> Fragment.getActivityViewModel(vm: Class<VM>): VM {
    val factory = ViewModelProvider.AndroidViewModelFactory.getInstance(MyApplication.instance)
    return ViewModelProvider(requireActivity(), factory).get(vm)
}

fun Activity.horizontalRecyclerView(recyclerView: RecyclerView): LinearLayoutManager {
    val manager = LinearLayoutManager(this, LinearLayoutManager.HORIZONTAL, false)
    recyclerView.layoutManager = manager
    return manager
}

fun Activity.verticalRecyclerView(recyclerView: RecyclerView): LinearLayoutManager {
    val manager = LinearLayoutManager(this, LinearLayoutManager.VERTICAL, false)
    recyclerView.layoutManager = manager
    return manager
}
