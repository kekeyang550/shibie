package com.ar.objectrecognition

import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.ar.objectrecognition.databinding.ActivityVersionInfoBinding
import com.ar.objectrecognition.manager.ModelManager
import com.ar.objectrecognition.manager.VersionManager

class VersionInfoActivity : AppCompatActivity() {

    private lateinit var binding: ActivityVersionInfoBinding
    private lateinit var versionManager: VersionManager
    private lateinit var modelManager: ModelManager

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityVersionInfoBinding.inflate(layoutInflater)
        setContentView(binding.root)

        versionManager = VersionManager(this)
        modelManager = ModelManager(this)

        setupUI()
        loadVersionInfo()
    }

    private fun setupUI() {
        binding.btnBack.setOnClickListener {
            finish()
        }

        binding.btnClearHistory.setOnClickListener {
            clearVersionHistory()
        }

        binding.swipeRefresh.setOnRefreshListener {
            loadVersionInfo()
            binding.swipeRefresh.isRefreshing = false
        }
    }

    private fun loadVersionInfo() {
        // 加载当前版本信息
        val currentVersion = versionManager.getCurrentVersion()
        if (currentVersion != null) {
            binding.tvCurrentVersion.text = "当前版本: ${currentVersion.versionName}"
            binding.tvVersionCode.text = "版本号: ${currentVersion.versionCode}"
            binding.tvReleaseDate.text = "发布时间: ${currentVersion.releaseDate}"
            binding.tvDescription.text = "更新说明: ${currentVersion.description}"
            binding.tvLastSync.text = "上次同步: ${versionManager.formatLastSyncTime()}"
            
            // 显示模型状态
            val hasModel = modelManager.hasModel()
            val hasConfig = modelManager.hasConfig()
            binding.tvModelStatus.text = "模型状态: ${if (hasModel) "已加载" else "未加载"}"
            binding.tvConfigStatus.text = "配置状态: ${if (hasConfig) "已加载" else "未加载"}"
        } else {
            binding.tvCurrentVersion.text = "当前版本: 未同步"
            binding.tvVersionCode.text = "版本号: -"
            binding.tvReleaseDate.text = "发布时间: -"
            binding.tvDescription.text = "更新说明: 请先同步模型"
            binding.tvLastSync.text = "上次同步: 从未"
            binding.tvModelStatus.text = "模型状态: 未加载"
            binding.tvConfigStatus.text = "配置状态: 未加载"
        }

        // 加载版本历史
        loadVersionHistory()
    }

    private fun loadVersionHistory() {
        val history = versionManager.getVersionHistory()
        
        if (history.isEmpty()) {
            binding.rvVersionHistory.visibility = View.GONE
            binding.tvEmptyHistory.visibility = View.VISIBLE
        } else {
            binding.rvVersionHistory.visibility = View.VISIBLE
            binding.tvEmptyHistory.visibility = View.GONE
            
            val adapter = VersionHistoryAdapter(history)
            binding.rvVersionHistory.layoutManager = LinearLayoutManager(this)
            binding.rvVersionHistory.adapter = adapter
        }
    }

    private fun clearVersionHistory() {
        versionManager.clearVersionHistory()
        Toast.makeText(this, "版本历史已清除", Toast.LENGTH_SHORT).show()
        loadVersionInfo()
    }
}

// 版本历史适配器
class VersionHistoryAdapter(private val versions: List<com.ar.objectrecognition.manager.ModelVersion>) : 
    RecyclerView.Adapter<VersionHistoryAdapter.ViewHolder>() {

    class ViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        // 这里可以添加视图绑定
    }

    override fun onCreateViewHolder(parent: android.view.ViewGroup, viewType: Int): ViewHolder {
        val view = android.view.LayoutInflater.from(parent.context)
            .inflate(android.R.layout.simple_list_item_2, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val version = versions[position]
        // 绑定数据到视图
    }

    override fun getItemCount(): Int = versions.size
}
