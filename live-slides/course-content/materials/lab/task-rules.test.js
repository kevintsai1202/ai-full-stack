import test from 'node:test';
import assert from 'node:assert/strict';
import {
  canCompleteTask,
  completeTask,
  completionRatio,
  getNextTask,
  getTaskView,
} from './task-rules.js';

const tasks = [
  { id: 1, title: '定義任務資料', priority: 1, createdAt: '2026-08-09T09:00:00+08:00', status: 'TODO', dependsOnTaskId: null },
  { id: 2, title: '建立阻塞狀態呈現', priority: 1, createdAt: '2026-08-09T09:01:00+08:00', status: 'TODO', dependsOnTaskId: 1 },
];

test('沒有前置任務時可以完成', () => {
  assert.equal(canCompleteTask(tasks[0], tasks), true);
});

test('前置任務未完成時顯示 BLOCKED 且不可完成', () => {
  const view = getTaskView(tasks[1], tasks);
  assert.equal(view.displayStatus, 'BLOCKED');
  assert.equal(view.canComplete, false);
});

test('完成前置任務後後續任務解除 BLOCKED', () => {
  const updated = completeTask(tasks, 1);
  const view = getTaskView(updated[1], updated);
  assert.equal(view.displayStatus, 'TODO');
  assert.equal(view.canComplete, true);
});

test('下一步不會推薦被阻塞任務', () => {
  assert.equal(getNextTask(tasks).id, 1);
});

test('完成比例由規則層計算', () => {
  assert.equal(completionRatio(completeTask(tasks, 1)), 0.5);
});

test('依賴不存在時要明確失敗', () => {
  assert.throws(
    () => canCompleteTask({ ...tasks[1], dependsOnTaskId: 999 }, tasks),
    /找不到任務 999 的前置任務/
  );
});
