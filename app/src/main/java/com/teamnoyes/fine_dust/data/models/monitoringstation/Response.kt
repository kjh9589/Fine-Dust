package com.teamnoyes.fine_dust.data.models.monitoringstation


import com.google.gson.annotations.SerializedName

data class Response(
    @SerializedName("body")
    val body: Body?,
    @SerializedName("header")
    val header: Header?
)