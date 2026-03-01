package com.tracy.industry.base

import android.app.Application
import android.content.res.Resources
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.MutableLiveData
import com.tracy.industry.database.AppDatabase
import com.tracy.industry.database.DeviceRepository
import com.tracy.industry.widget.Tip
import io.reactivex.ObservableTransformer
import io.reactivex.android.schedulers.AndroidSchedulers
import io.reactivex.disposables.CompositeDisposable
import io.reactivex.disposables.Disposable
import io.reactivex.schedulers.Schedulers
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlin.coroutines.EmptyCoroutineContext

/**
 * 描述:
 *
 * 作者：景阳
 *
 * 创建时间: 2020/1/21 15:02
 */
abstract class BaseViewModel(application: Application) : AndroidViewModel(application) {

    protected var compositeDisposable: CompositeDisposable = CompositeDisposable()

    var loadingObserver = MutableLiveData<Boolean>()
    var messageObserver = MutableLiveData<String>()

    /**
     * 协程下的线程池
     */
    val fixedPool by lazy { newFixedThreadPoolContext(5, "Fixed") }

    /**
     * 子线程scope
     */
    val fixedScope by lazy { CoroutineScope(SupervisorJob() + fixedPool) }

    /**
     * main线程scope
     */
    val mainScope by lazy { MainScope() }

    fun addDisposable(disposable: Disposable) {
        compositeDisposable.add(disposable)
    }

    fun getAppDatabase(): AppDatabase = AppDatabase.getInstance()

    fun dispatchCommonError(throwable: Throwable, errorTitle: String? = null) {
        throwable.printStackTrace()
        loadingObserver.postValue(false)
        messageObserver.value = if (errorTitle == null) {
            throwable.message
        }
        else {
            "$errorTitle: ${throwable.message}"
        }
    }

    fun getComposite() = compositeDisposable

    fun onDestroy() {
        compositeDisposable.clear()
    }

    fun showLoading(show: Boolean){
        loadingObserver.value=show
    }

    val former = ObservableTransformer<Any, Any> { upstream ->
        upstream
            .subscribeOn(Schedulers.io())
            .observeOn(AndroidSchedulers.mainThread())
    }

    fun <T> applySchedulers(): ObservableTransformer<T, T> {
        return former as ObservableTransformer<T, T>
    }

    fun getResource(): Resources = getApplication<Application>().resources

    /**
     * 统一以弹提示的方式处理协程里的异常
     */
    private val commonHandler = CoroutineExceptionHandler{ context, e ->
        dispatchCommonError(e)
    }

    interface FlowErrorHandler {
        fun handleError(throwable: Throwable)
    }

    /**
     * 在主线程中启动协程
     */
    fun launchMain(
            exceptionHandler: CoroutineExceptionHandler? = commonHandler,
            block: suspend CoroutineScope.() -> Unit
    ): Job {
        val context = exceptionHandler?: EmptyCoroutineContext
        return mainScope.launch(context) { block() }
    }

    /**
     * 在子线程中启动协程
     */
    fun launchThread(
            exceptionHandler: CoroutineExceptionHandler? = commonHandler,
            block: suspend CoroutineScope.() -> Unit
    ): Job {
        val context = exceptionHandler?: EmptyCoroutineContext
        return fixedScope.launch(context) { block() }
    }

    fun showMessage(message: String) {
        Tip.showShort(message)
    }

    /**
     * 主线程启动协程，并完全处理loading, 异步, error事件
     */
    fun <T> launchSingle(
            exceptionHandler: CoroutineExceptionHandler? = commonHandler,
            block: suspend () -> T,
            withLoading: Boolean = false,
            onComplete: (T) -> Unit
    ): Job {
        return launchMain(exceptionHandler) {
            if (withLoading) {
                loadingObserver.value = true
            }
            val result = block()
            if (withLoading) {
                loadingObserver.value = false
            }
            onComplete(result)
        }
    }

    /**
     * 主线程启动协程，子线程处理异步，主线程处理loading, error事件
     */
    fun <T> launchSingleThread(
            block: suspend () -> T,
            exceptionHandler: CoroutineExceptionHandler? = commonHandler,
            withLoading: Boolean = false,
            onComplete: (T) -> Unit
    ): Job {
        return launchMain(exceptionHandler) {
            if (withLoading) {
                loadingObserver.value = true
            }
            val response = withContext(fixedPool) { block() }
            if (withLoading) {
                loadingObserver.value = false
            }
            onComplete(response)
        }
    }

    /**
     * 主线程启动flow，完全处理loading, 异步, error事件
     */
    fun <T> launchFlow(
            flow: Flow<T>,
            exceptionHandler: (Throwable) -> Unit? = { dispatchCommonError(it) },
            withLoading: Boolean = false,
            action: suspend (value: T) -> Unit
    ) {
        launchMain {
            flowCurrent(
                    flow,
                    exceptionHandler = exceptionHandler,
                    withLoading = withLoading
            )
                .collect(action)
        }
    }

    /**
     * 主线程启动flow，子线程处理flow异步，主线程处理loading, error事件
     */
    fun <T> launchFlowThread(
            flow: Flow<T>,
            exceptionHandler: (Throwable) -> Unit? = { dispatchCommonError(it) },
            withLoading: Boolean = false,
            action: suspend (value: T) -> Unit
    ) {
        launchMain {
            flowThread(
                    flow,
                    exceptionHandler = exceptionHandler,
                    withLoading = withLoading
            )
                .collect(action)
        }
    }

    /**
     * 当前线程协程处理flow任务，loading, error也统一交由当前线程处理
     */
    fun <T> flowCurrent(
            flow: Flow<T>,
            exceptionHandler: (Throwable) -> Unit? = { dispatchCommonError(it) },
            withLoading: Boolean = false
    ): Flow<T> {
        return flow
            .onStart {
                if (withLoading) {
                    loadingObserver.value = true
                }
            }
            .catch {
                exceptionHandler?.invoke(it)
            }
            .onCompletion {
                if (withLoading) {
                    loadingObserver.value = false
                }
            }
    }

    /**
     * 异步线程处理flow任务，loading, error统一交由当前线程处理
     */
    fun <T> flowThread(
            flow: Flow<T>,
            exceptionHandler: (Throwable) -> Unit? = { dispatchCommonError(it) },
            withLoading: Boolean = false
    ): Flow<T> {
        return flow
            .flowOn(fixedPool)
            .onStart {
                if (withLoading) {
                    loadingObserver.value = true
                }
            }
            .catch {
                exceptionHandler?.invoke(it)
            }
            .onCompletion {
                if (withLoading) {
                    loadingObserver.value = false
                }
            }
    }

    override fun onCleared() {
        super.onCleared()
        fixedScope.cancel()
        mainScope.cancel()
    }
}
