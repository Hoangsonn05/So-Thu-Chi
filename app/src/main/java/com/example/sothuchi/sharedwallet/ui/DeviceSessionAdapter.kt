package com.example.sothuchi.sharedwallet.ui

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.example.sothuchi.R
import com.example.sothuchi.sharedwallet.model.DeviceSession

class DeviceSessionAdapter(
    private val currentDeviceId: String,
) : ListAdapter<DeviceSession, DeviceSessionAdapter.DeviceViewHolder>(Diff) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): DeviceViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_device_session, parent, false)
        return DeviceViewHolder(view)
    }

    override fun onBindViewHolder(holder: DeviceViewHolder, position: Int) {
        holder.bind(getItem(position), currentDeviceId)
    }

    class DeviceViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val tvNickname: TextView = itemView.findViewById(R.id.tv_device_nickname)
        private val tvDeviceName: TextView = itemView.findViewById(R.id.tv_device_name)
        private val tvCurrent: TextView = itemView.findViewById(R.id.tv_current_device)

        fun bind(item: DeviceSession, currentDeviceId: String) {
            val nickname = item.nickname.takeIf { it.isNotBlank() } ?: "Người dùng ẩn danh"
            val deviceName = item.deviceName.takeIf { it.isNotBlank() } ?: "Không rõ thiết bị"
            tvNickname.text = nickname
            tvDeviceName.text = deviceName
            tvCurrent.visibility = if (item.deviceId == currentDeviceId) View.VISIBLE else View.GONE
        }
    }

    private object Diff : DiffUtil.ItemCallback<DeviceSession>() {
        override fun areItemsTheSame(oldItem: DeviceSession, newItem: DeviceSession): Boolean {
            return oldItem.deviceId == newItem.deviceId
        }

        override fun areContentsTheSame(oldItem: DeviceSession, newItem: DeviceSession): Boolean {
            return oldItem == newItem
        }
    }
}
