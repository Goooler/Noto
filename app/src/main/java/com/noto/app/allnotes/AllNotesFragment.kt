package com.noto.app.allnotes

import android.annotation.SuppressLint
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.activity.addCallback
import androidx.core.view.MenuItemCompat
import androidx.core.view.forEach
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import com.noto.app.R
import com.noto.app.UiState
import com.noto.app.databinding.AllNotesFragmentBinding
import com.noto.app.domain.model.Font
import com.noto.app.domain.model.Library
import com.noto.app.library.noteItem
import com.noto.app.util.*
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import org.koin.androidx.viewmodel.ext.android.viewModel

class AllNotesFragment : Fragment() {

    private val viewModel by viewModel<AllNotesViewModel>()

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?,
    ): View = AllNotesFragmentBinding.inflate(inflater, container, false).withBinding {
        setupState()
        setupListeners()
    }

    private fun AllNotesFragmentBinding.setupListeners() {
        fab.setOnClickListener {
            navController?.navigateSafely(AllNotesFragmentDirections.actionAllNotesFragmentToSelectLibraryDialogFragment(libraryId = 0))
        }

        bab.setNavigationOnClickListener {
            navController?.navigateSafely(AllNotesFragmentDirections.actionAllNotesFragmentToMainFragment())
        }

        bab.setOnMenuItemClickListener { menuItem ->
            when (menuItem.itemId) {
                R.id.search -> {
                    if (viewModel.isSearchEnabled.value)
                        viewModel.disableSearch()
                    else
                        viewModel.enableSearch()
                    true
                }
                R.id.change_visibility -> {
                    if (viewModel.notesVisibility.value.any { it.value })
                        viewModel.collapseAll()
                    else
                        viewModel.expandAll()
                    true
                }
                else -> false
            }
        }

        bab.setOnSwipeGestureListener {
            navController?.navigateSafely(AllNotesFragmentDirections.actionAllNotesFragmentToMainFragment())
        }
    }

    private fun AllNotesFragmentBinding.setupState() {
        rv.edgeEffectFactory = BounceEdgeEffectFactory()
        rv.itemAnimator = VerticalListItemAnimator()
        bab.setRoundedCorners()
        context?.let { context ->
            val backgroundColor = context.attributeColoResource(R.attr.notoBackgroundColor)
            bab.navigationIcon?.mutate()?.setTint(backgroundColor)
            bab.menu.forEach { it.icon?.mutate()?.setTint(backgroundColor) }
        }

        combine(
            viewModel.notes,
            viewModel.notesVisibility,
            viewModel.font,
            viewModel.isSearchEnabled,
            viewModel.searchTerm,
        ) { notes, notesVisibility, font, isSearchEnabled, searchTerm ->
            setupNotes(notes, notesVisibility, font, isSearchEnabled, searchTerm)
        }.launchIn(lifecycleScope)

        viewModel.isSearchEnabled
            .onEach { isSearchEnabled ->
                if (isSearchEnabled)
                    rv.smoothScrollToPosition(0)
            }
            .launchIn(lifecycleScope)

        viewModel.isCollapseToolbar
            .onEach { isCollapseToolbar ->
                if (!abl.isExpanded)
                    abl.setExpanded(!isCollapseToolbar, false)
            }
            .launchIn(lifecycleScope)

        val menuItem = bab.menu.findItem(R.id.change_visibility)
        val menuItemColor = context?.attributeColoResource(R.attr.notoBackgroundColor)?.toColorStateList()
        val expandText = context?.stringResource(R.string.expand)
        val collapseText = context?.stringResource(R.string.collapse)

        viewModel.notesVisibility
            .onEach { visibility ->
                if (visibility.any { it.value }) {
                    menuItem.icon = context?.drawableResource(R.drawable.ic_round_collapse_24)
                    menuItem.title = collapseText
                    MenuItemCompat.setContentDescription(menuItem, collapseText)
                } else {
                    menuItem.icon = context?.drawableResource(R.drawable.ic_round_expand_24)
                    menuItem.title = expandText
                    MenuItemCompat.setContentDescription(menuItem, expandText)
                }
                MenuItemCompat.setIconTintList(menuItem, menuItemColor)
            }
            .launchIn(lifecycleScope)

        navController?.currentBackStackEntry?.savedStateHandle
            ?.getLiveData<Long>(Constants.LibraryId)
            ?.observe(viewLifecycleOwner) { libraryId ->
                if (libraryId != null) {
                    lifecycleScope.launch {
                        delay(150) // Wait for the fragment to be destroyed
                        navController?.currentBackStackEntry?.savedStateHandle?.remove<Long>(Constants.LibraryId)
                        navController?.navigateSafely(AllNotesFragmentDirections.actionAllNotesFragmentToNoteFragment(libraryId))
                    }
                }
            }
    }

    @SuppressLint("ClickableViewAccessibility")
    private fun AllNotesFragmentBinding.setupNotes(
        state: UiState<Map<Library, List<NoteWithLabels>>>,
        notesVisibility: Map<Library, Boolean>,
        font: Font,
        isSearchEnabled: Boolean,
        searchTerm: String,
    ) {
        when (state) {
            is UiState.Loading -> rv.setupProgressIndicator()
            is UiState.Success -> {
                val notes = state.value
                rv.withModels {

                    if (isSearchEnabled) {
                        searchItem {
                            id("search")
                            searchTerm(searchTerm)
                            callback { searchTerm -> viewModel.setSearchTerm(searchTerm) }
                            onBind { _, view, _ ->
                                if (view.binding.etSearch.text.toString().isBlank()) {
                                    view.binding.etSearch.requestFocus()
                                    activity?.showKeyboard(view.binding.etSearch)
                                }
                            }
                            onUnbind { _, view ->
                                activity?.hideKeyboard(view.binding.etSearch)
                            }
                        }
                        activity?.onBackPressedDispatcher?.addCallback(viewLifecycleOwner) {
                            viewModel.disableSearch()
                            if (isEnabled) isEnabled = false
                        }
                    }

                    context?.let { context ->
                        if (notes.values.all { it.isEmpty() }) {
                            placeholderItem {
                                id("placeholder")
                                placeholder(context.stringResource(R.string.no_notes_found))
                            }
                        } else {
                            notes.forEach { (library, notes) ->
                                val isVisible = notesVisibility[library] ?: true

                                headerItem {
                                    id("library ${library.id}")
                                    title(library.getTitle(context))
                                    color(library.color)
                                    isVisible(isVisible)
                                    onClickListener { _ -> viewModel.toggleVisibilityForLibrary(library.id) }
                                }

                                if (isVisible)
                                    notes.forEach { entry ->
                                        noteItem {
                                            id(entry.first.id)
                                            note(entry.first)
                                            font(font)
                                            labels(entry.second)
                                            color(library.color)
                                            previewSize(library.notePreviewSize)
                                            isShowCreationDate(library.isShowNoteCreationDate)
                                            isManualSorting(false)
                                            onClickListener { _ ->
                                                navController
                                                    ?.navigateSafely(
                                                        AllNotesFragmentDirections.actionAllNotesFragmentToNoteFragment(
                                                            entry.first.libraryId,
                                                            entry.first.id
                                                        )
                                                    )
                                            }
                                            onLongClickListener { _ ->
                                                navController
                                                    ?.navigateSafely(
                                                        AllNotesFragmentDirections.actionAllNotesFragmentToNoteDialogFragment(
                                                            entry.first.libraryId,
                                                            entry.first.id,
                                                            R.id.libraryFragment
                                                        )
                                                    )
                                                true
                                            }
                                            onDragHandleTouchListener { _, _ -> false }
                                        }
                                    }
                            }
                        }
                    }
                }
            }
        }
    }
}