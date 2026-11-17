{ pkgs, ... }:

{
  # Frontend (React, Vite, Vitest): Node 22 matches CI (ci.yml) and the gateway Dockerfile.
  languages.javascript = {
    enable = true;
    package = pkgs.nodejs_22;
    npm.enable = true;
  };

  # Services (Spring Boot): Java 21 matches the Temurin 21 images in the Dockerfiles.
  # Each service ships its own Maven wrapper (./mvnw), so no system Maven is needed.
  languages.java = {
    enable = true;
    jdk.package = pkgs.jdk21;
  };
}
