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

/**
 * Nen cua L65 {@code POST /api/v1/admin/jobs/{jobName}/run} — chay mot job bang tay va ghi
 * {@code trigger_type = MANUAL} (p8 §8.4.12 muc (f), p4 §K3).
 *
 * <p><b>Ba trang thai cua mot {@code jobName}, va day la ly do lop nay ton tai thay vi mot
 * {@code Map} trong controller:</b></p>
 * <ol>
 *   <li><b>Khong co trong danh muc</b> ({@link JobProperties#scheduledJobs()}) — ten job sai
 *       hoac job cua p12 chua duoc viet. {@link #findRunnable} tra {@link Optional#empty()} va
 *       controller tra {@code 404}.</li>
 *   <li><b>Co trong danh muc nhung chua co {@link ManualJobTrigger}</b> — job ton tai va chay
 *       theo lich binh thuong, nhung than cua no nam o module ngoai vung sua cua dot nay. Day
 *       la trang thai <b>khac</b> (1) va phai tra ma khac: nguoi van hanh can biet "job nay co
 *       that, chi la chua bam duoc" chu khong phai "khong co job nao ten vay".</li>
 *   <li><b>Co trigger</b> — chay.</li>
 * </ol>
 *
 * <p><b>Vi sao phai doi chieu voi danh muc chu khong chi tra cuu {@code ManualJobTrigger}:</b>
 * khong doi chieu thi mot ten go sai va mot job chua dang ky tra ve cung mot loi, va nguoi
 * truc se di tim bug o cho khong co bug.</p>
 */
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
        // LinkedHashMap sap theo ten: danh sach tra ve cho UI on dinh giua hai lan khoi dong,
        // vi thu tu inject cua Spring thi khong.
        this.triggers = triggers.stream()
                .sorted(Comparator.comparing(ManualJobTrigger::jobName))
                .collect(Collectors.toMap(ManualJobTrigger::jobName, Function.identity(),
                        (first, second) -> {
                            // Hai bean cung jobName nghia la hai than job tranh nhau mot dong
                            // job_run. Nem luc khoi dong thay vi de admin bam roi doan.
                            throw new IllegalStateException(
                                    "Hai ManualJobTrigger cung jobName: " + first.jobName());
                        },
                        LinkedHashMap::new));
    }

    /**
     * Cong tac tong {@code catcheck.jobs.enabled}.
     *
     * <p>Phai kiem <b>truoc</b> khi goi {@link #run}: {@link JobRunner#run} co y khong ghi dong
     * {@code job_run} nao khi cong tac tat (de {@code JobHeartbeatCheckJob} khong bao job khoe
     * manh trong luc no bi tat chu y), nen mot lan admin bam se tra ve "thanh cong, 0 item" ma
     * khong he chay va khong he de lai dau vet. Do la cau tra loi sai cho nguoi dang bam nut.</p>
     */
    public boolean jobsEnabled() {
        return jobProperties.enabled();
    }

    /** Ten job co trong danh muc p12 §12.6 cua ban build nay. */
    public boolean isKnownJob(String jobName) {
        return jobName != null && jobProperties.scheduledJobs().stream()
                .map(JobDescriptor::name)
                .anyMatch(jobName::equals);
    }

    /** Job vua co trong danh muc, vua co than chay duoc bang tay. */
    public Optional<ManualJobTrigger> findRunnable(String jobName) {
        return isKnownJob(jobName) ? Optional.ofNullable(triggers.get(jobName)) : Optional.empty();
    }

    /** Ten moi job hien bam chay duoc — dung cho {@code params} cua ma loi va cho UI. */
    public List<String> runnableJobNames() {
        return triggers.keySet().stream().filter(this::isKnownJob).toList();
    }

    /**
     * Chay job qua {@link JobRunner} voi {@code trigger_type = MANUAL}.
     *
     * <p>Khong bat ngoai le o day: {@link JobRunner#run} da bat moi {@code RuntimeException} cua
     * than job va dong dong {@code job_run} thanh {@code FAILED}, nen L65 luon tra ve mot
     * {@link JobOutcome} that — ke ca khi job hong — va admin doc duoc ly do ngay trong response
     * thay vi phai mo man L64.</p>
     */
    public JobOutcome run(ManualJobTrigger trigger, boolean dryRun) {
        return jobRunner.run(trigger.jobName(), JobTriggerType.MANUAL, dryRun, trigger::runOnce);
    }
}
