{
  description = "Living Lands - Hytale Server Mod Development Environment";

  inputs = {
    nixpkgs.url = "github:NixOS/nixpkgs/nixos-unstable";
    flake-utils.url = "github:numtide/flake-utils";
  };

  outputs =
    {
      nixpkgs,
      flake-utils,
    }:
    flake-utils.lib.eachDefaultSystem (
      system:
      let
        pkgs = nixpkgs.legacyPackages.${system};

        # Use Java 25 (available in nixos-unstable)
        jdk = pkgs.jdk25 or pkgs.openjdk25;

        # Common build dependencies
        buildDeps = with pkgs; [
          jdk
          gradle_9 # Gradle 9.3.0
        ];

        # Runtime and test dependencies
        runtimeDeps = with pkgs; [
          sqlite
        ];
      in
      {
        # Development shell
        devShells.default = pkgs.mkShell {
          buildInputs =
            buildDeps
            ++ runtimeDeps
            ++ (with pkgs; [
              # Development tools
              ktfmt
              ktlint

              # Useful utilities
              git
              jq
            ]);

          # Environment variables
          env = {
            JAVA_HOME = "${jdk}";
            JAVA_VERSION = "25";
            GRADLE_OPTS = "-Dorg.gradle.java.home=${jdk}";
          };

          # Shell hook for setup messages
          shellHook = ''
            echo "Living Lands Development Environment"
            echo "===================================="
            echo "Java:    $(java -version 2>&1 | head -1)"
            echo "Kotlin:  $(kotlin -version 2>&1 | head -1)"
            echo "Gradle:  $(gradle --version | head -3 | tail -1)"
            echo ""
            echo "Available commands:"
            echo "  ./gradlew build          - Build the project"
            echo "  ./gradlew clean          - Clean build artifacts"
            echo "  ./gradlew test           - Run tests"
            echo "  ./gradlew shadowJar      - Build packaged JAR"
            echo "  nix fmt                  - Format flake.nix"
            echo "  nix flake check          - Verify flake configuration"
            echo ""
            echo "Note: Java 25 is required for build but currently using Java 21"
            echo "The Gradle toolchain will auto-download JDK 25 when needed"
            echo ""
          '';
        };

        # Formatter for Nix code
        formatter = pkgs.alejandra;

        # Checks include building the shell and formatting
        checks = {
          devshell = pkgs.mkShell { buildInputs = buildDeps; };
        };
      }
    );
}
