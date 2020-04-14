package com.automattic.portkey.compose.story

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import com.automattic.portkey.compose.frame.FrameSaveService.SaveResultReason.SaveSuccess
import com.automattic.portkey.compose.story.StoryFrameItem.BackgroundSource.FileBackgroundSource
import com.automattic.portkey.compose.story.StoryFrameItem.BackgroundSource.UriBackgroundSource
import com.automattic.portkey.compose.story.StoryViewModel.StoryFrameListItemUiState.StoryFrameListItemUiStateFrame
import com.automattic.portkey.util.SingleLiveEvent

class StoryViewModel(private val repository: StoryRepository, val storyIndex: StoryIndex) : ViewModel() {
    private var currentSelectedFrameIndex: Int = DEFAULT_SELECTION

    private val _uiState: MutableLiveData<StoryFrameListUiState> = MutableLiveData()
    val uiState: LiveData<StoryFrameListUiState> = _uiState

    private val _uiStateErroredItem: MutableLiveData<StoryFrameListItemUiStateFrame> = MutableLiveData()
    val uiStateErroredItem: LiveData<StoryFrameListItemUiStateFrame> = _uiStateErroredItem

    private val _onSelectedFrameIndex: SingleLiveEvent<Pair<Int, Int>> by lazy {
        SingleLiveEvent<Pair<Int, Int>>().also {
            it.value = Pair(DEFAULT_SELECTION, currentSelectedFrameIndex)
        }
    }
    val onSelectedFrameIndex: SingleLiveEvent<Pair<Int, Int>> = _onSelectedFrameIndex

    private val _onFrameIndexMoved = SingleLiveEvent<Pair<Int, Int>>()
    val onFrameIndexMoved: SingleLiveEvent<Pair<Int, Int>> = _onFrameIndexMoved

    private val _addButtonClicked = SingleLiveEvent<Unit>()
    val addButtonClicked = _addButtonClicked

    private val _onUserSelectedFrame = SingleLiveEvent<Pair<Int, Int>>()
    val onUserSelectedFrame = _onUserSelectedFrame

    fun loadStory(storyIndex: StoryIndex) {
        repository.loadStory(storyIndex)?.let {
            updateUiState(createUiStateFromModelState(repository.getImmutableCurrentStoryFrames()))
            // default selected frame when loading a new Story
            _onSelectedFrameIndex.value = Pair(DEFAULT_SELECTION, DEFAULT_SELECTION)
        }
    }

    fun addStoryFrameItemToCurrentStory(item: StoryFrameItem) {
        repository.addStoryFrameItemToCurrentStory(item)
        updateUiState(createUiStateFromModelState(repository.getImmutableCurrentStoryFrames()))
    }

    fun discardCurrentStory() {
        repository.discardCurrentStory()
        currentSelectedFrameIndex = DEFAULT_SELECTION // default selected frame when loading a new Story
        _onSelectedFrameIndex.value = Pair(DEFAULT_SELECTION, currentSelectedFrameIndex)
        updateUiState(createUiStateFromModelState(repository.getImmutableCurrentStoryFrames()))
    }

    fun setCurrentStoryTitle(title: String) {
        repository.setCurrentStoryTitle(title)
    }

    fun setSelectedFrame(index: Int): StoryFrameItem {
        val newlySelectedFrame = repository.getImmutableCurrentStoryFrames()[index]
        val oldIndex = currentSelectedFrameIndex
        currentSelectedFrameIndex = index
        updateUiStateForSelection(oldIndex, index)
        return newlySelectedFrame
    }

    fun setSelectedFrameByUser(index: Int) {
        val oldIndex = currentSelectedFrameIndex
        setSelectedFrame(index)
        _onUserSelectedFrame.value = Pair(oldIndex, index)
    }

    fun getSelectedFrameIndex(): Int {
        return currentSelectedFrameIndex
    }

    fun getSelectedFrame(): StoryFrameItem {
        return getCurrentStoryFrameAt(currentSelectedFrameIndex)
    }

    fun getCurrentStoryFrameAt(index: Int): StoryFrameItem {
        return repository.getImmutableCurrentStoryFrames()[index]
    }

    fun getCurrentStorySize(): Int {
        return repository.getCurrentStorySize()
    }

    fun getImmutableCurrentStoryFrames(): List<StoryFrameItem> {
        return repository.getImmutableCurrentStoryFrames()
    }

    fun getCurrentStoryIndex(): Int {
        return repository.currentStoryIndex
    }

