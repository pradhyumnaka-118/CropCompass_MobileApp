package com.cropcompass.app.data.repository

import android.content.Context
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import java.io.FileNotFoundException
import java.time.LocalDate
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

data class AnalyticsDocument(val kind:String,val start:String,val end:String,val rows:List<JsonObject>)
data class AnalyticsResult(val documents:List<AnalyticsDocument>,val errors:List<String>)
object AnalyticsParser {
    fun parse(kind:String,json:String,data:MarketSnapshot):AnalyticsDocument {
        val root=JsonParser.parseString(json).asJsonObject
        require(root.get("schemaVersion").asInt==1){"Unsupported analytics schema."}
        require(root.get("dataSource").asString==data.info?.dataSource && root.get("dataVersion").asString==data.info?.dataVersion){"Analytics do not match the installed data version/source."}
        java.time.Instant.parse(root.get("generatedAt").asString)
        val unit=when(kind){"top_commodity_by_district"->"TONNES";"category_value_totals"->"INR";else->"INR_QUINTAL"}
        require(root.get("unit").asString==unit){"Incorrect analytics unit."}
        val start=root.get("periodStart").asString;val end=root.get("periodEnd").asString
        require(LocalDate.parse(start)<=LocalDate.parse(end))
        val rows=root.getAsJsonArray("records").map{it.asJsonObject}
        for(row in rows){
            val required=when(kind){
                "mandi_volatility"->listOf("stddevPrice","meanModalPrice","observationCount")
                "district_price_dispersion"->listOf("minModalPrice","maxModalPrice","medianModalPrice","mandiCount")
                "top_commodity_by_district"->listOf("arrivalsTonnes","observationCount")
                "category_value_totals"->listOf("estimatedValueInr","recordsUsed")
                else->error("Unknown analytics file.")
            }
            required.forEach{key->val value=row.get(key).asDouble;require(value.isFinite() && value>=0){"Invalid analytics value: $key"}}
            if(kind=="category_value_totals")require(row.get("category").asString.isNotBlank())
            if(kind=="mandi_volatility")require(data.mandis.any{it.id==row.get("mandiId").asString})
            if(kind!="category_value_totals")require(data.commodities.any{it.id==row.get("commodityId").asString})
            if(kind in listOf("district_price_dispersion","top_commodity_by_district"))require(data.districts.any{it.id==row.get("districtId").asString})
        }
        return AnalyticsDocument(kind,start,end,rows)
    }
    suspend fun load(context:Context,data:MarketSnapshot):AnalyticsResult=withContext(Dispatchers.IO){
        val docs=mutableListOf<AnalyticsDocument>();val errors=mutableListOf<String>()
        listOf("mandi_volatility","district_price_dispersion","top_commodity_by_district","category_value_totals").forEach{kind->
            try{val json=context.assets.open("analytics/$kind.json").bufferedReader().use{it.readText()};docs.add(parse(kind,json,data))}
            catch(_:FileNotFoundException){}catch(e:Exception){errors.add("${kind.replace('_',' ')}: ${e.message ?: "Could not load this insight"}")}
        };AnalyticsResult(docs,errors)
    }
}
