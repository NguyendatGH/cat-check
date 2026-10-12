package com.catcheck.shared.job;

public interface ManualJobTrigger {

    String jobName();

    JobOutcome runOnce(JobContext context);
}
