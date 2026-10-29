package com.xtrakick.app.ui.games

import android.content.Context
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import com.xtrakick.app.util.bundleOf
import com.xtrakick.app.databinding.DialogGamesSortBinding
import com.xtrakick.app.model.ui.Tag
import com.xtrakick.app.ui.common.AddTagDialog
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import com.google.android.material.chip.Chip

class GamesSortDialog : BottomSheetDialogFragment(), AddTagDialog.OnTagSelectedListener {

    interface OnFilter {
        fun onChange(tags: Array<Tag>)
    }

    companion object {
        private const val TAG_IDS = "tag_ids"
        private const val TAG_NAMES = "tag_names"

        fun newInstance(tagIds: Array<String>?, tagNames: Array<String>?): GamesSortDialog {
            return GamesSortDialog().apply {
                arguments = bundleOf(TAG_IDS to tagIds, TAG_NAMES to tagNames)
            }
        }
    }

    private var _binding: DialogGamesSortBinding? = null
    private val binding get() = _binding!!
    private lateinit var listener: OnFilter

    private var selectedTags = mutableListOf<Tag>()

    override fun onAttach(context: Context) {
        super.onAttach(context)
        listener = parentFragment as OnFilter
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = DialogGamesSortBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        val behavior = BottomSheetBehavior.from(view.parent as View)
        behavior.skipCollapsed = true
        behavior.state = BottomSheetBehavior.STATE_EXPANDED
        with(binding) {
            val args = requireArguments()
            val originalTagIds = args.getStringArray(TAG_IDS) ?: emptyArray()
            val originalTags = requireArguments().getStringArray(TAG_NAMES)?.let { names ->
                originalTagIds.zip(names).map {
                    Tag(
                        id = it.first,
                        name = it.second,
                    )
                }
            } ?: emptyList()
            selectedTags = originalTags.toMutableList()
            updateTagGroup()
            selectTags.visibility = View.VISIBLE
            selectTags.setOnClickListener {
                AddTagDialog.newInstance().show(childFragmentManager, "addTag")
            }
            apply.setOnClickListener {
                val tags = selectedTags.sortedBy { it.id }
                if (!tags.mapNotNull { it.id }.toTypedArray().contentEquals(originalTagIds)) {
                    listener.onChange(
                        tags.toTypedArray(),
                    )
                }
                dismiss()
            }
        }
    }

    private fun updateTagGroup() {
        with(binding) {
            tagGroup.removeAllViews()
            tagGroup.visibility = if (selectedTags.isNotEmpty()) View.VISIBLE else View.GONE
            selectedTags.forEach { tag ->
                tagGroup.addView(
                    Chip(requireContext()).apply {
                        text = tag.name ?: tag.id
                        isCloseIconVisible = true
                        setOnCloseIconClickListener {
                            selectedTags.removeAll { it.id.equals(tag.id, ignoreCase = true) || it.name.equals(tag.name, ignoreCase = true) }
                            updateTagGroup()
                        }
                    }
                )
            }
        }
    }

    override fun onTagSelected(tag: String) {
        if (!selectedTags.any { it.name.equals(tag, ignoreCase = true) || it.id.equals(tag, ignoreCase = true) }) {
            selectedTags.add(Tag(id = tag, name = tag))
            updateTagGroup()
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
