package com.cropcompass.app.ui.components

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.drawable.BitmapDrawable
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.cropcompass.app.data.local.Mandi
import com.cropcompass.app.domain.MarketLogic
import com.cropcompass.app.ui.MarketUiState
import org.osmdroid.config.Configuration
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.Marker
import org.osmdroid.events.MapListener
import org.osmdroid.events.ScrollEvent
import org.osmdroid.events.ZoomEvent
import java.io.File
import kotlin.math.floor
import kotlin.math.pow

private fun icon(context: android.content.Context, color: Int, text: String, approximate: Boolean): BitmapDrawable {
    val bitmap=Bitmap.createBitmap(88,88,Bitmap.Config.ARGB_8888); val canvas=Canvas(bitmap); val paint=Paint(Paint.ANTI_ALIAS_FLAG)
    paint.color=if(approximate) android.graphics.Color.WHITE else color; canvas.drawCircle(44f,44f,35f,paint)
    paint.style=Paint.Style.STROKE; paint.strokeWidth=6f; paint.color=color; canvas.drawCircle(44f,44f,35f,paint)
    paint.style=Paint.Style.FILL; paint.color=if(approximate) color else android.graphics.Color.WHITE; paint.textSize=29f; paint.textAlign=Paint.Align.CENTER
    canvas.drawText(text,44f,54f,paint)
    return BitmapDrawable(context.resources,bitmap)
}
@OptIn(ExperimentalMaterial3Api::class)
@Composable fun MandiMap(state: MarketUiState, commodityId: String?, mandiId: String? = null, onCommodity: (String,String) -> Unit, onCompare: (String) -> Unit, onSet: (Mandi) -> Unit, districtId: String? = null, quoteOverride: List<com.cropcompass.app.data.local.PriceRecord>? = null) {
    val context=LocalContext.current; val owner=LocalLifecycleOwner.current; val data=state.snapshot
    var selected by remember { mutableStateOf<Mandi?>(null) }; var tiles by remember { mutableStateOf(false) }; var zoom by remember { mutableDoubleStateOf(6.5) }
    var crop by remember(commodityId) { mutableStateOf(commodityId ?: data.commodities.firstOrNull()?.id.orEmpty()) }
    val candidates=quoteOverride ?: MarketLogic.latest(data.prices.filter { it.commodityId==crop })
    val markets=data.mandis.filter { it.lat!=null && it.lng!=null && (mandiId==null || it.id==mandiId) && (districtId==null || it.districtId==districtId) && (quoteOverride==null || quoteOverride.any{p->p.mandiId==it.id}) }
    val map=remember {
        Configuration.getInstance().apply { userAgentValue="CropCompass/1.0 (Android college project)"; osmdroidBasePath=File(context.cacheDir,"osmdroid"); osmdroidTileCache=File(context.cacheDir,"osmdroid/tiles"); tileFileSystemCacheMaxBytes=50L*1024*1024 }
        MapView(context).apply {
            tag="mandi-map"; setTileSource(TileSourceFactory.MAPNIK); setMultiTouchControls(true); setUseDataConnection(false)
            controller.setZoom(6.5); controller.setCenter(GeoPoint(14.5,76.5))
            addMapListener(object:MapListener { override fun onScroll(event: ScrollEvent?)=false; override fun onZoom(event: ZoomEvent?): Boolean { zoom=zoomLevelDouble; return false } })
            setOnTouchListener { view,event-> view.parent?.requestDisallowInterceptTouchEvent(event.action!=android.view.MotionEvent.ACTION_UP); false }
        }
    }
    DisposableEffect(map,owner) {
        val observer=LifecycleEventObserver { _,event->if(event==Lifecycle.Event.ON_RESUME) map.onResume() else if(event==Lifecycle.Event.ON_PAUSE) map.onPause() }
        owner.lifecycle.addObserver(observer); map.onResume()
        onDispose { owner.lifecycle.removeObserver(observer); map.onPause(); map.onDetach() }
    }
    if(quoteOverride==null) Choice(tr("Commodity"),data.commodities.find{it.id==crop}?.name(state.settings.language=="kn") ?: "Choose",data.commodities.map{it.name(state.settings.language=="kn")},tag="map-commodity"){label->crop=data.commodities.first{it.name(state.settings.language=="kn")==label}.id}
    else Text("${tr("Commodity")}: ${data.commodities.find{it.id==crop}?.name(state.settings.language=="kn")} • ranked candidates only")
    Text("Markers work offline. Background tiles need internet or a previous cache. No bulk tile download is performed.",style=MaterialTheme.typography.bodySmall)
    Row { Text("Load background tiles (internet)",Modifier.weight(1f)); Switch(tiles,{tiles=it}) }
    if(markets.isEmpty()) Notice("No coordinates available for these mandis. Import coordinates or run the optional geocoder.")
    AndroidView(factory={map},modifier=Modifier.fillMaxWidth().height(350.dp).testTag("map-view"),update={ view ->
        view.setUseDataConnection(tiles && state.online); view.overlays.clear()
        val cell=360.0/2.0.pow(zoom+2)
        val clusters=markets.groupBy { floor(it.lat!!/cell).toLong() to floor(it.lng!!/cell).toLong() }
        val prices=candidates.map{it.modalPrice}; val midpoint=prices.sorted().getOrNull(prices.size/2) ?: 0.0
        clusters.values.forEach { group ->
            val position=GeoPoint(group.map{it.lat!!}.average(),group.map{it.lng!!}.average())
            val marker=Marker(view); marker.position=position; marker.setAnchor(Marker.ANCHOR_CENTER,Marker.ANCHOR_CENTER)
            val m=group.first(); val p=candidates.filter{it.mandiId==m.id}.maxByOrNull{it.date}
            val color=if(p==null) 0xFF607D8B.toInt() else if(p.modalPrice>=midpoint) 0xFF2E7D32.toInt() else 0xFFB05C20.toInt()
            marker.icon=icon(context,color,if(group.size>1) group.size.toString() else if(m.geoConfidence=="DISTRICT") "≈" else "₹",group.size==1 && m.geoConfidence=="DISTRICT")
            marker.title=if(group.size>1) "${group.size} mandis — tap to zoom" else m.nameEn
            marker.setOnMarkerClickListener { _,_->
                if(group.size>1) { view.controller.setCenter(position); view.controller.setZoom(view.zoomLevelDouble+2) } else selected=m
                true
            }
            view.overlays.add(marker)
        }; view.invalidate()
    })
    Text("© OpenStreetMap contributors • green: upper half of modal prices; amber: lower half; ≈: approximate coordinate. Tap clusters to zoom.",style=MaterialTheme.typography.labelSmall)
    TextButton(onClick={context.startActivity(android.content.Intent(android.content.Intent.ACTION_VIEW,android.net.Uri.parse("https://www.openstreetmap.org/copyright")))}){Text("OpenStreetMap attribution and licence")}
    // A text alternative keeps every marker usable with screen readers and without map tiles.
    markets.take(80).forEach { m->TextButton(onClick={selected=m},modifier=Modifier.testTag("map-marker-${m.id}")){Text(m.name(state.settings.language=="kn"))} }
    selected?.let { m->ModalBottomSheet(onDismissRequest={selected=null}) {
        Column(Modifier.padding(20.dp),verticalArrangement=Arrangement.spacedBy(12.dp)) {
            Text(m.name(state.settings.language=="kn"),style=MaterialTheme.typography.titleLarge)
            if(m.geoConfidence=="DISTRICT") Notice("Approximate location — district-level coordinate.")
            DataBanner(state)
            val records=candidates.filter{it.mandiId==m.id}
            if(records.isEmpty()) Text("No observations for this commodity.")
            records.take(3).forEach { p->Text("${money(p.modalPrice)} / quintal • ${m.nameEn} • ${p.date} • ${p.variety ?: "Unspecified"}") }
            TextButton(onClick={selected=null;onCommodity(crop,m.id)}){Text("Open commodity details")}
            TextButton(onClick={selected=null;onCompare(crop)}){Text(tr("Compare"))}
            TextButton(onClick={onSet(m);selected=null}){Text("Set as my mandi")}
        }
    } }
}
