package com.example.sothuchi

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ProgressBar
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.example.sothuchi.realtime.config.FirestoreOfflineConfig
import com.example.sothuchi.sharedwallet.data.DeviceSessionRepository
import com.example.sothuchi.sharedwallet.identity.DeviceIdentityStore
import com.example.sothuchi.sharedwallet.model.DeviceSession
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.textfield.TextInputLayout
import com.google.firebase.auth.FirebaseAuth
import kotlinx.coroutines.launch

class SplashActivity : AppCompatActivity() {

    private lateinit var identityStore: DeviceIdentityStore
    private val sessionRepository = DeviceSessionRepository()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        FirestoreOfflineConfig.enableOfflinePersistence()
        identityStore = DeviceIdentityStore(this)
        handleStartupFlow()
    }

    private fun handleStartupFlow() {
        val hasExistingSession = FirebaseAuth.getInstance().currentUser != null
        val isOnline = NetworkUtils.hasInternetConnection(this)

        if (hasExistingSession) {
            if (isOnline) {
                ensureDeviceSessionThenNavigate(isOfflineMode = false)
            } else {
                showOfflineModeDialogForExistingSession()
            }
        } else {
            if (isOnline) {
                navigateToMain(isOfflineMode = false)
            } else {
                showNetworkRequiredDialogForLogin()
            }
        }
    }

    private fun showOfflineModeDialogForExistingSession() {
        MaterialAlertDialogBuilder(this)
            .setTitle("Không có kết nối mạng")
            .setMessage("Thiết bị chưa kết nối Internet. Dữ liệu sẽ chỉ được lưu cục bộ và không đồng bộ lên cơ sở dữ liệu. Bạn có muốn tiếp tục sử dụng ngoại tuyến không?")
            .setIcon(R.drawable.ic_network_off)
            .setCancelable(false)
            .setPositiveButton("Tiếp tục ngoại tuyến") { dialog, _ ->
                dialog.dismiss()
                ensureDeviceSessionThenNavigate(isOfflineMode = true)
            }
            .setNegativeButton("Thoát App") { _, _ ->
                finishAffinity()
            }
            .show()
    }

    private fun ensureDeviceSessionThenNavigate(isOfflineMode: Boolean) {
        val nickname = identityStore.getNickname()
        val isOnline = NetworkUtils.hasInternetConnection(this)

        if (nickname.isNotBlank()) {
            if (isOnline) {
                lifecycleScope.launch {
                    upsertCurrentDeviceSession(nickname)
                    navigateToMain(isOfflineMode)
                }
            } else {
                navigateToMain(isOfflineMode)
            }
            return
        }

        if (!isOnline) {
            showNetworkRequiredForNicknameDialog(isOfflineMode)
            return
        }

        showNicknameDialog(isOfflineMode)
    }

    private fun showNetworkRequiredForNicknameDialog(isOfflineMode: Boolean) {
        MaterialAlertDialogBuilder(this)
            .setTitle("Yêu cầu kết nối mạng")
            .setMessage("Bạn cần có mạng để thiết lập tên thiết bị lần đầu. Vui lòng kết nối mạng và thử lại.")
            .setIcon(R.drawable.ic_network_off)
            .setCancelable(false)
            .setPositiveButton("Thử lại") { dialog, _ ->
                dialog.dismiss()
                ensureDeviceSessionThenNavigate(isOfflineMode)
            }
            .setNegativeButton("Thoát") { _, _ -> finishAffinity() }
            .show()
    }

    private fun showNicknameDialog(isOfflineMode: Boolean) {
        val container = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(48, 20, 48, 8)
        }

        val inputLayout = TextInputLayout(this).apply {
            hint = "Nhập nickname"
        }
        val input = EditText(this)
        inputLayout.addView(input)

        val progressBar = ProgressBar(this).apply {
            visibility = View.GONE
        }

        container.addView(inputLayout)
        container.addView(progressBar)

        val dialog = MaterialAlertDialogBuilder(this)
            .setTitle("Thiết lập nickname thiết bị")
            .setCancelable(false)
            .setView(container)
            .setPositiveButton("Lưu", null)
            .setNegativeButton("Thoát") { _, _ -> finishAffinity() }
            .create()

        dialog.setOnShowListener {
            val saveButton = dialog.getButton(androidx.appcompat.app.AlertDialog.BUTTON_POSITIVE)
            saveButton.setOnClickListener {
                val nickname = input.text?.toString()?.trim().orEmpty()
                if (nickname.isBlank()) {
                    inputLayout.error = "Nickname không được để trống"
                    return@setOnClickListener
                }

                inputLayout.error = null
                setDialogLoadingState(input, saveButton, progressBar, true)

                lifecycleScope.launch {
                    try {
                        val deviceId = identityStore.getDeviceId()
                        val isTaken = sessionRepository.isNicknameTakenByAnotherDevice(deviceId, nickname)
                        if (isTaken) {
                            inputLayout.error = "Nickname đã được thiết bị khác sử dụng"
                            setDialogLoadingState(input, saveButton, progressBar, false)
                            return@launch
                        }

                        identityStore.saveNickname(nickname)
                        upsertCurrentDeviceSession(nickname)
                        dialog.dismiss()
                        navigateToMain(isOfflineMode)
                    } catch (e: Exception) {
                        inputLayout.error = e.message ?: "Không thể xác thực nickname"
                        setDialogLoadingState(input, saveButton, progressBar, false)
                    }
                }
            }
        }

        dialog.show()
    }

    private suspend fun upsertCurrentDeviceSession(nickname: String) {
        val session = DeviceSession(
            deviceId = identityStore.getDeviceId(),
            nickname = nickname,
            deviceName = identityStore.getDeviceName(),
            lastActive = System.currentTimeMillis(),
        )
        sessionRepository.upsertSession(session)
    }

    private fun setDialogLoadingState(
        input: EditText,
        positiveButton: View,
        progressBar: ProgressBar,
        isLoading: Boolean,
    ) {
        input.isEnabled = !isLoading
        positiveButton.isEnabled = !isLoading
        progressBar.visibility = if (isLoading) View.VISIBLE else View.GONE
    }

    private fun showNetworkRequiredDialogForLogin() {
        MaterialAlertDialogBuilder(this)
            .setTitle("Yêu cầu kết nối mạng")
            .setMessage("Bạn cần có kết nối Internet để đăng nhập hoặc xác thực tài khoản lần đầu. Vui lòng kết nối mạng và thử lại.")
            .setIcon(R.drawable.ic_network_off)
            .setCancelable(false)
            .setPositiveButton("Thử lại") { dialog, _ ->
                dialog.dismiss()
                handleStartupFlow()
            }
            .setNegativeButton("Thoát") { _, _ ->
                finishAffinity()
            }
            .show()
    }

    private fun navigateToMain(isOfflineMode: Boolean) {
        val intent = Intent(this, MainActivity::class.java).apply {
            putExtra(EXTRA_IS_OFFLINE_MODE, isOfflineMode)
        }
        startActivity(intent)
        finish()
    }

    companion object {
        const val EXTRA_IS_OFFLINE_MODE = "isOfflineMode"
    }
}
