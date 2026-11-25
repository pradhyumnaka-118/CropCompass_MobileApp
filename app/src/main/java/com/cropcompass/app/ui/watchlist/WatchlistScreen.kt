package com.cropcompass.app.ui.watchlist

import android.Manifest
import android.os.Build
import android.content.Intent
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.cropcompass.app.BuildConfig
import com.cropcompass.app.data.Alerts
import com.cropcompass.app.data.local.WatchlistItem
import com.cropcompass.app.domain.MarketLogic
import com.cropcompass.app.ui.*
import com.cropcompass.app.ui.components.*
import java.time.Instant
import java.time.ZoneId
import java.util.UUID

@Composable fun WatchDialog(state: MarketUiState, initialCrop: String?, initialMandi: String?, existing: WatchlistItem?, onClose: () -> Unit, onSave: (WatchlistItem) -> Unit) {
    val data=state.snapshot; val kn=state.settings.language=="kn"
    var crop by rememberSaveable { mutableStateOf(existing?.commodityId ?: initialCrop ?: data.commodities.firstOrNull()?.id.orEmpty()) }
    var mandi by rememberSaveable { mutableStateOf(if(existing!=null) existing.mandiId.orEmpty() else initialMandi ?: state.settings.homeMandiId) }
    var target by rememberSaveable { mutableStateOf(existing?.targetPrice?.toString().orEmpty()) }; var direction by rememberSaveable { mutableStateOf(existing?.direction ?: "ABOVE") }; var error by remember { mutableStateOf<String?>(null) }
    AlertDialog(onDismissRequest=onClose,title={Text(if(existing==null) "Add price target" else "Edit price target")},text={Column(Modifier.heightIn(max=500.dp).verticalScroll(rememberScrollState()),verticalArrangement=Arrangement.spacedBy(12.dp)) {
        DataBanner(state)
        Choice(tr("Commodity"),data.commodities.find{it.id==crop}?.name(kn).orEmpty(),data.commodities.map{it.name(kn)},tag="watch-commodity"){name->crop=data.commodities.first{it.name(kn)==name}.id}
        Choice(tr("Mandi"),data.mandis.find{it.id==mandi}?.name(kn) ?: "All Karnataka",listOf("All Karnataka")+data.mandis.map{it.name(kn)},tag="watch-mandi"){name->mandi=data.mandis.find{it.name(kn)==name}?.id.orEmpty()}
        OutlinedTextField(target,{target=it},label={Text("Target (₹ per quintal)")},singleLine=true,modifier=Modifier.testTag("watch-target"),keyboardOptions=androidx.compose.foundation.text.KeyboardOptions(keyboardType=androidx.compose.ui.text.input.KeyboardType.Decimal))
        Choice("Notify when",direction,listOf("ABOVE","BELOW")){direction=it}
        Text("Only observations at most 7 days old trigger alerts. WorkManager checks periodically, not at an exact time.",style=MaterialTheme.typography.bodySmall)
        error?.let{Notice(it,true)}
    }},confirmButton={TextButton(onClick={val value=target.toDoubleOrNull();if(value==null || !value.isFinite() || value<=0) error="Enter a positive target price." else {onSave((existing ?: WatchlistItem(UUID.randomUUID().toString(),crop,mandi.ifBlank{null},value,direction)).copy(commodityId=crop,mandiId=mandi.ifBlank{null},targetPrice=value,direction=direction,lastConditionMet=false));onClose()}},modifier=Modifier.testTag("save-watch")){Text(tr("Save"))}},dismissButton={TextButton(onClick=onClose){Text(tr("Cancel"))}})
}
@OptIn(ExperimentalMaterial3Api::class)
@Composable fun WatchlistScreen(state: MarketUiState, vm: MarketViewModel, initialCrop: String? = null, initialMandi: String? = null, onCommodity: (String,String) -> Unit) {
    var dialog by rememberSaveable(initialCrop,initialMandi) { mutableStateOf(initialCrop!=null) }; var editing by remember { mutableStateOf<WatchlistItem?>(null) }
    val context=LocalContext.current; var granted by remember { mutableStateOf(Alerts.permitted(context)) }
    val permission=rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()){granted=it}
    val owner=vm.container.accounts.uid ?: "guest"; val items=state.watches.filter{it.ownerUid==owner}
    Page {
        Heading(tr("Watchlist")); DataBanner(state)
        Button(onClick={editing=null;dialog=true},modifier=Modifier.testTag("new-watch")){Text("Add price target")}
        Text(if(granted) "Alert notifications enabled." else "Allow notifications to receive price-target alerts.")
        OutlinedButton(onClick={if(Build.VERSION.SDK_INT>=33 && !granted) permission.launch(Manifest.permission.POST_NOTIFICATIONS) else context.startActivity(Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(Settings.EXTRA_APP_PACKAGE,context.packageName))}){Text("Notification permission / settings")}
        OutlinedButton(onClick={vm.checkAlerts()}){Text("Check targets now")}
        if(BuildConfig.DEBUG) OutlinedButton(onClick={vm.checkAlerts(true)},modifier=Modifier.testTag("simulate-alert")){Text("Simulate alert (debug)")}
        if(items.isEmpty()) Notice("No targets yet. Add a crop and mandi, then set your price target.")
        items.forEach { item->key(item.id) {
            val dismiss=rememberSwipeToDismissBoxState(confirmValueChange={value->if(value!=SwipeToDismissBoxValue.Settled) {vm.deleteWatch(item.id);true}else true})
            SwipeToDismissBox(state=dismiss,backgroundContent={Surface(color=MaterialTheme.colorScheme.errorContainer,modifier=Modifier.fillMaxSize()){Text(tr("Delete"),Modifier.padding(20.dp))}}) {
                OutlinedCard(Modifier.fillMaxWidth()) { Column(Modifier.padding(16.dp),verticalArrangement=Arrangement.spacedBy(10.dp)) {
                    Text(state.snapshot.commodities.find{it.id==item.commodityId}?.name(state.settings.language=="kn") ?: item.commodityId,style=MaterialTheme.typography.titleMedium)
                    Text("Target ${item.direction}: ${money(item.targetPrice)} / quintal\n${state.snapshot.mandis.find{it.id==item.mandiId}?.nameEn ?: "All Karnataka"}")
                    val quote=state.snapshot.prices.filter{it.commodityId==item.commodityId && (item.mandiId==null || it.mandiId==item.mandiId)}.maxWithOrNull(compareBy<com.cropcompass.app.data.local.PriceRecord>{it.date}.thenBy{it.modalPrice})
                    if(quote!=null) {
                        Text("Current: ${money(quote.modalPrice)} / quintal • ${state.snapshot.mandis.find{it.id==quote.mandiId}?.nameEn} • ${quote.date}")
                        Text(if(MarketLogic.stale(quote.date)) "Stale — alerts paused" else if(Alerts.condition(quote.modalPrice,item.targetPrice,item.direction)) "Target met" else "Waiting for target")
                        TextButton(onClick={onCommodity(item.commodityId,quote.mandiId)}){Text("Open price details")}
                    } else Text("No observation available for this saved target.")
                    Text("Last checked: ${item.lastCheckedAt?.let{Instant.ofEpochMilli(it).atZone(ZoneId.systemDefault()).toLocalDateTime().toString()} ?: "Not checked yet"}",style=MaterialTheme.typography.bodySmall)
                    Row { Text("Enabled",Modifier.weight(1f)); Switch(item.enabled,{vm.saveWatch(item.copy(enabled=it,lastConditionMet=false))},modifier=Modifier.testTag("watch-enable")) }
                    Row { TextButton(onClick={editing=item;dialog=true},modifier=Modifier.testTag("edit-watch")){Text(tr("Edit"))}; TextButton(onClick={vm.deleteWatch(item.id)},modifier=Modifier.testTag("delete-watch")){Text(tr("Delete"))} }
                } }
            }
        } }
    }
    if(dialog) WatchDialog(state,initialCrop,initialMandi,editing,{dialog=false},vm::saveWatch)
}
