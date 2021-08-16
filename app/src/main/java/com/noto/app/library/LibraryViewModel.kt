package com.noto.app.library

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.noto.app.domain.model.*
import com.noto.app.domain.repository.LibraryRepository
import com.noto.app.domain.repository.NoteRepository
import com.noto.app.domain.source.LocalStorage
import com.noto.app.util.LayoutManager
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

private const val LayoutManagerKey = "Library_Layout_Manager"

class LibraryViewModel(
    private val libraryRepository: LibraryRepository,
    private val noteRepository: NoteRepository,
    private val storage: LocalStorage,
    private val libraryId: Long,
) : ViewModel() {

    private val mutableLibrary = MutableStateFlow(Library(libraryId, position = 0))
    val library get() = mutableLibrary.asStateFlow()

    private val mutableNotes = MutableStateFlow<List<Note>>(emptyList())
    val notes get() = mutableNotes.asStateFlow()

    val archivedNotes = noteRepository.getArchivedNotesByLibraryId(libraryId)
        .stateIn(viewModelScope, SharingStarted.Lazily, emptyList())

    val layoutManager = storage.get(LayoutManagerKey)
        .map { LayoutManager.valueOf(it) }
        .stateIn(viewModelScope, SharingStarted.Lazily, LayoutManager.Linear)

    private val mutableNotoColors = MutableStateFlow(NotoColor.values().associateWith { it == library.value.color }.toList())
    val notoColors get() = mutableNotoColors.asStateFlow()

    init {
        if (libraryId != 0L)
            libraryRepository.getLibraryById(libraryId)
                .filterNotNull()
                .onEach {
                    mutableLibrary.value = it
                    mutableNotoColors.value = mutableNotoColors.value
                        .mapTrueIfSameColor(it.color)
                }
                .launchIn(viewModelScope)

        noteRepository.getNotesByLibraryId(libraryId)
            .onEach { mutableNotes.value = it }
            .launchIn(viewModelScope)
    }

    fun createOrUpdateLibrary(title: String) = viewModelScope.launch {
        val color = notoColors.value.first { it.second }.first

        val library = library.value.copy(
            title = title.trim(),
            color = color,
        )

        if (libraryId == 0L)
            libraryRepository.createLibrary(library)
        else
            libraryRepository.updateLibrary(library)
    }

    fun deleteLibrary() = viewModelScope.launch {
        libraryRepository.deleteLibrary(library.value)
    }

    fun updateLayoutManager(value: LayoutManager) = viewModelScope.launch {
        storage.put(LayoutManagerKey, value.toString())
    }

    fun searchNotes(term: String) = viewModelScope.launch {
        val currentNotes = noteRepository.getNotesByLibraryId(libraryId)
            .first()

        mutableNotes.value = if (term.isBlank())
            currentNotes
        else
            currentNotes
                .filter { it.title.contains(term, ignoreCase = true) || it.body.contains(term, ignoreCase = true) }
    }

    fun selectNotoColor(notoColor: NotoColor) {
        mutableNotoColors.value = mutableNotoColors.value
            .mapTrueIfSameColor(notoColor)
    }

    fun updateSortingType(sortingType: SortingType) {
        viewModelScope.launch {
            libraryRepository.updateLibrary(library.value.copy(sortingType = sortingType))
        }
    }

    fun updateSortingMethod(sortingMethod: SortingMethod) {
        viewModelScope.launch {
            libraryRepository.updateLibrary(library.value.copy(sortingMethod = sortingMethod))
        }
    }

    private fun List<Pair<NotoColor, Boolean>>.mapTrueIfSameColor(notoColor: NotoColor) = map { it.first to (it.first == notoColor) }
}