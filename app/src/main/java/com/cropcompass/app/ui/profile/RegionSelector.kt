package com.cropcompass.app.ui.profile

import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import com.cropcompass.app.ui.MarketUiState
import com.cropcompass.app.ui.components.*
import com.cropcompass.app.domain.MarketLogic

@Composable fun RegionSelector(state: MarketUiState, onSave: (String, String) -> Unit) {
    val data=state.snapshot; val kn=state.settings.language=="kn"
    var district by rememberSaveable(state.settings.homeDistrictId) { mutableStateOf(state.settings.homeDistrictId.ifBlank { "kolar" }) }
    var mandi by rememberSaveable(state.settings.homeMandiId) { mutableStateOf(state.settings.homeMandiId) }
    val choices=data.mandis.filter { it.districtId==district }
    val labels=choices.associate { it.id to "${it.name(kn)} • ${it.lastReportDate ?: "No recent data"}" }
    Choice(tr("District"), data.districts.find { it.id==district }?.name(kn) ?: "Choose district", data.districts.map { it.name(kn) },tag="District") { value ->
        district=data.districts.first { it.name(kn)==value }.id; mandi=""
    }
    Choice(tr("Mandi"),labels[mandi] ?: "Choose mandi",labels.values.toList(),tag="Mandi") { value -> mandi=labels.entries.first { it.value==value }.key }
    if(choices.isEmpty()) Notice("No mandi records were supplied for this district. Choose a district with data or import a more complete CSV.")
    if(state.snapshot.info?.dataSource=="fixture") Notice(tr("TEST DATA — not real prices")+". Mandi names and coordinate positions are synthetic too.")
    LocationPicker { lat,lng ->
        val nearest=data.mandis.filter { it.lat!=null && it.lng!=null }.minByOrNull { MarketLogic.haversine(lat,lng,it.lat!!,it.lng!!) }
        if(nearest!=null) { district=nearest.districtId; mandi=nearest.id }
    }
    Button(onClick={onSave(district,mandi)},enabled=choices.any { it.id==mandi },modifier=Modifier.testTag("save-region")) { Text(tr("Save region")) }
}
