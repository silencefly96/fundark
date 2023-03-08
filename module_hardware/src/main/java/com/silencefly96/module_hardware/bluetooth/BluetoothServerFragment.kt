package com.silencefly96.module_hardware.bluetooth

import android.app.AlertDialog
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothServerSocket
import android.bluetooth.BluetoothSocket
import android.content.Context
import android.text.TextUtils
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.DividerItemDecoration
import androidx.recyclerview.widget.LinearLayoutManager
import com.silencefly96.module_base.base.BaseFragment
import com.silencefly96.module_base.base.BaseRecyclerAdapter
import com.silencefly96.module_base.base.ViewHolder
import com.silencefly96.module_hardware.R
import com.silencefly96.module_hardware.databinding.FragmentBluetoothServerBinding
import java.io.IOException
import java.io.InputStream
import java.util.*
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.Executors


class BluetoothServerFragment: BaseFragment() {

    companion object{
        const val MY_UUID = "00001101-0000-1000-8000-00805F9B34FB"
    }

    private var _binding: FragmentBluetoothServerBinding? = null
    private val binding get() = _binding!!

    //线程池
    private val threadPool = Executors.newCachedThreadPool()

    private var mBluetoothAdapter: BluetoothAdapter? = null

    private lateinit var mServerSocket: BluetoothServerSocket

    //选中下标，-1表示全部
    private var mSelectIndex = -1

    private lateinit var mAdapter: BaseRecyclerAdapter<BluetoothSocket>
    private val mBluetoothSockets: MutableList<BluetoothSocket> = CopyOnWriteArrayList()

    //服务端线程
    private inner class ServerThread(remotePeer: BluetoothSocket) : Runnable {
        private val remotePeer: BluetoothSocket?
        private val deviceName: String
        override fun run() {
            try {
                var inputStream: InputStream
                while (remotePeer != null && remotePeer.isConnected) {

                    //简单写写
                    inputStream = remotePeer.inputStream
                    val buffer = ByteArray(2560)
                    val len = inputStream.read(buffer, 0, 2560)
                    if (len <= 0) {
                        Thread.sleep(1000)
                        continue
                    }

                    //打印接收消息
                    activity!!.runOnUiThread {
                        binding.receive
                            .append("收到消息->$deviceName:${String(buffer, 0, len)}")
                    }
                }
            } catch (e: IOException) {
                e.printStackTrace()
            } catch (e: Exception) {
                e.printStackTrace()
            } finally {
                try {
                    remotePeer?.close()
                } catch (e: IOException) {
                    e.printStackTrace()
                }
            }
        }

        init {
            this.remotePeer = remotePeer
            deviceName = remotePeer.remoteDevice.name
        }
    }

