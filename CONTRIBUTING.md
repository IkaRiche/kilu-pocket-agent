# Contributing to KiLu Pocket Agent

We welcome all contributions! This guide will help you set up the project locally and submit your changes.

## Development Setup

1. **Clone the repository**:
   ```bash
   git clone https://github.com/IkaRiche/kilu-pocket-agent.git
   ```
2. **Open in Android Studio**:
   Use Android Studio Hedgehog (2023.1.1) or newer.
3. **Java Version**:
   Ensure you have JDK 17 configured for the project (`File > Project Structure > SDK Location`).
4. **Environment Defaults**:
   By default, you compile the `dev` flavor. This connects safely to the local `http://10.0.2.2:8788` Control Plane instance.

## Code Standards
- We enforce strict **Jetpack Compose** guidelines (no side-effects inside `@Composable` bodies, use `remember` heavily for state).
- Favor the MVVM paradigm. ViewModels should not contain `android.content.Context`.

## Pull Request Process
1. Fork the repo and create your branch from `main`.
2. Write clear, descriptive commit messages.
3. Ensure the project compiles natively: `./gradlew clean assembleDevDebug`.
4. Submit a Pull Request and wait for CI checks to pass.
