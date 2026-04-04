package com.noncey.android.ui.sms

import android.Manifest
import android.content.pm.PackageManager
import android.database.Cursor
import android.net.Uri
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.LinearLayoutManager
import com.noncey.android.NonceyApp
import com.noncey.android.databinding.FragmentSmsListBinding
import java.time.Instant
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter

data class SmsItem(
    val id: Long,
    val sender: String,
    val body: String,
    val dateMs: Long,
    val receivedAt: String
)

class SmsListFragment : Fragment() {

    private var _binding: FragmentSmsListBinding? = null
    private val binding get() = _binding!!
    private lateinit var adapter: SmsAdapter

    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) loadSms() else showPermissionHint()
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentSmsListBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        adapter = SmsAdapter { item -> onForwardClicked(item) }
        binding.recyclerView.layoutManager = LinearLayoutManager(requireContext())
        binding.recyclerView.adapter       = adapter

        binding.swipeRefresh.setOnRefreshListener {
            loadSms()
            binding.swipeRefresh.isRefreshing = false
        }

        checkPermissionAndLoad()
    }

    override fun onResume() {
        super.onResume()
        checkPermissionAndLoad()
    }

    private fun checkPermissionAndLoad() {
        when {
            ContextCompat.checkSelfPermission(
                requireContext(), Manifest.permission.READ_SMS
            ) == PackageManager.PERMISSION_GRANTED -> loadSms()

            shouldShowRequestPermissionRationale(Manifest.permission.READ_SMS) ->
                showPermissionHint()

            else -> permissionLauncher.launch(Manifest.permission.READ_SMS)
        }
    }

    private fun loadSms() {
        val uri    = Uri.parse("content://sms/inbox")
        val cursor: Cursor? = requireContext().contentResolver.query(
            uri,
            arrayOf("_id", "address", "body", "date"),
            null, null,
            "date DESC"
        )
        val items = mutableListOf<SmsItem>()
        cursor?.use {
            val idxId   = it.getColumnIndex("_id")
            val idxAddr = it.getColumnIndex("address")
            val idxBody = it.getColumnIndex("body")
            val idxDate = it.getColumnIndex("date")
            while (it.moveToNext()) {
                val dateMs = it.getLong(idxDate)
                val receivedAt = Instant.ofEpochMilli(dateMs)
                    .atOffset(ZoneOffset.UTC)
                    .format(DateTimeFormatter.ISO_OFFSET_DATE_TIME)
                items += SmsItem(
                    id         = it.getLong(idxId),
                    sender     = it.getString(idxAddr) ?: "(unknown)",
                    body       = it.getString(idxBody) ?: "",
                    dateMs     = dateMs,
                    receivedAt = receivedAt
                )
            }
        }
        adapter.submitList(items)
        binding.emptyText.visibility = if (items.isEmpty()) View.VISIBLE else View.GONE
    }

    private fun showPermissionHint() {
        binding.emptyText.visibility = View.VISIBLE
        binding.emptyText.text       = "READ_SMS permission is required to list messages."
    }

    private fun onForwardClicked(item: SmsItem) {
        val app = requireContext().applicationContext as NonceyApp
        val smsConfigs = app.cache.allConfigs().filter { it.activated }

        ForwardDialog.show(
            fragmentManager  = parentFragmentManager,
            smsItem          = item,
            activeSmsConfigs = smsConfigs
        )
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
