package org.wordpress.android.ui.sitecreation.verticals

import android.arch.lifecycle.LiveData
import android.arch.lifecycle.MutableLiveData
import android.arch.lifecycle.ViewModel
import android.support.annotation.StringRes
import kotlinx.coroutines.experimental.CoroutineScope
import kotlinx.coroutines.experimental.Job
import kotlinx.coroutines.experimental.delay
import kotlinx.coroutines.experimental.launch
import kotlinx.coroutines.experimental.withContext
import org.apache.commons.lang3.StringUtils
import org.wordpress.android.R
import org.wordpress.android.fluxc.Dispatcher
import org.wordpress.android.fluxc.model.vertical.VerticalModel
import org.wordpress.android.fluxc.store.VerticalStore.OnVerticalsFetched
import org.wordpress.android.models.networkresource.ListState
import org.wordpress.android.models.networkresource.ListState.Error
import org.wordpress.android.models.networkresource.ListState.Loading
import org.wordpress.android.modules.IO_DISPATCHER
import org.wordpress.android.modules.MAIN_DISPATCHER
import org.wordpress.android.ui.sitecreation.usecases.DummyOnVerticalsHeaderInfoFetched
import org.wordpress.android.ui.sitecreation.usecases.DummyVerticalsHeaderInfoModel
import org.wordpress.android.ui.sitecreation.usecases.FetchVerticalsHeaderInfoUseCase
import org.wordpress.android.ui.sitecreation.usecases.FetchVerticalsUseCase
import org.wordpress.android.ui.sitecreation.verticals.NewSiteCreationVerticalsViewModel.VerticalsListItemUiState.VerticalsFetchSuggestionsErrorUiState
import org.wordpress.android.ui.sitecreation.verticals.NewSiteCreationVerticalsViewModel.VerticalsListItemUiState.VerticalsModelUiState
import org.wordpress.android.ui.sitecreation.verticals.NewSiteCreationVerticalsViewModel.VerticalsUiState.VerticalsContentUiState
import org.wordpress.android.ui.sitecreation.verticals.NewSiteCreationVerticalsViewModel.VerticalsUiState.VerticalsFullscreenErrorUiState
import org.wordpress.android.ui.sitecreation.verticals.NewSiteCreationVerticalsViewModel.VerticalsUiState.VerticalsFullscreenProgressUiState
import org.wordpress.android.viewmodel.SingleLiveEvent
import javax.inject.Inject
import javax.inject.Named
import kotlin.coroutines.experimental.CoroutineContext

private const val throttleDelay: Int = 500

