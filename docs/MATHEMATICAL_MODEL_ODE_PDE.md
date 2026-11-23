# PerfusionLab — especificação do núcleo matemático ODE/PDE

**Status:** ODE v0.2.0, PDE experimental v0.3.0 e acoplamento ODE→PDE v0.1.0 implementados em protótipo.  
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

A execução é rejeitada se exceder o orçamento computacional ou sair do intervalo normalizado, sem clipping silencioso. No acoplamento, o orçamento também limita o produto entre passos temporais e nós espaciais para evitar execuções excessivamente custosas.

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


## 5. Campo transiente axial-transversal 2D

O endpoint experimental `POST /api/v2/simulations/coupled-field-preview` calcula um campo (C(x,z,t)), usando a mesma trajetória ODE de vazão para dirigir a advecção axial. A dimensão (z) representa uma faixa transversal idealizada do domínio, não uma geometria anatômica.

[
\frac{\partial C}{\partial t}+u(t)\frac{\partial C}{\partial x}
=D\left(\frac{\partial^2 C}{\partial x^2}+\frac{\partial^2 C}{\partial z^2}\right)-k(C-C_{eq})
]

- Malha cartesiana uniforme (N_x\times N_z), com (\Delta x=L_x/(N_x-1)) e (\Delta z=L_z/(N_z-1)).
- Advecção axial upwind; difusão central nas duas direções; integração SSP-RK2.
- Entrada axial: Dirichlet uniforme em z; saída axial e limites transversais: Neumann de gradiente nulo, com nós fantasmas espelhados.
- A resposta contém snapshots temporais em formato de pontos ((x,z,C)); a bancada renderiza a superfície e permite selecionar o instante.
- O passo é limitado por advecção axial, difusão em X/Z e reação. A API impõe limites de passos e atualizações de nós.

O campo 2D substitui a extrusão gráfica anterior: agora há variação transversal calculada pela equação discretizada. Ainda assim, coeficientes e geometria são homogêneos, o inlet é uniforme e o domínio é uma placa idealizada. Não representa CFD 3D, perfusão capilar, anatomia ou oxigenação clínica.

## 6. Verificação numérica

A suíte automatizada do campo 2D cobre atualmente:

1. dimensões da malha, quantidade de snapshots, instante final e concentração normalizada;
2. invariância de um campo constante quando concentração inicial, entrada e equilíbrio reacional coincidem;
3. ausência de gradiente transversal espúrio quando condições iniciais e de contorno são uniformes em z.

Esses testes verificam invariantes básicos do solver e a consistência da implementação das fronteiras transversais. **Ainda não constituem uma análise de convergência nem uma validação quantitativa de ordem de precisão.**

Próxima etapa de verificação: adicionar um caso de solução manufaturada com advecção nula e difusão pura, comparar a solução numérica com o modo analítico compatível com as condições de contorno e medir o erro L2 em malhas refinadas. Para isso, o modelo deverá permitir prescrever um perfil inicial 2D ou um modo separável sem alterar as condições físicas do caso. A ordem observada deverá ser reportada separadamente para refinamento espacial e temporal, pois o passo explícito atual depende da resolução da malha.


## 7. Limitações

O modelo não representa geometria vascular, distribuição radial, membrana do oxigenador, ligação Hb-O2, curva de dissociação, heterogeneidade tecidual, perfusão capilar ou consumo metabólico medido. Não há validação fisiológica ou clínica. Nenhum indicador deve ser interpretado como estabilidade clínica ou orientação de perfusão.


## 8. Fundação inicial de CFD

A primeira etapa computacional de CFD foi iniciada em cfd/. O problema reduzido resolve a evolução transiente da velocidade axial em um tubo circular reto, rígido, sob hipóteses de fluido incompressível e newtoniano, escoamento laminar e totalmente desenvolvido. A discretização radial usa volumes finitos em células centradas e integração explícita no tempo.

A solução estacionária é comparada ao perfil de Poiseuille. Os testes iniciais cobrem repouso sob gradiente de pressão nulo, perfil analítico, parâmetros inválidos, finitude e aproximação do estado estacionário. Isso constitui verificação inicial de um problema de referência; não valida o modelo contra sangue, cânulas, oxigenadores ou dados clínicos.

A especificação e as limitações estão em CFD_AND_3D_ROADMAP.md.