    fun anyOfCurrentStoryFramesIsErrored(): Boolean {
        val frames = repository.getImmutableCurrentStoryFrames()
        for (frame in frames) {
            if (frame.saveResultReason !is SaveSuccess) {
                return true
            }
        }
        return false
    }

    fun anyOfCurrentStoryFramesHasViews(): Boolean {
        val frames = repository.getImmutableCurrentStoryFrames()
        for (frame in frames) {
            if (frame.addedViews.size > 0) {
                return true
            }
        }
        return false
    }

    fun removeFrameAt(pos: Int) {
        // remove from the repo
        repository.removeFrameAt(pos)

        // adjust index
        if (currentSelectedFrameIndex > 0 && pos <= currentSelectedFrameIndex) {
            currentSelectedFrameIndex--
        }

        // if this Story no longer contains any frames, just discard it
        if (repository.getCurrentStorySize() == 0) {
            discardCurrentStory()
        } else {
            // if we have frames to keep, update the UI state with the new resulting frame array
            updateUiState(createUiStateFromModelState(repository.getImmutableCurrentStoryFrames()))
            // now let's select the one to the left
            setSelectedFrameByUser(currentSelectedFrameIndex)
        }
    }

    fun swapItemsInPositions(pos1: Int, pos2: Int) {
        repository.swapItemsInPositions(pos1, pos2)
        // adjust currentSelectedFrameIndex so it reflects the movement only
        // if the movement occurred entierly to the left of the selection, don't update it
        // if the movement occurred from its left to its right, set the currentselection to be one position less
        // if the movement occurred from its right to its left, an insertion occurred on the left so our position
        // should get moved one position to the right
        // if the movement occurred entirely to the right of the selection, don't update it
        if (pos1 < currentSelectedFrameIndex && pos2 >= currentSelectedFrameIndex) {
            currentSelectedFrameIndex--
        } else if (pos1 > currentSelectedFrameIndex && pos2 <= currentSelectedFrameIndex) {
            currentSelectedFrameIndex++
        } else if (pos1 == currentSelectedFrameIndex) {
            currentSelectedFrameIndex = pos2
        }

        updateUiStateForItemSwap(pos1, pos2)
    }

    fun onSwapActionEnded() {
        updateUiState(createUiStateFromModelState(repository.getImmutableCurrentStoryFrames()))
    }

    private fun updateUiState(uiState: StoryFrameListUiState) {
        _uiState.value = uiState
    }

    private fun updateUiStateForSelection(oldSelectedIndex: Int, newSelectedIndex: Int) {
        _uiState.value?.let { immutableStory ->
            (immutableStory.items[oldSelectedIndex] as? StoryFrameListItemUiStateFrame)?.let {
                it.selected = false
            }

            (immutableStory.items[newSelectedIndex] as? StoryFrameListItemUiStateFrame)?.let {
                it.selected = true
            }
            _onSelectedFrameIndex.value = Pair(oldSelectedIndex, newSelectedIndex)
        }
    }

    private fun updateUiStateForItemSwap(oldIndex: Int, newIndex: Int) {
        _onFrameIndexMoved.value = Pair(oldIndex, newIndex)
    }

    private fun createUiStateFromModelState(storyItems: List<StoryFrameItem>): StoryFrameListUiState {
        val uiStateItems = ArrayList<StoryFrameListItemUiState>()
        val newUiState = StoryFrameListUiState(uiStateItems)
        storyItems.forEachIndexed { index, model ->
            val isSelected = (getSelectedFrameIndex() == index)
            val filePath =
                if ((model.source is UriBackgroundSource)) {
                    model.source.contentUri.toString()
                } else {
                    (model.source as FileBackgroundSource).file.toString()
                }
            val oneFrameUiState = StoryFrameListItemUiStateFrame(
                selected = isSelected, filePath = filePath, errored = model.saveResultReason != SaveSuccess
            )
            oneFrameUiState.onItemTapped = {
                setSelectedFrameByUser(index)
            }
            uiStateItems.add(oneFrameUiState)
        }
        return newUiState
    }

    data class StoryFrameListUiState(val items: List<StoryFrameListItemUiState>)

    sealed class StoryFrameListItemUiState() {
        var onItemTapped: (() -> Unit)? = null

        data class StoryFrameListItemUiStateFrame(
            var selected: Boolean = false,
            var errored: Boolean = false,
            var filePath: String? = null
        ) : StoryFrameListItemUiState()
    }

    companion object {
        const val DEFAULT_SELECTION = 0
    }
}
