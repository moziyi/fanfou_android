package com.kansen.fanfan.ui.drafts

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.kansen.fanfan.core.model.DraftMapper
import com.kansen.fanfan.core.model.DraftUi
import com.kansen.fanfan.data.repository.DraftRepository
import com.kansen.fanfan.data.repository.StatusRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.mapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

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

    fun delete(id: String) {
        viewModelScope.launch { draftRepository.clear(id) }
    }
}
