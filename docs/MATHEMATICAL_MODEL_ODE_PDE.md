# PerfusionLab — especificação do núcleo matemático ODE/PDE

**Status:** proposta de modelo v0.1 para prototipação e discussão científica.  
**Escopo:** simulação educacional e pesquisa computacional; não validado clinicamente, não é dispositivo médico e não deve orientar condutas em pacientes.

## 1. Estratégia de modelagem

O modelo será híbrido e hierárquico:
1. **EDOs (modelo concentrado):** dinâmica global do circuito, pressão arterial equivalente, fluxo de bomba e compartimento de oxigênio.
2. **EDPs (modelo distribuído):** transporte convectivo-difusivo ao longo de um segmento do circuito ou módulo de oxigenador.
3. **Acoplamento:** as EDPs fornecem grandezas agregadas às EDOs; as EDOs fornecem condições de contorno às EDPs.

A primeira entrega executável deve ser um modelo ODE de baixa dimensão. A EDP entra como módulo experimental independente, depois de verificação numérica.

## 2. Variáveis e parâmetros ODE

| Símbolo | Descrição | Unidade |
|---|---|---|
| Q(t) | fluxo efetivo no circuito | L/min |
| P(t) | pressão equivalente do compartimento arterial | mmHg |
| S(t) | estoque/conteúdo equivalente de oxigênio tecidual | normalizado |

Entradas: fluxo-alvo Qcmd, resistência sistêmica Rsys, complacência arterial Cart, hemoglobina Hb, saturação SaO2, pressão parcial PaO2 e consumo VO2.

### 2.1 Dinâmica do fluxo

Resposta de primeira ordem ao comando da bomba:

\[
\tau_Q \frac{dQ}{dt}=Q_{cmd}(t)-Q(t)
\]

O parâmetro tau_Q representa um tempo de resposta agregado, não a dinâmica detalhada de uma bomba específica.

### 2.2 Dinâmica de pressão

Modelo concentrado complacência-resistência:

\[
C_{art}\frac{dP}{dt}=Q(t)-\frac{P(t)-P_v}{R_{sys}}
\]

As unidades devem ser convertidas para um sistema consistente antes da implementação. É uma aproximação Windkessel de um compartimento, não uma representação anatômica completa.

### 2.3 Transporte e estoque de oxigênio

Conteúdo arterial estimado:

\[
C_{aO_2}=1.34\,Hb\,SaO_2+0.003\,PaO_2
\]

Entrega de oxigênio estimada:

\[
DO_2=10\,Q\,C_{aO_2}
\]

Balanço fenomenológico exploratório para o estoque normalizado:

\[
\frac{dS}{dt}=k_{in}\,\frac{DO_2}{DO_{2,ref}}-k_{out}\,\frac{VO_2}{VO_{2,ref}}
\]

Esta equação é uma hipótese de trabalho, não fisiologia estabelecida. Coeficientes, escalas, limites e interpretação exigem fundamentação bibliográfica, análise dimensional, identificação de parâmetros e validação.

## 3. Módulo EDP de transporte axial

Para segmento unidimensional:

\[
\frac{\partial C}{\partial t}+u\frac{\partial C}{\partial x}
=D\frac{\partial^2 C}{\partial x^2}-k(C-C_{eq})
\]

C(x,t) é uma concentração equivalente, u é velocidade média, D é dispersão/difusão efetiva e k é termo fenomenológico de troca/relaxamento. A equação é uma plataforma matemática inicial; a escolha das grandezas depende do domínio físico (tubulação, oxigenador ou compartimento).

Condições iniciais para protótipo:
- C(x,0)=C0(x);
- entrada prescrita C(0,t)=Cin(t);
- saída convectiva com gradiente difusivo nulo, como hipótese inicial.

A formulação deverá ser revista se o domínio exigir membrana, duas fases, fluxo não newtoniano, distribuição radial ou geometria complexa.

## 4. Estratégia numérica

### EDO
- Integrador de referência: Runge–Kutta de quarta ordem (RK4).
- Comparação posterior: Dormand–Prince adaptativo (RK45) e análise de convergência.
- Registrar passo temporal, tolerâncias, versão e parâmetros.
- Testar positividade e limites físicos; não mascarar instabilidades com clipping silencioso.

### EDP
- Discretização espacial inicial: diferenças finitas, advecção upwind e difusão central.
- Integrador explícito somente com restrições CFL/difusivas verificadas.
- Evolução posterior para esquema implícito ou Crank–Nicolson.
- Testar refinamento de malha, conservação, estabilidade e soluções manufaturadas.

## 5. Arquitetura de software

- model-core: equações, unidades, parâmetros, integradores e testes independentes da interface.
- api: contratos REST versionados, validação, persistência e execução de jobs.
- android: cliente nativo Kotlin + Jetpack Compose.
- web: painel existente, preservado como cliente adicional.
- pde-lab: módulo experimental isolado até passar pelos testes numéricos.

A API deverá retornar série temporal, parâmetros, versão do modelo, método numérico, passo temporal e avisos. O Android não deve reimplementar as equações: deve consumir a API como fonte única da verdade.

## 6. Critérios mínimos de verificação

1. Testes unitários das equações e conversões de unidade.
2. Soluções de equilíbrio e casos limites conhecidos.
3. Convergência temporal (reduzir Δt e comparar resultados).
4. Sensibilidade paramétrica e análise de identificabilidade.
5. Testes de conservação/estabilidade para o módulo PDE.
6. Reprodutibilidade por versão, parâmetros e método.
7. Revisão por especialistas em perfusão, fisiologia e engenharia biomédica.

## 7. Limites explícitos

O protótipo atual calcula indicadores estáticos e timelines por interpolação de entradas. Isso **não** equivale a integrar EDOs. A nova versão deverá ser identificada como modelo dinâmico e não substituir silenciosamente os resultados anteriores. Nenhum rótulo de estabilidade matemática será apresentado como estabilidade clínica.
