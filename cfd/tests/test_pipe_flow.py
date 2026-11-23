import numpy as np
import pytest
from perfusion_cfd.pipe_flow import poiseuille_velocity, simulate_transient_pipe_flow

def test_zero_pressure_gradient_keeps_fluid_at_rest():
    result = simulate_transient_pipe_flow(pressure_gradient_pa_m=0.0, duration_s=0.05, radial_cells=16)
    assert np.allclose(result.axial_velocity_m_s, 0.0)
    assert result.max_velocity_m_s == 0.0

def test_analytical_poiseuille_profile_is_parabolic_and_zero_at_wall():
    r = np.array([0.0, 0.0025, 0.005])
    u = poiseuille_velocity(r, 0.005, 1000.0, 0.0035)
    assert u[0] > u[1] > u[2]
    assert u[2] == pytest.approx(0.0)

def test_transient_solution_is_finite_and_nonnegative():
    result = simulate_transient_pipe_flow(duration_s=0.25, radial_cells=32, pressure_gradient_pa_m=500.0)
    assert result.steps > 0
    assert result.dt_s * result.steps == pytest.approx(result.time_s)
    assert np.all(np.isfinite(result.axial_velocity_m_s))
    assert np.all(result.axial_velocity_m_s >= 0.0)

def test_long_time_profile_approaches_poiseuille_reference():
    result = simulate_transient_pipe_flow(duration_s=8.0, radial_cells=48, pressure_gradient_pa_m=500.0)
    assert result.relative_l2_error_to_steady < 0.03
    assert result.mean_velocity_m_s > 0.0

@pytest.mark.parametrize("kwargs", [
    {"pipe_radius_m": 0.0}, {"density_kg_m3": -1.0},
    {"dynamic_viscosity_pa_s": 0.0}, {"radial_cells": 4},
    {"duration_s": -1.0}, {"pressure_gradient_pa_m": -1.0},
])
def test_invalid_parameters_are_rejected(kwargs):
    with pytest.raises(ValueError):
        simulate_transient_pipe_flow(**kwargs)
