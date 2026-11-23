package com.perfusionlab.mobile

import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import retrofit2.http.Body
import retrofit2.http.POST

data class OdeSimulationRequest(
    val initialFlowLMin: Double,
    val targetFlowLMin: Double,
    val hemoglobinGdL: Double,
    val arterialSaturation: Double,
    val systemicVascularResistance: Double,
    val bodySurfaceAreaM2: Double,
    val arterialComplianceMlPerMmHg: Double,
    val flowTimeConstantSeconds: Double,
    val venousPressureMmHg: Double,
    val durationSeconds: Double,
    val stepSeconds: Double
)
data class OdeSimulationPoint(
    val timeSeconds: Double, val flowLMin: Double, val pressureMmHg: Double,
    val oxygenDeliveryMlMin: Double, val oxygenDeliveryIndexMlMinM2: Double
)
data class OdeSimulationResponse(
    val modelVersion: String, val integrator: String, val generatedAt: String,
    val stepSeconds: Double, val points: List<OdeSimulationPoint>,
    val modelDescription: String, val limitations: String
)

data class CoupledSimulationRequest(
    val ode: OdeSimulationRequest,
    val effectiveCrossSectionAreaMm2: Double,
    val domainLengthMm: Double,
    val dispersionMm2PerSecond: Double,
    val consumptionRatePerSecond: Double,
    val equilibriumConcentration: Double,
    val initialConcentration: Double,
    val inletConcentration: Double,
    val spatialNodes: Int,
    val initialProfile: List<Double>? = null
)
data class CoupledVelocityPoint(
    val timeSeconds: Double,
    val flowLMin: Double,
    val axialVelocityMmPerSecond: Double
)
data class CoupledProfilePoint(val xMm: Double, val concentration: Double)
data class CoupledSimulationResponse(
    val modelVersion: String,
    val generatedAt: String,
    val effectiveCrossSectionAreaMm2: Double,
    val domainLengthMm: Double,
    val spatialNodes: Int,
    val dxMm: Double,
    val pdeTimeStepSeconds: Double,
    val simulatedDurationSeconds: Double,
    val maximumAxialVelocityMmPerSecond: Double,
    val meanAxialVelocityMmPerSecond: Double,
    val odeTrajectory: List<OdeSimulationPoint>,
    val velocityTrajectory: List<CoupledVelocityPoint>,
    val finalConcentrationProfile: List<CoupledProfilePoint>,
    val outletConcentration: Double,
    val minimumConcentration: Double,
    val maximumConcentration: Double,
    val couplingDescription: String,
    val limitations: String
)

interface PerfusionApi {
    @POST("api/v2/simulations/ode-preview")
    suspend fun runOdePreview(@Body request: OdeSimulationRequest): OdeSimulationResponse

    @POST("api/v2/simulations/coupled-preview")
    suspend fun runCoupledPreview(@Body request: CoupledSimulationRequest): CoupledSimulationResponse
}
object ApiClient {
    val service: PerfusionApi by lazy {
        Retrofit.Builder().baseUrl(BuildConfig.API_BASE_URL)
            .addConverterFactory(GsonConverterFactory.create())
            .build().create(PerfusionApi::class.java)
    }
}