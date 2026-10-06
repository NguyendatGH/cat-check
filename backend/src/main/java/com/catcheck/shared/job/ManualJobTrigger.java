package com.catcheck.shared.job;

/**
 * Hop dong de mot job nen chay duoc bang tay qua L65
 * {@code POST /api/v1/admin/jobs/{jobName}/run} (p8 §8.4.12 muc (f), p14 §14.2.2 o Q26).
 *
 * <p><b>Vi sao mot interface chu khong phai mot bang dieu phoi trong module {@code admin}:</b>
 * 29 job cua p12 §12.6 nam rai o {@code credit}, {@code reminder}, {@code notification},
 * {@code privacy}, {@code shared}. Module {@code admin} khai
 * {@code allowedDependencies = {"shared", "audit::api", "notification::api"}} nen khong the —
 * va khong nen — import than cua tung job. Dat hop dong o {@code shared.job} (noi
 * {@link JobRunner} da song, va moi module da duoc phep phu thuoc) roi de tung job tu dang ky
 * la cach duy nhat noi hai dau ma khong pha ranh gioi Modulith.</p>
 *
 * <p><b>Cai dat KHONG duoc tu goi {@link JobRunner}.</b> {@code ManualJobLauncher} se boc lai
 * loi goi nay trong {@code jobRunner.run(..., JobTriggerType.MANUAL, ...)}; neu than job tu mo
 * mot dong {@code job_run} nua thi mot lan admin bam se de lai HAI dong, va p12 §12.6.1 quy tac
 * 4 chot "dung mot dong moi lan chay". Vi vay cai dat nen tra ve than job
 * ({@code dispatch(context)}) chu khong phai phuong thuc {@code run()} co
 * {@code @Scheduled}.</p>
 *
 * <p><b>Idempotency la trach nhiem cua than job, khong phai cua L65.</b> p12 §12.6.1 quy tac 3c
 * da doi moi job co dieu kien loc idempotent de lan chay thu hai khong tao tac dung phu — dung
 * tinh chat do la cai khien "admin bam chay lai" an toan.</p>
 */
public interface ManualJobTrigger {

    /**
     * Dung ten job o p12 §12.6, khong viet tat — khop {@code job_run.job_name} va khop
     * {@code {jobName}} trong URL cua L65.
     */
    String jobName();

    /**
     * Than job cho mot lan chay do admin kich hoat.
     *
     * @param context ngu canh lan chay hien tai; {@link JobContext#dryRun()} do admin chon va
     *                than job <b>phai</b> ton trong (p15 REQ-RET-01)
     * @return so dem de {@link JobRunner} dong dong {@code job_run}
     */
    JobOutcome runOnce(JobContext context);
}
