package com.perfusionlab.odepreview;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc
class OdePreviewApiIntegrationTest {
    @Autowired
    private MockMvc mvc;

    @Test
    void healthEndpointIsAvailableWithoutDatabaseOrQueue() throws Exception {
        mvc.perform(get("/api/v1/health"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.status").value("UP"));
    }

    @Test
    void odePreviewReturnsTimeSeriesUsingExistingContract() throws Exception {
        String request = """
            {
              "initialFlowLMin": 3.0,
              "targetFlowLMin": 4.8,
              "hemoglobinGdL": 10.0,
              "arterialSaturation": 0.98,
              "systemicVascularResistance": 1200.0,
              "bodySurfaceAreaM2": 1.9,
              "arterialComplianceMlPerMmHg": 2.0,
              "flowTimeConstantSeconds": 3.0,
              "venousPressureMmHg": 5.0,
              "durationSeconds": 2.0,
              "stepSeconds": 0.1
            }
            """;

        mvc.perform(post("/api/v2/simulations/ode-preview")
                .contentType(MediaType.APPLICATION_JSON)
                .content(request))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.modelVersion").value("0.2.0-ode-prototype"))
            .andExpect(jsonPath("$.integrator").value("RK4_FIXED_STEP"))
            .andExpect(jsonPath("$.points.length()").value(21))
            .andExpect(jsonPath("$.points[0].timeSeconds").value(0.0))
            .andExpect(jsonPath("$.points[20].timeSeconds").value(2.0))
            .andExpect(jsonPath("$.limitations").isNotEmpty());
    }

    @Test
    void invalidParametersAreRejected() throws Exception {
        String request = """
            {
              "initialFlowLMin": 3.0,
              "targetFlowLMin": 11.0,
              "hemoglobinGdL": 10.0,
              "arterialSaturation": 0.98,
              "systemicVascularResistance": 1200.0,
              "bodySurfaceAreaM2": 1.9,
              "arterialComplianceMlPerMmHg": 2.0,
              "flowTimeConstantSeconds": 3.0,
              "venousPressureMmHg": 5.0,
              "durationSeconds": 2.0,
              "stepSeconds": 0.1
            }
            """;

        mvc.perform(post("/api/v2/simulations/ode-preview")
                .contentType(MediaType.APPLICATION_JSON)
                .content(request))
            .andExpect(status().isBadRequest());
    }
    @Test
    void coupledPreviewUsesOdeFlowToDrivePdeVelocity() throws Exception {
        String request = """
            {
              "ode": {
                "initialFlowLMin": 2.0,
                "targetFlowLMin": 6.0,
                "hemoglobinGdL": 10.0,
                "arterialSaturation": 0.98,
                "systemicVascularResistance": 1200.0,
                "bodySurfaceAreaM2": 1.9,
                "arterialComplianceMlPerMmHg": 2.0,
                "flowTimeConstantSeconds": 3.0,
                "venousPressureMmHg": 5.0,
                "durationSeconds": 2.0,
                "stepSeconds": 0.1
              },
              "effectiveCrossSectionAreaMm2": 10000.0,
              "domainLengthMm": 20.0,
              "dispersionMm2PerSecond": 0.15,
              "consumptionRatePerSecond": 0.025,
              "equilibriumConcentration": 0.25,
              "initialConcentration": 0.2,
              "inletConcentration": 0.95,
              "spatialNodes": 41
            }
            """;

        mvc.perform(post("/api/v2/simulations/coupled-preview")
                .contentType(MediaType.APPLICATION_JSON)
                .content(request))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.modelVersion").value("0.1.0-ode-pde-coupled"))
            .andExpect(jsonPath("$.odeTrajectory.length()").value(21))
            .andExpect(jsonPath("$.velocityTrajectory.length()").value(21))
            .andExpect(jsonPath("$.velocityTrajectory[20].axialVelocityMmPerSecond").isNumber())
            .andExpect(jsonPath("$.finalConcentrationProfile.length()").value(41))
            .andExpect(jsonPath("$.finalConcentrationProfile[0].concentration").value(0.95))
            .andExpect(jsonPath("$.limitations").isNotEmpty());
    }

    @Test
    void coupledPreviewRejectsMissingOrInvalidEffectiveArea() throws Exception {
        String request = """
            {
              "ode": {
                "initialFlowLMin": 2.0,
                "targetFlowLMin": 6.0,
                "hemoglobinGdL": 10.0,
                "arterialSaturation": 0.98,
                "systemicVascularResistance": 1200.0,
                "bodySurfaceAreaM2": 1.9,
                "arterialComplianceMlPerMmHg": 2.0,
                "flowTimeConstantSeconds": 3.0,
                "venousPressureMmHg": 5.0,
                "durationSeconds": 2.0,
                "stepSeconds": 0.1
              },
              "effectiveCrossSectionAreaMm2": 0.0,
              "domainLengthMm": 20.0,
              "dispersionMm2PerSecond": 0.15,
              "consumptionRatePerSecond": 0.025,
              "equilibriumConcentration": 0.25,
              "initialConcentration": 0.2,
              "inletConcentration": 0.95,
              "spatialNodes": 41
            }
            """;
        mvc.perform(post("/api/v2/simulations/coupled-preview")
                .contentType(MediaType.APPLICATION_JSON)
                .content(request))
            .andExpect(status().isBadRequest());
    }

}
