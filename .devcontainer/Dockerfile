# syntax=docker/dockerfile:1
# check=skip=SecretsUsedInArgOrEnv

FROM eclipse-temurin:21

ENV CODESERVER_VERSION=4.100.2
ENV PATH=/opt/codeserver/bin:$PATH

# Fetch CodeServer
ADD https://github.com/coder/code-server/releases/download/v${CODESERVER_VERSION}/code-server-${CODESERVER_VERSION}-linux-amd64.tar.gz /tmp/code-server.tar

# Update Environment
RUN apt-get update -y && \
    apt-get -y upgrade && \
    apt-get install -y git && \
    apt upgrade -y


# Prepare codeserver dir
RUN mkdir -p /opt/codeserver


# Install CodeServer and the extension: Rust Analyzer
RUN tar -C /opt/codeserver -xf /tmp/code-server.tar --strip-components 1
RUN code-server --install-extension vscjava.vscode-java-pack

# CleanUp Environment
RUN apt-get autoclean && \
    apt-get autoremove && \
    rm -rf /tmp/* /var/lib/apt/lists/* /var/tmp/*


EXPOSE 8080
WORKDIR /data
ENTRYPOINT [ "code-server", "--auth", "none", "--bind-addr", "0.0.0.0:8080", "--disable-telemetry", "."]