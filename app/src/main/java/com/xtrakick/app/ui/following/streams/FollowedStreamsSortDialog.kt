package com.xtrakick.app.ui.following.streams

import android.content.Context
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.RadioButton
import androidx.core.view.isVisible
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import com.google.android.material.chip.Chip
import com.xtrakick.app.R
import com.xtrakick.app.databinding.DialogFollowedStreamsSortBinding
import com.xtrakick.app.ui.common.AddTagDialog
import com.xtrakick.app.util.bundleOf

class FollowedStreamsSortDialog : BottomSheetDialogFragment(), AddTagDialog.OnTagSelectedListener {

    interface OnFilter {
        fun onChange(
            sort: String,
            sortText: CharSequence,
            changed: Boolean,
            saveDefault: Boolean,
            tags: Array<String> = emptyArray(),
        )
    }

    companion object {
        const val SORT_VIEWERS = "VIEWER_COUNT"
        const val SORT_VIEWERS_ASC = "VIEWER_COUNT_ASC"
        const val SORT_RECENT = "RECENT"

        private const val SORT = "sort"
        private const val TAGS = "tags"

        fun newInstance(sort: String?, tags: Array<String> = emptyArray()): FollowedStreamsSortDialog {
            return FollowedStreamsSortDialog().apply {
                arguments = bundleOf(
                    SORT to sort,
                    TAGS to tags,
                )
            }
        }
    }

    private var _binding: DialogFollowedStreamsSortBinding? = null
    private val binding get() = _binding!!
    private lateinit var listener: OnFilter
    private val selectedTags = mutableListOf<String>()

    override fun onAttach(context: Context) {
        super.onAttach(context)
        listener = parentFragment as OnFilter
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = DialogFollowedStreamsSortBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        val behavior = BottomSheetBehavior.from(view.parent as View)
        behavior.skipCollapsed = true
        behavior.state = BottomSheetBehavior.STATE_EXPANDED
        with(binding) {
            val originalSortId = when (requireArguments().getString(SORT)) {
                SORT_VIEWERS_ASC -> R.id.viewers_low
                SORT_RECENT -> R.id.recent
                else -> R.id.viewers_high
            }
            sort.check(originalSortId)

            val originalTags = requireArguments().getStringArray(TAGS) ?: emptyArray()
            selectedTags.clear()
            selectedTags.addAll(originalTags)
            updateTagGroup()

            selectTags.setOnClickListener {
                val dialog = AddTagDialog.newInstance()
                dialog.onTagSelected = { tag -> onTagSelected(tag) }
                dialog.show(childFragmentManager, null)
            }

            saveDefault.setOnClickListener {
                applyFilters(originalSortId, originalTags, saveDefault = true)
                dismiss()
            }
            apply.setOnClickListener {
                applyFilters(originalSortId, originalTags, saveDefault = false)
                dismiss()
            }
        }
    }

    private fun updateTagGroup() {
        with(binding) {
            tagGroup.removeAllViews()
            tagGroup.isVisible = selectedTags.isNotEmpty()
            selectedTags.forEach { tag ->
                val chip = Chip(requireContext()).apply {
                    text = tag
                    isCloseIconVisible = true
                    setOnCloseIconClickListener {
                        selectedTags.remove(tag)
                        updateTagGroup()
                    }
                }
                tagGroup.addView(chip)
            }
        }
    }

    override fun onTagSelected(tag: String) {
        if (!selectedTags.any { it.equals(tag, ignoreCase = true) }) {
            selectedTags.add(tag)
            updateTagGroup()
        }
    }

    private fun applyFilters(originalSortId: Int, originalTags: Array<String>, saveDefault: Boolean) {
        with(binding) {
            val checkedSortId = sort.checkedRadioButtonId
            val sortBtn = requireView().findViewById<RadioButton>(checkedSortId)
            val tags = selectedTags.sorted().toTypedArray()
            val tagsChanged = !tags.contentEquals(originalTags.sortedArray())
            listener.onChange(
                when (checkedSortId) {
                    R.id.viewers_low -> SORT_VIEWERS_ASC
                    R.id.recent -> SORT_RECENT
                    else -> SORT_VIEWERS
                },
                sortBtn.text,
                checkedSortId != originalSortId || tagsChanged,
                saveDefault,
                tags,
            )
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
