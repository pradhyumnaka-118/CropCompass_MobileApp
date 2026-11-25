package com.cropcompass.app.navigation

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material.icons.automirrored.outlined.CompareArrows
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import com.cropcompass.app.R
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import androidx.navigation.compose.*
import com.cropcompass.app.AppContainer
import com.cropcompass.app.ui.auth.*
import com.cropcompass.app.ui.home.HomeScreen
import com.cropcompass.app.ui.profile.ProfileScreen
import com.cropcompass.app.ui.components.*
import com.cropcompass.app.ui.MarketViewModel
import com.cropcompass.app.ui.profile.RegionSelector
import androidx.hilt.navigation.compose.hiltViewModel
import com.cropcompass.app.ui.prices.*
import com.cropcompass.app.ui.compare.CompareScreen
import com.cropcompass.app.ui.watchlist.WatchlistScreen
import com.cropcompass.app.ui.reports.ReportsScreen
import com.cropcompass.app.ui.profile.AboutDataScreen
import com.cropcompass.app.ui.profile.InsightsScreen
import androidx.compose.ui.platform.testTag

@Composable fun CropCompassApp(container: AppContainer, initialRoute: String? = null) {
    val auth: AuthViewModel = viewModel(factory = viewModelFactory { initializer { AuthViewModel(container) } })
    val state by auth.state.collectAsStateWithLifecycle()
    val market: MarketViewModel = hiltViewModel()
    val data by market.state.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    LaunchedEffect(data.error, data.message) {
        (data.error ?: data.message)?.let { snackbar.showSnackbar(it,withDismissAction=true); market.dismiss() }
    }
    val authenticated = state.profile != null || state.guest
    val nav = key(authenticated) { rememberNavController() }
    val entry by nav.currentBackStackEntryAsState()
    val route = entry?.destination?.route
    var notificationHandled by androidx.compose.runtime.saveable.rememberSaveable(initialRoute) { mutableStateOf(false) }
    LaunchedEffect(authenticated,data.loading,data.settings.firstRun,initialRoute) {
        if(initialRoute=="watchlist" && !notificationHandled && authenticated && !data.loading && !data.settings.firstRun && data.snapshot.mandis.any{it.id==data.settings.homeMandiId}) {
            nav.navigate("watchlist"){launchSingleTop=true};notificationHandled=true
        }
    }
    val tabs = listOf("home" to Icons.Outlined.Home, "prices" to Icons.Outlined.Storefront, "compare" to Icons.AutoMirrored.Outlined.CompareArrows, "watchlist" to Icons.Outlined.BookmarkBorder, "profile" to Icons.Outlined.Person)
    Scaffold(modifier = Modifier.imePadding(), snackbarHost={SnackbarHost(snackbar)}, topBar={
        if(authenticated && !state.initializing && data.snapshot.info!=null) Surface(color=MaterialTheme.colorScheme.primaryContainer) {
            Row(Modifier.fillMaxWidth().statusBarsPadding().padding(horizontal=12.dp,vertical=6.dp),verticalAlignment=Alignment.CenterVertically) {
                if(route!=null && route !in tabs.map{it.first}) IconButton(onClick={nav.popBackStack()}){Icon(Icons.AutoMirrored.Outlined.ArrowBack,"Back")}
                Text(if(data.snapshot.info?.dataSource=="fixture") tr("TEST DATA — not real prices") else "Agmarknet • Data as of ${data.snapshot.info?.lastDate}",style=MaterialTheme.typography.labelMedium)
            }
        }
    }, bottomBar = {
        if (authenticated && !state.initializing && !data.loading && !data.settings.firstRun && data.snapshot.mandis.any { it.id==data.settings.homeMandiId }) NavigationBar {
            tabs.forEach { (target, icon) -> NavigationBarItem(modifier=Modifier.testTag("nav-$target"),selected = route == target || route?.startsWith("$target/")==true, onClick = {
                val secondary=route !in tabs.map{it.first}
                if(!(secondary && nav.popBackStack(target,false))) nav.navigate(target) { popUpTo(nav.graph.startDestinationId) { saveState = !secondary }; launchSingleTop = true; restoreState = true }
            }, icon = { Icon(icon, null) }, label = { Text(tr(target.replaceFirstChar { it.uppercase() })) }) }
        }
    }) { padding ->
        Surface(Modifier.fillMaxSize().padding(padding), color = MaterialTheme.colorScheme.background) {
            when {
                state.initializing -> Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(Icons.Outlined.Eco, null, Modifier.size(80.dp), tint = MaterialTheme.colorScheme.primary)
                    Text(stringResource(R.string.app_name), style = MaterialTheme.typography.headlineLarge)
                    Text(stringResource(R.string.tagline))
                    CircularProgressIndicator(Modifier.padding(24.dp))
                }
                !authenticated -> {
                    val authNav = rememberNavController()
                    NavHost(authNav, startDestination = "login") {
                    composable("login") { AuthScreen(state, auth.configured, false, { authNav.navigate("register") }, auth::guest) { name, email, password, confirm, district -> auth.submit(false, name, email, password, confirm, district) } }
                    composable("register") { AuthScreen(state, auth.configured, true, { authNav.popBackStack() }, auth::guest) { name, email, password, confirm, district -> auth.submit(true, name, email, password, confirm, district) } }
                    }
                }
                data.loading -> Column(Modifier.fillMaxSize(),verticalArrangement=Arrangement.Center,horizontalAlignment=Alignment.CenterHorizontally) { CircularProgressIndicator(); Text("Preparing offline market data…") }
                data.error != null && data.snapshot.info == null -> Page { Notice(data.error!!,true); Button(onClick=market::retry){Text(tr("Retry"))} }
                data.settings.firstRun || data.snapshot.mandis.none { it.id==data.settings.homeMandiId } -> Page { Heading(tr("Choose your region")); RegionSelector(data,market::region) }
                else -> NavHost(nav, startDestination = "home") {
                    composable("home") { HomeScreen(data,market,{c,m->nav.navigate("commodity/$c/$m")},{nav.navigate("compare")},{nav.navigate("watchlist")},{nav.navigate("reports/new")},{nav.navigate("map")},{nav.navigate("districts")}) }
                    composable("prices") { PricesScreen(data,{c,m->nav.navigate("commodity/$c/$m")},{nav.navigate("districts")},{m,c,d->MandiMap(data,c,m,{crop,mid->nav.navigate("commodity/$crop/$mid")},{nav.navigate("compare/$it")},{market.region(it.districtId,it.id)},districtId=d)}) }
                    composable("compare") { CompareScreen(data,null,{c,m->nav.navigate("commodity/$c/$m")},{c,quotes->MandiMap(data,c,null,{crop,mid->nav.navigate("commodity/$crop/$mid")},{nav.navigate("compare/$it")},{market.region(it.districtId,it.id)},quoteOverride=quotes)}) }
                    composable("compare/{crop}") { entry->CompareScreen(data,entry.arguments?.getString("crop"),{c,m->nav.navigate("commodity/$c/$m")},{c,quotes->MandiMap(data,c,null,{crop,mid->nav.navigate("commodity/$crop/$mid")},{nav.navigate("compare/$it")},{market.region(it.districtId,it.id)},quoteOverride=quotes)}) }
                    composable("watchlist") { WatchlistScreen(data,market,onCommodity={c,m->nav.navigate("commodity/$c/$m")}) }
                    composable("watch/{crop}/{mandi}") { entry->WatchlistScreen(data,market,entry.arguments?.getString("crop"),entry.arguments?.getString("mandi")){c,m->nav.navigate("commodity/$c/$m")} }
                    composable("profile") { ProfileScreen(state,data,market,auth::logout,{nav.navigate("about")},{nav.navigate("insights")}) }
                    composable("commodity/{crop}/{mandi}") { entry -> CommodityScreen(data,entry.arguments?.getString("crop").orEmpty(),entry.arguments?.getString("mandi").orEmpty(),{nav.navigate("mandi/$it")},{c,m->nav.navigate("watch/$c/$m")},{nav.navigate("compare/$it")},{c,m->nav.navigate("report/$c/$m")},{records->ForecastCard(data,records)}) }
                    composable("about"){AboutDataScreen(data)}
                    composable("insights"){InsightsScreen(data)}
                    composable("districts") { DistrictsScreen(data){nav.navigate("district/$it")} }
                    composable("district/{id}") { entry->DistrictScreen(data,entry.arguments?.getString("id").orEmpty()){nav.navigate("mandi/$it")} }
                    composable("mandi/{id}") { entry->MandiScreen(data,entry.arguments?.getString("id").orEmpty(),market,{c,m->nav.navigate("commodity/$c/$m")},{nav.navigate("home")}) }
                    composable("reports") { ReportsScreen(data,market) }
                    composable("reports/new") { ReportsScreen(data,market,openNew=true) }
                    composable("report/{crop}/{mandi}") { entry->ReportsScreen(data,market,entry.arguments?.getString("crop"),entry.arguments?.getString("mandi"),true) }
                    composable("map") { Page { Heading(tr("Map")); DataBanner(data); MandiMap(data,null,null,{c,m->nav.navigate("commodity/$c/$m")},{nav.navigate("compare/$it")},{market.region(it.districtId,it.id)}) } }
                }
            }
        }
    }
}