    override fun bindView(inflater: LayoutInflater, container: ViewGroup?): View {
        _binding = FragmentBluetoothServerBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun doBusiness(context: Context?) {
        mBluetoothAdapter = BluetoothAdapter.getDefaultAdapter()
        binding.receive.text = getString(R.string.wait_client_connect)

        //启动服务端
        mBluetoothAdapter?.let { bluetoothAdapter ->
            threadPool.execute(Thread {
                try {
                    mServerSocket = bluetoothAdapter
                        .listenUsingRfcommWithServiceRecord(tag, UUID.fromString(MY_UUID))
                    while (true) {
                        val remotePeer = mServerSocket.accept()
                        mBluetoothSockets.add(remotePeer)
                        //获取蓝牙设备
                        val device = remotePeer.remoteDevice
                        requireActivity().runOnUiThread {
                            mAdapter.notifyItemChanged(mBluetoothSockets.size - 1)
                            binding.receive.append("连接客户端成功:${device.name}".trimIndent())
                        }

                        //启动线程处理socket连接
                        threadPool.execute(ServerThread(remotePeer))
                    }
                } catch (e: IOException) {
                    e.printStackTrace()
                }
            })
        }

        //列表相关
        binding.recycler.layoutManager = LinearLayoutManager(requireContext())
        binding.recycler.addItemDecoration(
            DividerItemDecoration(requireContext(), DividerItemDecoration.VERTICAL)
        )
        //设置适配器
        mAdapter = object : BaseRecyclerAdapter<BluetoothSocket>(
            android.R.layout.simple_list_item_1, mBluetoothSockets
        ) {
            override fun convertView(viewHolder: ViewHolder?, item: BluetoothSocket, position: Int) {
                viewHolder?.setText(android.R.id.text1, item.remoteDevice.name)
            }
        }
        //设置点击事件
        mAdapter.setOnItemClickListener(object : BaseRecyclerAdapter.ItemClickListener<BluetoothSocket> {
            override fun onItemClick(view: View?, itemObj: BluetoothSocket, position: Int) {
                AlertDialog.Builder(requireContext())
                    .setTitle("客户端操作")
                    .setMessage("是否向${itemObj.remoteDevice.name}发送消息？")
                    .setPositiveButton("是") { _, _ ->
                        mSelectIndex = position
                        binding.select.text = itemObj.remoteDevice.name
                    }
                    .setNeutralButton("断开连接"){ _, _ ->
                        val deviceName: String = itemObj.remoteDevice.name
                        //断开该连接
                        try {
                            itemObj.close()

                            //移除断开设备
                            mBluetoothSockets.remove(itemObj)
                            mAdapter.notifyItemRemoved(mBluetoothSockets.size)
                            requireActivity().runOnUiThread {
                                binding.receive.append("断开连接成功：$deviceName\n")
                            }
                        } catch (e: IOException) {
                            e.printStackTrace()
                            requireActivity().runOnUiThread {
                                binding.receive.append("断开连接失败：$deviceName\n")
                            }
                        }
                    }
                    .setNegativeButton("否", null)
                    .create()
                    .show()
            }
        })
        binding.recycler.adapter = mAdapter

        //选择向全部设备发送消息
        binding.all.setOnClickListener {
            AlertDialog.Builder(requireContext())
                .setTitle("")
                .setMessage("是否向所有设备发送消息？")
                .setPositiveButton(
                    "是"
                ) { _, _ ->
                    mSelectIndex = -1
                    binding.select.text = "全部"
                }
                .setNegativeButton("否", null)
                .create()
                .show()
        }

        //发送消息按钮
        binding.send.setOnClickListener {
            val data = binding.input.text.toString()
            if (!TextUtils.isEmpty(data)) {
                if (mSelectIndex < 0) {
                    //遍历向所有客户端发送数据
                    for (socket in mBluetoothSockets) {
                        sendDataToClient(socket, data)
                    }
                } else {
                    //向单个蓝牙设备发送数据
                    val socket = mBluetoothSockets[mSelectIndex]
                    sendDataToClient(socket, data)
                }
            }
        }
    }


    //向指定客户端socket发送消息
    private fun sendDataToClient(socket: BluetoothSocket, data: String) {
        val deviceName = socket.remoteDevice.name
        if (socket.isConnected) {
            //异步线程发送
            threadPool.execute {
                try {
                    socket.outputStream.write(data.toByteArray())
                    requireActivity().runOnUiThread {
                        binding.receive.append("发送消息->$deviceName:$data")
                    }
                } catch (e: IOException) {
                    e.printStackTrace()
                    requireActivity().runOnUiThread {
                        binding.receive
                            .append("发送消息->$deviceName:失败！${e.message}".trimIndent())
                    }
                }
            }
        } else {
            requireActivity().runOnUiThread {
                binding.receive.append("发送消息->$deviceName:失败！\n")
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        try {
            for (socket in mBluetoothSockets) {
                socket.close()
            }
            mServerSocket.close()
        } catch (e: IOException) {
            e.printStackTrace()
        }
    }
}