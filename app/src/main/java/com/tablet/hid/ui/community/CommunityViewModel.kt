package com.tablet.hid.ui.community

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.tablet.hid.model.CommunityConfigRecord
import com.tablet.hid.model.CommunityUploadBody
import com.tablet.hid.util.ConfigApiClient
import com.tablet.hid.util.CommunityConfigCache
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class CommunityUiState(
    val isLoading: Boolean = false,
    val error: String? = null,
    val configs: List<CommunityConfigRecord> = emptyList(),
    val filterMode: String? = null,
    val filterPlatform: String? = null,
    val filterCategory: String? = null,
    val sortOrder: String = "recent",
    val searchQuery: String = "",
)

class CommunityViewModel(application: Application) : AndroidViewModel(application) {

    private val cache = CommunityConfigCache(application)
    private val _uiState = MutableStateFlow(CommunityUiState())
    val uiState: StateFlow<CommunityUiState> = _uiState.asStateFlow()

    init {
        val cached = cache.getAll()
        if (cached.isNotEmpty()) {
            _uiState.update { it.copy(configs = applyFilters(cached, it)) }
        }
    }

    fun setFilterMode(mode: String?) {
        _uiState.update { state ->
            state.copy(filterMode = mode, configs = applyFilters(cache.getAll(), state.copy(filterMode = mode)))
        }
    }

    fun setFilterPlatform(platform: String?) {
        _uiState.update { state ->
            state.copy(filterPlatform = platform, configs = applyFilters(cache.getAll(), state.copy(filterPlatform = platform)))
        }
    }

    fun setFilterCategory(category: String?) {
        _uiState.update { state ->
            state.copy(filterCategory = category, configs = applyFilters(cache.getAll(), state.copy(filterCategory = category)))
        }
    }

    fun setSortOrder(order: String) {
        _uiState.update { state ->
            state.copy(sortOrder = order, configs = applyFilters(cache.getAll(), state.copy(sortOrder = order)))
        }
    }

    fun setSearchQuery(query: String) {
        _uiState.update { state ->
            state.copy(searchQuery = query, configs = applyFilters(cache.getAll(), state.copy(searchQuery = query)))
        }
    }

    fun refresh() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, error = null) }
            val state = _uiState.value
            val result = ConfigApiClient.fetchConfigs(
                mode     = state.filterMode,
                platform = state.filterPlatform,
                category = state.filterCategory,
                sort     = state.sortOrder,
                limit    = 100,
            )
            result.fold(
                onSuccess = { response ->
                    cache.clear()
                    cache.replaceAll(response.configs)
                    response.latestAt?.let { cache.setLatestAt(it) }
                    _uiState.update { s ->
                        s.copy(
                            isLoading = false,
                            error = null,
                            configs = applyFilters(response.configs, s),
                        )
                    }
                },
                onFailure = { e ->
                    _uiState.update { it.copy(isLoading = false, error = e.message) }
                },
            )
        }
    }

    fun syncIfStale() {
        viewModelScope.launch {
            val since = cache.getLatestAt()
            _uiState.update { it.copy(isLoading = true, error = null) }
            val result = ConfigApiClient.fetchConfigs(
                sort  = "recent",
                limit = 100,
                since = since,
            )
            result.fold(
                onSuccess = { response ->
                    cache.mergeIn(response.configs)
                    response.latestAt?.let { cache.setLatestAt(it) }
                    val all = cache.getAll()
                    _uiState.update { s ->
                        s.copy(
                            isLoading = false,
                            error = null,
                            configs = applyFilters(all, s),
                        )
                    }
                },
                onFailure = { e ->
                    _uiState.update { it.copy(isLoading = false, error = e.message) }
                },
            )
        }
    }

    fun uploadConfig(body: CommunityUploadBody): Flow<Result<String>> = flow {
        emit(ConfigApiClient.uploadConfig(body))
    }

    private fun applyFilters(all: List<CommunityConfigRecord>, state: CommunityUiState): List<CommunityConfigRecord> {
        var result = all
        state.filterMode?.let { mode -> result = result.filter { it.mode == mode } }
        state.filterPlatform?.let { platform -> result = result.filter { it.platform == platform } }
        state.filterCategory?.let { category -> result = result.filter { it.category == category } }
        if (state.searchQuery.isNotBlank()) {
            val q = state.searchQuery.lowercase()
            result = result.filter { record ->
                record.profileName.lowercase().contains(q) ||
                record.description?.lowercase()?.contains(q) == true ||
                record.deviceName?.lowercase()?.contains(q) == true ||
                record.tags.any { it.lowercase().contains(q) }
            }
        }
        result = when (state.sortOrder) {
            "popular" -> result.sortedByDescending { it.downloadCount }
            else      -> result.sortedByDescending { it.uploadedAt }
        }
        return result
    }
}
