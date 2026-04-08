package com.example.sothuchi.ui

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import com.example.sothuchi.R
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import com.google.android.material.button.MaterialButton
import com.google.firebase.auth.FirebaseAuth

/**
 * BottomSheetDialogFragment to modernizing the Email Report confirmation.
 */
class ExportEmailBottomSheet(private val onConfirmClick: () -> Unit) : BottomSheetDialogFragment() {

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.layout_export_email_bottom_sheet, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        // Display current user email
        val tvTargetEmail = view.findViewById<TextView>(R.id.tv_target_email)
        val user = FirebaseAuth.getInstance().currentUser
        if (user != null && user.email != null) {
            tvTargetEmail.text = user.email
        } else {
            tvTargetEmail.text = "Chưa xác thực email"
        }

        // Confirm button click
        view.findViewById<MaterialButton>(R.id.btn_confirm_send_email).setOnClickListener {
            onConfirmClick()
            dismiss()
        }
    }

    companion object {
        const val TAG = "ExportEmailBottomSheet"
        fun newInstance(onConfirmClick: () -> Unit): ExportEmailBottomSheet {
            return ExportEmailBottomSheet(onConfirmClick)
        }
    }
}
