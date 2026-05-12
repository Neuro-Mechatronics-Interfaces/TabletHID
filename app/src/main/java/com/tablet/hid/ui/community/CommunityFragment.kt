package com.tablet.hid.ui.community

import android.content.pm.ActivityInfo
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.viewpager2.adapter.FragmentStateAdapter
import com.google.android.material.tabs.TabLayoutMediator
import com.tablet.hid.R
import com.tablet.hid.databinding.FragmentCommunityBinding

class CommunityFragment : Fragment() {

    private var _binding: FragmentCommunityBinding? = null
    private val binding get() = _binding!!

    val viewModel: CommunityViewModel by viewModels()

    private fun isPhone() = resources.configuration.smallestScreenWidthDp < 600

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?,
    ): View {
        _binding = FragmentCommunityBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val adapter = CommunityPagerAdapter(this)
        binding.communityViewPager.adapter = adapter

        TabLayoutMediator(binding.communityTabLayout, binding.communityViewPager) { tab, position ->
            tab.text = when (position) {
                0 -> getString(R.string.community_tab_browse)
                1 -> getString(R.string.community_tab_share)
                else -> ""
            }
        }.attach()
    }

    override fun onResume() {
        super.onResume()
        if (isPhone()) {
            requireActivity().requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
        }
    }

    override fun onPause() {
        super.onPause()
        if (isPhone()) {
            requireActivity().requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    private class CommunityPagerAdapter(fragment: Fragment) : FragmentStateAdapter(fragment) {
        override fun getItemCount(): Int = 2
        override fun createFragment(position: Int): Fragment = when (position) {
            0 -> BrowseFragment()
            1 -> ShareFragment()
            else -> BrowseFragment()
        }
    }
}
