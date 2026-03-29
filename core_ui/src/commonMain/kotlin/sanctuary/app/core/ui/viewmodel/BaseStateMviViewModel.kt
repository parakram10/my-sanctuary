package sanctuary.app.core.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filterIsInstance
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext

/**
 * MVI-style base [ViewModel]: internal [DataState], derived [ViewState], one-shot [ViewSideEffect]s.
 */
abstract class BaseStateMviViewModel<ViewIntent, DataState : Any, ViewState : Any, ViewSideEffect : Any> :
    ViewModel() {

    protected var initialIntentHandled = false

    private val _dataState: MutableStateFlow<DataState> by lazy {
        MutableStateFlow(initialDataState())
    }

    protected val dataState: StateFlow<DataState>
        get() = _dataState.asStateFlow()

    private val _viewState: MutableStateFlow<ViewState> by lazy {
        MutableStateFlow(initialViewState())
    }

    val viewState: StateFlow<ViewState>
        get() = _viewState.asStateFlow()

    private val _sideEffects = Channel<ViewSideEffect>(Channel.BUFFERED)

    val sideEffects: Flow<ViewSideEffect> = _sideEffects.receiveAsFlow()

    init {
        viewModelScope.launch {
            dataState.collect { data ->
                _viewState.value = convertToUiState(data)
            }
        }
        onViewStateActive()
    }

    abstract fun initialDataState(): DataState

    abstract suspend fun convertToUiState(dataState: DataState): ViewState

    protected open fun initialViewState(): ViewState =
        runBlocking {
            convertToUiState(initialDataState())
        }

    open fun processIntent(intent: ViewIntent) {}

    protected fun updateState(block: (DataState) -> DataState) {
        _dataState.update(block)
    }

    protected fun emitSideEffect(sideEffect: ViewSideEffect) {
        viewModelScope.launch {
            _sideEffects.send(sideEffect)
        }
    }

    protected fun repeatOnStarted(block: suspend CoroutineScope.() -> Unit): Job =
        viewModelScope.launch(block = block)

    protected fun doWhenLifecycleStarted(block: () -> Unit) {
        viewModelScope.launch(Dispatchers.Main.immediate) {
            block()
        }
    }

    protected fun launchAfterViewStarted(block: suspend CoroutineScope.() -> Unit): Job =
        viewModelScope.launch(block = block)

    protected fun launchAfterViewStartedAndNotCancelOnViewStop(block: suspend CoroutineScope.() -> Unit): Job {
        return viewModelScope.launch {
            withContext(Dispatchers.Main.immediate) { }
            block()
        }
    }

    protected open fun onViewStateActive() {}

    protected open fun onViewStateInactive() {}

    protected open fun onDestroy() {}

    protected suspend fun <T> Flow<T>.collectWhenViewStateActive(collector: suspend (T) -> Unit) {
        val counter = SequenceCounter()
        val dataAndReceivedAtFlow = map { value -> value to counter.next() }
        combine(
            flow = viewStateActiveStatusFlow(),
            flow2 = dataAndReceivedAtFlow,
        ) { isViewStateCollectionActive, flowResult ->
            if (isViewStateCollectionActive) {
                ViewStateCollectionResult.Data(flowResult.first, flowResult.second)
            } else {
                ViewStateCollectionResult.ScreenNotActive
            }
        }
            .filterIsInstance<ViewStateCollectionResult.Data<T>>()
            .distinctUntilChanged()
            .map { it.data }
            .collect(collector)
    }

    protected fun viewStateActiveStatusFlow(): Flow<Boolean> =
        _dataState.subscriptionCount
            .map { it >= 1 }
            .distinctUntilChanged()

    override fun onCleared() {
        onViewStateInactive()
        onDestroy()
        _sideEffects.close()
        super.onCleared()
    }

    private class SequenceCounter {
        private var n = 0
        fun next(): Int = ++n
    }
}

sealed class ViewStateCollectionResult<out T> {
    data object ScreenNotActive : ViewStateCollectionResult<Nothing>()
    data class Data<T>(val data: T, val counter: Int) : ViewStateCollectionResult<T>()
}
