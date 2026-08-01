package com.codingshuttle.promptic.workspace_service.service.impl;

import com.codingshuttle.promptic.workspace_service.dto.project.DeployResponse;
import com.codingshuttle.promptic.workspace_service.service.DeploymentService;
import io.fabric8.kubernetes.api.model.Pod;
import io.fabric8.kubernetes.client.KubernetesClient;
import io.fabric8.kubernetes.client.dsl.ExecWatch;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.TimeUnit;

@Service
@RequiredArgsConstructor
@Slf4j
public class KubernetesDeploymentServiceImpl implements DeploymentService {

    private final KubernetesClient client;
    private final StringRedisTemplate redisTemplate;

    @Value("${app.preview.namespace}")
    private String namespace;

    @Value("${app.preview.domain}")
    private String baseDomain;

    @Value("${app.preview.proxy-port}")
    private String proxyPort;

    private static final String POOL_LABEL = "status";
    private static final String PROJECT_LABEL = "project-id";
    private static final String IDLE = "idle";
    private static final String BUSY = "busy";
    private static final String CLAIM_KEY_PREFIX = "preview-runner-claim:";
    private static final long CLAIM_TTL_SECONDS = 60;
    private static final int STREAM_TAIL_LIMIT = 2000;
    private static final long INSTALL_TIMEOUT_SECONDS = 300;

    public DeployResponse deploy(Long projectId) {
        // Dynamically build the domain: project-123.app.domain.com
        String domain = "project-" + projectId + "." + baseDomain;

        // Use default port 80 format logic for clean URLs, or explicit ports for local testing
        String formattedUrl = proxyPort.equals("80")
                ? "https://" + domain
                : "https://" + domain + ":" + proxyPort;

        Pod existingPod = findActivePod(projectId);

        if (existingPod != null) {
            String podName = existingPod.getMetadata().getName();
            if (!isPreviewServerReady(podName)) {
                log.warn("Preview process is not running in pod {} for project {}. Restarting it.", podName, projectId);
                startPreviewServer(podName);
            }
            log.info("Found healthy pod {} for project {}. Resuming...", podName, projectId);
            registerRoute(domain, existingPod);
            return new DeployResponse(formattedUrl);
        }

        return claimAndStartNewPod(projectId, domain, formattedUrl);
    }

    @Override
    public void release(Long projectId) {
        String domain = "project-" + projectId + "." + baseDomain;

        try {
            redisTemplate.delete("route:" + domain);
            var deletedPods = client.pods().inNamespace(namespace)
                    .withLabel(PROJECT_LABEL, projectId.toString())
                    .delete();
            log.info("Released {} preview pod(s) and route for deleted project {}", deletedPods.size(), projectId);
        } catch (Exception e) {
            // The project has already been soft-deleted. Do not expose it again if
            // best-effort infrastructure cleanup is temporarily unavailable.
            log.error("Failed to release preview resources for deleted project {}", projectId, e);
        }
    }

    private Pod findActivePod(Long projectId) {
        return client.pods().inNamespace(namespace)
                .withLabel(PROJECT_LABEL, projectId.toString())
                .withLabel(POOL_LABEL, BUSY)
                .list().getItems().stream()
                .filter(pod -> pod.getStatus() != null && "Running".equals(pod.getStatus().getPhase()))
                .findFirst()
                .orElse(null);
    }

    private DeployResponse claimAndStartNewPod(Long projectId, String domain, String formattedUrl) {
        Pod pod = claimIdlePod(projectId);

        String podName = pod.getMetadata().getName();
        log.info("Claiming pod {} for project {}", podName, projectId);

        try {
            client.pods().inNamespace(namespace).withName(podName).edit(p -> {
                p.getMetadata().getLabels().put(POOL_LABEL, BUSY);
                p.getMetadata().getLabels().put(PROJECT_LABEL, projectId.toString());
                return p;
            });
            redisTemplate.delete(CLAIM_KEY_PREFIX + podName);

            String initialSyncCmd = String.format("rm -rf /app/* && mc mirror --overwrite myminio/projects/%d/ /app/", projectId);
            execCommand(podName, "syncer", "sh", "-c", initialSyncCmd);

            String watchCmd = String.format("nohup mc mirror --overwrite --watch myminio/projects/%d/ /app/ > /app/sync.log 2>&1 &", projectId);
            execCommand(podName, "syncer", "sh", "-c", watchCmd);

            startPreviewServer(podName);

            Pod updatedPod = client.pods().inNamespace(namespace).withName(podName).get();
            registerRoute(domain, updatedPod);

            log.info("Deployment successful: {}", formattedUrl);
            return new DeployResponse(formattedUrl);

        } catch (Exception e) {
            log.error("Deployment failed for project {}. Releasing pod {}.", projectId, podName, e);
            client.pods().inNamespace(namespace).withName(podName).delete();
            redisTemplate.delete(CLAIM_KEY_PREFIX + podName);
            throw new RuntimeException("Failed to deploy project " + projectId + ": " + e.getMessage(), e);
        }
    }

