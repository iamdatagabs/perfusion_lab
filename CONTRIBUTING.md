# Contributing to PerfusionLab

Obrigado pelo interesse em contribuir. O projeto está atualmente sob desenvolvimento individual; este guia prepara a entrada gradual de colaboradores sem pressupor uma equipe já constituída.

## Princípios

- Priorizar reprodutibilidade, clareza matemática e rastreabilidade de hipóteses.
- Separar implementação, verificação numérica e validação fisiológica/experimental.
- Não descrever protótipos como dispositivos médicos ou ferramentas clínicas.
- Não inserir dados identificáveis de pacientes, segredos, credenciais ou materiais sem autorização.
- Toda variável física deve ter unidade, convenção de sinal e origem documentadas.

## Áreas de trabalho

- Medicina/perfusão: revisão de conceitos, terminologia, fontes e plausibilidade fisiológica.
- Modelagem matemática: formulação, hipóteses, condições iniciais/de contorno e análise dimensional.
- Software: API, frontend, persistência, filas e testes.
- CFD/CAD: discretização, benchmarks, malhas e geometria paramétrica.

## Fluxo de contribuição

1. Abra uma issue descrevendo pergunta, escopo e critério de aceite.
2. Trabalhe em branch curta derivada da branch indicada na issue.
3. Inclua testes automatizados e documentação junto da implementação.
4. Abra PR com hipótese, alteração, evidências de teste, limitações e impacto nos contratos.
5. Não faça merge de mudanças que alterem significado fisiológico sem revisão especializada.

## Verificações locais

~~~bash
# CFD foundation
python -m pip install -e "./cfd[test]"
python -m pytest cfd/tests

# ODE/PDE preview API
mvn --batch-mode -f backend/ode-preview/pom.xml verify

# Web simulator regression
node --test tests/simulator.test.js
~~~

## Padrão mínimo para modelos científicos

Cada modelo novo deve declarar:
- domínio e variáveis de estado;
- unidades e convenções;
- hipóteses constitutivas;
- condições iniciais e de contorno;
- integrador/discretização e restrições de estabilidade;
- solução analítica, benchmark ou solução manufaturada quando aplicável;
- estudo de refinamento/convergência;
- limitações e uso pretendido.

A aprovação dos testes demonstra apenas que os critérios implementados foram satisfeitos. Não constitui validação clínica.