class NewSiteCreationVerticalsViewModel @Inject constructor(
    private val dispatcher: Dispatcher,
    private val fetchVerticalsHeaderInfoUseCase: FetchVerticalsHeaderInfoUseCase,
    private val fetchVerticalsUseCase: FetchVerticalsUseCase,
    @Named(IO_DISPATCHER) private val IO: CoroutineContext,
    @Named(MAIN_DISPATCHER) private val MAIN: CoroutineContext
) : ViewModel(), CoroutineScope {
    private val job = Job()
    private var fetchVerticalsJob: Job? = null
    override val coroutineContext: CoroutineContext
        get() = IO + job
    private var isStarted = false

    private val _uiState: MutableLiveData<VerticalsUiState> = MutableLiveData()
    val uiState: LiveData<VerticalsUiState> = _uiState

    private var listState: ListState<VerticalModel> = ListState.Init()
    private lateinit var headerInfo: DummyVerticalsHeaderInfoModel

    private val _clearBtnClicked = SingleLiveEvent<Void>()
    val clearBtnClicked = _clearBtnClicked

    init {
        dispatcher.register(fetchVerticalsUseCase)
    }

    override fun onCleared() {
        super.onCleared()
        dispatcher.unregister(fetchVerticalsUseCase)
    }

    fun start() {
        if (isStarted) {
            return
        }
        isStarted = true
        fetchHeaderInfo()
    }

    private fun fetchHeaderInfo() {
        launch {
            withContext(MAIN) {
                updateUiState(VerticalsFullscreenProgressUiState)
            }
            val headerInfoEvent = fetchVerticalsHeaderInfoUseCase.fetchVerticalHeaderInfo()
            withContext(MAIN) {
                onHeaderInfoFetched(headerInfoEvent)
            }
        }
    }

    private fun onHeaderInfoFetched(event: DummyOnVerticalsHeaderInfoFetched) {
        if (event.isError) {
            updateUiState(VerticalsFullscreenErrorUiState)
        } else {
            headerInfo = event.headerInfo!!
            updateUiStateToContent("", ListState.Ready(emptyList()))
        }
    }

    fun onFetchHeaderInfoRetry() {
        fetchHeaderInfo()
    }

    fun onClearTextBtnClicked() {
        _clearBtnClicked.call()
    }

    fun updateQuery(query: String, delay: Int = throttleDelay) {
        fetchVerticalsJob?.cancel() // cancel any previous requests
        if (query.isNotEmpty()) {
            fetchVerticals(query, delay)
        } else {
            updateUiStateToContent(query, ListState.Ready(emptyList()))
        }
    }

    private fun fetchVerticals(query: String, throttleDelay: Int) {
        fetchVerticalsJob = launch {
            withContext(MAIN) {
                updateUiStateToContent(query, ListState.Loading(ListState.Ready(emptyList()), false))
            }
            delay(throttleDelay)
            val fetchedVerticals = fetchVerticalsUseCase.fetchVerticals(query)
            withContext(MAIN) {
                onVerticalsFetched(query, fetchedVerticals)
            }
        }
    }

    private fun onVerticalsFetched(query: String, event: OnVerticalsFetched) {
        if (event.isError) {
            updateUiStateToContent(query, ListState.Error(listState, event.error.message))
        } else {
            updateUiStateToContent(query, ListState.Success(event.verticalList))
        }
    }

    private fun updateUiStateToContent(query: String, state: ListState<VerticalModel>) {
        listState = state
        updateUiState(
                VerticalsContentUiState(
                        showSkipButton = StringUtils.isEmpty(query),
                        headerUiState = createHeaderUiState(
                                shouldShowHeader(query),
                                headerInfo
                        ),
                        searchInputState = createSearchInputUiState(
                                query,
                                showProgress = state is Loading,
                                hint = headerInfo.inputHint
                        ),
                        items = createSuggestionsUiStates(
                                onRetry = { updateQuery(query) },
                                data = state.data,
                                errorFetchingSuggestions = state is Error
                        )
                )
        )
    }

    private fun updateUiState(uiState: VerticalsUiState) {
        _uiState.value = uiState
    }

    private fun createSuggestionsUiStates(
        onRetry: () -> Unit,
        data: List<VerticalModel>,
        errorFetchingSuggestions: Boolean
    ): List<VerticalsListItemUiState> {
        val items: ArrayList<VerticalsListItemUiState> = ArrayList()
        if (errorFetchingSuggestions) {
            val errorUiState = VerticalsFetchSuggestionsErrorUiState(
                    messageResId = R.string.site_creation_fetch_suggestions_failed,
                    retryButtonResId = R.string.button_retry
            )
            errorUiState.onItemTapped = onRetry
            items.add(errorUiState)
        } else {
            val lastItemIndex = data.size - 1
            data.forEachIndexed { index, model ->
                items.add(VerticalsModelUiState(model.verticalId, model.name, showDivider = index != lastItemIndex))
            }
        }
        return items
    }

    private fun shouldShowHeader(query: String): Boolean {
        return StringUtils.isEmpty(query)
    }

    private fun createHeaderUiState(
        isVisible: Boolean,
        headerInfo: DummyVerticalsHeaderInfoModel
    ): VerticalsHeaderUiState {
        return if (isVisible)
            VerticalsHeaderUiState.Visible(
                    headerInfo.title,
                    headerInfo.subtitle
            ) else VerticalsHeaderUiState.Hidden
    }

    private fun createSearchInputUiState(
        query: String,
        showProgress: Boolean,
        hint: String
    ): VerticalsSearchInputUiState {
        return VerticalsSearchInputUiState.Visible(
                hint,
                showProgress,
                showClearButton = !StringUtils.isEmpty(query)
        )
    }

    sealed class VerticalsUiState {
        open val showFullscreenError: Boolean = false
        open val showFullscreenProgress: Boolean = false
        open val showContent: Boolean = false
        open val showSkipButton: Boolean = false
        open val headerUiState: VerticalsHeaderUiState = VerticalsHeaderUiState.Hidden
        open val searchInputState: VerticalsSearchInputUiState = VerticalsSearchInputUiState.Hidden
        open val items: List<VerticalsListItemUiState> = emptyList()

        object VerticalsFullscreenErrorUiState : VerticalsUiState() {
            override val showFullscreenError: Boolean = true
        }

        object VerticalsFullscreenProgressUiState : VerticalsUiState() {
            override val showFullscreenProgress: Boolean = true
        }

        data class VerticalsContentUiState(
            override val showSkipButton: Boolean,
            override val headerUiState: VerticalsHeaderUiState,
            override val searchInputState: VerticalsSearchInputUiState,
            override val items: List<VerticalsListItemUiState>
        ) : VerticalsUiState() {
            override val showContent: Boolean = true
        }
    }

    sealed class VerticalsSearchInputUiState(val isVisible: Boolean) {
        open val hint: String = ""
        open val showProgress: Boolean = false
        open val showClearButton: Boolean = false

        object Hidden : VerticalsSearchInputUiState(false)
        data class Visible(
            override val hint: String,
            override val showProgress: Boolean,
            override val showClearButton: Boolean
        ) : VerticalsSearchInputUiState(true)
    }

    sealed class VerticalsHeaderUiState(val isVisible: Boolean) {
        open val title: String = ""
        open val subtitle: String = ""

        object Hidden : VerticalsHeaderUiState(false)
        data class Visible(
            override val title: String,
            override val subtitle: String
        ) : VerticalsHeaderUiState(true)
    }

    sealed class VerticalsListItemUiState {
        data class VerticalsModelUiState(val id: String, val title: String, val showDivider: Boolean) :
                VerticalsListItemUiState()

        data class VerticalsFetchSuggestionsErrorUiState(
            @StringRes val messageResId: Int,
            @StringRes val retryButtonResId: Int
        ) : VerticalsListItemUiState() {
            lateinit var onItemTapped: () -> Unit
        }
    }
}
