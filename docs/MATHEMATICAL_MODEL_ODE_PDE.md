# PerfusionLab — especificação do núcleo matemático ODE/PDE

**Status:** ODE v0.2.0 e PDE experimental v0.3.0 implementados em protótipo.  
**Escopo:** simulação educacional e pesquisa computacional; não validado clinicamente, não é dispositivo médico e não deve orientar condutas em pacientes.

## 1. Estratégia de modelagem

1. **EDOs concentradas:** resposta de fluxo e pressão equivalente.
2. **EDP distribuída:** transporte convectivo-difusivo-reacional unidimensional de concentração equivalente.
3. **Acoplamento experimental ODE→PDE:** a trajetória de fluxo da ODE alimenta a velocidade axial temporal da PDE. O acoplamento é unidirecional; não há feedback da PDE para a ODE.

## 2. ODE existente

A dinâmica do fluxo é `tau_Q dQ/dt = Q_cmd - Q`; a pressão usa um compartimento resistência-complacência. O integrador é RK4 com subpassos internos. DO2/DO2i são estimativas derivadas e não representam oxigenação tecidual dinâmica.

## 3. PDE experimental

[
\frac{\partial C}{\partial t}+u\frac{\partial C}{\partial x}
=D\frac{\partial^2 C}{\partial x^2}-k(C-C_{eq})
]

- (C(x,t)): concentração equivalente adimensional normalizada em [0,1].
- (u): velocidade axial (mm/s).
- (D): dispersão efetiva (mm²/s).
- (k): taxa fenomenológica de consumo/relaxamento (1/s).
- (C_{eq}): concentração de equilíbrio do termo reacional.

O termo de reação é fenomenológico; ainda não equivale a consumo metabólico medido nem descreve difusão capilar completa.

### Condições de contorno e inicial

- Entrada: Dirichlet, (C(0,t)=C_{in}).
- Saída: Neumann, (\partial C/\partial x|_{x=L}=0), implementada por nó fantasma espelhado.
- Condição inicial padrão: uniforme (C(x,0)=C_0).
- Para verificação numérica, a API também aceita `initialProfile`, com exatamente um valor normalizado por nó.

### Discretização

- Malha uniforme: (\Delta x=L/(N-1)).
- Advecção: upwind.
- Difusão: diferenças centrais.
- Tempo: SSP-RK2 (Heun), de segunda ordem temporal e com propriedade strong-stability-preserving.
- Passo conservador adotado para as etapas de Euler que compõem o SSP-RK2:

[
\Delta t \leq \frac{0.8}{u/\Delta x+2D/\Delta x^2+k}
]

A execução é rejeitada se exceder o orçamento computacional ou sair do intervalo normalizado, sem clipping silencioso.

### Endpoint

`POST /api/v2/simulations/pde-transport-preview`

O endpoint retorna perfil espacial final, dx, dt efetivo, extremos de concentração, condições de contorno e limitações.


## 4. Acoplamento ODE→PDE experimental

O endpoint `POST /api/v2/simulations/coupled-preview` executa a ODE e utiliza cada ponto da trajetória de fluxo para dirigir o transporte PDE.

A conversão dimensional é explícita:

[
u(t)=\frac{Q(t)\;10^6}{60\,A_{eff}}
]

- (Q(t)): fluxo da ODE em L/min.
- (A_{eff}): área transversal efetiva fornecida pelo usuário em mm².
- (u(t)): velocidade axial em mm/s.
- O fator (10^6) converte litros em mm³; o fator 60 converte minutos em segundos.

A área é um parâmetro efetivo abstrato do domínio computacional. Não deve ser interpretada como área anatômica, diâmetro de vaso, área de cânula ou medida do circuito. O usuário precisa declará-la; não há inferência automática.

A velocidade é interpolada linearmente entre os pontos da trajetória ODE. O solver PDE usa o maior valor de velocidade previsto para estabelecer o limite conservador de passo explícito e executa SSP-RK2 com velocidade avaliada nas duas etapas. A resposta contém as trajetórias ODE e de velocidade, perfil espacial final, velocidade máxima/média, passo temporal e limitações.

**Natureza do acoplamento:** unidirecional e exploratório. O fluxo da ODE influencia a advecção da PDE; pressão e concentração não retroagem sobre a ODE. A concentração permanece adimensional e não representa saturação sanguínea ou oxigenação tecidual mensurada.

## 5. Verificação numérica

A suíte inclui:
1. preservação de equilíbrio constante;
2. concentração normalizada e condição de saída;
3. refinamento de malha contra referência mais fina;
4. **solução manufaturada:** difusão pura com modo (C(x,0)=\sin(\pi x/(2L))), entrada nula e saída de gradiente nulo. A solução analítica é (C(x,t)=\sin(\pi x/(2L))e^{-D(\pi/(2L))^2t}).
5. estimativa da ordem observada pela redução do erro L2 em malhas sucessivamente refinadas. O passo temporal também é reduzido com dx², portanto o teste mede a convergência conjunta do esquema explícito sob essa política de passo.

A resposta numérica preserva 12 casas decimais para não mascarar erros pequenos nos testes de convergência. O SSP-RK2 é implementado como combinação convexa de duas etapas de Euler; por isso, mantém o limite de monotonicidade da etapa Euler sob o passo indicado.

## 6. Limitações

O modelo não representa geometria vascular, distribuição radial, membrana do oxigenador, ligação Hb-O2, curva de dissociação, heterogeneidade tecidual, perfusão capilar ou consumo metabólico medido. Não há validação fisiológica ou clínica. Nenhum indicador deve ser interpretado como estabilidade clínica ou orientação de perfusão.
