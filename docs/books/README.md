# Apostilas do PerfusionLab

Duas apostilas independentes, com escopos complementares:

1. **Perfusão e CEC** (perfusion-cec/main.tex): fundamentos médicos, fisiologia cardiovascular, componentes do circuito, monitorização e interpretação conceitual.
2. **Engenharia aplicada à perfusão** (engineering-mathematics/main.tex): conservação, análise dimensional, EDOs, EDPs, Navier–Stokes, métodos numéricos, CFD, geometria 3D, verificação e validação.

## Compilação

Requisito: uma distribuição LaTeX com latexmk ou pdflatex, incluindo os pacotes listados nos preâmbulos.

~~~bash
cd docs/books/perfusion-cec
latexmk -pdf main.tex

cd ../engineering-mathematics
latexmk -pdf main.tex
~~~

As apostilas estão em desenvolvimento e devem receber revisão técnica de profissionais de perfusão, cirurgia cardiovascular, fisiologia e engenharia antes de serem consideradas materiais didáticos finais. Não são protocolos assistenciais.
