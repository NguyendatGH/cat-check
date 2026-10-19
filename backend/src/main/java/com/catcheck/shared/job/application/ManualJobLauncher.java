package com.catcheck.shared.job.application;

import com.catcheck.shared.job.JobDescriptor;
import com.catcheck.shared.job.JobOutcome;
import com.catcheck.shared.job.JobProperties;
import com.catcheck.shared.job.JobTriggerType;
import com.catcheck.shared.job.ManualJobTrigger;
import org.springframework.stereotype.Service;

import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
public class ManualJobLauncher {

    private final JobRunner jobRunner;
    private final JobProperties jobProperties;
    private final Map<String, ManualJobTrigger> triggers;

    public ManualJobLauncher(JobRunner jobRunner,
                             JobProperties jobProperties,
                             List<ManualJobTrigger> triggers) {
        this.jobRunner = jobRunner;
        this.jobProperties = jobProperties;
        this.triggers = triggers.stream()
                .sorted(Comparator.comparing(ManualJobTrigger::jobName))
                .collect(Collectors.toMap(ManualJobTrigger::jobName, Function.identity(),
                        (first, second) -> {
                            throw new IllegalStateException(
                                    "Hai ManualJobTrigger cung jobName: " + first.jobName());
                        },
                        LinkedHashMap::new));
    }

    public boolean jobsEnabled() {
        return jobProperties.enabled();
    }

    public boolean isKnownJob(String jobName) {
        return jobName != null && jobProperties.scheduledJobs().stream()
                .map(JobDescriptor::name)
                .anyMatch(jobName::equals);
    }

    public Optional<ManualJobTrigger> findRunnable(String jobName) {
        return isKnownJob(jobName) ? Optional.ofNullable(triggers.get(jobName)) : Optional.empty();
    }

    public List<String> runnableJobNames() {
        return triggers.keySet().stream().filter(this::isKnownJob).toList();
    }

    public JobOutcome run(ManualJobTrigger trigger, boolean dryRun) {
        return jobRunner.run(trigger.jobName(), JobTriggerType.MANUAL, dryRun, trigger::runOnce);
    }
}
