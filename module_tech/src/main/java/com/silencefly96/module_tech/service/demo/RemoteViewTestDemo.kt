@file:Suppress("unused")

package com.silencefly96.module_tech.service.demo

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.silencefly96.module_base.base.BaseFragment
import com.silencefly96.module_base.base.BaseRecyclerAdapter
import com.silencefly96.module_base.base.ViewHolder
import com.silencefly96.module_tech.R
import com.silencefly96.module_tech.databinding.FragmentRemoteTestBinding
import com.silencefly96.module_tech.service.remote_view.AudioPlayerManager
import com.silencefly96.module_tech.service.remote_view.audio.AudioPlayer
import com.silencefly96.module_tech.service.remote_view.audio.AudioLoader

class RemoteViewTestDemo: BaseFragment() {

    private var _binding: FragmentRemoteTestBinding? = null
    private val binding get() = _binding!!

    // 数据
    private val mData = arrayListOf<AudioLoader.Audio>()

    //
    private lateinit var mAudioPlayerManager: AudioPlayerManager

    // 适配器
    private val adapter =
        object: BaseRecyclerAdapter<AudioLoader.Audio>(R.layout.item_main, mData) {
        override fun convertView(viewHolder: ViewHolder?, item: AudioLoader.Audio, position: Int) {
            viewHolder?.setText(R.id.title, item.title)
            viewHolder?.setText(R.id.desc, item.duration.toString())
        }
    }

    // 播放器
    private val audioPlayer: AudioPlayer by lazy {
        AudioPlayer(requireContext())
    }

    override fun bindView(inflater: LayoutInflater, container: ViewGroup?): View {
        _binding = FragmentRemoteTestBinding.inflate(inflater, container, false)
        return binding.root
    }

    @SuppressLint("SetTextI18n")
    override fun doBusiness(context: Context?) {
        // 设置列表
        binding.recycler.layoutManager =
            LinearLayoutManager(context, RecyclerView.VERTICAL, false)
        binding.recycler.adapter = adapter
        adapter.setOnItemClickListener(
            object : BaseRecyclerAdapter.ItemClickListener<AudioLoader.Audio> {
                override fun onItemClick(view: View?, itemObj: AudioLoader.Audio, position: Int) {
                    val intent = Intent(AudioPlayer.ACTION_PLAY)
                    intent.putExtra("position", position)
                    intent.putExtra("audio", itemObj)
                    requireContext().sendBroadcast(intent)
                    Log.d("TAG", "onItemClick: $itemObj")
                }
            }
        )

        // 使用协程
        AudioLoader.loadAudioByMediaStoreCoroutines(
            requireContext(),
            object : AudioLoader.OnAudioPrepared {
                override fun onAudioPrepared(result: List<AudioLoader.Audio>?) {
                    result?.let {
                        mData.addAll(it)
                        adapter.notifyItemRangeChanged(0, mData.size)
                    }
                }
            },
            lifecycle)

        mAudioPlayerManager = AudioPlayerManager(requireContext())

        // 打开前台通知，里面有播放器
        binding.notiBtn.setOnClickListener {
            mAudioPlayerManager.startForegroundServiceFromForeground()
        }

    }

    override fun onDestroyView() {
        super.onDestroyView()
        mAudioPlayerManager.cancelForegroundServiceFromForeground()
        _binding = null
    }
}