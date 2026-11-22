# PerfusionLab Android — MVP

Aplicativo Android nativo em Kotlin + Jetpack Compose.

## Funcionalidades implementadas nesta etapa
- Formulário de parâmetros do modelo ODE.
- Chamada REST assíncrona para `POST /api/v2/simulations/ode-preview`.
- Exibição dos valores finais de fluxo, pressão, DO₂ e DO₂i.
- Gráficos temporais de fluxo e pressão.
- Execução do experimento acoplado ODE→PDE por `POST /api/v2/simulations/coupled-preview`.
- Parâmetros explícitos de área transversal efetiva e dispersão.
- Exibição de velocidade axial média/máxima, concentração de saída, passo temporal e perfil espacial final.
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
- JDK 21
- Gradle 8.9

O workflow `.github/workflows/android-ci.yml` executa `assembleDebug` em pushes/PRs que alterem o app e publica o APK de debug como artefato de CI.

## Contrato do acoplamento
O cliente envia os parâmetros ODE junto com a área transversal efetiva em mm² e os parâmetros do transporte. A resposta inclui a trajetória ODE, a velocidade axial derivada de `Q(t)/A` e o perfil espacial PDE. A área é abstrata, não anatômica. A concentração é adimensional. O acoplamento é unidirecional.

## Limites
O cliente Android não calcula as equações localmente: os resultados vêm do backend. Sem o serviço Spring Boot ativo no endereço configurado, a tela exibirá um erro de conexão. O modelo continua sendo um protótipo matemático educacional, sem validação clínica.
