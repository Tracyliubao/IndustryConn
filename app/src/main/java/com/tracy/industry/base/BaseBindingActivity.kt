package com.tracy.industry.base

import android.os.Bundle
import androidx.databinding.DataBindingUtil
import androidx.databinding.ViewDataBinding
import com.tracy.industry.ui.theme.showMessage

/**
 * Des:
 * Author:LiuBao
 * Time:2026/2/26 21:03
 */
abstract class BaseBindingActivityKt<DB : ViewDataBinding, VM : BaseViewModel> : BaseActivityKt() {
    lateinit var mBinding: DB
    lateinit var mModel: VM

    override fun onCreate(savedInstanceState: Bundle?) {
        mBinding = DataBindingUtil.setContentView(this, getLayoutResource())
        mModel = createViewModel()
        mModel.messageObserver.observe(this, { showMessage(it ?: "") })

        super.onCreate(savedInstanceState)
    }

    abstract fun createViewModel(): VM

    override fun onDestroy() {
        super.onDestroy()
        mModel.onDestroy()
        // 清除 DataBinding 引用，避免内存泄漏
        mBinding.unbind()
    }
}