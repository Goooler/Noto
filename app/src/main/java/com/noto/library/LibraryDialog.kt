package com.noto.library

import android.content.Context
import android.os.Bundle
import android.view.WindowManager
import android.view.inputmethod.InputMethodManager
import android.widget.RadioButton
import android.widget.RadioGroup
import androidx.core.content.ContextCompat
import androidx.core.content.res.ResourcesCompat
import com.noto.domain.model.NotoColor
import com.noto.domain.model.NotoIcon
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.noto.R
import com.noto.databinding.DialogLibraryBinding
import com.noto.domain.model.Library
import com.noto.util.getValue

class LibraryDialog(context: Context, private val viewModel: LibraryListViewModel) : BottomSheetDialog(context, R.style.BottomSheetDialog) {

    private val library = Library(libraryPosition = viewModel.libraries.value!!.size)

    private val binding = DialogLibraryBinding.inflate(layoutInflater)

    init {
        create()
        window?.clearFlags(WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_ALT_FOCUSABLE_IM)
        show()
        binding.et.requestFocus()
        window?.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_VISIBLE)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(binding.root)

        for (notoColor in NotoColor.values()) {
            val radBtn = RadioButton(context)
            radBtn.id = notoColor.ordinal
            radBtn.buttonDrawable = context.getDrawable(R.drawable.selector_dialog_rbtn_gray)
            radBtn.buttonTintList = context.resources.getColorStateList(notoColor.getValue())
            val layoutParams = RadioGroup.LayoutParams(RadioGroup.LayoutParams.WRAP_CONTENT, RadioGroup.LayoutParams.WRAP_CONTENT)
            layoutParams.setMargins(24, 16, 24, 16)
            radBtn.layoutParams = layoutParams
            binding.rgNotoColors.addView(radBtn)
        }

        for (notoIcon in NotoIcon.values()) {
            val radBtn = RadioButton(context)
            radBtn.id = notoIcon.ordinal
            radBtn.buttonDrawable = context.getDrawable(notoIcon.getValue())
            radBtn.buttonTintList = context.resources.getColorStateList(R.color.colorOnSecondary)
            val layoutParams = RadioGroup.LayoutParams(RadioGroup.LayoutParams.WRAP_CONTENT, RadioGroup.LayoutParams.WRAP_CONTENT)
            layoutParams.setMargins(24, 16, 24, 16)
            radBtn.layoutParams = layoutParams
            binding.rgNotoIcons.addView(radBtn)
        }

        binding.rgNotoColors.setOnCheckedChangeListener { group, checkedId ->
            val notoColor = NotoColor.values()[checkedId]
            library.notoColor = notoColor
            binding.til.setEndIconTintList(binding.root.resources.getColorStateList(notoColor.getValue()))
        }

        binding.rgNotoIcons.setOnCheckedChangeListener { group, checkedId ->
            val notoIcon = NotoIcon.values()[checkedId]
            library.notoIcon = notoIcon
            binding.til.startIconDrawable = context.getDrawable(notoIcon.getValue())
        }

        binding.btnCreate.setOnClickListener {
            val title = binding.et.text.toString()

            when {
                title.isBlank() -> binding.til.error = "Title can't be empty"

                viewModel.libraries.value?.any { it.libraryTitle == title } == true -> binding.til.error = "Title already exists"

                else -> {
                    library.libraryTitle = title
                    viewModel.saveLibrary(library)
                    dismiss()
                }
            }
        }
    }
}