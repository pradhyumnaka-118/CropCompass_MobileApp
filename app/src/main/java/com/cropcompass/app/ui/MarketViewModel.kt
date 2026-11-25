package com.cropcompass.app.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.cropcompass.app.AppContainer
import com.cropcompass.app.data.local.*
import com.cropcompass.app.data.repository.MarketSnapshot
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*

data class MarketUiState(val loading: Boolean = true, val snapshot: MarketSnapshot = MarketSnapshot(), val settings: Settings = Settings(), val watches: List<WatchlistItem> = emptyList(), val reports: List<FarmerReport> = emptyList(), val error: String? = null, val message: String? = null, val refreshing: Boolean = false, val online: Boolean = true)
@HiltViewModel
class MarketViewModel @Inject constructor(val container: AppContainer) : ViewModel() {
    private val mutable = MutableStateFlow(MarketUiState())
    val state = mutable.asStateFlow()
    init {
        viewModelScope.launch { com.cropcompass.app.data.NetworkMonitor(container.context).online.collect { online->mutable.update{it.copy(online=online)} } }
        viewModelScope.launch {
            combine(container.market.snapshot, container.preferences.settings, container.market.dao.watchlist(), container.market.dao.reports()) { data, settings, watches, reports ->
                MarketUiState(loading = data.info == null, snapshot = data, settings = settings, watches = watches, reports = reports)
            }.catch { e -> mutable.update { it.copy(loading = false, error = e.message ?: "Could not read local data.") } }
                .collect { value -> mutable.update { value.copy(error = it.error, message = it.message,refreshing=it.refreshing,online=it.online) } }
        }
        retry()
    }
    fun action(block: suspend () -> Unit) { viewModelScope.launch {
        try { block(); mutable.update { it.copy(error = null) } }
        catch (e: Exception) { if (e is CancellationException) throw e; mutable.update { it.copy(loading = false, error = e.message ?: "Something went wrong. Please retry.") } }
    } }
    fun retry() = action { container.market.seed(); container.preferences.source(container.market.dao.currentInfo()?.dataSource ?: "fixture") }
    fun region(district: String, mandi: String) = action {
        require(state.value.snapshot.mandis.any { it.id == mandi && it.districtId == district }) { "Choose a mandi in the selected district." }
        container.preferences.region(district, mandi)
    }
    fun configure(language: String, rate: Double, radius: Double) = action {
        require(rate.isFinite() && rate >= 0 && rate <= 1000) { "Enter a transport rate between 0 and 1000." }
        require(radius > 0)
        container.preferences.configure(language, rate, radius)
    }
    fun recent(id: String) = action { container.preferences.recent(id) }
    fun language(code: String) = action {
        container.preferences.configure(code,state.value.settings.transportRate,state.value.settings.radiusKm)
        androidx.appcompat.app.AppCompatDelegate.setApplicationLocales(androidx.core.os.LocaleListCompat.forLanguageTags(code))
    }
    fun dismiss() { mutable.update { it.copy(error = null, message = null) } }
    fun refresh() {viewModelScope.launch{mutable.update{it.copy(refreshing=true)};try{val message=container.remote.refresh();mutable.update{it.copy(message=message,error=null)}}catch(e:Exception){if(e is CancellationException)throw e;mutable.update{it.copy(error="Refresh failed. Cached observations are still available. Please retry.")}}finally{mutable.update{it.copy(refreshing=false)}}}}
    fun saveWatch(item: WatchlistItem) = action {
        require(item.targetPrice.isFinite() && item.targetPrice>0) { "Enter a positive target price." }
        require(item.direction in listOf("ABOVE","BELOW"))
        require(state.value.snapshot.commodities.any { it.id==item.commodityId })
        require(item.mandiId==null || state.value.snapshot.mandis.any { it.id==item.mandiId })
        container.market.dao.watch(item.copy(ownerUid=container.accounts.uid ?: "guest"))
    }
    fun deleteWatch(id: String) = action { require(state.value.watches.any { it.id==id && it.ownerUid==(container.accounts.uid ?: "guest") }); container.market.dao.deleteWatch(id); androidx.core.app.NotificationManagerCompat.from(container.context).cancel(id.hashCode()) }
    fun checkAlerts(simulate: Boolean = false) = action {
        require(!simulate || com.cropcompass.app.BuildConfig.DEBUG)
        val posted=com.cropcompass.app.data.Alerts.check(container,simulate)
        mutable.update { it.copy(message=if(posted>0) "$posted alert notification posted." else "No new alerts. Check notification permission, enabled targets, and fresh observations.") }
    }
    fun saveReport(item: FarmerReport) = action {
        val previous=container.market.dao.report(item.id)
        require(item.price.isFinite() && item.price>0) { "Enter a positive price per quintal." }
        require(state.value.snapshot.mandis.any { it.id==item.mandiId }) { "Choose a mandi." }
        require(state.value.snapshot.commodities.any { it.id==item.commodityId }) { "Choose a commodity." }
        require(java.time.LocalDate.parse(item.observationDate)<=java.time.LocalDate.now()) { "Observation date cannot be in the future." }
        require(item.note.orEmpty().length<=1000) { "Keep notes under 1000 characters." }
        val owner=container.accounts.uid ?: "guest"
        require(item.ownerUid==owner || item.ownerUid=="guest") { "You can only edit your own reports." }
        container.market.dao.report(item.copy(ownerUid=owner,synced=false,updatedAt=System.currentTimeMillis()))
        if(previous?.photoUri!=item.photoUri)com.cropcompass.app.data.PhotoStore.delete(container.context,previous?.photoUri)
        com.cropcompass.app.data.WorkScheduler.reports(container.context)
        mutable.update { it.copy(message="Saved on device. ${if(owner=="guest") "Sign in to sync." else "Sync queued when connected."}") }
    }
    fun deleteReport(item: FarmerReport) = action {
        val owner=container.accounts.uid ?: "guest"; require(item.ownerUid==owner || item.ownerUid=="guest")
        if(item.ownerUid=="guest") container.market.dao.deleteReport(item.id)
        else { container.market.dao.report(item.copy(deleted=true,synced=false,updatedAt=System.currentTimeMillis())); com.cropcompass.app.data.WorkScheduler.reports(container.context) }
        com.cropcompass.app.data.PhotoStore.delete(container.context,item.photoUri)
    }
    fun syncReports() = action {
        val uid=requireNotNull(container.accounts.uid) { "Sign in to sync device reports." }
        state.value.reports.filter { it.ownerUid=="guest" && !it.deleted }.forEach { container.market.dao.report(it.copy(ownerUid=uid,synced=false,updatedAt=System.currentTimeMillis())) }
        com.cropcompass.app.data.WorkScheduler.reports(container.context)
        mutable.update { it.copy(message="Report sync queued. Only your own reports are uploaded.") }
    }
}
