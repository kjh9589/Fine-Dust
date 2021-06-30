package com.teamnoyes.fine_dust

import android.Manifest.permission.*
import android.annotation.SuppressLint
import android.content.pm.PackageManager
import android.os.Build
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.RequiresApi
import androidx.core.content.ContextCompat
import androidx.core.view.isGone
import androidx.core.view.isVisible
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationServices
import com.google.android.gms.tasks.CancellationTokenSource
import com.teamnoyes.fine_dust.data.Repository
import com.teamnoyes.fine_dust.data.models.airquality.Grade
import com.teamnoyes.fine_dust.data.models.airquality.MeasuredValue
import com.teamnoyes.fine_dust.data.models.monitoringstation.MonitoringStation
import com.teamnoyes.fine_dust.databinding.ActivityMainBinding
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

class MainActivity : AppCompatActivity() {
    companion object {
        private val PERMISSIONS_REQUEST = arrayOf(
            ACCESS_FINE_LOCATION,
            ACCESS_COARSE_LOCATION
        )

        @RequiresApi(Build.VERSION_CODES.Q)
        private val BACKGROUND_REQUEST = arrayOf(
            ACCESS_BACKGROUND_LOCATION
        )
    }

    //locationManager보다 권장됨
    private lateinit var fusedLocationProviderClient: FusedLocationProviderClient
    private var cancellationTokenSource: CancellationTokenSource? = null

    private val binding by lazy { ActivityMainBinding.inflate(layoutInflater) }
    private val scope = MainScope()

    private val requestPermissions = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) {
        for (permission in it.entries) {
            if (!permission.value) {
                Toast.makeText(this, "권한이 필요합니다.", Toast.LENGTH_SHORT).show()
                finish()
            }
        }

        // 원래라면 백그라운드 기능 실행시 요청
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            if (ContextCompat.checkSelfPermission(this, ACCESS_BACKGROUND_LOCATION) != PackageManager.PERMISSION_GRANTED){
                backgroundPermission.launch(BACKGROUND_REQUEST)
            } else {
                fetchAirQualityData()
            }
        } else {
            fetchAirQualityData()
        }
    }

    private val backgroundPermission = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) {
        for (permission in it.entries) {
            if (!permission.value) {
                Toast.makeText(this, "권한이 필요합니다.", Toast.LENGTH_SHORT).show()
                finish()
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(binding.root)

        bindViews()
        initVariables()
        requestPermissions.launch(PERMISSIONS_REQUEST)
    }

    private fun bindViews() {
        binding.refresh.setOnRefreshListener {
            fetchAirQualityData()
        }
    }

    private fun initVariables() {
        fusedLocationProviderClient = LocationServices.getFusedLocationProviderClient(this)
    }

    @SuppressLint("MissingPermission")
    private fun fetchAirQualityData() {
        cancellationTokenSource = CancellationTokenSource()
        fusedLocationProviderClient.getCurrentLocation(
            LocationRequest.PRIORITY_HIGH_ACCURACY,
            cancellationTokenSource!!.token
        ).addOnSuccessListener { location ->
            scope.launch {
                binding.errorDescriptionTextView.isGone = true
                try {
                    val monitoringStation =
                        Repository.getNearbyMonitoringStation(location.latitude, location.longitude)

                    val measuredValue = Repository.getLatestAirQualityData(monitoringStation!!.stationName!!)

                    displayAirQualityData(monitoringStation, measuredValue!!)
                } catch (e: Exception) {
                    binding.errorDescriptionTextView.isVisible = true
                    binding.contentsLayout.alpha = 0F

                } finally {
                    binding.progressBar.isGone = true
                    binding.refresh.isRefreshing = false
                }

            }
        }
    }

    @SuppressLint("SetTextI18n")
    private fun displayAirQualityData(monitoringStation: MonitoringStation, measuredValue: MeasuredValue) = with(binding) {
        contentsLayout.animate()
            .alpha(1F)
            .start()

        measuringStationNameTextView.text = monitoringStation.stationName
        measuringStationAddressTextView.text = monitoringStation.addr

        (measuredValue.khaiGrade ?: Grade.UNKNOWN).let { grade ->
            root.setBackgroundResource(grade.colorResId)
            totalGradeLabelTextView.text = grade.label
            totalGradeEmojiTextView.text = grade.emoji
        }

        with(measuredValue) {
            fineDustInformationTextView.text =
                "미세먼지: $pm10Value ㎍/㎥ ${(pm10Grade ?: Grade.UNKNOWN).emoji}"
            ultraFineDustInformationTextView.text =
                "초미세먼지: $pm25Value ㎍/㎥ ${(pm25Grade ?: Grade.UNKNOWN).emoji}"

            with(so2Item) {
                labelTextView.text = "아황산가스"
                gradeTextView.text = (so2Grade ?: Grade.UNKNOWN).toString()
                valueTextView.text = "$so2Value ppm"
            }

            with(binding.coItem) {
                labelTextView.text = "일산화탄소"
                gradeTextView.text = (coGrade ?: Grade.UNKNOWN).toString()
                valueTextView.text = "$coValue ppm"
            }

            with(binding.o3Item) {
                labelTextView.text = "오존"
                gradeTextView.text = (o3Grade ?: Grade.UNKNOWN).toString()
                valueTextView.text = "$o3Value ppm"
            }

            with(binding.no2Item) {
                labelTextView.text = "이산화질소"
                gradeTextView.text = (no2Grade ?: Grade.UNKNOWN).toString()
                valueTextView.text = "$no2Value ppm"
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        cancellationTokenSource?.cancel()
        scope.cancel()
    }
}