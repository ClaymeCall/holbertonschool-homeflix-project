{ pkgs, ... }:

{
  # Frontend (React, Vite, Vitest): Node 24 matches @types/node in frontend/package.json.
  languages.javascript = {
    enable = true;
    package = pkgs.nodejs_24;
    npm.enable = true;
  };

  # Services (Spring Boot): Java 21 matches the Temurin 21 images in the Dockerfiles.
  # Each service ships its own Maven wrapper (./mvnw), so no system Maven is needed.
  languages.java = {
    enable = true;
    jdk.package = pkgs.jdk21;
  };
}
