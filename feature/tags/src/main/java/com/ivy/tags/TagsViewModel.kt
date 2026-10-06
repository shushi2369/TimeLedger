package com.ivy.tags

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ivy.data.model.Tag
import com.ivy.data.model.primitive.NotBlankTrimmedString
import com.ivy.data.repository.TagRepository
import com.ivy.data.repository.mapper.TagMapper
import com.ivy.legacy.utils.toLowerCaseLocal
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject

@HiltViewModel
class TagsViewModel @Inject constructor(
    private val tagRepository: TagRepository,
    private val tagMapper: TagMapper,
) : ViewModel() {

    data class TagWithCount(
        val tag: Tag,
        val usageCount: Int,
    )

    data class State(
        val tags: List<TagWithCount> = emptyList(),
        val loading: Boolean = true,
    )

    private val _state = MutableStateFlow(State())
    val state = _state.asStateFlow()

    fun start() {
        refresh()
    }

    fun refresh() {
        viewModelScope.launch(Dispatchers.IO) {
            val tags = tagRepository.findAll()
            // 每个标签被多少笔交易/规划交易关联（distinct associatedId）
            val counts = tagRepository.findByAllTagsForAssociations()
                .values
                .flatten()
                .groupBy({ it.id.value }) { it.associatedId.value }
                .mapValues { (_, ids) -> ids.distinct().size }
            _state.value = State(
                tags = tags
                    .map { TagWithCount(it, counts[it.id.value] ?: 0) }
                    .sortedWith(compareByDescending<TagWithCount> { it.usageCount }.thenBy { it.tag.name.value }),
                loading = false,
            )
        }
    }

    fun addTag(name: String) {
        viewModelScope.launch(Dispatchers.IO) {
            NotBlankTrimmedString.from(name.toLowerCaseLocal()).onRight {
                // fork 修复：同名标签不重复创建
                val normalized = it.value
                if (tagRepository.findAll().any { t -> t.name.value == normalized }) {
                    refresh()
                    return@onRight
                }
                tagRepository.save(with(tagMapper) { tagMapper.createNewTag(name = it) })
                refresh()
            }
        }
    }

    fun updateTag(oldTag: Tag, newTag: Tag) {
        viewModelScope.launch(Dispatchers.IO) {
            // fork 修复：仅当标签仍存在才保存（防删除后 upsert 复活）
            if (tagRepository.findById(oldTag.id) != null) {
                tagRepository.save(newTag)
            }
            refresh()
        }
    }

    fun deleteTag(tag: Tag) {
        viewModelScope.launch(Dispatchers.IO) {
            tagRepository.deleteById(tag.id)
            refresh()
        }
    }

    suspend fun tagName(tagId: com.ivy.data.model.TagId): String =
        withContext(Dispatchers.IO) {
            tagRepository.findById(tagId)?.name?.value.orEmpty()
        }
}
