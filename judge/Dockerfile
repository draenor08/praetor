# praetor-judge — sandbox image submissions compile/run inside.
# Backend launches this per submission via `docker run` (docker.sock),
# always with: --rm --network none --memory <MB> --cpus <n> --pids-limit <n>.
# Build once: docker build -t praetor-judge:latest judge/
FROM gcc:14

# Unprivileged user; submissions never run as root inside the sandbox.
RUN useradd -m -u 1000 judge
USER judge
WORKDIR /work

# No ENTRYPOINT: the backend passes the compile+run command per invocation.
