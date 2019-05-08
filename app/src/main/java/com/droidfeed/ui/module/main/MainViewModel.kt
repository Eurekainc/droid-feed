package com.droidfeed.ui.module.main

import android.content.SharedPreferences
import android.util.Patterns
import android.webkit.URLUtil
import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.Transformations.map
import com.droidfeed.R
import com.droidfeed.data.DataStatus
import com.droidfeed.data.model.Source
import com.droidfeed.data.repo.PostRepo
import com.droidfeed.data.repo.SourceRepo
import com.droidfeed.ui.adapter.model.SourceUIModel
import com.droidfeed.ui.common.BaseViewModel
import com.droidfeed.util.event.Event
import com.droidfeed.util.hasAcceptedTerms
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import javax.inject.Inject

class MainViewModel @Inject constructor(
    private val sourceRepo: SourceRepo,
    private val postRepo: PostRepo,
    sharedPrefs: SharedPreferences
) : BaseViewModel() {

    val toolbarTitle = MutableLiveData<@StringRes Int>().apply { value = R.string.app_name }
    val onNavigation = MutableLiveData<Destination>().apply { value = Destination.FEED }
    val scrollTop = MutableLiveData<Event<Unit>>()

    val isUserTermsAccepted = MutableLiveData<Boolean>().apply {
        value = sharedPrefs.hasAcceptedTerms
    }
    val sourceAddIcon = MutableLiveData<@DrawableRes Int>().apply {
        value = R.drawable.avd_close_to_add
    }
    val sourceRemoveIcon = MutableLiveData<@DrawableRes Int>().apply {
        value = R.drawable.avd_close_to_minus
    }
    val isMenuVisible = MutableLiveData<Boolean>().apply { value = false }
    val isSourceInputVisible = MutableLiveData<Boolean>().apply { value = false }
    val isRemoveSourceVisible = MutableLiveData<Boolean>().apply { value = true }
    val isRemoveSourceEnabled = MutableLiveData<Boolean>().apply { value = true }
    val isAddSourceEnabled = MutableLiveData<Boolean>().apply { value = true }
    val isSourceFilterVisible = MutableLiveData<Event<Boolean>>().apply { value = Event(false) }
    val closeKeyboardEvent = MutableLiveData<Event<Boolean>>().apply { value = Event(false) }
    val sourceErrText = MutableLiveData<@StringRes Int>().apply { value = R.string.empty_string }
    val sourceInputText = MutableLiveData<@StringRes Int>().apply { value = R.string.empty_string }
    val isSourceProgressVisible = MutableLiveData<Boolean>().apply { value = false }
    val isSourceAddButtonEnabled = MutableLiveData<Boolean>().apply { value = true }
    val isFilterButtonVisible = MutableLiveData<Boolean>().apply { value = true }
    val isBookmarksShown = MutableLiveData<Boolean>().apply { value = false }
    val isBookmarksButtonVisible = MutableLiveData<Boolean>().apply { value = true }
    val isBookmarksButtonSelected = MutableLiveData<Boolean>().apply { value = false }
    val sourceTransformation = MutableLiveData<(List<SourceUIModel>) -> List<SourceUIModel>>()
    val showUndoSourceRemoveSnack = MutableLiveData<Event<() -> Unit>>()


    private var isUserAddedSourceExist = false

    val sourceUIModelData: LiveData<List<SourceUIModel>> = map(sourceRepo.getAll()) { sourceList ->
        isUserAddedSourceExist = !sourceList.none { it.isUserSource }

        isRemoveSourceVisible.postValue(isUserAddedSourceExist)

        sourceList.map { source ->
            SourceUIModel(
                source = source,
                onClick = this::onSourceClicked,
                onRemove = this::onSourceRemoveClicked
            )
        }
    }

    init {
        updateSources(sourceRepo)
    }

    private fun updateSources(sourceRepo: SourceRepo) = launch(Dispatchers.IO) {
        val result = sourceRepo.pull()
        if (result is DataStatus.Successful) {
            sourceRepo.insert(result.data ?: emptyList())
        }
    }

    private fun onSourceRemoveClicked(source: Source) {
        launch(Dispatchers.IO) {
            sourceRepo.remove(source)
            sourceRemoveIcon.postValue(R.drawable.avd_close_to_minus)
            isSourceAddButtonEnabled.postValue(true)
            isAddSourceEnabled.postValue(true)

            showUndoSourceRemoveSnack.postValue(Event {
                sourceRepo.insert(listOf(source))
            })
        }
    }

    private fun onSourceClicked(source: Source) {
        source.isActive = !source.isActive

        launch(Dispatchers.IO) {
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

    fun onNewsletterNavSelected() {
        toolbarTitle.postValue(R.string.nav_title_newsletter)
        onNavigation.postValue(Destination.NEWSLETTER)
        isFilterButtonVisible.postValue(false)
        isBookmarksButtonVisible.postValue(false)
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
        isSourceFilterVisible.postValue(Event(true))
    }

    fun onAddSourceClicked() {
        val shouldOpenInputField = !(isSourceInputVisible.value!!)
        isSourceInputVisible.postValue(shouldOpenInputField)
        isRemoveSourceVisible.postValue(!shouldOpenInputField && isUserAddedSourceExist)

        if (shouldOpenInputField) {
            R.drawable.avd_add_to_close
        } else {
            R.drawable.avd_close_to_add
        }.also(sourceAddIcon::postValue)

        analytics.logAddSourceButtonClick()
    }

    fun onSaveSourceClicked(url: String) {
        launch(Dispatchers.IO) {
            if (Patterns.WEB_URL.matcher(url.toLowerCase()).matches()) {
                sourceErrText.postValue(R.string.empty_string)

                val cleanUrl = URLUtil.guessUrl(url)

                val alreadyExists = sourceRepo.isSourceExisting(cleanUrl)

                if (alreadyExists) {
                    sourceErrText.postValue(R.string.error_source_exists)
                } else {
                    isSourceProgressVisible.postValue(true)
                    isSourceAddButtonEnabled.postValue(false)
                    closeKeyboardEvent.postValue(Event(true))

                    when (sourceRepo.addFromUrl(cleanUrl)) {
                        is DataStatus.Successful -> {
                            isSourceProgressVisible.postValue(false)
                            isSourceAddButtonEnabled.postValue(true)
                            isSourceInputVisible.postValue(false)
                            sourceAddIcon.postValue(R.drawable.avd_close_to_add)
                        }
                        is DataStatus.Failed -> {
                            sourceErrText.postValue(R.string.error_add_source)
                            isSourceProgressVisible.postValue(false)
                            isSourceAddButtonEnabled.postValue(true)

                        }
                        is DataStatus.HttpFailed -> {
                            sourceErrText.postValue(R.string.error_internet_or_url)
                            isSourceProgressVisible.postValue(false)
                            isSourceAddButtonEnabled.postValue(true)
                        }
                    }

                }
            } else if (url.isEmpty()) {
                sourceErrText.postValue(R.string.error_empty_source_url)
            } else {
                sourceErrText.postValue(R.string.error_invalid_url)
            }

            analytics.logSaveSourceButtonClick()
        }

    }

    fun onSourceInputTextChanged(text: CharSequence, start: Int, before: Int, count: Int) {
        if (text.isNotEmpty()) {
            sourceErrText.postValue(R.string.empty_string)
        }
    }

    fun onRemoveSourceClicked() {
        val isListRemovable = sourceRemoveIcon.value == R.drawable.avd_close_to_minus
        isAddSourceEnabled.postValue(!isListRemovable)

        transformSourcesRemovable(isListRemovable)

        if (!isListRemovable) {
            R.drawable.avd_close_to_minus
        } else {
            R.drawable.avd_minus_to_close
        }.also(sourceRemoveIcon::postValue)

        analytics.logRemoveSourceButtonClick()
    }

    private fun transformSourcesRemovable(isRemovable: Boolean) {
        sourceTransformation.postValue { sourceList ->
            sourceList
                .filter { uiModel -> uiModel.getData().isUserSource }
                .forEach { uiModel -> uiModel.getData().isRemovable.set(isRemovable) }
            sourceList
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
        scrollTop.postValue(Event(Unit))
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
            isFilterDrawerOpen -> isSourceFilterVisible.postValue(Event(false))
            else -> navigateBack()
        }
    }

    fun onFilterDrawerClosed() {
        isSourceInputVisible.postValue(false)
        sourceInputText.postValue(R.string.empty_string)
        sourceErrText.postValue(R.string.empty_string)
        sourceAddIcon.postValue(R.drawable.avd_close_to_add)
        sourceRemoveIcon.postValue(R.drawable.avd_close_to_minus)
        isAddSourceEnabled.postValue(true)
        isRemoveSourceVisible.postValue(isUserAddedSourceExist)
        isRemoveSourceEnabled.postValue(true)
        transformSourcesRemovable(false)
    }
}