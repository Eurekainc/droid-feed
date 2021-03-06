package com.droidfeed.ui.module.main

import android.util.Patterns
import android.webkit.URLUtil
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.Transformations.map
import androidx.lifecycle.viewModelScope
import com.droidfeed.R
import com.droidfeed.data.DataStatus
import com.droidfeed.data.model.Source
import com.droidfeed.data.repo.PostRepo
import com.droidfeed.data.repo.SharedPrefsRepo
import com.droidfeed.data.repo.SourceRepo
import com.droidfeed.ui.adapter.model.SourceUIModel
import com.droidfeed.ui.common.BaseViewModel
import com.droidfeed.util.event.Event
import com.droidfeed.util.extension.postEvent
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.util.*
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class MainViewModel @Inject constructor(
    private val sourceRepo: SourceRepo,
    private val postRepo: PostRepo
) : BaseViewModel() {

    val toolbarTitle = MutableLiveData(R.string.app_name)
    val onNavigation = MutableLiveData(Destination.FEED)
    val scrollTop = MutableLiveData<Event<Unit>>()
    val sourceAddIcon = MutableLiveData(R.drawable.avd_close_to_add)
    val isMenuVisible = MutableLiveData(false)
    val isSourceInputVisible = MutableLiveData(false)
    val isSourceFilterVisible = MutableLiveData(Event(false))
    val closeKeyboardEvent = MutableLiveData(Event(false))
    val sourceErrText = MutableLiveData(R.string.empty_string)
    val sourceInputText = MutableLiveData(R.string.empty_string)
    val isSourceProgressVisible = MutableLiveData(false)
    val isSourceAddButtonEnabled = MutableLiveData(true)
    val isFilterButtonVisible = MutableLiveData(true)
    val isBookmarksShown = MutableLiveData(false)
    val isBookmarksButtonVisible = MutableLiveData(true)
    val isBookmarksButtonSelected = MutableLiveData(false)
    val showUndoSourceRemoveSnack = MutableLiveData<Event<() -> Unit>>()
    val shareSourceEvent = MutableLiveData<Event<String>>()

    private var isUserAddedSourceExist = false

    val sourceUIModelData: LiveData<List<SourceUIModel>> = map(sourceRepo.getAll()) { sourceList ->
        isUserAddedSourceExist = !sourceList.none { it.isUserSource }

        sourceList.map { source ->
            SourceUIModel(
                source = source,
                onClick = this::onSourceClicked,
                onRemoveClick = this::onSourceRemoveClicked,
                onShareClick = this::onSourceShareClicked
            )
        }
    }

    init {
        updateSources(sourceRepo)
    }

    private fun onSourceShareClicked(source: Source) {
        shareSourceEvent.postEvent("${source.name}\n ${source.url}")
    }

    private fun updateSources(sourceRepo: SourceRepo) {
        viewModelScope.launch(Dispatchers.IO) {
            when (val result = sourceRepo.pull()) {
                is DataStatus.Successful -> sourceRepo.insert(result.data ?: emptyList())
            }
        }
    }

    private fun onSourceRemoveClicked(source: Source) {
        viewModelScope.launch(Dispatchers.IO) {
            sourceRepo.remove(source)
            isSourceAddButtonEnabled.postValue(true)
            showUndoSourceRemoveSnack.postEvent { addSource(source) }
        }
    }

    private fun addSource(source: Source) {
        viewModelScope.launch(Dispatchers.IO) { sourceRepo.insert(source) }
    }

    private fun onSourceClicked(source: Source) {
        source.isActive = !source.isActive

        viewModelScope.launch(Dispatchers.IO) {
            /* update source when activated */
            if (source.isActive) {
                postRepo.refresh(this, listOf(source))
            }

            sourceRepo.update(source)
        }
    }

    fun onHomeNavSelected() {
        toolbarTitle.postValue(R.string.app_name)
        onNavigation.postValue(Destination.FEED)
        isFilterButtonVisible.postValue(!(isBookmarksButtonSelected.value ?: true))
        isBookmarksButtonVisible.postValue(true)
        isMenuVisible.postValue(false)
    }

    fun onContributeNavSelected() {
        toolbarTitle.postValue(R.string.nav_title_contribute)
        onNavigation.postValue(Destination.CONTRIBUTE)
        isFilterButtonVisible.postValue(false)
        isBookmarksButtonVisible.postValue(false)
        isMenuVisible.postValue(false)
    }

    fun onConferencesNavSelected() {
        toolbarTitle.postValue(R.string.nav_title_conferences)
        onNavigation.postValue(Destination.CONFERENCES)
        isFilterButtonVisible.postValue(false)
        isBookmarksButtonVisible.postValue(false)
        isMenuVisible.postValue(false)
    }

    fun onAboutNavSelected() {
        toolbarTitle.postValue(R.string.nav_title_about)
        onNavigation.postValue(Destination.ABOUT)
        isFilterButtonVisible.postValue(false)
        isBookmarksButtonVisible.postValue(false)
        isMenuVisible.postValue(false)
    }

    fun onSourceFilterClicked() {
        isSourceFilterVisible.postEvent(true)
    }

    fun onAddSourceClicked() {
        val shouldOpenInputField = !(isSourceInputVisible.value!!)
        isSourceInputVisible.postValue(shouldOpenInputField)

        when {
            shouldOpenInputField -> R.drawable.avd_add_to_close
            else -> R.drawable.avd_close_to_add
        }.also(sourceAddIcon::postValue)
    }

    fun onSaveSourceClicked(url: String) = viewModelScope.launch(Dispatchers.IO) {
        val trimmedUrl = url.trimIndent()
        if (Patterns.WEB_URL.matcher(trimmedUrl.toLowerCase(Locale.US)).matches()) {
            sourceErrText.postValue(R.string.empty_string)

            val cleanUrl = URLUtil.guessUrl(trimmedUrl)
            val alreadyExists = sourceRepo.isSourceExisting(cleanUrl)

            if (alreadyExists) {
                sourceErrText.postValue(R.string.error_source_exists)
            } else {
                isSourceProgressVisible.postValue(true)
                isSourceAddButtonEnabled.postValue(false)
                closeKeyboardEvent.postEvent(true)
                addSource(cleanUrl)
            }
        } else if (url.isEmpty()) {
            sourceErrText.postValue(R.string.error_empty_source_url)
        } else {
            sourceErrText.postValue(R.string.error_invalid_url)
        }
    }

    private fun addSource(url: String) {
        when (sourceRepo.addFromUrl(url)) {
            is DataStatus.Successful -> {
                isSourceInputVisible.postValue(false)
                sourceAddIcon.postValue(R.drawable.avd_close_to_add)
                sourceInputText.postValue(R.string.empty_string)
            }
            is DataStatus.Failed -> {
                sourceErrText.postValue(R.string.error_add_source)
            }
            is DataStatus.HttpFailed -> {
                sourceErrText.postValue(R.string.error_internet_or_url)

            }
        }

        isSourceProgressVisible.postValue(false)
        isSourceAddButtonEnabled.postValue(true)
    }

    @Suppress("UNUSED_PARAMETER")
    fun onSourceInputTextChanged(text: CharSequence, start: Int, before: Int, count: Int) {
        if (text.isNotEmpty()) {
            sourceErrText.postValue(R.string.empty_string)
        }
    }

    fun onMenuClicked() {
        val isCurrentlyVisible = isMenuVisible.value ?: false
        isMenuVisible.postValue(!isCurrentlyVisible)
    }

    fun onBookmarksEvent() {
        val isCurrentlySelected = (isBookmarksButtonSelected.value ?: false)
        isBookmarksShown.postValue(!isCurrentlySelected)
        isBookmarksButtonSelected.postValue(!isCurrentlySelected)
        isFilterButtonVisible.postValue(isCurrentlySelected)
    }

    fun onToolbarTitleClicked() {
        scrollTop.postEvent(Unit)
    }

    fun onCollapseMenu() {
        isMenuVisible.postValue(false)
    }

    fun onBackPressed(
        isFilterDrawerOpen: Boolean,
        navigateBack: () -> Unit
    ) {
        when {
            isMenuVisible.value == true -> isMenuVisible.postValue(false)
            isFilterDrawerOpen -> isSourceFilterVisible.postEvent(false)
            else -> navigateBack()
        }
    }

    fun onFilterDrawerClosed() {
        isSourceInputVisible.postValue(false)
        sourceInputText.postValue(R.string.empty_string)
        sourceErrText.postValue(R.string.empty_string)
        sourceAddIcon.postValue(R.drawable.avd_close_to_add)
    }
}