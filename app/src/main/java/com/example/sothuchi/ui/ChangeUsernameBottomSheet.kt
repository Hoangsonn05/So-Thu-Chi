package com.example.sothuchi.ui

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import com.example.sothuchi.R
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import com.google.android.material.button.MaterialButton
import com.google.android.material.textfield.TextInputEditText
import com.google.android.material.textfield.TextInputLayout

class ChangeUsernameBottomSheet(
    private val currentNickname: String,
    private val onConfirmClick: (String) -> Unit,
) : BottomSheetDialogFragment() {

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?,
    ): View {
        return inflater.inflate(R.layout.layout_change_username_bottom_sheet, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val inputLayout = view.findViewById<TextInputLayout>(R.id.til_username)
        val editText = view.findViewById<TextInputEditText>(R.id.et_username)
        val btnConfirm = view.findViewById<MaterialButton>(R.id.btn_confirm_change_username)

        editText.setText(currentNickname)
        editText.setSelection(editText.text?.length ?: 0)

        btnConfirm.setOnClickListener {
            val username = editText.text?.toString()?.trim().orEmpty()
            if (username.isBlank()) {
                inputLayout.error = "Vui lòng nhập username"
                return@setOnClickListener
            }

            inputLayout.error = null
            onConfirmClick(username)
            dismiss()
        }
    }

    companion object {
        const val TAG = "ChangeUsernameBottomSheet"

        fun newInstance(
            currentNickname: String,
            onConfirmClick: (String) -> Unit,
        ): ChangeUsernameBottomSheet {
            return ChangeUsernameBottomSheet(currentNickname, onConfirmClick)
        }
    }
}
