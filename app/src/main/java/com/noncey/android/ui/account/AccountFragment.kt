package com.noncey.android.ui.account

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.appcompat.app.AppCompatActivity
import androidx.fragment.app.Fragment
import androidx.viewpager2.adapter.FragmentStateAdapter
import androidx.viewpager2.widget.ViewPager2
import com.google.android.material.tabs.TabLayoutMediator
import com.noncey.android.databinding.FragmentAccountBinding
import com.noncey.android.ui.configs.ConfigsFragment

class AccountFragment : Fragment() {

    private var _binding: FragmentAccountBinding? = null
    private val binding get() = _binding!!

    private val subTitleFull = listOf("Server", "Settings", "Provider Configurations")

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentAccountBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        binding.viewPager.adapter = AccountPagerAdapter(this)
        TabLayoutMediator(binding.tabLayout, binding.viewPager) { tab, pos ->
            tab.text = when (pos) { 0 -> "Server"; 1 -> "Settings"; else -> "Providers" }
        }.attach()

        binding.viewPager.registerOnPageChangeCallback(object : ViewPager2.OnPageChangeCallback() {
            override fun onPageSelected(position: Int) = updateTitle(position)
        })
    }

    override fun onResume() {
        super.onResume()
        updateTitle(binding.viewPager.currentItem)
    }

    private fun updateTitle(position: Int) {
        val sub = subTitleFull.getOrElse(position) { "Account" }
        // M2 = 3 spaces, same as MainActivity's "   $tabName" pattern
        (requireActivity() as? AppCompatActivity)?.supportActionBar?.title = "   Account/$sub"
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}

private class AccountPagerAdapter(fragment: Fragment) : FragmentStateAdapter(fragment) {
    override fun getItemCount() = 3
    override fun createFragment(pos: Int): Fragment = when (pos) {
        0 -> ServerFragment()
        1 -> AccountSettingsFragment()
        else -> ConfigsFragment()
    }
}
