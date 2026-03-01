package com.tracy.industry.base

import android.os.Build
import android.os.Bundle
import android.view.View
import android.view.WindowManager
import androidx.appcompat.app.AppCompatActivity
import io.reactivex.disposables.Disposable

/**
 * Desc: base activity by kotlin
 * @author：Jing Yang
 * @date: 2021/7/16 16:50
 */
abstract class BaseActivityKt: AppCompatActivity() {

    private var mBusDisposable: Disposable? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // 全屏
        window.addFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN)
        supportActionBar?.hide()
        // 屏幕常亮
        //window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        // 全屏
        hideSystemActionBar()

        onSubCreate()
    }

    private fun fullScreen() {
        window.addFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN)
        hideNavigationBar()
    }

    private fun hideSystemActionBar() {
        window.decorView.systemUiVisibility = (
                View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
                        or View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                        or View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                        or View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                        or View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                        or View.SYSTEM_UI_FLAG_FULLSCREEN
                )
    }

    private fun hideNavigationBar() {
        if (Build.VERSION.SDK_INT <= Build.VERSION_CODES.P) {
            val decorView = window.decorView
            val uiOptions =
                View.SYSTEM_UI_FLAG_HIDE_NAVIGATION or View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
            decorView.systemUiVisibility = uiOptions
        }
    }

    /**
     * systemUiVisibility的属性集在离开/暂时离开当前页面后都会清除flag
     * 所以在onResume()里重新设置这些属性
     */
    override fun onResume() {
        super.onResume()
        fullScreen()
    }

    override fun onDestroy() {
        super.onDestroy()
        if (mBusDisposable != null) {
            mBusDisposable!!.dispose()
        }
    }

    /**
     * 子类选择覆盖
     *
     * @param action
     */
    protected open fun handleBusAction(action: String?) {}

    abstract fun getLayoutResource(): Int

    abstract fun onSubCreate()

}