{
  description = "Android toolchain and emulator for AntennaPod";

  inputs = {
    nixpkgs.url = "github:NixOS/nixpkgs/nixos-26.05";
  };

  outputs = { self, nixpkgs }:
    let
      system = "x86_64-linux";

      pkgs = import nixpkgs {
        inherit system;
        config = {
          allowUnfree = true;
          android_sdk.accept_license = true;
        };
      };

      jdk = pkgs.jdk21;

      sdkArgs = {
        platformVersions = [ "36" ];
        buildToolsVersions = [ "36.0.0" ];
        includeEmulator = true;
        includeSystemImages = true;
        systemImageTypes = [ "google_apis" ];
        abiVersions = [ "x86_64" ];
        includeNDK = false;
      };
      androidSdk = (pkgs.androidenv.composeAndroidPackages sdkArgs).androidsdk;
      androidSdkRoot = "${androidSdk}/libexec/android-sdk";

      emulator = pkgs.androidenv.emulateApp {
        name = "antennapod-emulator";
        platformVersion = "36";
        abiVersion = "x86_64";
        systemImageType = "google_apis";
        androidUserHome = "$PWD/.android-nix";
        configOptions = {
          "hw.ramSize" = "4096";
          "hw.keyboard" = "yes";
        };
        androidEmulatorFlags = "-gpu swiftshader_indirect -no-snapshot-save -camera-back none";
        sdkExtraArgs = {
          inherit (sdkArgs) buildToolsVersions;
        };
      };

      emulatorTv = pkgs.androidenv.emulateApp {
        name = "antennapod-tv-emulator";
        platformVersion = "36";
        abiVersion = "x86_64";
        systemImageType = "android-tv";
        deviceName = "tv";
        androidUserHome = "$PWD/.android-nix";
        androidAvdFlags = "-d tv_1080p";
        configOptions = {
          "hw.ramSize" = "4096";
          "hw.keyboard" = "yes";
        };
        androidEmulatorFlags = "-gpu swiftshader_indirect -no-snapshot-save -camera-back none";
        sdkExtraArgs = {
          inherit (sdkArgs) buildToolsVersions;
        };
      };
    in
    {
      packages.${system} = {
        inherit androidSdk emulator emulatorTv;
        default = emulator;
      };


      apps.${system} = {
        emulator = {
          type = "app";
          program = "${emulator}/bin/run-test-emulator";
        };
        emulator-tv = {
          type = "app";
          program = "${emulatorTv}/bin/run-test-emulator";
        };
      };

      devShells.${system}.default = pkgs.mkShell {
        packages = [ jdk androidSdk pkgs.git ];

        JAVA_HOME = jdk.home;
        ANDROID_HOME = androidSdkRoot;
        ANDROID_SDK_ROOT = androidSdkRoot;
        GRADLE_OPTS = "-Dorg.gradle.project.android.aapt2FromMavenOverride=${androidSdkRoot}/build-tools/36.0.0/aapt2";

        shellHook = ''
          export ANDROID_USER_HOME="$PWD/.android-nix"
        '';
      };

      formatter.${system} = pkgs.nixpkgs-fmt;
    };
}
