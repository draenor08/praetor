package com.praetor.submission.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

/**
 * Logs the resolved {@link JudgeProperties} once at startup — proof the
 * {@code SANDBOX_*}/{@code JUDGE_WORKERS} env actually reached the running container.
 * No secrets in judge config, so this is safe to log (contrast: never log the JWT secret).
 */
@Component
public class JudgeConfigLogger implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(JudgeConfigLogger.class);

    private final JudgeProperties props;

    public JudgeConfigLogger(JudgeProperties props) {
        this.props = props;
    }

    @Override
    public void run(ApplicationArguments args) {
        log.info("Judge config: image={} cpuSeconds={} memMb={} pidsMax={} workers={}",
                props.image(), props.cpuSeconds(), props.memMb(), props.pidsMax(), props.workers());
    }
}
