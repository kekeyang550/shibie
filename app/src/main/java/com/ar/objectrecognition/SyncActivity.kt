package com.ar.objectrecognition

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.View
import android.widget.EditText
import android.widget.ProgressBar
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.ar.objectrecognition.databinding.ActivitySyncBinding
import com.ar.objectrecognition.manager.SyncManager

class SyncActivity : AppCompatActivity() {

    private lateinit var binding: ActivitySyncBinding
    private lateinit var syncManager: SyncManager

    companion object {
        private const val REQUEST_MODEL_FILE = 1
        private const val REQUEST_CONFIG_FILE = 2
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivitySyncBinding.inflate(layoutInflater)
        setContentView(binding.root)

        syncManager = SyncManager(this)

        setupListeners()
    }

    private fun setupListeners() {
        binding.btnSyncFromServer.setOnClickListener {
            syncFromServer()
        }

        binding.btnSyncFromLocal.setOnClickListener {
            selectModelFile()
        }

        binding.btnBack.setOnClickListener {
            finish()
        }
    }

    private fun syncFromServer() {
        val serverUrl = binding.etServerUrl.text.toString().trim()
        if (serverUrl.isEmpty()) {
            Toast.makeText(this, "请输入服务器地址", Toast.LENGTH_SHORT).show()
            return
        }

        showProgress(true)

        syncManager.syncFromServer(serverUrl, object : SyncManager.SyncCallback {
            override fun onSuccess() {
                showProgress(false)
                Toast.makeText(this@SyncActivity, "同步成功！", Toast.LENGTH_SHORT).show()
                setResult(RESULT_OK)
                finish()
            }

            override fun onFailure(error: String) {
                showProgress(false)
                Toast.makeText(this@SyncActivity, error, Toast.LENGTH_SHORT).show()
            }

            override fun onProgress(progress: Int) {
                runOnUiThread {
                    binding.progressBar.progress = progress
                    binding.tvProgress.text = "同步中... $progress%"
                }
            }
        })
    }

    private fun selectModelFile() {
        val intent = Intent(Intent.ACTION_GET_CONTENT)
        intent.type = "*/*"
        intent.putExtra(Intent.EXTRA_MIME_TYPES, arrayOf("application/octet-stream", "model/tflite"))
        startActivityForResult(intent, REQUEST_MODEL_FILE)
    }

    private fun selectConfigFile(modelUri: Uri) {
        val intent = Intent(Intent.ACTION_GET_CONTENT)
        intent.type = "application/json"
        startActivityForResult(Intent.createChooser(intent, "选择配置文件"), REQUEST_CONFIG_FILE)
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (resultCode == RESULT_OK && data != null) {
            when (requestCode) {
                REQUEST_MODEL_FILE -> {
                    val modelUri = data.data
                    modelUri?.let {
                        selectConfigFile(it)
                    }
                }
                REQUEST_CONFIG_FILE -> {
                    val configUri = data.data
                    val modelUri = intent.getParcelableExtra<Uri>("modelUri")
                    if (modelUri != null && configUri != null) {
                        syncFromLocal(modelUri, configUri)
                    }
                }
            }
        }
    }

    private fun syncFromLocal(modelUri: Uri, configUri: Uri) {
        showProgress(true)

        syncManager.syncFromLocal(modelUri, configUri, object : SyncManager.SyncCallback {
            override fun onSuccess() {
                showProgress(false)
                Toast.makeText(this@SyncActivity, "同步成功！", Toast.LENGTH_SHORT).show()
                setResult(RESULT_OK)
                finish()
            }

            override fun onFailure(error: String) {
                showProgress(false)
                Toast.makeText(this@SyncActivity, error, Toast.LENGTH_SHORT).show()
            }

            override fun onProgress(progress: Int) {
                runOnUiThread {
                    binding.progressBar.progress = progress
                    binding.tvProgress.text = "同步中... $progress%"
                }
            }
        })
    }

    private fun showProgress(show: Boolean) {
        if (show) {
            binding.progressContainer.visibility = View.VISIBLE
            binding.contentContainer.visibility = View.GONE
        } else {
            binding.progressContainer.visibility = View.GONE
            binding.contentContainer.visibility = View.VISIBLE
        }
    }
}
