package com.catcheck.admin.domain.port;

import com.catcheck.admin.domain.JobRunRow;
import com.catcheck.admin.domain.OutboxRow;

import java.util.List;

public interface OpsQueryPort {

    List<JobRunRow> findJobRuns(String jobName, String status, int offset, int limit);

    long countJobRuns(String jobName, String status);

    List<OutboxRow> findOutbox(String channel, String status, int offset, int limit);

    long countOutbox(String channel, String status);
}