    /**
     * A Deployment request can reach different workspace-service replicas at the
     * same time. A Redis lease makes selecting a pool pod atomic across replicas
     * so two projects can never initialise the same shared workspace.
     */
    private Pod claimIdlePod(Long projectId) {
        for (Pod candidate : client.pods().inNamespace(namespace).withLabel(POOL_LABEL, IDLE).list().getItems()) {
            String podName = candidate.getMetadata().getName();
            Boolean claimed = redisTemplate.opsForValue().setIfAbsent(
                    CLAIM_KEY_PREFIX + podName, projectId.toString(), CLAIM_TTL_SECONDS, TimeUnit.SECONDS);
            if (!Boolean.TRUE.equals(claimed)) {
                continue;
            }

            Pod current = client.pods().inNamespace(namespace).withName(podName).get();
            boolean stillIdle = current != null
                    && IDLE.equals(current.getMetadata().getLabels().get(POOL_LABEL));
            if (stillIdle) {
                return current;
            }
            redisTemplate.delete(CLAIM_KEY_PREFIX + podName);
        }
        throw new RuntimeException("No idle runners available. Please scale up the runner-pool.");
    }

    private void registerRoute(String domain, Pod pod) {
        String podIp = pod.getStatus().getPodIP();
        if (podIp == null) throw new RuntimeException("Pod is running but has no IP!");

        redisTemplate.opsForValue().set("route:" + domain, podIp + ":5173", 6, TimeUnit.HOURS);
        log.info("Route Registered: {} -> {}", domain, podIp);
    }

    private void startPreviewServer(String podName) {
        // Never copy a prebuilt node_modules directory into a generated project:
        // it can be incompatible with that project's package.json and npm ci
        // deletes it anyway. The runner image retains npm's download cache, so
        // --prefer-offline still speeds up installations without corrupting deps.
        String installCmd = "if [ -f package-lock.json ]; then "
                + "npm ci --prefer-offline --no-audit --no-fund; "
                + "else npm install --prefer-offline --no-audit --no-fund; fi";
        execCommand(podName, "runner", INSTALL_TIMEOUT_SECONDS, "sh", "-c", installCmd);

        String startCmd = "nohup npm run dev -- --host 0.0.0.0 --port 5173 --strictPort "
                + "> /app/dev.log 2>&1 &";
        execCommand(podName, "runner", "sh", "-c", startCmd);
        waitForPreviewServer(podName);
    }

    private boolean isPreviewServerReady(String podName) {
        try {
            execCommand(podName, "runner", "sh", "-c",
                    "wget -q -T 2 -O /dev/null http://127.0.0.1:5173");
            return true;
        } catch (RuntimeException ignored) {
            return false;
        }
    }

    /** Wait until Vite is accepting requests before reporting a successful deployment. */
    private void waitForPreviewServer(String podName) {
        String readinessCommand = "for i in $(seq 1 90); do "
                + "wget -q -T 2 -O /dev/null http://127.0.0.1:5173 && exit 0; "
                + "sleep 1; "
                + "done; exit 1";
        execCommand(podName, "runner", 120, "sh", "-c", readinessCommand);
    }

    private void execCommand(String podName, String container, String... command) {
        execCommand(podName, container, 30, command);
    }

    /**
     * Runs a command to completion inside a pool pod and fails loudly if it did not
     * succeed.
     *
     * <p>Success is determined from {@link ExecWatch#exitCode()} — the process exit
     * status — and not from the listener close code, which carries the RFC-6455
     * WebSocket status (1000 on every clean stream close, success or failure) and so
     * cannot distinguish a working command from a broken one.
     *
     * <p>Background commands must be detached by the caller; every command reaching
     * this method is expected to terminate on its own.
     */
    private void execCommand(String podName, String container, long timeoutSeconds, String... command) {
        log.debug("Exec in {}:{} -> {}", podName, container, String.join(" ", command));

        ByteArrayOutputStream out = new ByteArrayOutputStream();
        ByteArrayOutputStream err = new ByteArrayOutputStream();

        try (ExecWatch watch = client.pods().inNamespace(namespace).withName(podName)
                .inContainer(container)
                .writingOutput(out)
                .writingError(err)
                .exec(command)) {

            // null: the stream closed before a status arrived. -1: the status held no
            // usable code. Neither can be read as a successful run.
            Integer exitCode = watch.exitCode().get(timeoutSeconds, TimeUnit.SECONDS);
            if (exitCode == null || exitCode != 0) {
                throw new IllegalStateException(
                        "Command exited with status " + exitCode + " in " + podName + ":" + container
                                + " -> " + String.join(" ", command) + describeStreams(out, err));
            }

        } catch (Exception e) {
            log.error("Exec failed in {}:{} -> {}{}",
                    podName, container, String.join(" ", command), describeStreams(out, err), e);
            throw new RuntimeException("Pod execution failed in " + container + ": " + e.getMessage(), e);
        }
    }

    /** Surface pod stdout/stderr so a failure names its cause instead of just a code. */
    private String describeStreams(ByteArrayOutputStream out, ByteArrayOutputStream err) {
        String stdout = tail(out.toString(StandardCharsets.UTF_8));
        String stderr = tail(err.toString(StandardCharsets.UTF_8));
        StringBuilder sb = new StringBuilder();
        if (!stderr.isBlank()) sb.append(" | stderr: ").append(stderr);
        if (!stdout.isBlank()) sb.append(" | stdout: ").append(stdout);
        return sb.toString();
    }

    private String tail(String value) {
        String trimmed = value.strip();
        return trimmed.length() > STREAM_TAIL_LIMIT
                ? trimmed.substring(trimmed.length() - STREAM_TAIL_LIMIT)
                : trimmed;
    }


}
