package org.wordpress.android.ui.prefs.timezone

import android.app.Activity
import android.text.TextUtils
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.Transformations
import androidx.lifecycle.ViewModel
import com.android.volley.Response
import com.android.volley.VolleyError
import com.android.volley.toolbox.StringRequest
import com.android.volley.toolbox.Volley
import org.json.JSONException
import org.json.JSONObject
import org.wordpress.android.Constants
import org.wordpress.android.networking.RestClientUtils
import org.wordpress.android.util.AppLog
import org.wordpress.android.util.AppLog.T.SETTINGS
import org.wordpress.android.util.StringUtils
import javax.inject.Inject

class SiteSettingsTimezoneViewModel @Inject constructor() : ViewModel() {
    private val timezonesList = mutableListOf<Timezone>()

    private val _showEmpty = MutableLiveData<Boolean>()
    val showEmptyView: LiveData<Boolean> = _showEmpty

    private val _showProgress = MutableLiveData<Boolean>()
    val showProgressView: LiveData<Boolean> = _showProgress

    private val _dismiss = MutableLiveData<Unit>()
    val dismissWithError: LiveData<Unit> = _dismiss

    private val searchInput = MutableLiveData<String>()
    val timezoneSearch: LiveData<List<Timezone>> = Transformations.switchMap(searchInput) { term ->
        filterTimezones(term)
    }

    private val _timezones = MutableLiveData<List<Timezone>>()
    val timezones = _timezones


    fun searchTimezones(city: String) {
        searchInput.value = city
    }

    private fun filterTimezones(city: String): LiveData<List<Timezone>> {
        val filteredTimezones = MutableLiveData<List<Timezone>>()

        timezonesList.filter { timezone ->
            timezone.label.contains(city, true)
        }.also {
            filteredTimezones.value = it
        }

        return filteredTimezones
    }

    fun onSearchCancelled() {
        _timezones.postValue(timezonesList)
    }

    // TODO: Might need to refactor and move api call to use a Repository or FluxC Store
    fun requestTimezones(context: Activity) {
        val listener = Response.Listener { response: String? ->
            AppLog.d(SETTINGS, "timezones requested")
            _showProgress.postValue(false)

            if (!TextUtils.isEmpty(response)) {
                timezonesList.clear()
                loadTimezones(response)
            } else {
                AppLog.w(SETTINGS, "empty response requesting timezones")
                _dismiss.postValue(Unit)
            }
        }

        val errorListener = Response.ErrorListener { error: VolleyError? ->
            AppLog.e(SETTINGS, "Error requesting timezones", error)
            _dismiss.postValue(Unit)
        }

        val request: StringRequest = object : StringRequest(Constants.URL_TIMEZONE_ENDPOINT, listener, errorListener) {
            override fun getParams(): Map<String, String> {
                return RestClientUtils.getRestLocaleParams(context)
            }
        }

        _showProgress.postValue(true)
        val queue = Volley.newRequestQueue(context)
        queue.add(request)
    }

    private fun loadTimezones(responseJson: String?) {
        try {
            val jsonResponse = JSONObject(responseJson.orEmpty())
            val jsonTimezones = jsonResponse.getJSONArray("timezones")
            for (i in 0 until jsonTimezones.length()) {
                val json = jsonTimezones.getJSONObject(i)
                timezonesList.add(
                        Timezone(json.getString("label"), json.getString("value"))
                )
            }
            // sort by label
            // TODO: Group by continents
            timezonesList.sortWith { t1: Timezone, t2: Timezone ->
                StringUtils.compare(t1.label, t2.label)
            }

            _timezones.postValue(timezonesList)
        } catch (e: JSONException) {
            AppLog.e(SETTINGS, "Error parsing timezones", e)
            _dismiss.postValue(Unit)
        }
    }
}
