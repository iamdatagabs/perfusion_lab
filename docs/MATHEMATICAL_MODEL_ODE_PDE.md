# PerfusionLab — especificação do núcleo matemático ODE/PDE

**Status:** ODE v0.2.0 e PDE experimental v0.1.0 implementados em protótipo.  
**Escopo:** simulação educacional e pesquisa computacional; não validado clinicamente, não é dispositivo médico e não deve orientar condutas em pacientes.

## 1. Estratégia de modelagem

O núcleo é híbrido e hierárquico:
1. **EDOs concentradas:** resposta de fluxo e pressão equivalente.
2. **EDP distribuída:** transporte convectivo-difusivo-reacional unidimensional de concentração equivalente.
3. **Acoplamento futuro:** variáveis agregadas e condições de contorno poderão conectar os módulos após verificação independente.

## 2. ODE existente

A dinâmica do fluxo é `tau_Q dQ/dt = Q_cmd - Q`; a pressão usa um compartimento resistência-complacência. O integrador é RK4 com subpassos internos. DO2/DO2i são estimativas derivadas e não representam oxigenação tecidual dinâmica.

## 3. PDE experimental de transporte/consumo

O primeiro módulo implementa:

[
\frac{\partial C}{\partial t}
+u\frac{\partial C}{\partial x}
=D\frac{\partial^2 C}{\partial x^2}
-k(C-C_{eq})
]

- (C(x,t)): concentração equivalente adimensional normalizada em [0,1].
- (u): velocidade axial (mm/s).
- (D): dispersão efetiva (mm²/s).
- (k): taxa de consumo/relaxamento fenomenológica (1/s).
- (C_{eq}): concentração de equilíbrio do termo reacional.

Esta é uma equação reduzida de transporte e reação, não um modelo anatômico completo de microcirculação ou difusão capilar. O termo reacional é fenomenológico; sua interpretação como consumo metabólico exige refinamento e calibração experimental.

### Condições

- Inicial: (C(x,0)=C_0), campo uniforme.
- Entrada: (C(0,t)=C_{in}), condição de Dirichlet.
- Saída: (\partial C/\partial x|_{x=L}=0), condição de Neumann homogênea.

### Discretização numérica

- Malha uniforme com (N) nós e (\Delta x=L/(N-1)).
- Advecção: diferenças upwind, selecionadas pelo sinal de (u).
- Difusão: diferenças centrais de segunda ordem.
- Tempo: Euler explícito.
- Passo automático limitado por condição suficiente de monotonicidade:

[
\Delta t \leq \frac{0.8}{|u|/\Delta x+2D/\Delta x^2+k}
]

O serviço rejeita execuções que ultrapassem o limite computacional ou produzam concentração fora de [0,1]; não aplica clipping silencioso.

### Endpoint

`POST /api/v2/simulations/pde-transport-preview`

Parâmetros: `domainLengthMm`, `velocityMmPerSecond`, `dispersionMm2PerSecond`, `consumptionRatePerSecond`, `equilibriumConcentration`, `initialConcentration`, `inletConcentration`, `durationSeconds`, `spatialNodes`.

A resposta informa perfil espacial final, dx, passo temporal usado, extremos da concentração, condições de contorno e limitações.

## 4. Verificação e convergência

Os testes incluem:
1. equilíbrio constante preservado;
2. limites normalizados e condição de saída;
3. refinamento espacial com comparação L2 contra malha de referência mais fina;
4. CI Maven e construção da imagem Docker.

O estudo de convergência automatizado é uma verificação numérica inicial, não uma validação fisiológica. Próxima etapa: solução manufaturada com erro observado e ordem de convergência, balanço de massa e comparação com esquema implícito.

## 5. Limites

O modelo ainda não representa geometria vascular, distribuição radial, membrana do oxigenador, ligação Hb-O2, curvas de dissociação, heterogeneidade tecidual, perfusão capilar ou consumo metabólico medido. Nenhum indicador numérico deve ser interpretado como estabilidade clínica ou orientação de perfusão.
