package com.example.sothuchi.ui

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import com.example.sothuchi.R
import com.google.android.material.bottomsheet.BottomSheetDialogFragment

/**
 * BottomSheetDialog for triggering the PDF export flow.
 */
class ExportPdfBottomSheet(private val onExportClick: () -> Unit) : BottomSheetDialogFragment() {

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.layout_export_pdf_bottom_sheet, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        view.findViewById<Button>(R.id.btn_confirm_export_pdf).setOnClickListener {
            onExportClick()
            dismiss()
        }
    }

    companion object {
        const val TAG = "ExportPdfBottomSheet"
        fun newInstance(onExportClick: () -> Unit): ExportPdfBottomSheet {
            return ExportPdfBottomSheet(onExportClick)
        }
    }
}
