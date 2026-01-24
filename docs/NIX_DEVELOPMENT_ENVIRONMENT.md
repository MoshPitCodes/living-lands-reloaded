# Nix Development Environment Setup

## Overview

This project uses **Nix Flakes** to provide a reproducible, declarative development environment. All dependencies (Java, Kotlin, Gradle, etc.) are pinned and versioned automatically.

## Prerequisites

### System Requirements
- **Nix** version 2.18+ with Flakes enabled
- **Nixpkgs** access (for pulling packages)
- **Linux** or **NixOS** environment

### Check Installation

```bash
# Check Nix version
nix --version

# Check if Flakes are enabled
nix flake --help

# Check Java availability
java -version
```

## Installation (if Nix is not available)

### 1. Install Nix (Single-user setup)

For Linux (not NixOS):

```bash
# Download and run the official installer
curl -L https://nixos.org/nix/install | sh

# Source the Nix configuration
. ~/.nix-profile/etc/profile.d/nix.sh
```

### 2. Enable Nix Flakes

Add to `~/.config/nix/nix.conf` (create if doesn't exist):

```text
experimental-features = nix-command flakes
```

Restart your shell after adding the configuration.

## Using the Development Environment

### Option A: Using `nix develop` (Recommended)

Enter the development environment:

```bash
nix develop
```

You'll see a banner confirming the environment is loaded with versions of Java, Kotlin, and Gradle.

Build the project within the environment:

```bash
./gradlew build
# OR
./gradlew shadowJar
```

Exit the environment by simply closing the terminal or typing `exit`.

### Option B: Using Direnv (Automatic)

1. **Install direnv** (optional but recommended):

   For NixOS:
   ```nix
   environment.systemPackages = [ pkgs.direnv ];
   ```

   For Linux:
   ```bash
   # Ubuntu/Debian
   sudo apt install direnv

   # Fedora/RHEL
   sudo dnf install direnv

   # Arch Linux
   sudo pacman -S direnv
   ```

2. **Setup direnv hook** in your shell:

   For Bash: add this to `~/.bashrc`
   ```bash
   eval "$(direnv hook bash)"
   ```

   For Zsh: add this to `~/.zshrc`
   ```bash
   eval "$(direnv hook zsh)"
   ```

   For Fish: add this to `~/.config/fish/config.fish`
   ```bash
   direnv hook fish | source
   ```

3. **Restart your shell or run:**

   ```bash
   # Reload shell configuration
   source ~/.bashrc  # or ~/.zshrc
   ```

4. **Allow the project's .envrc:**

   ```bash
   cd /path/to/hytale-livinglands
   direnv allow
   ```

Now the environment loads automatically when you enter the project directory!

## Available Tools in Environment

The provided environment includes:

| Tool | Version | Purpose |
|------|---------|---------|
| **Java** | 21 (auto-downloads 25 for build) | Java runtime and JDK |
| **Kotlin** | 1.9+ | Kotlin compiler |
| **Gradle** | Latest via wrapper | Build tool (also uses project wrapper) |
| **ktfmt** | Latest | Kotlin code formatter |
| **ktlint** | Latest | Kotlin linter |
| **SQLite** | Latest | Database for testing |
| **git** | Latest | Version control |
| **jq** | Latest | JSON processor |

## Common Workflows

### Building the Project

```bash
# Clean build
./gradlew clean build

# Build shadow JAR (includes dependencies)
./gradlew shadowJar

# Output: build/libs/livinglands-{version}.jar
```

### Running Tests

```bash
./gradlew test
```

### Code Formatting

```bash
# Format Kotlin code
ktfmt -i src/

# Check Kotlin style
ktlint src/
```

### Update Nix Dependencies

```bash
# Update flake inputs (nixpkgs, etc.)
nix flake update

# Verify the updated configuration
nix flake check
```

### Format Nix Code

```bash
# Format flake.nix with alejandra
nix fmt
```

## Java Version Note

**Important:** This project requires **Java 25** for building (as specified in `build.gradle.kts`), but **Java 21** is currently provided in the Nix environment because Java 25 is not yet available in nixpkgs as of 2026-01-24.

**How it works:**
- The environment provides Java 21 for general use
- Gradle's Java toolchain configuration in `build.gradle.kts` auto-downloads JDK 25 when needed
- This ensures reproducibility while meeting the Java 25 requirement

**Verification:**
```bash
# Check Java version in environment
java -version
# Should show: openjdk version "21.x.x"

# Let Gradle verify its toolchain
./gradlew --version
# Will show Java toolchain: 25
```

## Customizing the Environment

### Adding Dependencies

Edit `flake.nix` and add packages to `buildDeps` or `runtimeDeps`:

```nix
buildDeps = with pkgs; [
  gradle
  jdk
  kotlin
  your-package  # Add here
];
```

Then run:
```bash
nix flake check
```

### Changing Java Version

To use a different Java provider, modify the `jdk` variable:

```nix
# Use OpenJDK instead of Temurin
jdk = pkgs.openjdk21;

# Use a specific version
jdk = pkgs.temurin-bin-17;
```

## Troubleshooting

### Issue: `nix: command not found`

**Solution:** Install Nix following the installation steps above.

### Issue: Flakes not enabled

**Error:** `error: experimental feature 'flakes' is disabled`

**Solution:** Add to `~/.config/nix/nix.conf`:
```text
experimental-features = nix-command flakes
```

### Issue: Direnv not loading automatically

**Solution:**
1. Verify direnv is installed: `direnv --version`
2. Verify hook is in your shell config (`~/.bashrc` or `~/.zshrc`)
3. Restart your shell
4. Run `direnv allow` in the project directory

### Issue: Java version mismatch

**Symptom:** Build fails with Java version error

**Solution:** This is expected - Gradle will auto-download JDK 25. The environment provides Java 21 for compatibility. Check your `build.gradle.kts` toolchain settings.

### Issue: Changes to flake.nix not reflected

**Solution:**
```bash
# Reload the Nix environment
nix develop --recreate
```

### Issue: Build cache issues (Nix)

**Solution:**
```bash
# Clear Nix store cache for this derivation
nix-store --gc

# Or use nix-direnv if using direnv
nix-collect-garbage -d
```

## CI/CD Integration

To use this environment in GitHub Actions, add:

```yaml
jobs:
  build:
    runs-on: ubuntu-latest
    steps:
      - uses: actions/checkout@v4
      - uses: cachix/install-nix-action@v25
        with:
          extra_nix_config: |
            experimental-features = nix-command flakes
      - name: Build
        run: nix develop -c ./gradlew build
```

## Additional Resources

- **Nix Documentation:** https://nixos.org/manual/nix/stable/
- **Flake Documentation:** https://nixos.wiki/wiki/Flakes
- **Nixpkgs Search:** https://search.nixos.org/packages
- **Direnv Documentation:** https://direnv.net/

## Support

If you encounter issues with the Nix environment:

1. Check `docs/NIX_SETUP_GUIDE.md` for common solutions
2. Verify Nix version: `nix --version` (should be 2.18+)
3. Check environment: `nix flake check`
4. File an issue in the project repository

---

**Last Updated:** 2026-01-24
**Nix Version Tested:** 2.31.3