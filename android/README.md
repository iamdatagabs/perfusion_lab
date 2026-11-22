# PerfusionLab Android — MVP

Aplicativo Android nativo em Kotlin + Jetpack Compose.

## Funcionalidades implementadas nesta etapa
- Formulário de parâmetros do modelo ODE.
- Chamada REST assíncrona para `POST /api/v2/simulations/ode-preview`.
- Exibição dos valores finais de fluxo, pressão, DO₂ e DO₂i.
- Gráficos temporais de fluxo e pressão.
- Estados de carregamento e erro.
- Aviso de uso educacional e ausência de validação clínica.

## Configuração da API
O endereço-base é configurado pela propriedade Gradle `PERFUSIONLAB_API_BASE_URL`. O valor padrão reservado é `https://perfusionlab-ode-api.onrender.com/`; o serviço Spring Boot ainda precisa ser provisionado nesse endereço para que a chamada remota funcione. O serviço Node de prévia já existente é separado e não implementa este endpoint.

Para apontar a um backend local ou outro host, defina em `android/gradle.properties`:

```properties
PERFUSIONLAB_API_BASE_URL=https://seu-host-backend/
```

A URL deve terminar com barra. Não inclua credenciais nessa propriedade.

## Build
- Android SDK 35
- JDK 21 (compilação Java/Kotlin com bytecode JVM 17 para compatibilidade do Android)
- Gradle 8.9

O workflow `.github/workflows/android-ci.yml` executa `assembleDebug` em pushes/PRs que alterem o app e publica o APK de debug como artefato de CI.

## Limites
O cliente Android não calcula as equações localmente: os resultados vêm do backend. Sem o serviço Spring Boot ativo no endereço configurado, a tela exibirá um erro de conexão. O modelo continua sendo um protótipo matemático educacional, sem validação clínica.
