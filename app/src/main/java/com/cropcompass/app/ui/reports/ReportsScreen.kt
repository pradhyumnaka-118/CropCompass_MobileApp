package com.cropcompass.app.ui.reports

import android.graphics.BitmapFactory
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import com.cropcompass.app.data.PhotoStore
import com.cropcompass.app.data.local.FarmerReport
import com.cropcompass.app.ui.*
import com.cropcompass.app.ui.components.*
import kotlinx.coroutines.*
import java.io.File
import java.time.LocalDate
import java.util.UUID

@Composable private fun PhotoPreview(uri: String) {
    var image by remember(uri){mutableStateOf<android.graphics.Bitmap?>(null)}
    LaunchedEffect(uri){image=withContext(Dispatchers.IO){runCatching{BitmapFactory.decodeFile(Uri.parse(uri).path)}.getOrNull()}}
    image?.let{Image(it.asImageBitmap(),contentDescription="Attached report photo",modifier=Modifier.fillMaxWidth().height(150.dp))}
    Text(if(image==null) "Photo attached (preview unavailable)" else "Photo attached",style=MaterialTheme.typography.labelSmall)
}
@Composable fun ReportDialog(state: MarketUiState, initialCrop: String?, initialMandi: String?, existing: FarmerReport?, onClose: () -> Unit, onSave: (FarmerReport) -> Unit) {
    val data=state.snapshot;val kn=state.settings.language=="kn";val context=LocalContext.current;val scope=rememberCoroutineScope()
    var crop by rememberSaveable{mutableStateOf(existing?.commodityId ?: initialCrop ?: data.commodities.firstOrNull()?.id.orEmpty())}
    var mandi by rememberSaveable{mutableStateOf(existing?.mandiId ?: initialMandi ?: state.settings.homeMandiId)}
    var price by rememberSaveable{mutableStateOf(existing?.price?.toString().orEmpty())};var date by rememberSaveable{mutableStateOf(existing?.observationDate ?: LocalDate.now().toString())}
    var note by rememberSaveable{mutableStateOf(existing?.note.orEmpty())};var photo by rememberSaveable{mutableStateOf(existing?.photoUri)}
    var cameraUri by rememberSaveable{mutableStateOf<String?>(null)};var busy by remember{mutableStateOf(false)};var error by remember{mutableStateOf<String?>(null)}
    fun attach(uri: Uri) {scope.launch{busy=true;try{photo=PhotoStore.import(context,uri);error=null}catch(e:Exception){if(e is CancellationException)throw e;error=e.message ?: "Photo could not be attached."}finally{busy=false}}}
    val gallery=rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()){uri->if(uri!=null)attach(uri)}
    val camera=rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()){success->if(success)cameraUri?.let{attach(Uri.parse(it))}else error="Camera capture was cancelled. You can still save without a photo."}
    AlertDialog(onDismissRequest={if(!busy)onClose()},title={Text(if(existing==null) tr("Report a price") else "Edit report")},text={Column(Modifier.heightIn(max=550.dp).verticalScroll(rememberScrollState()),verticalArrangement=Arrangement.spacedBy(12.dp)){
        DataBanner(state);Notice("Farmer-reported, unverified. These prices are never used in official statistics or recommendations.")
        Choice(tr("Mandi"),data.mandis.find{it.id==mandi}?.name(kn).orEmpty(),data.mandis.map{it.name(kn)},tag="report-mandi"){name->mandi=data.mandis.first{it.name(kn)==name}.id}
        Choice(tr("Commodity"),data.commodities.find{it.id==crop}?.name(kn).orEmpty(),data.commodities.map{it.name(kn)},tag="report-commodity"){name->crop=data.commodities.first{it.name(kn)==name}.id}
        OutlinedTextField(price,{price=it},label={Text("Price (₹ per quintal)")},keyboardOptions=androidx.compose.foundation.text.KeyboardOptions(keyboardType=androidx.compose.ui.text.input.KeyboardType.Decimal),modifier=Modifier.testTag("report-price"))
        OutlinedTextField(date,{date=it},label={Text("Observation date (YYYY-MM-DD)")},singleLine=true)
        OutlinedTextField(note,{if(it.length<=1000)note=it},label={Text("Notes (optional)")},maxLines=4,modifier=Modifier.testTag("report-note"))
        photo?.let{PhotoPreview(it);TextButton(onClick={photo=null}){Text("Remove photo")}}
        Text("Photo optional. The system picker grants access only to the selected image; the camera opens as a separate app.",style=MaterialTheme.typography.bodySmall)
        Row {
            TextButton(enabled=!busy,onClick={gallery.launch(arrayOf("image/*"))},modifier=Modifier.testTag("gallery-photo")){Text("Choose photo")}
            TextButton(enabled=!busy,onClick={
                try {val directory=File(context.cacheDir,"camera").apply{mkdirs()};val file=File(directory,"${UUID.randomUUID()}.jpg");val uri=FileProvider.getUriForFile(context,"${context.packageName}.photos",file);cameraUri=uri.toString();camera.launch(uri)}catch(_:Exception){error="No camera application is available. Choose a photo instead."}
            }){Text("Camera")}
        }
        if(busy) {CircularProgressIndicator();Text("Saving photo on device…")}
        error?.let{Notice(it,true)}
    }},confirmButton={TextButton(enabled=!busy,onClick={
        val number=price.toDoubleOrNull();val observed=runCatching{LocalDate.parse(date)}.getOrNull()
        if(number==null || !number.isFinite() || number<=0)error="Enter a positive price per quintal."
        else if(observed==null || observed>LocalDate.now())error="Use a valid observation date, no later than today."
        else {onSave((existing ?: FarmerReport(UUID.randomUUID().toString(),mandi,crop,number,observationDate=date)).copy(mandiId=mandi,commodityId=crop,price=number,note=note.ifBlank{null},photoUri=photo,observationDate=date));onClose()}
    },modifier=Modifier.testTag("save-report")){Text(tr("Save"))}},dismissButton={TextButton(enabled=!busy,onClick=onClose){Text(tr("Cancel"))}})
}
@Composable fun ReportsScreen(state: MarketUiState, vm: MarketViewModel, initialCrop: String? = null, initialMandi: String? = null, openNew: Boolean = false) {
    var show by rememberSaveable{mutableStateOf(openNew)};var editing by remember{mutableStateOf<FarmerReport?>(null)}
    val owner=vm.container.accounts.uid ?: "guest";val reports=state.reports.filter{!it.deleted && (it.ownerUid==owner || it.ownerUid=="guest")}
    var deleting by remember{mutableStateOf<FarmerReport?>(null)}
    Page {
        Heading(tr("My reports"));DataBanner(state)
        Notice("Farmer-reported, unverified. Kept separate from Agmarknet prices and charts.")
        Button(onClick={editing=null;show=true},modifier=Modifier.testTag("new-report")){Text(tr("Report a price"))}
        OutlinedButton(onClick=vm::syncReports,enabled=owner!="guest"){Text("Sync my device reports")}
        if(owner=="guest") Text("Saved on device. Sign in to sync reports and photos.")
        if(reports.isEmpty()) Notice("No reports saved yet. Add your first market observation.")
        reports.forEach{report->OutlinedCard(Modifier.fillMaxWidth()) { Column(Modifier.padding(16.dp),verticalArrangement=Arrangement.spacedBy(10.dp)) {
            Text(state.snapshot.commodities.find{it.id==report.commodityId}?.name(state.settings.language=="kn") ?: report.commodityId,style=MaterialTheme.typography.titleMedium)
            Text("${money(report.price)} / quintal • ${state.snapshot.mandis.find{it.id==report.mandiId}?.nameEn ?: report.mandiId}\n${report.observationDate}")
            Text("Farmer-reported, unverified • ${if(report.synced) "Synced" else if(report.ownerUid=="guest") "Saved on device" else "Pending sync"}")
            report.note?.let{Text(it)};report.photoUri?.let{PhotoPreview(it)}
            Row {TextButton(onClick={editing=report;show=true}){Text(tr("Edit"))};TextButton(onClick={deleting=report}){Text(tr("Delete"))}}
        } } }
    }
    if(show) ReportDialog(state,initialCrop,initialMandi,editing,{show=false},vm::saveReport)
    deleting?.let{report->AlertDialog(onDismissRequest={deleting=null},title={Text("Delete your report?")},text={Text("This removes the report from this device and queues deletion of your synced copy when connected.")},confirmButton={TextButton(onClick={vm.deleteReport(report);deleting=null},modifier=Modifier.testTag("confirm-delete-report")){Text(tr("Delete"))}},dismissButton={TextButton(onClick={deleting=null}){Text(tr("Cancel"))}})}
}
