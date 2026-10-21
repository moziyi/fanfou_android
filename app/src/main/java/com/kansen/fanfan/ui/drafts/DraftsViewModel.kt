package com.kansen.fanfan.ui.drafts

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.kansen.fanfan.core.database.entity.DraftEntity
import com.kansen.fanfan.core.model.DraftMapper
import com.kansen.fanfan.core.model.DraftUi
import com.kansen.fanfan.data.repository.DraftRepository
import com.kansen.fanfan.data.repository.StatusRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.mapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/**
 * The draft box: every saved draft, the most recently changed first.
 *
 * The author of a reply or repost is looked up in the status cache, so the
 * list can say 「回复 @某人」. A target that is no longer cached is shown
 * without a name.
 */
@HiltViewModel
class DraftsViewModel @Inject constructor(
    private val draftRepository: DraftRepository,
    private val statusRepository: StatusRepository,
) : ViewModel() {

    @OptIn(ExperimentalCoroutinesApi::class)
    val drafts: StateFlow<List<DraftUi>> = draftRepository.observe()
        .mapLatest { drafts ->
            val ids = drafts.mapNotNull { it.inReplyToStatusId ?: it.repostStatusId }.distinct()
            val names = statusRepository.namesOf(ids)
            drafts.map { DraftMapper.toUi(it, names) }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /** The draft that was deleted last, so the screen can offer an undo. */
    private val _lastDeleted = MutableStateFlow<DraftEntity?>(null)
    val lastDeleted: StateFlow<DraftEntity?> = _lastDeleted.asStateFlow()

    fun delete(draft: DraftUi) {
        viewModelScope.launch {
            val entity = draftRepository.load(draft.id) ?: return@launch
            draftRepository.clear(draft.id)
            _lastDeleted.value = entity
        }
    }

    /** Puts the deleted draft back. */
    fun undoDelete() {
        val entity = _lastDeleted.value ?: return
        _lastDeleted.value = null
        viewModelScope.launch {
            draftRepository.save(
                draftId = entity.id,
                text = entity.text,
                inReplyToStatusId = entity.inReplyToStatusId,
                repostStatusId = entity.repostStatusId,
            )
        }
    }
}
