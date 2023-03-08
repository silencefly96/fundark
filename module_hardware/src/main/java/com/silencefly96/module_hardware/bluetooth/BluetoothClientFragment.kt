package com.silencefly96.module_hardware.bluetooth

import android.annotation.SuppressLint
import android.app.AlertDialog
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothSocket
import android.content.*
import android.text.TextUtils
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.DividerItemDecoration
import androidx.recyclerview.widget.LinearLayoutManager
import com.silencefly96.module_base.base.BaseFragment
import com.silencefly96.module_base.base.BaseRecyclerAdapter
import com.silencefly96.module_base.base.ViewHolder
import com.silencefly96.module_hardware.databinding.FragmentBluetoothClientBinding
import java.io.IOException
import java.io.InputStream
import java.lang.Exception
import java.util.*
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.Executors

class BluetoothClientFragment: BaseFragment() {

    companion object{
        const val MY_UUID = "00001101-0000-1000-8000-00805F9B34FB"
    }

    private var _binding: FragmentBluetoothClientBinding? = null
    private val binding get() = _binding!!

    //线程池,只连接一个吧，不搞这么复杂
    private val threadPool = Executors.newSingleThreadExecutor()

    private var mBluetoothAdapter: BluetoothAdapter? = null

    private var socket: BluetoothSocket? = null

    private var isScanning = false

    // 设备列表
    private lateinit var adapter: BaseRecyclerAdapter<BluetoothDevice>
    private val deviceList: MutableList<BluetoothDevice> = CopyOnWriteArrayList()

    // 要连接的目标蓝牙设备。
    private var targetDeviceName = ""

    // 广播接收发现蓝牙设备
    private var isRegisterReceiver = false
    private val mBroadcastReceiver: BroadcastReceiver = object : BroadcastReceiver() {
        @SuppressLint("SetTextI18n")
        override fun onReceive(context: Context, intent: Intent) {
            when (intent.action) {
                BluetoothAdapter.ACTION_DISCOVERY_STARTED -> {
                    binding.state.text = "searching..."
                    isScanning = true

                    //清空列表
                    val size = deviceList.size
                    deviceList.clear()
                    adapter.notifyItemRangeRemoved(0, size)
                }
                BluetoothAdapter.ACTION_DISCOVERY_FINISHED -> {
                    binding.state.text = "finished"
                    isScanning = false
                }
                BluetoothDevice.ACTION_FOUND -> {
                    //发现一个设备
                    val device =
                        intent.getParcelableExtra<BluetoothDevice>(BluetoothDevice.EXTRA_DEVICE)
                    //设备可能多次被发现
                    if (null != device && !deviceList.contains(device)) {
                        //添加到列表中，更新列表
                        deviceList.add(device)
                        adapter.notifyItemInserted(deviceList.size - 1)

                        //匹配是否是想要的目标
                        val name = device.name
                        if (name != null) Log.d(TAG, "发现设备:$name")
                        if (name != null && name == targetDeviceName) {
                            Log.d(TAG, "发现目标设备，开始线程连接!")
                            //蓝牙搜索是非常消耗系统资源开销的过程，一旦发现了目标感兴趣的设备，可以考虑关闭扫描。
                            //mBluetoothAdapter.cancelDiscovery();
                            connectDevice(device)
                        }
                    }
                }
                else -> { }
            }
        }
    }

    // 发送数据到另一端
    private fun sendDataToServer(data: String) {
        Thread {
            try {
                val os = socket!!.outputStream
                os.write(data.toByteArray())
                os.flush()
                //os.close();
                requireActivity().runOnUiThread {
                    binding.receive.append("发送消息->${socket!!.remoteDevice.name}:$data")
                }
            } catch (e: Exception) {
                e.printStackTrace()
                requireActivity().runOnUiThread {
                    binding.receive.append("发送消息:失败！".trimIndent())
                }
            }
        }.start()
    }

