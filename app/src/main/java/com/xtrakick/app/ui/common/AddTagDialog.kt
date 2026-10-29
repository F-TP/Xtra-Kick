package com.xtrakick.app.ui.common

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.EditorInfo
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import com.google.android.material.chip.Chip
import com.xtrakick.app.databinding.DialogAddTagBinding

class AddTagDialog : BottomSheetDialogFragment() {

    interface OnTagSelectedListener {
        fun onTagSelected(tag: String)
    }

    var onTagSelected: ((String) -> Unit)? = null

    private var _binding: DialogAddTagBinding? = null
    private val binding get() = _binding!!

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = DialogAddTagBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        val behavior = BottomSheetBehavior.from(view.parent as View)
        behavior.skipCollapsed = true
        behavior.state = BottomSheetBehavior.STATE_EXPANDED

        fun selectAndDismiss(tag: String) {
            val normalized = tag.trim()
            if (normalized.isNotBlank()) {
                val listener = (parentFragment as? OnTagSelectedListener) ?: (activity as? OnTagSelectedListener)
                listener?.onTagSelected(normalized)
                onTagSelected?.invoke(normalized)
                dismiss()
            }
        }

        with(binding) {
            tagInput.setOnEditorActionListener { _, actionId, _ ->
                if (actionId == EditorInfo.IME_ACTION_DONE) {
                    selectAndDismiss(tagInput.text?.toString().orEmpty())
                    true
                } else false
            }

            btnAdd.setOnClickListener {
                selectAndDismiss(tagInput.text?.toString().orEmpty())
            }

            val suggestions = listOf(
                "Shooter", "FPS", "Action", "RPG", "Open World",
                "IRL", "Casual", "Adventure", "Strategy", "MOBA",
                "Horror", "Survival", "Simulation", "Sports", "Racing",
                "Drops", "Music"
            )

            suggestions.forEach { suggestion ->
                val chip = Chip(requireContext()).apply {
                    text = suggestion
                    isClickable = true
                    setOnClickListener {
                        selectAndDismiss(suggestion)
                    }
                }
                suggestedTagGroup.addView(chip)
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    companion object {
        fun newInstance(): AddTagDialog = AddTagDialog()
    }
}
