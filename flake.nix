{
  inputs = {
    flake-parts.url = "github:hercules-ci/flake-parts";
    nixpkgs.url = "github:abbradar/nixpkgs/stable";
  };

  outputs = inputs @ {flake-parts, ...}:
    flake-parts.lib.mkFlake {inherit inputs;} {
      systems = ["x86_64-linux"];
      perSystem = {
        config,
        self',
        inputs',
        pkgs,
        system,
        ...
      }: {
        formatter = pkgs.alejandra;
        devShells.default = pkgs.mkShell {
          nativeBuildInputs = [pkgs.clojure pkgs.leiningen pkgs.nodejs];

          LD_LIBRARY_PATH = pkgs.lib.makeLibraryPath [pkgs.xorg.libX11 pkgs.xorg.libXext];
        };
      };
    };
}
