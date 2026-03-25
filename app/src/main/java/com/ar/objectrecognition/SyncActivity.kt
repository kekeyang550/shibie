package com.ar.objectrecognition

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import com.ar.objectrecognition.databinding.ActivitySyncBinding
import com.ar.objectrecognition.manager.SyncManager

class SyncActivity : AppCompatActivity() {

    private lateinit var binding: ActivitySyncBinding
    private lateinit var syncManager: SyncManager
    private var pendingModelUri: Uri? = null

    private val selectModelFileLauncher = registerForActivityResult(
        ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        uri?.let {
            pendingModelUri = it
            selectConfigFile()
        }
    }

    private val selectConfigFileLauncher = registerForActivityResult(
        ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        uri?.let { configUri ->
            pendingModelUri?.let { modelUri ->
                syncFromLocal(modelUri, configUri)
            }
        }
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
            Toast.makeText(this, R.string.please_enter_server_url, Toast.LENGTH_SHORT).show()
            return
        }

        showProgress(true)

        syncManager.syncFromServer(serverUrl, object : SyncManager.SyncCallback {
            override fun onSuccess() {
                showProgress(false)
                Toast.makeText(this@SyncActivity, R.string.sync_success, Toast.LENGTH_SHORT).show()
                setResult(RESULT_OK)
                finish()
            }

            override fun onFailure(error: String) {
                showProgress(false)
                Toast.makeText(this@SyncActivity, String.format(getString(R.string.sync_failed), error), Toast.LENGTH_SHORT).show()
            }

            override fun onProgress(progress: Int) {
                runOnUiThread {
                    binding.progressBar.progress = progress
                    binding.tvProgress.text = String.format(getString(R.string.syncing), progress)
                }
            }
        })
    }

    private fun selectModelFile() {
        selectModelFileLauncher.launch("*/*")
    }

    private fun selectConfigFile() {
        selectConfigFileLauncher.launch("application/json")
    }

    private fun syncFromLocal(modelUri: Uri, configUri: Uri) {
        showProgress(true)

        syncManager.syncFromLocal(modelUri, configUri, object : SyncManager.SyncCallback {
            override fun onSuccess() {
                showProgress(false)
                Toast.makeText(this@SyncActivity, R.string.sync_success, Toast.LENGTH_SHORT).show()
                setResult(RESULT_OK)
                finish()
            }

            override fun onFailure(error: String) {
                showProgress(false)
                Toast.makeText(this@SyncActivity, String.format(getString(R.string.sync_failed), error), Toast.LENGTH_SHORT).show()
            }

            override fun onProgress(progress: Int) {
                runOnUiThread {
                    binding.progressBar.progress = progress
                    binding.tvProgress.text = String.format(getString(R.string.syncing), progress)
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
