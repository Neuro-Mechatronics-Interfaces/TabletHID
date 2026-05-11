package com.tablet.hid.ui.community

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.os.bundleOf
import androidx.fragment.app.setFragmentResultListener
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.snackbar.Snackbar
import com.tablet.hid.R
import com.tablet.hid.databinding.FragmentBrowseBinding
import com.tablet.hid.model.CommunityConfigRecord
import kotlinx.coroutines.launch

class BrowseFragment : Fragment() {

    private var _binding: FragmentBrowseBinding? = null
    private val binding get() = _binding!!

    private val viewModel: CommunityViewModel by viewModels(ownerProducer = { requireParentFragment() })

    private lateinit var adapter: ConfigListAdapter

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?,
    ): View {
        _binding = FragmentBrowseBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        // ── Import result feedback ──────────────────────────────────────────
        childFragmentManager.setFragmentResultListener(
            ImportSheet.REQUEST_APPLY, viewLifecycleOwner
        ) { _, result ->
            val profileName = result.getString(ImportSheet.KEY_PROFILE) ?: return@setFragmentResultListener
            Snackbar.make(binding.root, getString(R.string.community_applied_snackbar, profileName), Snackbar.LENGTH_SHORT).show()
        }

        // ── RecyclerView setup ──────────────────────────────────────────────
        adapter = ConfigListAdapter { record -> showImportSheet(record) }
        binding.browseRecyclerView.layoutManager = LinearLayoutManager(requireContext())
        binding.browseRecyclerView.adapter = adapter

        // ── Mode filter chips ───────────────────────────────────────────────
        binding.chipGroupMode.setOnCheckedStateChangeListener { _, checkedIds ->
            val mode = when (checkedIds.firstOrNull()) {
                R.id.chipModeGamepad -> "gamepad"
                R.id.chipModeTouchMouse -> "touch_mouse"
                else -> null
            }
            viewModel.setFilterMode(mode)
        }

        // ── Platform filter chips ───────────────────────────────────────────
        binding.chipGroupPlatform.setOnCheckedStateChangeListener { _, checkedIds ->
            val platform = when (checkedIds.firstOrNull()) {
                R.id.chipPlatformAndroid -> "android"
                R.id.chipPlatformIos -> "ios"
                else -> null
            }
            viewModel.setFilterPlatform(platform)
        }

        // ── Sort toggle button ──────────────────────────────────────────────
        var sortIsRecent = true
        binding.btnSortToggle.setOnClickListener {
            sortIsRecent = !sortIsRecent
            val order = if (sortIsRecent) "recent" else "popular"
            binding.btnSortToggle.text = getString(
                if (sortIsRecent) R.string.community_sort_recent else R.string.community_sort_popular
            )
            viewModel.setSortOrder(order)
        }

        // ── Pull-to-refresh ─────────────────────────────────────────────────
        binding.browseSwipeRefresh.setOnRefreshListener {
            viewModel.refresh()
        }

        // ── Observe state ───────────────────────────────────────────────────
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.uiState.collect { state ->
                    adapter.submitList(state.configs)
                    binding.browseSwipeRefresh.isRefreshing = state.isLoading
                    state.error?.let { msg ->
                        Snackbar.make(binding.root, msg, Snackbar.LENGTH_LONG).show()
                    }
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        viewModel.syncIfStale()
    }

    private fun showImportSheet(record: CommunityConfigRecord) {
        val sheet = ImportSheet()
        sheet.arguments = bundleOf(ImportSheet.ARG_RECORD_JSON to recordToJson(record))
        sheet.show(childFragmentManager, "import_sheet")
    }

    private fun recordToJson(record: CommunityConfigRecord): String {
        val obj = org.json.JSONObject().apply {
            put("id", record.id)
            put("schemaVersion", record.schemaVersion)
            put("platform", record.platform)
            put("mode", record.mode)
            put("profileName", record.profileName)
            put("description", record.description ?: "")
            put("tags", org.json.JSONArray(record.tags))
            put("category", record.category ?: "")
            put("appVersion", record.appVersion ?: "")
            put("configJson", record.configJson)
            put("uploadedAt", record.uploadedAt)
            put("downloadCount", record.downloadCount)
            put("deviceName", record.deviceName ?: "")
            put("deviceHwId", record.deviceHwId ?: "")
            put("deviceOsVersion", record.deviceOsVersion ?: "")
            put("deviceOsApiLevel", record.deviceOsApiLevel ?: 0)
            put("deviceScreenWidthPx", record.deviceScreenWidthPx ?: 0)
            put("deviceScreenHeightPx", record.deviceScreenHeightPx ?: 0)
            put("deviceScreenDensityDpi", record.deviceScreenDensityDpi ?: 0)
            put("deviceScreenDiagonalIn", record.deviceScreenDiagonalIn ?: 0f)
        }
        return obj.toString()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}

class ConfigListAdapter(
    private val onItemClick: (CommunityConfigRecord) -> Unit,
) : androidx.recyclerview.widget.ListAdapter<CommunityConfigRecord, ConfigListAdapter.ViewHolder>(
    object : androidx.recyclerview.widget.DiffUtil.ItemCallback<CommunityConfigRecord>() {
        override fun areItemsTheSame(a: CommunityConfigRecord, b: CommunityConfigRecord) = a.id == b.id
        override fun areContentsTheSame(a: CommunityConfigRecord, b: CommunityConfigRecord) = a == b
    }
) {
    inner class ViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val binding = com.tablet.hid.databinding.ItemConfigBinding.bind(itemView)

        fun bind(record: CommunityConfigRecord) {
            binding.itemProfileName.text = record.profileName
            binding.itemDeviceLine.text = record.deviceName ?: ""
            val osVer = record.deviceOsVersion ?: ""
            val diag = record.deviceScreenDiagonalIn
            binding.itemOsLine.text = if (diag != null && diag > 0f) {
                "$osVer · ${"%.1f".format(diag)}\""
            } else {
                osVer
            }
            binding.itemDownloadCount.text = "↓ ${record.downloadCount}"

            val iconRes = if (record.mode == "gamepad") R.drawable.ic_gamepad else R.drawable.ic_mouse
            binding.itemModeIcon.setImageResource(iconRes)

            itemView.setOnClickListener { onItemClick(record) }
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val v = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_config, parent, false)
        return ViewHolder(v)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.bind(getItem(position))
    }
}
