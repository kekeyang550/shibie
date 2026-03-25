package com.ar.objectrecognition

import android.content.Intent
import android.os.Bundle
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.ar.objectrecognition.databinding.ActivityLogViewerBinding
import com.ar.objectrecognition.manager.LogEntry
import com.ar.objectrecognition.manager.LogLevel
import com.ar.objectrecognition.manager.LogManager
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class LogViewerActivity : AppCompatActivity() {

    private lateinit var binding: ActivityLogViewerBinding
    private lateinit var logManager: LogManager
    private lateinit var logAdapter: LogAdapter
    private var currentFilter: LogLevel? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityLogViewerBinding.inflate(layoutInflater)
        setContentView(binding.root)

        logManager = LogManager.getInstance(this)

        setupToolbar()
        setupRecyclerView()
        setupFilterSpinner()
        setupButtons()
        loadLogs()
    }

    private fun setupToolbar() {
        setSupportActionBar(binding.toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.title = "日志查看"
    }

    private fun setupRecyclerView() {
        logAdapter = LogAdapter()
        binding.recyclerViewLogs.apply {
            layoutManager = LinearLayoutManager(this@LogViewerActivity)
            adapter = logAdapter
        }
    }

    private fun setupFilterSpinner() {
        val filters = listOf("全部", "DEBUG", "INFO", "WARN", "ERROR")
        val adapter = ArrayAdapter(this, android.R.layout.simple_spinner_item, filters)
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        binding.spinnerFilter.adapter = adapter

        binding.spinnerFilter.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                currentFilter = when (position) {
                    1 -> LogLevel.DEBUG
                    2 -> LogLevel.INFO
                    3 -> LogLevel.WARN
                    4 -> LogLevel.ERROR
                    else -> null
                }
                loadLogs()
            }

            override fun onNothingSelected(parent: AdapterView<*>?) {}
        }
    }

    private fun setupButtons() {
        binding.btnRefresh.setOnClickListener {
            loadLogs()
        }

        binding.btnClear.setOnClickListener {
            clearLogs()
        }

        binding.btnExport.setOnClickListener {
            exportLogs()
        }
    }

    private fun loadLogs() {
        val logs = logManager.getLogs()
        val filteredLogs = if (currentFilter != null) {
            logs.filter { it.level == currentFilter }
        } else {
            logs
        }

        logAdapter.submitList(filteredLogs)
        binding.tvLogCount.text = "共 ${filteredLogs.size} 条日志"

        if (filteredLogs.isEmpty()) {
            binding.tvEmpty.visibility = View.VISIBLE
            binding.recyclerViewLogs.visibility = View.GONE
        } else {
            binding.tvEmpty.visibility = View.GONE
            binding.recyclerViewLogs.visibility = View.VISIBLE
        }
    }

    private fun clearLogs() {
        logManager.clearLogs()
        Toast.makeText(this, "日志已清除", Toast.LENGTH_SHORT).show()
        loadLogs()
    }

    private fun exportLogs() {
        val exportFile = logManager.exportLogs()
        if (exportFile != null) {
            shareLogFile(exportFile)
        } else {
            Toast.makeText(this, "导出失败", Toast.LENGTH_SHORT).show()
        }
    }

    private fun shareLogFile(file: File) {
        val uri = androidx.core.content.FileProvider.getUriForFile(
            this,
            "${packageName}.fileprovider",
            file
        )

        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }

        startActivity(Intent.createChooser(intent, "分享日志"))
    }

    override fun onCreateOptionsMenu(menu: Menu?): Boolean {
        menuInflater.inflate(R.menu.menu_log_viewer, menu)
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            android.R.id.home -> {
                finish()
                true
            }
            R.id.action_auto_scroll -> {
                item.isChecked = !item.isChecked
                logAdapter.setAutoScroll(item.isChecked)
                true
            }
            else -> super.onOptionsItemSelected(item)
        }
    }

    override fun onDestroy() {
        super.onDestroy()
    }
}

// 日志适配器
class LogAdapter : RecyclerView.Adapter<LogAdapter.LogViewHolder>() {

    private var logs = listOf<LogEntry>()
    private var autoScroll = false

    fun submitList(newLogs: List<LogEntry>) {
        logs = newLogs
        notifyDataSetChanged()
    }

    fun setAutoScroll(enabled: Boolean) {
        autoScroll = enabled
    }

    override fun onCreateViewHolder(parent: android.view.ViewGroup, viewType: Int): LogViewHolder {
        val view = android.view.LayoutInflater.from(parent.context)
            .inflate(R.layout.item_log_entry, parent, false)
        return LogViewHolder(view)
    }

    override fun onBindViewHolder(holder: LogViewHolder, position: Int) {
        holder.bind(logs[position])
    }

    override fun getItemCount(): Int = logs.size

    class LogViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val tvTimestamp: android.widget.TextView = itemView.findViewById(R.id.tvTimestamp)
        private val tvLevel: android.widget.TextView = itemView.findViewById(R.id.tvLevel)
        private val tvTag: android.widget.TextView = itemView.findViewById(R.id.tvTag)
        private val tvMessage: android.widget.TextView = itemView.findViewById(R.id.tvMessage)

        fun bind(entry: LogEntry) {
            val dateFormat = SimpleDateFormat("HH:mm:ss.SSS", Locale.getDefault())
            tvTimestamp.text = dateFormat.format(Date(entry.timestamp))
            tvLevel.text = entry.level.name
            tvTag.text = entry.tag
            tvMessage.text = entry.message

            // 根据日志级别设置颜色
            val color = when (entry.level) {
                LogLevel.DEBUG -> android.graphics.Color.GRAY
                LogLevel.INFO -> android.graphics.Color.BLACK
                LogLevel.WARN -> android.graphics.Color.parseColor("#FF9800")
                LogLevel.ERROR -> android.graphics.Color.RED
            }
            tvLevel.setTextColor(color)
        }
    }
}
