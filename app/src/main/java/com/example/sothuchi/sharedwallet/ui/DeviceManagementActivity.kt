package com.example.sothuchi.sharedwallet.ui

import android.os.Bundle
import android.view.View
import android.widget.ImageView
import android.widget.ProgressBar
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.lifecycle.ViewModelProvider
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.sothuchi.R
import com.example.sothuchi.sharedwallet.identity.DeviceIdentityStore
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

class DeviceManagementActivity : AppCompatActivity() {

    private val viewModel: DeviceManagementViewModel by lazy {
        ViewModelProvider(this)[DeviceManagementViewModel::class.java]
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_device_management)

        val identityStore = DeviceIdentityStore(this)
        val adapter = DeviceSessionAdapter(identityStore.getDeviceId())

        val rvDevices = findViewById<RecyclerView>(R.id.rv_devices)
        val progress = findViewById<ProgressBar>(R.id.progress_devices)
        val empty = findViewById<View>(R.id.layout_empty_devices)
        val btnBack = findViewById<ImageView>(R.id.btn_back_devices)

        rvDevices.adapter = adapter
        rvDevices.layoutManager = LinearLayoutManager(this)
        btnBack.setOnClickListener { finish() }

        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.devices.collectLatest { devices ->
                    try {
                        val safeList = devices.ifEmpty { emptyList() }
                        progress.visibility = View.GONE
                        adapter.submitList(safeList)
                        empty.visibility = if (safeList.isEmpty()) View.VISIBLE else View.GONE
                    } catch (cancel: CancellationException) {
                        throw cancel
                    } catch (_: Exception) {
                        progress.visibility = View.GONE
                        adapter.submitList(emptyList())
                        empty.visibility = View.VISIBLE
                    }
                }
            }
        }
    }
}
