package com.tracy.industry.page.adapter

import android.annotation.SuppressLint
import android.bluetooth.BluetoothDevice
import android.view.LayoutInflater
import android.view.ViewGroup
import com.tracy.industry.base.BaseBindingAdapterKt
import com.tracy.industry.databinding.AdapterBluetoothItemBinding

/**
 * Des:
 * Author:LiuBao
 * Time:2026/3/9 20:48
 */
class BluetoothAdapter: BaseBindingAdapterKt<AdapterBluetoothItemBinding, BluetoothDevice>() {
    override fun onCreateBind(
        inflater: LayoutInflater,
        parent: ViewGroup
    ): AdapterBluetoothItemBinding = AdapterBluetoothItemBinding.inflate(inflater, parent, false)

    @SuppressLint("MissingPermission")
    override fun onBindItem(binding: AdapterBluetoothItemBinding, position: Int, bean: BluetoothDevice) {
        binding.tvName.text = "${bean.name ?: "未知"}-${bean.address ?: ""}"
    }
}