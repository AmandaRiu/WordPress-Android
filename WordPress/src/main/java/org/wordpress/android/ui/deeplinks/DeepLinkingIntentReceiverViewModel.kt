package org.wordpress.android.ui.deeplinks

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import kotlinx.coroutines.CoroutineDispatcher
import org.wordpress.android.modules.UI_THREAD
import org.wordpress.android.ui.deeplinks.DeepLinkNavigator.NavigateAction
import org.wordpress.android.ui.deeplinks.DeepLinkNavigator.NavigateAction.OpenInBrowser
import org.wordpress.android.util.UriWrapper
import org.wordpress.android.viewmodel.Event
import org.wordpress.android.viewmodel.ScopedViewModel
import javax.inject.Inject
import javax.inject.Named

class DeepLinkingIntentReceiverViewModel
@Inject constructor(
    @Named(UI_THREAD) private val uiDispatcher: CoroutineDispatcher,
    private val editorLinkHandler: EditorLinkHandler,
    private val statsLinkHandler: StatsLinkHandler,
    private val startLinkHandler: StartLinkHandler,
    private val readerLinkHandler: ReaderLinkHandler,
    private val notificationsLinkHandler: NotificationsLinkHandler,
    private val deepLinkUriUtils: DeepLinkUriUtils,
    private val serverTrackingHandler: ServerTrackingHandler
) : ScopedViewModel(uiDispatcher) {
    private val _navigateAction = MutableLiveData<Event<NavigateAction>>()
    val navigateAction = _navigateAction as LiveData<Event<NavigateAction>>
    val toast = editorLinkHandler.toast

    /**
     * Handles the following URLs
     * `wordpress.com/post...`
     * `wordpress.com/stats...`
     * `public-api.wordpress.com/mbar`
     * and builds the navigation action based on them
     */
    fun handleUrl(uriWrapper: UriWrapper): Boolean {
        return buildNavigateAction(uriWrapper)?.also { _navigateAction.value = Event(it) } != null
    }

    /**
     * Tracking URIs like `public-api.wordpress.com/mbar/...` come from emails and should be handled here
     */
    private fun isTrackingUrl(uri: UriWrapper): Boolean {
        // https://public-api.wordpress.com/mbar/
        return uri.host == HOST_API_WORDPRESS_COM &&
                uri.pathSegments.firstOrNull() == MOBILE_TRACKING_PATH
    }

    private fun isWpLoginUrl(uri: UriWrapper): Boolean {
        // https://wordpress.com/wp-login.php/
        return uri.host == HOST_WORDPRESS_COM &&
                uri.pathSegments.firstOrNull() == WP_LOGIN
    }

    private fun buildNavigateAction(uri: UriWrapper, rootUri: UriWrapper = uri): NavigateAction? {
        return when {
            isTrackingUrl(uri) -> getRedirectUriAndBuildNavigateAction(uri, rootUri)
                    ?.also {
                        // The new URL was build so we need to hit the original `mbar` tracking URL
                        serverTrackingHandler.request(uri)
                    }
                    ?: OpenInBrowser(rootUri.copy(REGULAR_TRACKING_PATH))
            isWpLoginUrl(uri) -> getRedirectUriAndBuildNavigateAction(uri, rootUri)
            readerLinkHandler.isReaderUrl(uri) -> readerLinkHandler.buildOpenInReaderNavigateAction(uri)
            editorLinkHandler.isEditorUrl(uri) -> editorLinkHandler.buildOpenEditorNavigateAction(uri)
            statsLinkHandler.isStatsUrl(uri) -> statsLinkHandler.buildOpenStatsNavigateAction(uri)
            startLinkHandler.isStartUrl(uri) -> startLinkHandler.buildNavigateAction()
            notificationsLinkHandler.isNotificationsUrl(uri) -> notificationsLinkHandler.buildNavigateAction()
            else -> null
        }
    }

    private fun getRedirectUriAndBuildNavigateAction(uri: UriWrapper, rootUri: UriWrapper): NavigateAction? {
        return getRedirectUri(uri)?.let { buildNavigateAction(it, rootUri) }
    }

    private fun getRedirectUri(uri: UriWrapper): UriWrapper? {
        return deepLinkUriUtils.getUriFromQueryParameter(uri, REDIRECT_TO_PARAM)
    }

    override fun onCleared() {
        serverTrackingHandler.clear()
        super.onCleared()
    }

    companion object {
        const val HOST_WORDPRESS_COM = "wordpress.com"
        private const val HOST_API_WORDPRESS_COM = "public-api.wordpress.com"
        private const val MOBILE_TRACKING_PATH = "mbar"
        private const val REGULAR_TRACKING_PATH = "bar"
        private const val REDIRECT_TO_PARAM = "redirect_to"
        private const val WP_LOGIN = "wp-login.php"
    }
}
