# praetor-judge — sandbox image submissions compile/run inside.
# Backend launches this per submission via `docker run` (docker.sock), always with:
#   --rm --network none --memory <MB> --cpus <n> --pids-limit <n>
#   -v praetor_work:/judge          # shares the per-run work dir with the backend
# Build once (the image must exist on the HOST daemon, not via docker-compose):
#   docker build -t praetor-judge:latest judge/
FROM gcc:14

# GNU `time` = /usr/bin/time (needed for -v: wall-clock + peak RSS %M). `timeout` is in
# coreutils (already present in the base image).
RUN apt-get update && apt-get install -y --no-install-recommends time \
 && rm -rf /var/lib/apt/lists/*

# Unprivileged user; submissions never run as root inside the sandbox.
RUN useradd -m -u 1000 judge
USER judge
WORKDIR /work

# No ENTRYPOINT: the backend passes the compile+run command per invocation.
# (PR-2b adds python3 here for multi-language support.)
