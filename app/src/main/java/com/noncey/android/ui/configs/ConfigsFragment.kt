package com.noncey.android.ui.configs

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.noncey.android.NonceyApp
import com.noncey.android.data.ConfigCache
import com.noncey.android.databinding.FragmentConfigsBinding
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class ConfigsFragment : Fragment() {

    private var _binding: FragmentConfigsBinding? = null
    private val binding get() = _binding!!
    private lateinit var adapter: ConfigsAdapter

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentConfigsBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        adapter = ConfigsAdapter(
            onActivate   = { config -> toggleActivation(config) },
            onUnsubscribe = { config -> unsubscribe(config) }
        )
        binding.recyclerView.layoutManager = LinearLayoutManager(requireContext())
        binding.recyclerView.adapter       = adapter

        binding.swipeRefresh.setOnRefreshListener { refreshConfigs(force = true) }
        refreshConfigs(force = false)
    }

    override fun onResume() {
        super.onResume()
        refreshConfigs(force = false)
    }

    private fun refreshConfigs(force: Boolean) {
        val app = requireContext().applicationContext as NonceyApp
        lifecycleScope.launch {
            if (force) app.cache.refresh() else app.cache.refreshIfStale()
            val configs = app.cache.allConfigs()
            adapter.submitList(configs)
            binding.emptyText.visibility  = if (configs.isEmpty()) View.VISIBLE else View.GONE
            binding.swipeRefresh.isRefreshing = false
        }
    }

    private fun toggleActivation(config: ConfigCache.SmsConfig) {
        val app = requireContext().applicationContext as NonceyApp
        lifecycleScope.launch {
            try {
                val resp = withContext(Dispatchers.IO) {
                    if (config.activated) app.api.deactivate(config.id)
                    else                  app.api.activate(config.id)
                }
                if (resp.isSuccessful) {
                    val action = if (config.activated) "deactivated" else "activated"
                    app.traceLog.add("Config $action: '${config.name}'")
                    app.cache.invalidate()
                    refreshConfigs(force = true)
                } else {
                    Toast.makeText(requireContext(),
                        "Failed (${resp.code()})", Toast.LENGTH_SHORT).show()
                }
            } catch (e: Exception) {
                Toast.makeText(requireContext(),
                    "Network error: ${e.message}", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun unsubscribe(config: ConfigCache.SmsConfig) {
        val app = requireContext().applicationContext as NonceyApp
        lifecycleScope.launch {
            try {
                val resp = withContext(Dispatchers.IO) { app.api.unsubscribe(config.id) }
                if (resp.isSuccessful) {
                    app.cache.invalidate()
                    refreshConfigs(force = true)
                } else {
                    Toast.makeText(requireContext(),
                        "Failed (${resp.code()})", Toast.LENGTH_SHORT).show()
                }
            } catch (e: Exception) {
                Toast.makeText(requireContext(),
                    "Network error: ${e.message}", Toast.LENGTH_SHORT).show()
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
