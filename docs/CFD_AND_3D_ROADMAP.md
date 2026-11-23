# PerfusionLab — início de CFD e modelagem 3D

**Estado:** fundação computacional exploratória v0.1.0.  
**Uso:** pesquisa e educação; não é dispositivo médico nem ferramenta de decisão clínica.

## CFD: primeiro problema de verificação

O primeiro solver resolve a equação axial transiente para escoamento laminar, incompressível, newtoniano e totalmente desenvolvido em tubo circular rígido:

\[
\frac{\partial u}{\partial t}=\frac{G}{\rho}
+\nu\frac{1}{r}\frac{\partial}{\partial r}
\left(r\frac{\partial u}{\partial r}\right),\quad 0<r<R.
\]

Condições: simetria no eixo (\partial u/\partial r=0 em r=0), não deslizamento (u=0 em r=R), repouso inicial e gradiente de pressão G=-\partial p/\partial z constante e não negativo.

O estado estacionário de referência é Poiseuille:

\[
u_{est}(r)=\frac{G}{4\mu}(R^2-r^2),\qquad
u_{max}=\frac{GR^2}{4\mu}.
\]

A discretização radial é por volumes finitos em células centradas; o tempo usa Euler explícito com limite conservador de passo. Os testes verificam repouso para gradiente nulo, perfil analítico, parâmetros inválidos, finitude e aproximação do estado estacionário.

## O que este solver ainda não é

Não resolve Navier–Stokes 3D, curvaturas, bifurcações, fluxo pulsátil, turbulência, interação fluido-estrutura, sangue não newtoniano, cânulas reais, oxigenador ou circuito completo. Não extrapolar resultados para pacientes, dispositivos ou parâmetros de perfusão.

## Próximas etapas

1. Verificação independente e refinamento de malha/tempo.
2. Soluções manufaturadas e estudo de ordem de convergência.
3. Extensão para fluxo pulsátil e desenvolvimento axial.
4. Geometria CAD parametrizada e exportação STL/STEP, após revisão dimensional.
5. Malha CFD e comparação entre solver próprio e ferramenta de referência.
6. Escoamento em curvas/conectores; depois, modelos 3D mais completos.
7. Acoplamento ODE–PDE–CFD somente após verificação independente.

## Modelo 3D inicial

`geometry/perfusion_circuit.scad` contém montagem esquemática paramétrica com reservatório, cabeça de bomba, câmara de oxigenador, tubulações e cânulas estilizadas. A geometria é conceitual: dimensões, conectores e volumes não representam produtos comerciais nem anatomia.

## Reprodutibilidade

Na pasta `cfd/`:

~~~bash
python -m pip install -e '.[test]'
pytest
~~~

Os valores padrão são parâmetros ilustrativos para exercitar o solver, não valores recomendados para uso clínico.
