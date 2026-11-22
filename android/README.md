# PerfusionLab Android — MVP

Aplicativo nativo Android em Kotlin + Jetpack Compose. Esta pasta inicia a estrutura do cliente móvel; ainda não é um APK publicado.

## Escopo do MVP
- Tela inicial com identificação do modelo e aviso educacional.
- Formulário de parâmetros.
- Disparo de simulação pela API.
- Visualização da série temporal e métricas retornadas.
- Histórico local simples de cenários (fase posterior).

## Decisões técnicas
- Kotlin + Jetpack Compose + Material 3.
- Coroutines para chamadas assíncronas.
- Retrofit/OkHttp para REST, a integrar com o contrato ODE.
- ViewModel + StateFlow para estado de tela.
- Matemática no backend/model-core; o Android não mantém uma segunda implementação.
- Min SDK 26 como alvo inicial.

## Estado desta entrega
Estrutura inicial do projeto e tela de entrada. A integração REST e o gráfico serão adicionados junto com o contrato versionado do motor ODE. O app não deve exibir resultados calculados localmente como se fossem resultados do motor científico.

## Abrir no Android Studio
Abra android/ no Android Studio com Android SDK e JDK compatíveis. O projeto usa Gradle Kotlin DSL e plugin Android/Compose. O wrapper Gradle deve ser gerado pelo Android Studio na primeira abertura, caso não esteja presente no repositório.
