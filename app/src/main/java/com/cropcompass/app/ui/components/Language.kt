package com.cropcompass.app.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.cropcompass.app.R

@Composable fun tr(text: String): String {
    val id = when (text) {
        "Home" -> R.string.home
        "Prices" -> R.string.prices
        "Compare" -> R.string.compare
        "Watchlist" -> R.string.watchlist
        "Profile" -> R.string.profile
        "District" -> R.string.district
        "Mandi" -> R.string.mandi
        "Choose your region" -> R.string.choose_region
        "Save region" -> R.string.save_region
        "Search commodities" -> R.string.search_commodities
        "Your mandi" -> R.string.your_mandi
        "Today at your mandi" -> R.string.today_mandi
        "Commodity" -> R.string.commodity
        "Save" -> R.string.save
        "Cancel" -> R.string.cancel
        "Delete" -> R.string.delete
        "Edit" -> R.string.edit
        "Map" -> R.string.map
        "List" -> R.string.list
        "Report a price" -> R.string.report_price
        "My reports" -> R.string.my_reports
        "About the data" -> R.string.about_data
        "Market insights" -> R.string.insights
        "Best mandi" -> R.string.best_mandi
        "TEST DATA — not real prices" -> R.string.test_data
        "Stale" -> R.string.stale
        "Retry" -> R.string.retry
        else -> return text
    }
    return stringResource(id)
}
