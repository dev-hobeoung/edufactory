package com.hobeoung.edufactory.exam;

import com.hobeoung.edufactory.material.MaterialEvents;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/** 문항 정답·배점이 바뀌면 응답을 다시 채점하고 통계를 다시 낸다 */
@Component
public class ExamEvents {

  private final ScoreService scores;

  public ExamEvents(ScoreService scores) {
    this.scores = scores;
  }

  @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
  public void onItemChanged(MaterialEvents.ItemChanged e) {
    scores.recompute(e.materialId());
  }
}
