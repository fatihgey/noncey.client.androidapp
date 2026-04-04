package com.noncey.android.ui.sms

import android.content.ContentValues
import android.net.Uri
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.fragment.app.FragmentManager
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import com.noncey.android.NonceyApp
import com.noncey.android.data.ConfigCache
import com.noncey.android.databinding.DialogForwardBinding
import com.noncey.android.service.ForwardService
import com.noncey.android.util.PhoneNormalizer
import android.telephony.TelephonyManager

class ForwardDialog : BottomSheetDialogFragment() {

    private var _binding: DialogForwardBinding? = null
    private val binding get() = _binding!!

    private lateinit var item: SmsItem
    private lateinit var configs: List<ConfigCache.SmsConfig>

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = DialogForwardBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        binding.tvSender.text = item.sender
        binding.tvBody.text   = item.body.take(200)

        // Populate config picker
        val configNames = listOf("Let the server choose") + configs.map { it.name }
        val adapter = android.widget.ArrayAdapter(
            requireContext(),
            android.R.layout.simple_list_item_1,
            configNames
        )
        binding.spinnerConfig.adapter = adapter

        binding.btnForward.setOnClickListener {
            val selectedIndex = binding.spinnerConfig.selectedItemPosition
            val configId = if (selectedIndex == 0) null else configs[selectedIndex - 1].id

            // Normalise sender
            val app        = requireContext().applicationContext as NonceyApp
            val tm         = requireContext().getSystemService(TelephonyManager::class.java)
            val simCountry = tm.simCountryIso?.takeIf { it.isNotEmpty() }
            val sender     = PhoneNormalizer.normalize(item.sender, simCountry, app.prefs.countryCallingCode)

            // Mark as Read
            markAsRead(sender)

            ForwardService.enqueueAndStart(
                requireContext(), sender, item.body, item.receivedAt, configId
            )
            Toast.makeText(requireContext(), "Queued for forwarding.", Toast.LENGTH_SHORT).show()
            dismiss()
        }

        binding.btnCancel.setOnClickListener { dismiss() }
    }

    private fun markAsRead(rawSender: String) {
        try {
            val cursor = requireContext().contentResolver.query(
                Uri.parse("content://sms/inbox"),
                arrayOf("_id"),
                "address=? AND body=? AND read=0",
                arrayOf(rawSender, item.body),
                "date DESC"
            ) ?: return
            cursor.use {
                while (it.moveToNext()) {
                    val id = it.getLong(0)
                    val values = ContentValues().apply { put("read", 1) }
                    requireContext().contentResolver.update(
                        Uri.parse("content://sms/$id"), values, null, null
                    )
                }
            }
        } catch (_: Exception) {}
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    companion object {
        private const val TAG = "ForwardDialog"

        fun show(
            fragmentManager: FragmentManager,
            smsItem: SmsItem,
            activeSmsConfigs: List<ConfigCache.SmsConfig>
        ) {
            ForwardDialog().apply {
                item    = smsItem
                configs = activeSmsConfigs
            }.show(fragmentManager, TAG)
        }
    }
}
