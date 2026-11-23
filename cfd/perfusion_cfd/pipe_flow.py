"""Axisymmetric transient laminar pipe-flow demonstrator.

Solves du/dt = G/rho + nu*(1/r)*d/dr(r*du/dr) in a rigid circular tube.
Cell-centred finite volumes are used radially; explicit Euler is bounded by
a conservative diffusion time step. This is not a full 3-D CFD solver.
"""
from __future__ import annotations
from dataclasses import dataclass
import math
import numpy as np

@dataclass(frozen=True)
class PipeFlowResult:
    radius_m: np.ndarray
    axial_velocity_m_s: np.ndarray
    time_s: float
    dt_s: float
    steps: int
    max_velocity_m_s: float
    mean_velocity_m_s: float
    analytical_steady_max_m_s: float
    relative_l2_error_to_steady: float

def poiseuille_velocity(radius_m: np.ndarray, pipe_radius_m: float,
                         pressure_gradient_pa_m: float,
                         dynamic_viscosity_pa_s: float) -> np.ndarray:
    """Analytical fully developed laminar profile for positive pressure drop."""
    if pipe_radius_m <= 0 or dynamic_viscosity_pa_s <= 0:
        raise ValueError("Pipe radius and dynamic viscosity must be positive.")
    if pressure_gradient_pa_m < 0:
        raise ValueError("Pressure-gradient magnitude must be non-negative.")
    r = np.asarray(radius_m, dtype=float)
    return pressure_gradient_pa_m * np.maximum(pipe_radius_m**2-r**2, 0.0) / (4.0*dynamic_viscosity_pa_s)

def simulate_transient_pipe_flow(*, pipe_radius_m: float = 0.005,
    pressure_gradient_pa_m: float = 1000.0, density_kg_m3: float = 1060.0,
    dynamic_viscosity_pa_s: float = 0.0035, radial_cells: int = 64,
    duration_s: float = 8.0, safety_factor: float = 0.2) -> PipeFlowResult:
    """Simulate startup flow with symmetry at r=0 and no-slip at r=R."""
    vals = (pipe_radius_m, density_kg_m3, dynamic_viscosity_pa_s, duration_s)
    if any(not math.isfinite(v) or v <= 0 for v in vals):
        raise ValueError("Radius, density, viscosity and duration must be finite and positive.")
    if not math.isfinite(pressure_gradient_pa_m) or pressure_gradient_pa_m < 0:
        raise ValueError("Pressure-gradient magnitude must be finite and non-negative.")
    if radial_cells < 8:
        raise ValueError("At least 8 radial cells are required.")
    if not (0.0 < safety_factor <= 0.25):
        raise ValueError("safety_factor must be in (0, 0.25].")
    dr = pipe_radius_m / radial_cells
    r = (np.arange(radial_cells, dtype=float) + 0.5) * dr
    nu = dynamic_viscosity_pa_s / density_kg_m3
    dt_limit = safety_factor * dr * dr / (4.0 * nu)
    steps = max(1, int(math.ceil(duration_s / dt_limit)))
    dt = duration_s / steps
    u = np.zeros(radial_cells, dtype=float)
    acceleration = pressure_gradient_pa_m / density_kg_m3
    for _ in range(steps):
        flux = np.zeros(radial_cells + 1, dtype=float)
        flux[1:-1] = (np.arange(1, radial_cells, dtype=float) * dr) * (u[1:]-u[:-1]) / dr
        flux[-1] = pipe_radius_m * (0.0-u[-1]) / (0.5*dr)
        radial_laplacian = (flux[1:]-flux[:-1]) / (r*dr)
        u += dt * (acceleration + nu*radial_laplacian)
        if not np.all(np.isfinite(u)):
            raise FloatingPointError("Non-finite velocity encountered.")
    mean_velocity = float(2.0/pipe_radius_m**2 * np.sum(u*r*dr))
    steady = poiseuille_velocity(r, pipe_radius_m, pressure_gradient_pa_m, dynamic_viscosity_pa_s)
    denom = float(np.linalg.norm(steady))
    rel_error = float(np.linalg.norm(u-steady)/denom) if denom else float(np.linalg.norm(u))
    return PipeFlowResult(r, u, duration_s, dt, steps, float(np.max(u)), mean_velocity,
        pressure_gradient_pa_m*pipe_radius_m**2/(4.0*dynamic_viscosity_pa_s), rel_error)