    override fun bindView(inflater: LayoutInflater, container: ViewGroup?): View {
        _binding = FragmentBluetoothClientBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun doBusiness(context: Context?) {
        //蓝牙适配器
        mBluetoothAdapter = BluetoothAdapter.getDefaultAdapter()

        //列表相关
        binding.recycler.layoutManager = LinearLayoutManager(requireContext())
        binding.recycler.addItemDecoration(
            DividerItemDecoration(requireContext(), DividerItemDecoration.VERTICAL)
        )
        //设置适配器
        adapter = object : BaseRecyclerAdapter<BluetoothDevice>(
            android.R.layout.simple_list_item_1, deviceList
        ) {
            override fun convertView(viewHolder: ViewHolder?, item: BluetoothDevice, position: Int) {
                viewHolder?.setText(android.R.id.text1, item.name + ":" + item.address)
            }
        }
        //设置点击事件
        adapter.setOnItemClickListener(object : BaseRecyclerAdapter.ItemClickListener<BluetoothDevice> {
            override fun onItemClick(view: View?, itemObj: BluetoothDevice, position: Int) {
                AlertDialog.Builder(requireContext())
                    .setTitle("")
                    .setMessage("是否开始连接" + itemObj.name + "？")
                    .setPositiveButton("是") { _, _ ->
                        //设置当前设备名
                        targetDeviceName = itemObj.name
                        binding.target.setText(targetDeviceName)
                        //连接蓝牙
                        connectDevice(itemObj)
                    }
                    .setNegativeButton("否", null)
                    .create()
                    .show()
            }
        })
        binding.recycler.adapter = adapter

        //设置蓝牙目标，并开始连接
        binding.set.setOnClickListener {
            val deviceName: String = binding.target.text.toString()
            if (!TextUtils.isEmpty(deviceName)) {
                AlertDialog.Builder(requireContext())
                    .setTitle("")
                    .setMessage("是否开始连接$deviceName？")
                    .setPositiveButton("是") { _, _ ->
                        //先在扫描到的列表里面找
                        var index = -1
                        for (i in deviceList.indices) {
                            val device = deviceList[i]
                            val name = device.name //可能没有name
                            if (!TextUtils.isEmpty(name) && deviceName == name) {
                                index = i
                                break
                            }
                        }
                        if (index >= 0) {
                            //在扫描列表找到了
                            connectDevice(deviceList[index])
                        } else {
                            //扫描设备里面没有，就看已配对和再扫描了
                            connectDevice(deviceName)
                        }
                    }
                    .setNegativeButton("否", null)
                    .create()
                    .show()
            }
        }

        //发送消息
        binding.send.setOnClickListener {
            val data: String = binding.input.text.toString()
            if (!TextUtils.isEmpty(data)) {
                sendDataToServer(data)
            }
        }

        //手动启动扫描
        binding.scan.setOnClickListener {
            if (!isScanning) {
                scanBlueTooth()
            } else {
                stopScan()
            }
        }

        //先在列表中显示已配对过设备
        addPairedDevices()
    }


    //添加已配对过设备
    private fun addPairedDevices() {
        val pairedDevices = mBluetoothAdapter!!.bondedDevices
        deviceList.addAll(pairedDevices)
        adapter.notifyItemRangeInserted(deviceList.size - pairedDevices.size - 1,
            deviceList.size)
    }

    //通过设备名称连接蓝牙
    private fun connectDevice(deviceName: String) {
        //设置目标蓝牙设备名称
        targetDeviceName = deviceName

        //从已连接设备中获取
        val device = getPairedDevices(deviceName)
        if (device == null) {
            //启动蓝牙扫描，成功后匹配targetDeviceName，再连接
            scanBlueTooth()
        } else {
            connectDevice(device)
        }
    }

    private fun scanBlueTooth() {
        //检查蓝牙是否开启
        if (!mBluetoothAdapter!!.isEnabled) {
            AlertDialog.Builder(requireContext())
                .setTitle("开启蓝牙")
                .setMessage("当前设备蓝牙未开启，是否开启蓝牙？")
                .setPositiveButton("是") { _, _ ->
                    mBluetoothAdapter!!.enable()
                    startScan()
                }
                .setNegativeButton("否", null)
                .create()
                .show()
        } else {
            startScan()
        }
    }

    //扫描蓝牙设备
    private fun startScan() {
        // 注册广播接收器，接收蓝牙发现讯息
        if (!isRegisterReceiver) {
            val filter = IntentFilter()

            //开始扫描
            filter.addAction(BluetoothAdapter.ACTION_DISCOVERY_STARTED)
            //结束扫描
            filter.addAction(BluetoothAdapter.ACTION_DISCOVERY_FINISHED)
            //发现设备
            filter.addAction(BluetoothDevice.ACTION_FOUND)
            //状态变化
            filter.addAction(BluetoothDevice.ACTION_BOND_STATE_CHANGED)
            requireContext().registerReceiver(mBroadcastReceiver, filter)
            isRegisterReceiver = true
        }
        if (mBluetoothAdapter!!.startDiscovery()) {
            binding.receive.append("启动蓝牙扫描设备...\n")
            binding.scan.text = "停止"
            isScanning = true
        }
    }

    //停止扫描
    private fun stopScan() {
        if (mBluetoothAdapter!!.isDiscovering) {
            mBluetoothAdapter!!.cancelDiscovery()
            binding.scan.text = "扫描"
            isScanning = false
        }
    }

    //从已连接设备里面找
    private fun getPairedDevices(deviceName: String): BluetoothDevice? {
        // 获得和当前Android已经配对的蓝牙设备。
        val pairedDevices = mBluetoothAdapter!!.bondedDevices
        if (pairedDevices != null && pairedDevices.size > 0) {
            // 遍历
            for (device in pairedDevices) {
                // 把已经取得配对的蓝牙设备名字和地址打印出来。
                Log.d(TAG, device.name + " : " + device.address)
                if (TextUtils.equals(deviceName, device.name)) {
                    binding.receive.append("已配对目标设备 -> $deviceName\n")
                    return device
                }
            }
        }
        return null
    }

    //通过设备连接
    private fun connectDevice(device: BluetoothDevice) {

        //关闭已有socket
        if (null != socket) {
            socket = try {
                socket!!.close()
                null
            } catch (e: Exception) {
                e.printStackTrace()
                binding.receive.append("关闭socket失败！\n")
                showToast("关闭socket失败！")
                return
            }
        }

        //线程池执行,排队等待上一个单线程执行完毕
        threadPool.execute {
            try {
                socket = device.createRfcommSocketToServiceRecord(UUID.fromString(MY_UUID))
                socket!!.connect()
            } catch (e: IOException) {
                e.printStackTrace()
                requireActivity().runOnUiThread { binding.receive.append("创建socket失败！\n") }
            }
            if (socket != null && socket!!.isConnected) {
                requireActivity().runOnUiThread { binding.receive.append("创建socket成功！\n") }
                try {
                    var inputStream: InputStream
                    while (socket != null) {

                        //随便写写
                        inputStream = socket!!.inputStream
                        val buffer = ByteArray(2560)
                        val len = inputStream.read(buffer, 0, 2560)
                        if (len <= 0) {
                            Thread.sleep(1000)
                            continue
                        }
                        requireActivity().runOnUiThread {
                            binding.receive.append(
                                "收到消息->${socket!!.remoteDevice.name}:${String(buffer, 0, len)}")
                        }
                    }
                } catch (e: IOException) {
                    e.printStackTrace()
                    Log.e("TAG", "startConnect: " + e.message)
                    requireActivity().runOnUiThread { binding.receive.append("socket异常！\n") }
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        try {
            socket!!.close()
            if (isRegisterReceiver) {
                requireContext().unregisterReceiver(mBroadcastReceiver)
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
}