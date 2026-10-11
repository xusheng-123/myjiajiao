package com.nurture.shared.storage;

/** Owner-only retained storage; removing a UI item does not reclaim audit storage. */
public record StorageQuotaView(long storedBytes,long limitBytes,long maxFileBytes,long remainingBytes,
                               long removedRetainedBytes,boolean nearLimit,String retentionNotice) {
 public static StorageQuotaView of(long stored,long limit,long max,long removed) {
  return new StorageQuotaView(stored,limit,max,Math.max(0,limit-stored),removed,stored>=limit*4/5,
      "课程、作业、答疑、工单证据及已发咨询附件在有引用时持续保留；逻辑移除仍计容量，当前不自动到期删除。容量不足请联系运营核查。");
 }
}
