{
  inputs = {
    nixpkgs.url = "github:nixos/nixpkgs/nixos-unstable";
  };

  outputs = {
    nixpkgs,
    flake-utils,
    ...
  }:
    flake-utils.lib.eachDefaultSystem (system: let
      pkgs = import nixpkgs {
        inherit system;
        config.allowUnfree = true;
      };
      lib = pkgs.lib;
    in {
      devShells.default = pkgs.mkShell {
        LD_LIBRARY_PATH = lib.makeLibraryPath (with pkgs; [
          gtk2
          gtk3
        ]);
        packages = with pkgs; [
          chromedriver
          google-chrome
        ];
        SE_CHROMEDRIVER = lib.getExe pkgs.chromedriver;
        CHROME_BINARY = lib.getExe pkgs.google-chrome;
      };
    });
}
